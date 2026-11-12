package me.petr1furious.potatominigames.games.finals;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Import regression checks; run with a small heap to verify bounded file streaming. */
public final class FinalsMapImportCheck {
    private static final long REGION_BYTES = 65L * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        Path temporary = Files.createTempDirectory("finals-import-check-");
        try {
            largeImports(temporary);
            zip64(temporary);
            unsafeArchives(temporary);
            System.out.println("FinalsMapImportCheck passed");
        } finally {
            try (var paths = Files.walk(temporary)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
    }

    private static void largeImports(Path temporary) throws Exception {
        Path archive = temporary.resolve("large.zip");
        byte[] buffer = new byte[8192];
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            entry(zip, "wrapped/level.dat");
            for (int region = 0; region < 8; region++) {
                zip.putNextEntry(new ZipEntry("wrapped/region/r." + region + ".0.mca"));
                for (long remaining = REGION_BYTES; remaining > 0; remaining -= buffer.length)
                    zip.write(buffer, 0, (int) Math.min(remaining, buffer.length));
                zip.closeEntry();
            }
            for (int i = 0; i < 20_001; i++) entry(zip, "ignored/file-" + i);
        }
        Path extracted = temporary.resolve("large-extracted");
        FinalsMapFiles.copyWorld(archive, extracted);
        long total = 0;
        for (int region = 0; region < 8; region++) {
            long size = Files.size(extracted.resolve("region/r." + region + ".0.mca"));
            require(size == REGION_BYTES, "Regions larger than 64 MiB copy in full");
            total += size;
        }
        require(total > 512L * 1024 * 1024, "Worlds larger than 512 MiB import in full");
        require(!Files.exists(extracted.resolve("ignored")), "Extra archive entries stay excluded");

        // The folder path must have the same unrestricted, streaming behavior as ZIPs.
        Path directory = Files.createDirectory(temporary.resolve("directory"));
        Files.writeString(directory.resolve("level.dat"), "level");
        Files.createDirectory(directory.resolve("region"));
        Files.move(extracted.resolve("region/r.0.0.mca"), directory.resolve("region/r.0.0.mca"));
        Path copied = temporary.resolve("directory-copy");
        FinalsMapFiles.copyWorld(directory, copied);
        require(Files.size(copied.resolve("region/r.0.0.mca")) == REGION_BYTES,
                "Folder imports retain large regions");
    }

    private static void zip64(Path temporary) throws Exception {
        Path many = temporary.resolve("zip64-count.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(many))) {
            entry(zip, "level.dat");
            entry(zip, "region/r.0.0.mca");
            for (int i = 0; i < 65_535; i++) entry(zip, "ignored/" + i);
        }
        Path manyCopy = temporary.resolve("zip64-count-copy");
        FinalsMapFiles.copyWorld(many, manyCopy);
        require(Files.exists(manyCopy.resolve("region/r.0.0.mca")),
                "ZIP64 entry counts above 65535 import");

        Path archive = worldZip(temporary.resolve("zip64-offsets.zip"));
        promoteZip64(archive);
        Path copy = temporary.resolve("zip64-offsets-copy");
        FinalsMapFiles.copyWorld(archive, copy);
        require(Files.readString(copy.resolve("level.dat")).equals("data"),
                "ZIP64 size and offset sentinels import");

        Path badOffset = Files.copy(archive, temporary.resolve("zip64-bad-offset.zip"));
        try (RandomAccessFile file = new RandomAccessFile(badOffset.toFile(), "rw")) {
            file.seek(file.length() - 34); // Locator's ZIP64 EOCD offset.
            littleLong(file, Long.MAX_VALUE);
        }
        rejected(badOffset, temporary.resolve("bad-offset"));
        Path badSize = Files.copy(archive, temporary.resolve("zip64-bad-size.zip"));
        try (RandomAccessFile file = new RandomAccessFile(badSize.toFile(), "rw")) {
            file.seek(file.length() - 94); // ZIP64 EOCD record size.
            littleLong(file, Long.MAX_VALUE);
        }
        rejected(badSize, temporary.resolve("bad-size"));
        Path multiDisk = Files.copy(archive, temporary.resolve("zip64-multidisk.zip"));
        try (RandomAccessFile file = new RandomAccessFile(multiDisk.toFile(), "rw")) {
            file.seek(file.length() - 26); // Locator's disk count.
            littleInt(file, 2);
        }
        rejected(multiDisk, temporary.resolve("multidisk"));
        Path badCount = Files.copy(archive, temporary.resolve("zip64-bad-count.zip"));
        try (RandomAccessFile file = new RandomAccessFile(badCount.toFile(), "rw")) {
            file.seek(file.length() - 66); // ZIP64 EOCD total entry count.
            littleLong(file, 3);
        }
        rejected(badCount, temporary.resolve("bad-count"));
        Path symlink = Files.copy(archive, temporary.resolve("zip64-link.zip"));
        markCentral(symlink, 38, 0120777L << 16, false);
        rejected(symlink, temporary.resolve("zip64-link"));
    }

