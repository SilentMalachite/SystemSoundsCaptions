package captions.asr;

import captions.caption.CaptionBus;
import captions.caption.CaptionEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SilenceGuardTest {

    private static final byte[] ZERO = new byte[3200];
    private static final byte[] SOUND = {0, 0, 10, 0};

    @Test
    void warnsOnceAfterThreeSecondsOfZeros() {
        var bus = new CaptionBus();
        var events = new ArrayList<CaptionEvent>();
        bus.subscribe(events::add);
        var guard = new SilenceGuard(bus, "許可してください");

        for (int i = 0; i < 29; i++) {
            guard.check(ZERO);
        }
        assertTrue(events.isEmpty());
        guard.check(ZERO);
        for (int i = 0; i < 100; i++) {
            guard.check(ZERO);
        }
        assertEquals(1, events.size());
        assertEquals(new CaptionEvent.Error("許可してください"), events.getFirst());
    }

    @Test
    void anySoundResetsTheCount() {
        var bus = new CaptionBus();
        var events = new ArrayList<CaptionEvent>();
        bus.subscribe(events::add);
        var guard = new SilenceGuard(bus, "x");
        for (int round = 0; round < 5; round++) {
            for (int i = 0; i < 29; i++) {
                guard.check(ZERO);
            }
            guard.check(SOUND);
        }
        assertTrue(events.isEmpty());
    }
}
