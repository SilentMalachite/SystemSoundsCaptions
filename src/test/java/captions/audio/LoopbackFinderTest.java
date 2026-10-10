package captions.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopbackFinderTest {

    @Test
    void knowsCommonLoopbackDevices() {
        assertTrue(LoopbackFinder.looksLikeLoopback("BlackHole 2ch"));
        assertTrue(LoopbackFinder.looksLikeLoopback("ステレオ ミキサー (Realtek(R) Audio)"));
        assertTrue(LoopbackFinder.looksLikeLoopback("Stereo Mix (Realtek High Definition Audio)"));
        assertTrue(LoopbackFinder.looksLikeLoopback("CABLE Output (VB-Audio Virtual Cable)"));
    }

    @Test
    void ignoresMicrophones() {
        assertFalse(LoopbackFinder.looksLikeLoopback("MacBook Pro Microphone"));
        assertFalse(LoopbackFinder.looksLikeLoopback("マイク (USB Audio Device)"));
    }
}
