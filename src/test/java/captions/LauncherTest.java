package captions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LauncherTest {

    @Test
    void readsOldAndNewVersionStrings() {
        assertEquals(8, Launcher.major("1.8"));
        assertEquals(21, Launcher.major("21"));
        assertEquals(25, Launcher.major("25"));
        assertEquals(27, Launcher.major("27.0"));
    }
}
