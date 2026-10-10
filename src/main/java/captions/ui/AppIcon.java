package captions.ui;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The app icon. macOS shows it in the Dock (a jar would otherwise show the Java icon);
 * Windows picks the size it needs from the set for the title bar and the taskbar.
 * An icon that cannot be read is skipped, so the app still starts with the default icon.
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
                macImage().ifPresent(Taskbar.getTaskbar()::setIconImage);
            }
        } else {
            window.setIconImages(windowsImages());
        }
    }

    static List<Image> windowsImages() {
        var images = new ArrayList<Image>();
        for (int size : WINDOWS_SIZES) {
            load("win-" + size + ".png").ifPresent(images::add);
        }
        return images;
    }

    static Optional<Image> macImage() {
        return load("mac-512.png");
    }

    static Optional<Image> load(String name) {
        var in = AppIcon.class.getResourceAsStream("/captions/icons/" + name);
        if (in == null) {
            System.err.println("icon not found: " + name);
            return Optional.empty();
        }
        return read(name, in);
    }

    static Optional<Image> read(String name, InputStream in) {
        try (in) {
            var image = ImageIO.read(in);
            if (image == null) {
                System.err.println("icon is not an image: " + name);
            }
            return Optional.ofNullable(image);
        } catch (IOException e) {
            System.err.println("icon cannot be read: " + name + ": " + e.getMessage());
            return Optional.empty();
        }
    }
}
