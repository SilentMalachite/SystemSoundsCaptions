package captions.audio;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Carries PCM chunks from a source thread to the recognizer thread.
 * If the recognizer falls behind, the oldest chunk goes, so captions stay close to real time.
 */
public final class PcmQueue {

    /** Put after the last chunk of a file. */
    public static final byte[] END = new byte[0];

    private final BlockingQueue<byte[]> queue;

    public PcmQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public void offer(byte[] chunk) {
        if (chunk.length == 0 && chunk != END) {
            return;
        }
        while (!queue.offer(chunk)) {
            queue.poll();
        }
    }

    public void end() {
        offer(END);
    }

    /** Returns the next chunk, or null if none came in time. */
    public byte[] poll(long millis) throws InterruptedException {
        return queue.poll(millis, TimeUnit.MILLISECONDS);
    }

    public int size() {
        return queue.size();
    }
}
