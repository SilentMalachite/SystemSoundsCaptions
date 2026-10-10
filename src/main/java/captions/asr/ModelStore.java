package captions.asr;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Downloads a Vosk model once, into the user's folder, and finds it after that. */
public final class ModelStore {

    public static final String JAPANESE = "vosk-model-small-ja-0.22";
    public static final URI VOSK_MODELS = URI.create("https://alphacephei.com/vosk/models/");

    /** {@code total} is -1 when the server does not say the size. */
    public interface Progress {
        void update(long done, long total);
    }

    private final Path root;
    private final URI base;
    private final ReentrantLock installing = new ReentrantLock();

    public ModelStore(Path root, URI base) {
        this.root = root;
        this.base = base;
    }

    /** The environment variable that sets another model folder. */
    public static final String MODELS_ENV = "SYSTEMSOUNDSCAPTIONS_MODELS";

    public static Path defaultRoot() {
        String custom = System.getenv(MODELS_ENV);
        if (custom != null && !custom.isBlank() && Path.of(custom).isAbsolute()) {
            return Path.of(custom);
        }
        return Path.of(System.getProperty("user.home"), ".systemsoundscaptions", "models");
    }

    public Path dir(String name) {
        return root.resolve(name);
    }

    public boolean isInstalled(String name) {
        Path dir = dir(name);
        return Files.isDirectory(dir.resolve("am")) && Files.isDirectory(dir.resolve("conf"));
    }

    /** Returns the model folder. Downloads and unpacks the model first if it is not there. */
    public Path ensure(String name, Progress progress) throws IOException, InterruptedException {
        Path dir = dir(name);
        if (isInstalled(name)) {
            return dir;
        }
        // a cancelled run can still be cleaning up its .zip.part when the user starts again
        installing.lockInterruptibly();
        try {
            if (isInstalled(name)) {
                return dir;
            }
            return install(name, dir, progress);
        } finally {
            installing.unlock();
        }
    }

    private Path install(String name, Path dir, Progress progress) throws IOException, InterruptedException {
        Files.createDirectories(root);
        Path part = root.resolve(name + ".zip.part");
        Path unpack = null;
        try {
            download(base.resolve(name + ".zip"), part, progress);
            unpack = Files.createTempDirectory(root, name + ".unpack");
            extract(part, unpack);
            Path top = unpack.resolve(name);
            Path content = Files.isDirectory(top) ? top : unpack;
            deleteTree(dir);
            Files.move(content, dir, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(part);
            if (unpack != null) {
                deleteTree(unpack);
            }
        }
        if (!isInstalled(name)) {
            throw new IOException("ダウンロードしたモデルの形式が正しくありません: " + name);
        }
        return dir;
    }

    private static void download(URI uri, Path target, Progress progress) throws IOException, InterruptedException {
        try (var http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build()) {
            var request = HttpRequest.newBuilder(uri).GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("モデルをダウンロードできません (HTTP " + response.statusCode() + "): " + uri);
                }
                long total = response.headers().firstValueAsLong("content-length").orElse(-1);
                try (OutputStream out = Files.newOutputStream(target)) {
                    byte[] buf = new byte[64 * 1024];
                    long done = 0;
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (Thread.interrupted()) {
                            throw new InterruptedException("ダウンロードを中止しました");
                        }
                        out.write(buf, 0, n);
                        done += n;
                        progress.update(done, total);
                    }
                }
            }
        }
    }

    /** Unpacks a zip. Refuses entries that point outside the target, such as "../x". */
    static void extract(Path zip, Path target) throws IOException {
        Path base = target.toAbsolutePath().normalize();
        try (var file = new ZipFile(zip.toFile())) {
            var entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path out = base.resolve(entry.getName()).normalize();
                if (!out.startsWith(base)) {
                    throw new IOException("zip の中身が正しくありません: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = file.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
