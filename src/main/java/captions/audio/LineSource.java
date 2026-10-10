package captions.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** A microphone or a loopback device, read through Java Sound. */
public final class LineSource implements AudioSource {

    /** Tried in order. Many devices give 16 kHz mono directly; the rest are converted. */
    private static final List<AudioFormat> CANDIDATES = List.of(
            FORMAT,
            new AudioFormat(48_000f, 16, 2, true, false),
            new AudioFormat(44_100f, 16, 2, true, false),
            new AudioFormat(48_000f, 16, 1, true, false),
            new AudioFormat(44_100f, 16, 1, true, false));

    private final Mixer.Info mixer;   // null: the default input
    private final String label;
    private volatile boolean running;
    private TargetDataLine line;
    private Thread thread;

    public LineSource(Mixer.Info mixer, String label) {
        this.mixer = mixer;
        this.label = label;
    }

    /** Every device that can record. */
    public static List<Mixer.Info> inputs() {
        var want = new Line.Info(TargetDataLine.class);
        return Arrays.stream(AudioSystem.getMixerInfo())
                .filter(i -> AudioSystem.getMixer(i).isLineSupported(want))
                .toList();
    }

    @Override
    public synchronized void start(PcmQueue out, Consumer<Exception> onFail) throws IOException {
        line = open();
        var format = line.getFormat();
        var converter = new PcmConverter(format);
        int chunk = Math.max(format.getFrameSize(),
                (int) (format.getFrameRate() / 10) * format.getFrameSize()); // 0.1 s
        running = true;
        line.start();
        var reading = line;
        thread = Thread.ofPlatform().daemon().name("audio-in").start(() -> {
            byte[] buf = new byte[chunk];
            try {
                while (running) {
                    int n = reading.read(buf, 0, buf.length);
                    if (n > 0) {
                        out.offer(converter.convert(buf, n));
                    } else if (running && !reading.isOpen()) {
                        throw new IOException("入力デバイスが切断されました。");
                    }
                }
            } catch (Exception e) {
                if (running) {
                    onFail.accept(e);
                }
            }
        });
    }

    private TargetDataLine open() throws IOException {
        Mixer device = mixer == null ? null : AudioSystem.getMixer(mixer);
        for (var format : CANDIDATES) {
            var info = new DataLine.Info(TargetDataLine.class, format);
            boolean supported = device == null ? AudioSystem.isLineSupported(info) : device.isLineSupported(info);
            if (!supported) {
                continue;
            }
            try {
                var candidate = (TargetDataLine) (device == null ? AudioSystem.getLine(info) : device.getLine(info));
                int bytesPerSecond = (int) format.getFrameRate() * format.getFrameSize();
                candidate.open(format, bytesPerSecond / 2);
                return candidate;
            } catch (LineUnavailableException | IllegalArgumentException e) {
                // try the next format
            }
        }
        throw new IOException("入力デバイスを開けません: " + label);
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public synchronized void close() {
        running = false;
        if (line != null) {
            line.stop();
            line.close();
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
