package captions.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Plays a file on the speakers and sends the same sound to the recognizer.
 * The speaker write blocks, so the file moves at real time and captions match what the user hears.
 */
public final class FileSource implements AudioSource {

    /** Reports how far the file played. {@code total} is null if the length is not known. */
    public interface Progress {
        void update(Duration played, Duration total);
    }

    private final Path file;
    private final Progress progress;
    private volatile boolean running;
    private Decoders.Decoded decoded;
    private SourceDataLine speaker;
    private Thread thread;

    public FileSource(Path file, Progress progress) {
        this.file = file;
        this.progress = progress;
    }

    @Override
    public synchronized void start(PcmQueue out, Consumer<Exception> onFail) throws IOException {
        decoded = Decoders.open(file);
        AudioFormat format = decoded.stream().getFormat();
        speaker = openSpeaker(format);
        var converter = new PcmConverter(format);
        int frameSize = format.getFrameSize();
        int chunk = Math.max(frameSize, (int) (format.getFrameRate() / 10) * frameSize); // 0.1 s
        double bytesPerSecond = format.getFrameRate() * frameSize;
        running = true;
        var in = decoded.stream();
        var line = speaker;
        var source = decoded;
        thread = Thread.ofPlatform().daemon().name("audio-file").start(() -> {
            byte[] buf = new byte[chunk];
            long played = 0;
            long startNanos = System.nanoTime();
            try {
                int n;
                while (running && (n = readFully(in, buf) / frameSize * frameSize) > 0) {
                    if (line != null) {
                        line.write(buf, 0, n);
                    } else {
                        pace(startNanos, played, bytesPerSecond);
                    }
                    out.offer(converter.convert(buf, n));
                    played += n;
                    progress.update(Duration.ofMillis((long) (played * 1000 / bytesPerSecond)), source.total());
                }
                if (!running) {
                    return;
                }
                if (played == 0 || source.exitCode() != 0) {
                    String detail = source.lastError();
                    throw new IOException((played == 0 ? "ファイルを読めません: " : "ファイルの途中で読めなくなりました: ")
                            + file.getFileName() + (detail == null ? "" : "\n" + detail));
                }
                if (line != null) {
                    line.drain();
                }
                out.end();
            } catch (Exception e) {
                if (running) {
                    onFail.accept(e);
                }
            }
        });
    }

    private static SourceDataLine openSpeaker(AudioFormat format) {
        try {
            SourceDataLine line = AudioSystem.getSourceDataLine(format);
            int bytesPerSecond = (int) format.getFrameRate() * format.getFrameSize();
            line.open(format, bytesPerSecond / 4);
            line.start();
            return line;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            return null; // no speaker: captions still work, paced by the clock
        }
    }

    private static void pace(long startNanos, long played, double bytesPerSecond) throws InterruptedException {
        long due = startNanos + (long) (played / bytesPerSecond * 1e9);
        long wait = due - System.nanoTime();
        if (wait > 0) {
            Thread.sleep(Duration.ofNanos(wait));
        }
    }

    /** Reads until the buffer is full or the stream ends, keeping whole frames. */
    private static int readFully(InputStream in, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int n = in.read(buf, total, buf.length - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    @Override
    public String label() {
        return file.getFileName().toString();
    }

    @Override
    public synchronized void close() {
        running = false;
        if (speaker != null) {
            speaker.stop();
            speaker.flush();
            speaker.close();
        }
        if (decoded != null) {
            decoded.close();
        }
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
