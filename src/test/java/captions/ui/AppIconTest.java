package captions.ui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppIconTest {

    @Test
    void loadsEveryWindowsSize() {
        var images = AppIcon.windowsImages();
        assertEquals(AppIcon.WINDOWS_SIZES.length, images.size());
        for (int i = 0; i < images.size(); i++) {
            var image = (BufferedImage) images.get(i);
            assertEquals(AppIcon.WINDOWS_SIZES[i], image.getWidth());
            assertEquals(AppIcon.WINDOWS_SIZES[i], image.getHeight());
        }
    }

    @Test
    void loadsTheDockImage() {
        var image = (BufferedImage) AppIcon.macImage().orElseThrow();
        assertEquals(512, image.getWidth());
        assertEquals(512, image.getHeight());
    }

    @Test
    void missingIconIsSkipped() {
        assertTrue(AppIcon.load("missing.png").isEmpty());
    }

    @Test
    void unreadableIconIsSkipped() {
        var notAnImage = new ByteArrayInputStream("not an image".getBytes(StandardCharsets.US_ASCII));
        assertTrue(AppIcon.read("broken.png", notAnImage).isEmpty());
    }
}
