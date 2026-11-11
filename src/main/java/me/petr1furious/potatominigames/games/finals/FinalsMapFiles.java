package me.petr1furious.potatominigames.games.finals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Import boundary: no caller-supplied path ever becomes a writable world path. */
final class FinalsMapFiles {
    static final long MAX_BYTES = 512L * 1024 * 1024;
    static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    static final int MAX_FILES = 20_000;

    static String name(String value) {
        if (value == null
                || !value.matches("[a-z0-9][a-z0-9_-]{0,31}")
                || value.equals("generated")) throw FinalsMaps.failure("name");
        return value;
    }

    static Path source(Path imports, String value) throws IOException {
        if (value == null || value.isBlank() || value.indexOf('\\') >= 0 || value.indexOf(':') >= 0)
            throw FinalsMaps.failure("source");
        Path relative;
        try {
            relative = Path.of(value);
        } catch (java.nio.file.InvalidPathException error) {
            throw FinalsMaps.failure("source");
        }
        if (relative.isAbsolute()) throw FinalsMaps.failure("source");
        for (Path part : relative)
            if (part.toString().equals("..") || part.toString().equals("."))
                throw FinalsMaps.failure("source");
        Path root = imports.toAbsolutePath().normalize();
        Path candidate = root.resolve(relative).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root))
            throw FinalsMaps.failure("source");
        rejectLinks(root, candidate);
        if (!candidate.toRealPath().startsWith(root.toRealPath()))
            throw FinalsMaps.failure("source");
        return candidate;
    }

    private static void rejectLinks(Path root, Path candidate) throws IOException {
        if (Files.isSymbolicLink(root)) throw FinalsMaps.failure("source");
        Path current = root;
        for (Path part : root.relativize(candidate)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)
                    || Files.readAttributes(
                                    current,
                                    java.nio.file.attribute.BasicFileAttributes.class,
                                    LinkOption.NOFOLLOW_LINKS)
                            .isOther()) throw FinalsMaps.failure("source");
        }
    }

    static void copyWorld(Path source, Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw FinalsMaps.failure("source");
        Files.createDirectory(target);
        copyWorldContents(source, target);
    }

    static void copyWorldContents(Path source, Path target) throws IOException {
        Budget budget = new Budget();
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
            copyDirectory(source, target, budget);
        else if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
            extract(source, target, budget);
        else throw FinalsMaps.failure("source");
        if (!Files.isRegularFile(target.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(target.resolve("region"), LinkOption.NOFOLLOW_LINKS))
            throw FinalsMaps.failure("world_format");
    }

    private static String worldEntry(String entry) {
        // Minecraft 26.3 stores the overworld beneath a shared save root. Normalize only
        // that dimension; arbitrary extra dimensions and executable datapacks stay out.
        String nativePrefix = "dimensions/minecraft/overworld/";
        String relative =
                entry.startsWith(nativePrefix) ? entry.substring(nativePrefix.length()) : entry;
        if (entry.equals("level.dat")) return entry;
        return relative.matches("region/r\\.-?[0-9]+\\.-?[0-9]+\\.mca") ? relative : null;
    }

    private static void copyDirectory(Path source, Path target, Budget budget) throws IOException {
        try (var paths = Files.walk(source)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (Files.isSymbolicLink(path)) throw FinalsMaps.failure("source");
                if (!path.toRealPath().startsWith(source.toRealPath()))
                    throw FinalsMaps.failure("source");
                budget.entry();
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    throw FinalsMaps.failure("source");
                String relative = source.relativize(path).toString().replace('\\', '/');
                String destination = worldEntry(relative);
                if (destination == null) continue;
                Path output = target.resolve(destination);
                if (Files.exists(output)) throw FinalsMaps.failure("world_format");
                Files.createDirectories(output.getParent());
                try (InputStream input = Files.newInputStream(path)) {
                    copy(input, output, budget);
                }
            }
        }
    }

    static String zipPath(String value) {
        if (value.isBlank()
                || value.startsWith("/")
                || value.indexOf('\\') >= 0
                || value.indexOf(':') >= 0
                || value.indexOf('\0') >= 0) throw FinalsMaps.failure("source");
        for (String part : value.split("/"))
            if (part.equals("..") || part.equals(".")) throw FinalsMaps.failure("source");
        return value;
    }

    private static void extract(Path source, Path target, Budget budget) throws IOException {
        rejectZipLinks(source);
        try (ZipFile zip = new ZipFile(source.toFile())) {
            String prefix = null;
            Set<String> seen = new HashSet<>();
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                budget.entry();
                String path = zipPath(entry.getName());
                if (!seen.add(path.toLowerCase(Locale.ROOT))) throw FinalsMaps.failure("source");
                if (!entry.isDirectory()
                        && (path.equals("level.dat") || path.endsWith("/level.dat"))) {
                    if (prefix != null) throw FinalsMaps.failure("world_format");
                    prefix = path.substring(0, path.length() - "level.dat".length());
                }
            }
            if (prefix == null) throw FinalsMaps.failure("world_format");
            entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String path = entry.getName();
                if (entry.isDirectory() || !path.startsWith(prefix)) continue;
                String relative = path.substring(prefix.length());
                String destination = worldEntry(relative);
                if (destination == null) continue;
                if (entry.getSize() > MAX_FILE_BYTES) throw FinalsMaps.failure("import_size");
                Path output = target.resolve(destination).normalize();
                if (Files.exists(output)) throw FinalsMaps.failure("world_format");
                if (!output.startsWith(target)) throw FinalsMaps.failure("source");
                Files.createDirectories(output.getParent());
                try (InputStream input = zip.getInputStream(entry)) {
                    copy(input, output, budget);
                }
            }
        }
    }

    /**
     * Java ZIP extraction does not materialize links; reject Unix link entries explicitly as well.
     */
    private static void rejectZipLinks(Path source) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(source.toFile(), "r")) {
            long length = file.length();
            if (length > MAX_BYTES || length < 22) throw FinalsMaps.failure("import_size");
            long eocd = -1;
            for (long p = length - 22, min = Math.max(0, length - 65_557); p >= min; p--) {
                file.seek(p);
                if (littleInt(file) == 0x06054b50L) {
                    file.seek(p + 20);
                    if (p + 22 + littleShort(file) == length) {
                        eocd = p;
                        break;
                    }
                }
            }
            if (eocd < 0) throw FinalsMaps.failure("world_format");
            file.seek(eocd + 4);
            int disk = littleShort(file),
                    centralDisk = littleShort(file),
                    diskEntries = littleShort(file),
                    count = littleShort(file);
            long size = littleInt(file), offset = littleInt(file);
            if (disk != 0
                    || centralDisk != 0
                    || diskEntries != count
                    || count > MAX_FILES
                    || size == 0xffffffffL
                    || offset == 0xffffffffL
                    || offset + size > eocd) throw FinalsMaps.failure("import_size");
            file.seek(offset);
            for (int i = 0; i < count; i++) {
                long start = file.getFilePointer();
                if (littleInt(file) != 0x02014b50L) throw FinalsMaps.failure("world_format");
                file.seek(start + 28);
                int name = littleShort(file),
                        extra = littleShort(file),
                        comment = littleShort(file);
                file.seek(start + 38);
                long external = littleInt(file);
                if (((external >>> 16) & 0170000) == 0120000) throw FinalsMaps.failure("source");
                long next = start + 46L + name + extra + comment;
                if (next > offset + size) throw FinalsMaps.failure("world_format");
                file.seek(next);
            }
        }
    }

    private static int littleShort(RandomAccessFile file) throws IOException {
        return Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
    }

    private static long littleInt(RandomAccessFile file) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
    }

    private static void copy(InputStream input, Path output, Budget budget) throws IOException {
        try (OutputStream stream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[8192];
            long size = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                budget.bytes += count;
                if (size > MAX_FILE_BYTES || budget.bytes > MAX_BYTES)
                    throw FinalsMaps.failure("import_size");
                stream.write(buffer, 0, count);
            }
        }
    }

    static void replace(Path temporary, Path destination) throws IOException {
        // A failed atomic move preserves the previous registry. No delete-and-rewrite fallback.
        for (int attempt = 1; ; attempt++) {
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (AtomicMoveNotSupportedException failure) {
                throw new IOException("Atomic replacement is unavailable", failure);
            } catch (AccessDeniedException denied) {
                // Windows readers can briefly deny replacement while releasing a file handle.
                if (attempt >= 5) throw denied;
                try {
                    Thread.sleep(25);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    IOException failure =
                            new IOException(
                                    "Interrupted while waiting to replace map data", interrupted);
                    failure.addSuppressed(denied);
                    throw failure;
                }
            }
        }
    }

    private static final class Budget {
        int files;
        long bytes;

        void entry() {
            if (++files > MAX_FILES) throw FinalsMaps.failure("import_size");
        }
    }

    private FinalsMapFiles() {}
}
