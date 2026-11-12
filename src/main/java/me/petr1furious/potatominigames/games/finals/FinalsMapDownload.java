package me.petr1furious.potatominigames.games.finals;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Downloads direct ZIP links without blocking the server or overwriting local imports. */
final class FinalsMapDownload implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Path imports;
    private final Set<Download> active = ConcurrentHashMap.newKeySet();
    private volatile boolean closed;

    FinalsMapDownload(JavaPlugin plugin, Path imports) {
        this.plugin = plugin;
        this.imports = imports.toAbsolutePath().normalize();
    }

    /** The successful file exists only for the duration of the server-thread callback. */
    void download(String source, Consumer<String> success, Consumer<RuntimeException> failure) {
        URI uri = url(source);
        if (closed) throw FinalsMaps.failure("download_failed");
        Download download = new Download(uri, success, failure);
        active.add(download);
        try {
            download.task = Bukkit.getScheduler().runTaskAsynchronously(plugin, download::run);
        } catch (RuntimeException error) {
            active.remove(download);
            download.cancel();
            throw error;
        }
    }

    @Override
    public void close() {
        closed = true;
        for (Download download : List.copyOf(active)) download.cancel();
        active.clear();
    }

    static URI url(String source) {
        try {
            URI uri = new URI(source);
            if (!("https".equalsIgnoreCase(uri.getScheme())
                            || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getPort() == 0
                    || uri.getPort() > 65535) throw FinalsMaps.failure("download_url");
            return uri;
        } catch (URISyntaxException | NullPointerException invalid) {
            throw FinalsMaps.failure("download_url");
        }
    }

    /** Preflight each hop; the standard HTTP client still resolves the host when connecting. */
    private static void publicHost(URI uri) throws IOException {
        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        if (addresses.length == 0) throw FinalsMaps.failure("download_address");
        for (InetAddress address : addresses)
            if (!publicAddress(address)) throw FinalsMaps.failure("download_address");
    }

    static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int a = bytes[0] & 255, b = bytes[1] & 255;
        if (bytes.length == 4) {
            int c = bytes[2] & 255;
            return a != 0
                    && a < 224
                    && !(a == 100 && b >= 64 && b <= 127)
                    && !(a == 192 && b == 0 && (c == 0 || c == 2))
                    && !(a == 198 && (b == 18 || b == 19))
                    && !(a == 198 && b == 51 && c == 100)
                    && !(a == 203 && b == 0 && c == 113);
        }
        return bytes.length == 16
                && (a & 0xe0) == 0x20 // Global unicast; excludes IPv4-compatible/NAT64 shortcuts.
                && !(a == 0x20
                        && b == 0x01
                        && (bytes[2] & 255) == 0x0d
                        && (bytes[3] & 255) == 0xb8);
    }

    private final class Download {
        private final URI initial;
        private final Consumer<String> success;
        private final Consumer<RuntimeException> failure;
        private volatile boolean cancelled;
        private volatile BukkitTask task;
        private volatile HttpURLConnection connection;
        private volatile Path temporary;

        Download(URI initial, Consumer<String> success, Consumer<RuntimeException> failure) {
            this.initial = initial;
            this.success = success;
            this.failure = failure;
        }

        private boolean cancelled() {
            return cancelled || closed || !plugin.isEnabled();
        }

        void run() {
            boolean delivered = false;
            RuntimeException error = null;
            try {
                checkCancelled(this::cancelled);
                if (Files.isSymbolicLink(imports)
                        || !Files.isDirectory(imports, LinkOption.NOFOLLOW_LINKS))
                    throw FinalsMaps.failure("source");
                temporary = Files.createTempFile(imports, ".download-", ".zip");
                URI next = initial;
                for (int redirects = 0; ; redirects++) {
                    checkCancelled(this::cancelled);
                    publicHost(next);
                    HttpURLConnection current =
                            (HttpURLConnection) next.toURL().openConnection(Proxy.NO_PROXY);
                    connection = current;
                    try {
                        current.setInstanceFollowRedirects(false);
                        current.setConnectTimeout(15_000);
                        current.setReadTimeout(30_000);
                        current.setRequestProperty("User-Agent", "PotatoMinigames-Finals/1.0");
                        current.setRequestProperty(
                                "Accept", "application/zip, application/octet-stream");
                        current.setRequestProperty("Accept-Encoding", "identity");
                        checkCancelled(this::cancelled);
                        int status = current.getResponseCode();
                        if (status == 301
                                || status == 302
                                || status == 303
                                || status == 307
                                || status == 308) {
                            String location = current.getHeaderField("Location");
                            if (redirects >= 5 || location == null)
                                throw FinalsMaps.failure("download_failed");
                            try {
                                next = url(next.resolve(location).toString());
                            } catch (IllegalArgumentException invalid) {
                                throw FinalsMaps.failure("download_url");
                            }
                            continue;
                        }
                        if (status != HttpURLConnection.HTTP_OK)
                            throw FinalsMaps.failure("download_failed");
                        copyZip(current, temporary, this::cancelled);
                        break;
                    } finally {
                        current.disconnect();
                        connection = null;
                    }
                }
            } catch (IOException io) {
                error = FinalsMaps.failure("download_failed");
            } catch (RuntimeException invalid) {
                error = invalid;
            }
            try {
                if (!cancelled()) {
                    RuntimeException result = error;
                    Bukkit.getScheduler().runTask(plugin, () -> complete(result));
                    delivered = true;
                }
            } catch (RuntimeException unavailable) {
                // Plugin shutdown may race the scheduling boundary. close() suppresses callbacks.
            } finally {
                if (!delivered) clean();
            }
        }

        private void complete(RuntimeException error) {
            try {
                if (cancelled()) return;
                if (error != null) {
                    clean();
                    failure.accept(error);
                } else {
                    try {
                        success.accept(temporary.getFileName().toString());
                    } catch (RuntimeException invalid) {
                        clean();
                        failure.accept(invalid);
                    }
                }
            } finally {
                clean();
            }
        }

        void cancel() {
            cancelled = true;
            BukkitTask running = task;
            if (running != null) running.cancel();
            HttpURLConnection current = connection;
            if (current != null) current.disconnect();
            clean();
        }

        private void clean() {
            Path owned = temporary;
            if (owned != null) {
                try {
                    Files.deleteIfExists(owned);
                } catch (IOException cleanup) {
                    // An active stream on Windows may hold the file until the worker's finally.
                    if (!cancelled())
                        plugin.getLogger()
                                .warning("Cannot remove temporary Finals download: " + owned);
                }
            }
            active.remove(this);
        }
    }

    /** Streaming keeps memory bounded independently of the map's archive size. */
    static void copyZip(HttpURLConnection connection, Path target, BooleanSupplier cancelled)
            throws IOException {
        checkCancelled(cancelled);
        long copied = 0;
        try (InputStream input = connection.getInputStream();
                var output =
                        Files.newOutputStream(
                                target,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] signature = input.readNBytes(4);
            if (signature.length != 4
                    || signature[0] != 'P'
                    || signature[1] != 'K'
                    || !((signature[2] == 3 && signature[3] == 4)
                            || (signature[2] == 5 && signature[3] == 6)))
                throw FinalsMaps.failure("download_zip");
            output.write(signature);
            copied += signature.length;
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) != -1) {
                checkCancelled(cancelled);
                output.write(buffer, 0, length);
                copied += length;
            }
        }
        checkCancelled(cancelled);
        long expected = connection.getContentLengthLong();
        if (expected >= 0 && copied != expected)
            throw new IOException("Incomplete map archive response");
        try (ZipFile ignored = new ZipFile(target.toFile())) {
            // Verify the central directory before handing a completed file to the world importer.
        } catch (ZipException invalid) {
            throw FinalsMaps.failure("download_zip");
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new InterruptedIOException("Map download cancelled");
    }
}
