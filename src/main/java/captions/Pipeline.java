package captions;

import captions.asr.ModelStore;
import captions.asr.RecognizerWorker;
import captions.asr.SilenceGuard;
import captions.asr.VoskEngine;
import captions.audio.AudioSource;
import captions.audio.FileSource;
import captions.audio.LineSource;
import captions.audio.PcmQueue;
import captions.caption.CaptionBus;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.file.Path;
import java.time.Duration;

/** Connects a source, the queue and the recognizer, and moves the session through its states. */
public final class Pipeline {

    /** What the user picked in the window. */
    public sealed interface Choice {
        String label();

        record Microphone(Mixer.Info mixer, String label) implements Choice {
        }

        record SystemSound(Mixer.Info mixer, String label) implements Choice {
        }

        record AudioFile(Path path) implements Choice {
            @Override
            public String label() {
                return path.getFileName().toString();
            }
        }
    }

    public interface ProgressListener {
        void download(long done, long total);

        void file(Duration played, Duration total);
    }

    private static final String MODEL = ModelStore.JAPANESE;

    private final Session session;
    private final CaptionBus bus;
    private final ModelStore models;
    private volatile ProgressListener progress = new ProgressListener() {
        @Override
        public void download(long done, long total) {
        }

        @Override
        public void file(Duration played, Duration total) {
        }
    };

    private final Object engineLock = new Object();
    private VoskEngine engine;          // kept between sessions: loading a model is slow
    private Thread starter;
    private AudioSource source;
    private RecognizerWorker worker;
    private long generation;

    public Pipeline(Session session, CaptionBus bus, ModelStore models) {
        this.session = session;
        this.bus = bus;
        this.models = models;
    }

    public void setProgressListener(ProgressListener listener) {
        this.progress = listener;
    }

    public synchronized void start(Choice choice) {
        if (!session.fire(Session.Event.START, choice.label())) {
            return;
        }
        long run = ++generation;
        starter = Thread.ofPlatform().daemon().name("pipeline-start").start(() -> {
            try {
                VoskEngine loaded = engine();
                synchronized (this) {
                    if (run != generation || session.state() != Session.State.LOADING) {
                        return; // the user pressed stop while the model loaded
                    }
                    var queue = new PcmQueue(600);
                    SilenceGuard guard = choice instanceof Choice.Microphone
                            ? new SilenceGuard(bus, SilenceGuard.defaultMessage()) : null;
                    worker = new RecognizerWorker(loaded.newRecognizer(), queue, bus, guard, listener(run));
                    Thread.ofPlatform().daemon().name("recognizer").start(worker);
                    source = create(choice);
                    source.start(queue, e -> fail(run, e));
                    session.fire(Session.Event.READY, choice.label());
                }
            } catch (InterruptedException e) {
                // stopped during the download
            } catch (Exception | LinkageError e) {
                fail(run, e);
            }
        });
    }

    public void stop() {
        Runnable close;
        synchronized (this) {
            generation++;
            if (starter != null) {
                starter.interrupt();
            }
            close = detach();
            session.fire(Session.Event.STOP, "");
        }
        close.run(); // outside the lock: a failing audio thread may wait for it
    }

    private VoskEngine engine() throws IOException, InterruptedException {
        Path dir = models.ensure(MODEL, (done, total) -> progress.download(done, total));
        synchronized (engineLock) { // not "this": stop() must not wait for a slow model load
            if (engine == null || !engine.modelDir().equals(dir)) {
                engine = VoskEngine.load(dir, true);
            }
            return engine;
        }
    }

    private AudioSource create(Choice choice) {
        return switch (choice) {
            case Choice.Microphone m -> new LineSource(m.mixer(), m.label());
            case Choice.SystemSound s -> new LineSource(s.mixer(), s.label());
            case Choice.AudioFile f -> new FileSource(f.path(), (played, total) -> progress.file(played, total));
        };
    }

    private RecognizerWorker.Listener listener(long run) {
        return new RecognizerWorker.Listener() {
            @Override
            public void onEnd() {
                Runnable close;
                synchronized (Pipeline.this) {
                    if (run != generation) {
                        return;
                    }
                    generation++;
                    close = detach();
                    session.fire(Session.Event.END, "");
                }
                close.run();
            }

            @Override
            public void onFail(Throwable error) {
                fail(run, error);
            }
        };
    }

    private void fail(long run, Throwable error) {
        Runnable close;
        synchronized (this) {
            if (run != generation) {
                return;
            }
            generation++;
            close = detach();
            session.fire(Session.Event.FAIL, message(error));
        }
        close.run();
    }

    /** Takes the running parts out under the lock. The caller closes them after it lets the lock go. */
    private Runnable detach() {
        AudioSource oldSource = source;
        RecognizerWorker oldWorker = worker;
        source = null;
        worker = null;
        return () -> {
            if (oldSource != null) {
                oldSource.close();
            }
            if (oldWorker != null) {
                oldWorker.stop();
            }
        };
    }

    static String message(Throwable error) {
        return switch (error) {
            case UnknownHostException e -> "モデルをダウンロードできません。ネット接続を確認してください。";
            case ConnectException e -> "モデルをダウンロードできません。ネット接続を確認してください。";
            case HttpConnectTimeoutException e -> "モデルのダウンロードがタイムアウトしました。";
            case LineUnavailableException e -> "入力デバイスを開けません。";
            case UnsatisfiedLinkError e -> "音声認識ライブラリを読み込めません: " + e.getMessage();
            default -> error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        };
    }
}
