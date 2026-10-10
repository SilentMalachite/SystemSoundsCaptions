package captions.asr;

/** One recognition session. Only the recognizer thread calls it. */
public interface SpeechRecognizer extends AutoCloseable {

    /** Reads 16 kHz mono PCM. Returns true when an utterance ends and {@link #result()} is ready. */
    boolean accept(byte[] pcm, int length);

    /** The text of the utterance that just ended. */
    String result();

    /** The text so far of the utterance in progress. */
    String partial();

    /** The text left when the sound stops. */
    String finalResult();

    @Override
    void close();
}
