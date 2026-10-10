package captions.audio;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.util.function.Consumer;

/** A place sound comes from. Every source gives the same format to the same queue. */
public sealed interface AudioSource extends AutoCloseable permits LineSource, FileSource {

    /** 16 kHz, 16 bit, mono, signed, little-endian: the format the recognizer reads. */
    AudioFormat FORMAT = new AudioFormat(16_000f, 16, 1, true, false);

    /**
     * Starts a thread that offers PCM in {@link #FORMAT} to {@code out}. Returns at once.
     * A failure after the start goes to {@code onFail}.
     */
    void start(PcmQueue out, Consumer<Exception> onFail) throws IOException;

    /** The name shown in the window. */
    String label();

    @Override
    void close();
}
