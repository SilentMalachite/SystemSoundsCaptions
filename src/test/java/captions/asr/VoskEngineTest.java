package captions.asr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Loads the real libvosk through FFM. On CI this runs on macOS, Windows and Linux. */
class VoskEngineTest {

    @Test
    void loadsLibvoskAndRefusesAMissingModel(@TempDir Path tmp) {
        assumeFalse(VoskNative.resources(System.getProperty("os.name"), System.getProperty("os.arch")).isEmpty(),
                "no libvosk for this platform");
        var error = assertThrows(IOException.class, () -> VoskEngine.load(tmp.resolve("no-model"), true));
        assertTrue(error.getMessage().contains("音声モデルを読み込めません"), error.getMessage());
    }
}
