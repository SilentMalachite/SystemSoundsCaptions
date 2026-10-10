package captions.ui;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The app icon. macOS shows it in the Dock (a jar would otherwise show the Java icon);
 * Windows picks the size it needs from the set for the title bar and the taskbar.
 * The PNGs are made by packaging/icons/build-icons.sh.
 */
public final class AppIcon {

    static final int[] WINDOWS_SIZES = {16, 20, 24, 32, 40, 48, 64, 256};

    private AppIcon() {
    }

    /** Sets the icon of the app and of the given window. Call on the event thread before showing it. */
    public static void apply(Window window) {
        if (System.getProperty("os.name", "").startsWith("Mac")) {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().setIconImage(macImage());
            }
        } else {
            window.setIconImages(windowsImages());
        }
    }

    static List<Image> windowsImages() {
        var images = new ArrayList<Image>();
        for (int size : WINDOWS_SIZES) {
            images.add(load("win-" + size + ".png"));
        }
        return images;
    }

    static Image macImage() {
        return load("mac-512.png");
    }

    private static Image load(String name) {
        try (var in = AppIcon.class.getResourceAsStream("/captions/icons/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing icon: " + name);
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
