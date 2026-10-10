package captions.asr;

import captions.audio.PcmQueue;
import captions.caption.CaptionBus;
import captions.caption.CaptionEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecognizerWorkerTest {

    /** Each accepted chunk takes the next scripted step: "P:text" is a partial, "F:text" ends an utterance. */
    static class FakeRecognizer implements SpeechRecognizer {
        final Deque<String> script;
        final AtomicBoolean closed = new AtomicBoolean();
        String current = "";
        String last = "";

        FakeRecognizer(String... steps) {
            script = new ArrayDeque<>(List.of(steps));
        }

        @Override
        public boolean accept(byte[] pcm, int length) {
            String step = script.isEmpty() ? "P:" + current : script.poll();
            current = step.substring(2);
            if (step.startsWith("F:")) {
                last = current;
                current = "";
                return true;
            }
            return false;
        }

        @Override
        public String result() {
            return last;
        }

        @Override
        public String partial() {
            return current;
        }

        @Override
        public String finalResult() {
            return current.isEmpty() ? "" : current;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    @Test
    void publishesPartialsThenFinalAndEndsOnEnd() throws InterruptedException {
        var recognizer = new FakeRecognizer("P:今日", "P:今日", "P:今日は", "F:今日は晴れ", "P:明日");
        var queue = new PcmQueue(10);
        var bus = new CaptionBus();
        var events = new CopyOnWriteArrayList<CaptionEvent>();
        bus.subscribe(events::add);
        var ended = new CountDownLatch(1);

        var worker = new RecognizerWorker(recognizer, queue, bus, null, new RecognizerWorker.Listener() {
            @Override
            public void onEnd() {
                ended.countDown();
            }

            @Override
            public void onFail(Throwable error) {
            }
        });
        for (int i = 0; i < 5; i++) {
            queue.offer(new byte[]{1, 0});
        }
        queue.end();
        Thread.ofPlatform().start(worker);

        assertTrue(ended.await(2, TimeUnit.SECONDS));
        assertEquals(List.of(
                new CaptionEvent.Partial("今日"),          // the repeat is not sent again
                new CaptionEvent.Partial("今日は"),
                new CaptionEvent.Final("今日は晴れ", ((CaptionEvent.Final) events.get(2)).at()),
                new CaptionEvent.Partial("明日"),
                new CaptionEvent.Final("明日", ((CaptionEvent.Final) events.get(4)).at())), events);
        Thread.sleep(50);
        assertTrue(recognizer.closed.get());
    }

    @Test
    void blankFinalClearsTheGrayLine() throws InterruptedException {
        var recognizer = new FakeRecognizer("P:あ", "F:");
        var queue = new PcmQueue(10);
        var bus = new CaptionBus();
        var events = new CopyOnWriteArrayList<CaptionEvent>();
        bus.subscribe(events::add);
        var worker = new RecognizerWorker(recognizer, queue, bus, null, null);
        queue.offer(new byte[]{1, 0});
        queue.offer(new byte[]{1, 0});
        var thread = Thread.ofPlatform().start(worker);
        Thread.sleep(300);
        worker.stop();
        thread.join(1000);
        assertEquals(List.of(new CaptionEvent.Partial("あ"), new CaptionEvent.Partial("")), events);
    }

    @Test
    void reportsRecognizerFailure() throws InterruptedException {
        var recognizer = new FakeRecognizer() {
            @Override
            public boolean accept(byte[] pcm, int length) {
                throw new IllegalStateException("boom");
            }
        };
        var queue = new PcmQueue(10);
        var failed = new CountDownLatch(1);
        var error = new Throwable[1];
        var worker = new RecognizerWorker(recognizer, queue, new CaptionBus(), null, new RecognizerWorker.Listener() {
            @Override
            public void onEnd() {
            }

            @Override
            public void onFail(Throwable e) {
                error[0] = e;
                failed.countDown();
            }
        });
        queue.offer(new byte[]{1, 0});
        Thread.ofPlatform().start(worker);
        assertTrue(failed.await(2, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error[0]);
    }
}
