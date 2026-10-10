package captions.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class PcmQueueTest {

    @Test
    void dropsTheOldestWhenFull() throws InterruptedException {
        var queue = new PcmQueue(2);
        queue.offer(new byte[]{1});
        queue.offer(new byte[]{2});
        queue.offer(new byte[]{3});
        assertArrayEquals(new byte[]{2}, queue.poll(10));
        assertArrayEquals(new byte[]{3}, queue.poll(10));
        assertNull(queue.poll(10));
    }

    @Test
    void endIsNeverLostAndEmptyChunksAreSkipped() throws InterruptedException {
        var queue = new PcmQueue(1);
        queue.offer(new byte[]{1});
        queue.offer(new byte[0]);
        queue.end();
        assertSame(PcmQueue.END, queue.poll(10));
    }
}
