package captions.asr;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelStoreTest {

    private static final String NAME = "vosk-model-test";

    @TempDir
    Path tmp;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile byte[] body;
    private volatile int status = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
            if (status == 200) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ModelStore store() {
        return new ModelStore(tmp.resolve("models"), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
    }

    private static byte[] zip(String... names) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String name : names) {
                zip.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) {
                    zip.write("x".getBytes());
                }
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void downloadsOnceAndUnpacks() throws Exception {
        body = zip(NAME + "/", NAME + "/am/final.mdl", NAME + "/conf/model.conf");
        var done = new AtomicLong();
        Path dir = store().ensure(NAME, (d, total) -> {
            done.set(d);
            assertEquals(body.length, total);
        });

        assertEquals(tmp.resolve("models").resolve(NAME), dir);
        assertTrue(Files.isRegularFile(dir.resolve("am/final.mdl")));
        assertEquals(body.length, done.get());
        assertFalse(Files.exists(tmp.resolve("models").resolve(NAME + ".zip.part")));

        store().ensure(NAME, (d, t) -> { });
        assertEquals(1, requests.get());
    }

    @Test
    void acceptsZipWithoutTopFolder() throws Exception {
        body = zip("am/final.mdl", "conf/model.conf");
        Path dir = store().ensure(NAME, (d, t) -> { });
        assertTrue(Files.isRegularFile(dir.resolve("conf/model.conf")));
    }

    @Test
    void refusesEntriesOutsideTheFolder() throws Exception {
        body = zip(NAME + "/am/final.mdl", "../evil.txt");
        assertThrows(IOException.class, () -> store().ensure(NAME, (d, t) -> { }));
        assertFalse(Files.exists(tmp.resolve("evil.txt")));
        assertFalse(store().isInstalled(NAME));
    }

    @Test
    void reportsHttpErrorAndLeavesNoPartFile() {
        status = 404;
        var error = assertThrows(IOException.class, () -> store().ensure(NAME, (d, t) -> { }));
        assertTrue(error.getMessage().contains("404"));
        assertFalse(Files.exists(tmp.resolve("models").resolve(NAME + ".zip.part")));
    }

    @Test
    void twoRunsAtOnceDownloadOnce() throws Exception {
        body = zip(NAME + "/am/final.mdl", NAME + "/conf/model.conf");
        var store = store();
        var errors = new java.util.concurrent.CopyOnWriteArrayList<Throwable>();
        Runnable run = () -> {
            try {
                store.ensure(NAME, (d, t) -> { });
            } catch (Exception e) {
                errors.add(e);
            }
        };
        var a = Thread.ofPlatform().start(run);
        var b = Thread.ofPlatform().start(run);
        a.join();
        b.join();
        assertTrue(errors.isEmpty(), errors.toString());
        assertEquals(1, requests.get());
        assertTrue(store.isInstalled(NAME));
    }

    @Test
    void refusesZipWithoutModelFiles() throws Exception {
        body = zip(NAME + "/readme.txt");
        assertThrows(IOException.class, () -> store().ensure(NAME, (d, t) -> { }));
    }
}
