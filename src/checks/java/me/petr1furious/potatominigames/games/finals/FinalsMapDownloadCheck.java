package me.petr1furious.potatominigames.games.finals;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Real HTTP response and public-address boundary checks; no external network is required. */
public final class FinalsMapDownloadCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        require(
                FinalsMapDownload.url("https://example.com/get?id=1").getRawQuery().equals("id=1"),
                "Download URLs preserve query strings used by direct download providers");
        for (String invalid :
                new String[] {
                    "file:///tmp/world.zip", "ftp://example.com/map.zip",
                            "http://user:pass@example.com/a",
                    "http:///map.zip", "https://example.com:65536/a",
                            "http://example.com/a\r\nHost: x"
                }) {
            expectInvalid(() -> FinalsMapDownload.url(invalid), "Reject unsafe URL " + invalid);
        }
        for (String address :
                new String[] {
                    "127.0.0.1", "0.0.0.0", "10.1.2.3", "172.16.4.5", "192.168.1.1",
                            "169.254.169.254",
                    "100.64.1.1", "224.0.0.1", "198.18.0.1", "::1", "fc00::1", "fe80::1",
                    "::127.0.0.1", "64:ff9b::a00:1"
                }) {
            require(
                    !FinalsMapDownload.publicAddress(InetAddress.getByName(address)),
                    "Private/special address rejected: " + address);
        }
        require(
                FinalsMapDownload.publicAddress(InetAddress.getByName("8.8.8.8")),
                "Public IPv4 is allowed");
        require(
                FinalsMapDownload.publicAddress(InetAddress.getByName("2606:4700:4700::1111")),
                "Public IPv6 is allowed");

        byte[] payload = new byte[160_000];
        for (int i = 0; i < payload.length; i++) payload[i] = (byte) (i * 47);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("world/region/r.0.0.mca"));
            zip.write(payload);
            zip.closeEntry();
        }
        byte[] archive = bytes.toByteArray();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/zip",
                exchange -> {
                    exchange.sendResponseHeaders(
                            200, 0); // Exercise chunked streaming without a length.
                    try (var body = exchange.getResponseBody()) {
                        body.write(archive);
                    }
                });
        server.createContext(
                "/html",
                exchange -> {
                    byte[] page = "<html>Download page, not an archive</html>".getBytes();
                    exchange.sendResponseHeaders(200, page.length);
                    try (var body = exchange.getResponseBody()) {
                        body.write(page);
                    }
                });
        server.createContext(
                "/truncated",
                exchange -> {
                    exchange.sendResponseHeaders(200, archive.length + 99);
                    try (var body = exchange.getResponseBody()) {
                        body.write(archive);
                    }
                });
        server.start();
        Path target = Files.createTempFile("finals-http-check-", ".zip");
        try {
            HttpURLConnection connection = connect(server, "/zip");
            try {
                FinalsMapDownload.copyZip(connection, target, () -> false);
                require(
                        Arrays.equals(Files.readAllBytes(target), archive),
                        "A chunked HTTP ZIP is streamed to disk without changing its bytes");
            } finally {
                connection.disconnect();
            }
            connection = connect(server, "/html");
            try {
                HttpURLConnection response = connection;
                expectInvalid(
                        () -> FinalsMapDownload.copyZip(response, target, () -> false),
                        "A website's HTML download page is rejected before world import");
            } finally {
                connection.disconnect();
            }
            connection = connect(server, "/truncated");
            try {
                boolean failed = false;
                try {
                    FinalsMapDownload.copyZip(connection, target, () -> false);
                } catch (IOException expected) {
                    failed = true;
                }
                require(
                        failed,
                        "A body shorter than Content-Length cannot become a successful import");
            } finally {
                connection.disconnect();
            }
            connection = connect(server, "/zip");
            try {
                AtomicBoolean cancelled = new AtomicBoolean(true);
                boolean failed = false;
                try {
                    FinalsMapDownload.copyZip(connection, target, cancelled::get);
                } catch (IOException expected) {
                    failed = true;
                }
                require(failed, "Cancelled downloads cannot be reported as complete");
            } finally {
                connection.disconnect();
            }
        } finally {
            server.stop(0);
            Files.deleteIfExists(target);
        }
        System.out.println("FinalsMapDownloadCheck: " + checks + " checks passed");
    }

    private static HttpURLConnection connect(HttpServer server, String path) throws Exception {
        var url = new java.net.URI("http://127.0.0.1:" + server.getAddress().getPort() + path);
        HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
        connection.setReadTimeout(2_000);
        return connection;
    }

    private interface Checked {
        void run() throws Exception;
    }

    private static void expectInvalid(Checked action, String message) throws Exception {
        boolean failed = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            failed = true;
        }
        require(failed, message);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
