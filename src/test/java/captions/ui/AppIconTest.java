package captions.ui;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        var image = (BufferedImage) AppIcon.macImage();
        assertEquals(512, image.getWidth());
        assertEquals(512, image.getHeight());
    }
}