    private static void unsafeArchives(Path temporary) throws Exception {
        for (String entry : new String[] {"../escape", "root/../level.dat", "root\\file"}) {
            Path path = temporary.resolve("unsafe-" + Integer.toUnsignedString(entry.hashCode()) + ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
                entry(zip, "level.dat");
                entry(zip, "region/r.0.0.mca");
                entry(zip, entry);
            }
            rejected(path, temporary.resolve(path.getFileName() + "-copy"));
        }
        Path duplicate = temporary.resolve("duplicate.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(duplicate))) {
            entry(zip, "level.dat");
            entry(zip, "LEVEL.DAT");
            entry(zip, "region/r.0.0.mca");
        }
        rejected(duplicate, temporary.resolve("duplicate-copy"));
        Path ambiguous = temporary.resolve("ambiguous.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(ambiguous))) {
            entry(zip, "a/level.dat");
            entry(zip, "b/level.dat");
            entry(zip, "a/region/r.0.0.mca");
        }
        rejected(ambiguous, temporary.resolve("ambiguous-copy"));
        Path truncated = worldZip(temporary.resolve("truncated-central.zip"));
        markCentral(truncated, 28, 0xffff, true);
        rejected(truncated, temporary.resolve("truncated-copy"));
        Path entryDisk = worldZip(temporary.resolve("entry-multidisk.zip"));
        markCentral(entryDisk, 34, 1, true);
        rejected(entryDisk, temporary.resolve("entry-multidisk-copy"));
        Path shortDirectory = worldZip(temporary.resolve("short-central.zip"));
        try (RandomAccessFile file = new RandomAccessFile(shortDirectory.toFile(), "rw")) {
            file.seek(file.length() - 10); // EOCD central-directory size.
            littleInt(file, 1);
        }
        rejected(shortDirectory, temporary.resolve("short-copy"));
        require(!Files.exists(temporary.resolve("escape")), "Traversal writes no outside file");
    }

    private static Path worldZip(Path path) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(zip, "level.dat");
            entry(zip, "region/r.0.0.mca");
        }
        return path;
    }

    private static void entry(ZipOutputStream zip, String name) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write("data".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Add standard single-disk ZIP64 end records without allocating the archive in memory. */
    private static void promoteZip64(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            long eocd = file.length() - 22;
            file.seek(eocd + 10);
            int count = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
            long size = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
            long offset = Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
            file.seek(eocd);
            littleInt(file, 0x06064b50L);
            littleLong(file, 44);
            littleShort(file, 45);
            littleShort(file, 45);
            littleInt(file, 0);
            littleInt(file, 0);
            littleLong(file, count);
            littleLong(file, count);
            littleLong(file, size);
            littleLong(file, offset);
            littleInt(file, 0x07064b50L);
            littleInt(file, 0);
            littleLong(file, eocd);
            littleInt(file, 1);
            littleInt(file, 0x06054b50L);
            littleShort(file, 0);
            littleShort(file, 0);
            littleShort(file, 0xffff);
            littleShort(file, 0xffff);
            littleInt(file, 0xffffffffL);
            littleInt(file, 0xffffffffL);
            littleShort(file, 0);
        }
    }

    private static void markCentral(Path path, int field, long value, boolean shortField)
            throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            for (long p = 0; p <= file.length() - 46; p++) {
                file.seek(p);
                if (Integer.reverseBytes(file.readInt()) == 0x02014b50) {
                    file.seek(p + field);
                    if (shortField) littleShort(file, (int) value);
                    else littleInt(file, value);
                    return;
                }
            }
            throw new AssertionError("Missing central directory");
        }
    }

    private static void littleShort(RandomAccessFile file, int value) throws IOException {
        file.writeShort(Short.reverseBytes((short) value));
    }

    private static void littleInt(RandomAccessFile file, long value) throws IOException {
        file.writeInt(Integer.reverseBytes((int) value));
    }

    private static void littleLong(RandomAccessFile file, long value) throws IOException {
        file.writeLong(Long.reverseBytes(value));
    }

    private static void rejected(Path archive, Path destination) throws Exception {
        try {
            FinalsMapFiles.copyWorld(archive, destination);
        } catch (IllegalArgumentException | IOException expected) {
            require(!Files.exists(destination.resolve("level.dat")),
                    "Unsafe archives fail before extracting world files");
            return;
        }
        throw new AssertionError("Unsafe archive accepted: " + archive.getFileName());
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
