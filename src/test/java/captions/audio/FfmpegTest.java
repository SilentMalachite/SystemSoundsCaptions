package captions.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FfmpegTest {

    @TempDir
    Path tmp;

    private Path executable(Path dir, String name) throws IOException {
        Files.createDirectories(dir);
        Path p = Files.createFile(dir.resolve(name));
        p.toFile().setExecutable(true);
        return p;
    }

    @Test
    void searchesPathThenExtraFolders() throws IOException {
        Path onPath = executable(tmp.resolve("a"), "ffmpeg");
        executable(tmp.resolve("b"), "ffmpeg");
        String path = tmp.resolve("empty") + File.pathSeparator + tmp.resolve("a");
        assertEquals(Optional.of(onPath), Ffmpeg.find(path, List.of(tmp.resolve("b")), false));
    }

    @Test
    void usesExtraFolderWhenPathHasNone() throws IOException {
        Path brew = executable(tmp.resolve("brew"), "ffmpeg");
        assertEquals(Optional.of(brew), Ffmpeg.find("", List.of(tmp.resolve("brew")), false));
    }

    @Test
    void looksForExeOnWindows() throws IOException {
        Path exe = executable(tmp.resolve("w"), "ffmpeg.exe");
        assertEquals(Optional.of(exe), Ffmpeg.find(tmp.resolve("w").toString(), List.of(), true));
    }

    @Test
    void skipsQuotesAndBadEntries() throws IOException {
        Path quoted = executable(tmp.resolve("q"), "ffmpeg");
        String path = "\u0000bad" + File.pathSeparator + "\"" + tmp.resolve("q") + "\"";
        assertEquals(Optional.of(quoted), Ffmpeg.find(path, List.of(), false));
    }

    @Test
    void ignoresRelativeEntries() throws IOException {
        executable(tmp.resolve("rel"), "ffmpeg");
        assertEquals(Optional.empty(), Ffmpeg.find("." + File.pathSeparator + "rel", List.of(), false));
    }

    @Test
    void readsDuration() {
        assertEquals(Optional.of(Duration.ofMillis(870_120)),
                Ffmpeg.parseDuration("  Duration: 00:14:30.12, start: 0.000000, bitrate: 128 kb/s"));
        assertEquals(Optional.empty(), Ffmpeg.parseDuration("  Duration: N/A, bitrate: N/A"));
    }
}
