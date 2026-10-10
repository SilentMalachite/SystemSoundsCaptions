package captions.asr;

import captions.audio.PcmQueue;
import captions.caption.CaptionBus;
import captions.caption.CaptionEvent;

import java.time.Instant;

/** Reads PCM from the queue and publishes partial and final text. Owns the recognizer and closes it. */
public final class RecognizerWorker implements Runnable {

    public interface Listener {
        /** The source sent {@link PcmQueue#END}, and the last text went out. */
        void onEnd();

        void onFail(Throwable error);
    }

    private final SpeechRecognizer recognizer;
    private final PcmQueue queue;
    private final CaptionBus bus;
    private final SilenceGuard guard; // null for sources where silence is normal
    private final Listener listener;
    private volatile boolean stopped;

    public RecognizerWorker(SpeechRecognizer recognizer, PcmQueue queue, CaptionBus bus,
                            SilenceGuard guard, Listener listener) {
        this.recognizer = recognizer;
        this.queue = queue;
        this.bus = bus;
        this.guard = guard;
        this.listener = listener;
    }

    public void stop() {
        stopped = true;
    }

    @Override
    public void run() {
        String lastPartial = "";
        try (recognizer) {
            while (!stopped) {
                byte[] chunk = queue.poll(100);
                if (chunk == null) {
                    continue;
                }
                if (chunk == PcmQueue.END) {
                    publishFinal(recognizer.finalResult());
                    listener.onEnd();
                    return;
                }
                if (guard != null) {
                    guard.check(chunk);
                }
                if (recognizer.accept(chunk, chunk.length)) {
                    publishFinal(recognizer.result());
                    lastPartial = "";
                } else {
                    String partial = recognizer.partial();
                    if (!partial.equals(lastPartial)) {
                        publish(new CaptionEvent.Partial(partial));
                        lastPartial = partial;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception | LinkageError e) {
            if (!stopped) {
                listener.onFail(e);
            }
        }
    }

    private void publishFinal(String text) {
        if (text.isBlank()) {
            publish(new CaptionEvent.Partial(""));
        } else {
            publish(new CaptionEvent.Final(text, Instant.now()));
        }
    }

    /** After stop, the chunk in progress must not put old text on the next session's captions. */
    private void publish(CaptionEvent event) {
        if (!stopped) {
            bus.publish(event);
        }
    }
}
