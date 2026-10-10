package captions.asr;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/** Holds a loaded Vosk model. Loading takes seconds, so the app keeps one engine per model. */
public final class VoskEngine implements AutoCloseable {

    private static final int LOG_WARNINGS_ONLY = -1;

    private final VoskNative vosk;
    private final Path modelDir;
    private final MemorySegment model;
    private final boolean removeSpaces;
    private boolean closed;

    private VoskEngine(VoskNative vosk, Path modelDir, MemorySegment model, boolean removeSpaces) {
        this.vosk = vosk;
        this.modelDir = modelDir;
        this.model = model;
        this.removeSpaces = removeSpaces;
    }

    /** Loads a model. {@code removeSpaces} is for Japanese: Vosk puts a space between words. */
    public static VoskEngine load(Path modelDir, boolean removeSpaces) throws IOException {
        VoskNative vosk = VoskNative.get();
        vosk.setLogLevel(LOG_WARNINGS_ONLY);
        MemorySegment model = vosk.modelNew(modelDir);
        if (model.equals(MemorySegment.NULL)) {
            throw new IOException("音声モデルを読み込めません: " + modelDir);
        }
        return new VoskEngine(vosk, modelDir, model, removeSpaces);
    }

    public Path modelDir() {
        return modelDir;
    }

    public SpeechRecognizer newRecognizer() throws IOException {
        MemorySegment recognizer = vosk.recognizerNew(model, 16_000f);
        if (recognizer.equals(MemorySegment.NULL)) {
            throw new IOException("音声認識を開始できません。");
        }
        return new Recognizer(recognizer);
    }

    String clean(String text) {
        return removeSpaces ? text.replace(" ", "") : text.strip();
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            vosk.modelFree(model);
        }
    }

    /** Used by one thread at a time; the shared arena lets the start thread make it and the worker use it. */
    private final class Recognizer implements SpeechRecognizer {
        private final MemorySegment handle;
        private final Arena arena = Arena.ofShared();
        private MemorySegment buffer = MemorySegment.NULL;
        private boolean closed;

        Recognizer(MemorySegment handle) {
            this.handle = handle;
        }

        @Override
        public boolean accept(byte[] pcm, int length) {
            if (buffer.byteSize() < length) {
                buffer = arena.allocate(Math.max(length, 8192));
            }
            MemorySegment.copy(pcm, 0, buffer, JAVA_BYTE, 0, length);
            int status = vosk.acceptWaveform(handle, buffer, length);
            if (status < 0) {
                throw new IllegalStateException("音声認識でエラーが起きました。");
            }
            return status == 1;
        }

        @Override
        public String result() {
            return clean(Json.field(vosk.result(handle), "text"));
        }

        @Override
        public String partial() {
            return clean(Json.field(vosk.partialResult(handle), "partial"));
        }

        @Override
        public String finalResult() {
            return clean(Json.field(vosk.finalResult(handle), "text"));
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                vosk.recognizerFree(handle);
                arena.close();
            }
        }
    }
}
