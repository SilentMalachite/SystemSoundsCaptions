package captions.ui;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlWindowTest {

    @Test
    void formatsPlaybackTime() {
        assertEquals("02:41", ControlWindow.clock(Duration.ofSeconds(161)));
        assertEquals("1:02:03", ControlWindow.clock(Duration.ofSeconds(3723)));
    }
}
