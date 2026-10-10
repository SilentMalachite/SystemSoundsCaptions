package captions.audio;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Opens an audio or video file as 16-bit PCM: Java Sound first, then ffmpeg. */
public final class Decoders {

    static final Set<String> JAVA_SOUND = Set.of("wav", "wave", "aif", "aiff", "aifc", "au", "snd", "mp3");

    private Decoders() {
    }

    /** An open file. Close it to stop ffmpeg. */
    public static final class Decoded implements AutoCloseable {
        private final AudioInputStream stream;
        private final AtomicReference<Duration> total;
        private final Process process;
        private final AtomicReference<String> lastError;

        Decoded(AudioInputStream stream, Duration total, Process process, AtomicReference<String> lastError) {
            this.stream = stream;
            this.total = new AtomicReference<>(total);
            this.process = process;
            this.lastError = lastError;
        }

        public AudioInputStream stream() {
            return stream;
        }

        /** The length of the file, or null if it is not known. */
        public Duration total() {
            return total.get();
        }

        /** The last line ffmpeg wrote, for an error message. */
        public String lastError() {
            return lastError == null ? null : lastError.get();
        }

        @Override
        public void close() {
            try {
                stream.close();
            } catch (IOException ignored) {
                // nothing to do
            }
            if (process != null) {
                process.destroy();
            }
        }
    }

    public static Decoded open(Path file) throws IOException {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (JAVA_SOUND.contains(ext)) {
            try {
                return openWithJavaSound(file);
            } catch (UnsupportedAudioFileException | IllegalArgumentException e) {
                // fall through to ffmpeg
            }
        }
        return openWithFfmpeg(file);
    }

    private static Decoded openWithJavaSound(Path file) throws IOException, UnsupportedAudioFileException {
        AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(file.toFile());
        AudioInputStream raw = AudioSystem.getAudioInputStream(file.toFile());
        try {
            AudioInputStream in = toPcm16(raw);
            return new Decoded(in, length(fileFormat, in), null, null);
        } catch (RuntimeException e) {
            raw.close(); // on Windows an open handle locks the file
            throw e;
        }
    }

    private static Duration length(AudioFileFormat fileFormat, AudioInputStream in) {
        if (fileFormat.properties().get("duration") instanceof Long micros && micros > 0) {
            return Duration.ofNanos(micros * 1000);
        }
        long frames = in.getFrameLength();
        float rate = in.getFormat().getFrameRate();
        if (frames > 0 && rate > 0) {
            return Duration.ofMillis((long) (frames * 1000 / rate));
        }
        return null;
    }

    static AudioInputStream toPcm16(AudioInputStream in) {
        AudioFormat f = in.getFormat();
        if (f.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && f.getSampleSizeInBits() == 16 && !f.isBigEndian()) {
            return in;
        }
        float rate = f.getSampleRate() > 0 ? f.getSampleRate() : 44_100f;
        int channels = Math.min(2, Math.max(1, f.getChannels()));
        var target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, rate, 16, channels, channels * 2, rate, false);
        return AudioSystem.getAudioInputStream(target, in);
    }

    private static Decoded openWithFfmpeg(Path file) throws IOException {
        Path ffmpeg = Ffmpeg.find().orElseThrow(() -> new IOException(
                "この形式を読むには ffmpeg が必要です。ffmpeg を入れてから、もう一度開いてください。"));
        // arguments as a list: no shell, so a file name cannot run a command
        // file only: a playlist (m3u8, concat) inside the file cannot make ffmpeg open a URL
        Process process = new ProcessBuilder(ffmpeg.toString(), "-hide_banner", "-nostdin", "-nostats",
                "-protocol_whitelist", "file",
                "-i", "file:" + file.toAbsolutePath(),
                "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ac", "2", "-ar", "44100", "-")
                .start();
        var format = new AudioFormat(44_100f, 16, 2, true, false);
        var stream = new AudioInputStream(process.getInputStream(), format, AudioSystem.NOT_SPECIFIED);
        var lastError = new AtomicReference<String>();
        var decoded = new Decoded(stream, null, process, lastError);
        Thread.ofPlatform().daemon().name("ffmpeg-log").start(() -> {
            try (var log = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = log.readLine()) != null) {
                    Ffmpeg.parseDuration(line).ifPresent(d -> decoded.total.compareAndSet(null, d));
                    if (!line.isBlank()) {
                        lastError.set(line.strip());
                    }
                }
            } catch (IOException ignored) {
                // ffmpeg stopped
            }
        });
        return decoded;
    }
}
