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
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
            copyDirectory(source, target);
        else if (source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip"))
            extract(source, target);
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

    private static void copyDirectory(Path source, Path target) throws IOException {
        Path realSource = source.toRealPath();
        try (var paths = Files.walk(source)) {
            var iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path path = iterator.next();
                if (Files.isSymbolicLink(path)) throw FinalsMaps.failure("source");
                if (!path.toRealPath().startsWith(realSource))
                    throw FinalsMaps.failure("source");
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
                    copy(input, output);
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

    private static void extract(Path source, Path target) throws IOException {
        rejectZipLinks(source);
        try (ZipFile zip = new ZipFile(source.toFile())) {
            String prefix = null;
            Set<String> seen = new HashSet<>();
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
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
                Path output = target.resolve(destination).normalize();
                if (Files.exists(output)) throw FinalsMaps.failure("world_format");
                if (!output.startsWith(target)) throw FinalsMaps.failure("source");
                Files.createDirectories(output.getParent());
                try (InputStream input = zip.getInputStream(entry)) {
                    copy(input, output);
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
            if (length < 22) throw FinalsMaps.failure("world_format");
            long eocd = -1;
            for (long p = length - 22, min = Math.max(0, length - 65_557); p >= min; p--) {
                file.seek(p);
                if (littleInt(file) == 0x06054b50L) {
                    file.seek(p + 20);
                    if (littleShort(file) == length - p - 22) {
                        eocd = p;
                        break;
                    }
                }
            }
            if (eocd < 0) throw FinalsMaps.failure("world_format");
            file.seek(eocd + 4);
            int disk = littleShort(file), centralDisk = littleShort(file);
            long diskEntries = littleShort(file), count = littleShort(file);
            long size = littleInt(file), offset = littleInt(file);
            long directoryLimit = eocd;
            boolean zip64 = false;
            if (eocd >= 20) {
                file.seek(eocd - 20);
                zip64 = littleInt(file) == 0x07064b50L;
            }
            if (zip64) {
                if ((disk != 0 && disk != 0xffff)
                        || (centralDisk != 0 && centralDisk != 0xffff))
                    throw FinalsMaps.failure("world_format");
                long locator = eocd - 20;
                if (littleInt(file) != 0) throw FinalsMaps.failure("world_format");
                long record = littleLong(file);
                if (littleInt(file) != 1) throw FinalsMaps.failure("world_format");
                checkedEnd(record, 56, locator);
                file.seek(record);
                if (littleInt(file) != 0x06064b50L) throw FinalsMaps.failure("world_format");
                long recordSize = littleLong(file);
                if (recordSize < 44) throw FinalsMaps.failure("world_format");
                if (checkedEnd(record + 12, recordSize, locator) != locator)
                    throw FinalsMaps.failure("world_format");
                file.seek(record + 16);
                if (littleInt(file) != 0 || littleInt(file) != 0)
                    throw FinalsMaps.failure("world_format");
                long zip64DiskEntries = littleLong(file), zip64Count = littleLong(file);
                long zip64Size = littleLong(file), zip64Offset = littleLong(file);
                if (zip64DiskEntries != zip64Count
                        || !matchesZip64(diskEntries, 0xffffL, zip64DiskEntries)
                        || !matchesZip64(count, 0xffffL, zip64Count)
                        || !matchesZip64(size, 0xffffffffL, zip64Size)
                        || !matchesZip64(offset, 0xffffffffL, zip64Offset))
                    throw FinalsMaps.failure("world_format");
                count = zip64Count;
                size = zip64Size;
                offset = zip64Offset;
                directoryLimit = record;
            } else if (disk != 0 || centralDisk != 0 || diskEntries != count
                    || size == 0xffffffffL || offset == 0xffffffffL) {
                throw FinalsMaps.failure("world_format");
            }
            long end = checkedEnd(offset, size, directoryLimit);
            if (count > size / 46) throw FinalsMaps.failure("world_format");
            file.seek(offset);
            for (long i = 0; i < count; i++) {
                long start = file.getFilePointer();
                checkedEnd(start, 46, end);
                if (littleInt(file) != 0x02014b50L) throw FinalsMaps.failure("world_format");
                file.seek(start + 20);
                long compressed = littleInt(file), uncompressed = littleInt(file);
                file.seek(start + 28);
                int name = littleShort(file),
                        extra = littleShort(file),
                        comment = littleShort(file);
                int entryDisk = littleShort(file);
                file.seek(start + 38);
                long external = littleInt(file);
                if (((external >>> 16) & 0170000) == 0120000) throw FinalsMaps.failure("source");
                long localOffset = littleInt(file);
                long next = checkedEnd(start + 46, (long) name + extra + comment, end);
                if (entryDisk == 0xffff) {
                    rejectZip64Disk(file, start + 46 + name, extra, compressed, uncompressed, localOffset);
                } else if (entryDisk != 0) throw FinalsMaps.failure("world_format");
                file.seek(next);
            }
            if (file.getFilePointer() != end) throw FinalsMaps.failure("world_format");
        }
    }

    private static boolean matchesZip64(long legacy, long sentinel, long actual) {
        return legacy == sentinel || legacy == actual;
    }

    /** Validate lengths by subtraction so malformed unsigned ZIP fields cannot overflow. */
    private static long checkedEnd(long start, long size, long limit) {
        if (start < 0 || size < 0 || start > limit || size > limit - start)
            throw FinalsMaps.failure("world_format");
        return start + size;
    }

    private static void rejectZip64Disk(
            RandomAccessFile file, long start, int size, long compressed, long uncompressed,
            long localOffset) throws IOException {
        long end = start + size;
        file.seek(start);
        while (file.getFilePointer() < end) {
            checkedEnd(file.getFilePointer(), 4, end);
            int type = littleShort(file), length = littleShort(file);
            long field = file.getFilePointer();
            long next = checkedEnd(field, length, end);
            if (type == 1) {
                int skip = (uncompressed == 0xffffffffL ? 8 : 0)
                        + (compressed == 0xffffffffL ? 8 : 0)
                        + (localOffset == 0xffffffffL ? 8 : 0);
                checkedEnd(field, skip + 4L, next);
                file.seek(field + skip);
                if (littleInt(file) != 0) throw FinalsMaps.failure("world_format");
                return;
            }
            file.seek(next);
        }
        throw FinalsMaps.failure("world_format");
    }

    private static int littleShort(RandomAccessFile file) throws IOException {
        return Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
    }

    private static long littleInt(RandomAccessFile file) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
    }

    private static long littleLong(RandomAccessFile file) throws IOException {
        long value = Long.reverseBytes(file.readLong());
        if (value < 0) throw FinalsMaps.failure("world_format");
        return value;
    }

    private static void copy(InputStream input, Path output) throws IOException {
        try (OutputStream stream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
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

    private FinalsMapFiles() {}
}
