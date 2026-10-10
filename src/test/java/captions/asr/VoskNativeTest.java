package captions.asr;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoskNativeTest {

    @Test
    void picksLibrariesPerOs() {
        assertEquals(List.of("darwin/libvosk.dylib"), VoskNative.resources("Mac OS X", "aarch64"));
        assertEquals(List.of("darwin/libvosk.dylib"), VoskNative.resources("Mac OS X", "x86_64"));
        assertEquals("win32-x86-64/libvosk.dll", VoskNative.resources("Windows 11", "amd64").getLast());
        assertEquals(4, VoskNative.resources("Windows 11", "amd64").size());
        assertTrue(VoskNative.resources("Windows 11", "aarch64").isEmpty());
    }

    @Test
    void librariesAreInTheClasspath() {
        for (String os : List.of("Mac OS X", "Windows 11", "Linux")) {
            for (String name : VoskNative.resources(os, "amd64")) {
                assertTrue(VoskNative.class.getResource("/" + name) != null, name);
            }
        }
    }

    @Test
    void pathIsNullTerminated() {
        try (var arena = Arena.ofConfined()) {
            Path model = Path.of("tmp", "model");
            var segment = VoskNative.nativePath(arena, model);
            assertEquals(0, segment.get(java.lang.foreign.ValueLayout.JAVA_BYTE, segment.byteSize() - 1));
            // ASCII path: one byte per character in any native encoding, plus the terminator
            assertEquals(model.toAbsolutePath().toString().length() + 1, segment.byteSize());
        }
    }
}
