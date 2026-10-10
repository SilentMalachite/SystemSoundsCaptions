package captions;

import captions.asr.ModelStore;
import captions.caption.CaptionBus;
import captions.ui.CaptionOverlay;
import captions.ui.ControlWindow;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.prefs.Preferences;

/** Builds the parts and shows the window. Started by Launcher after the Java version check. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.application.name", "System Sounds Captions");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // the default look is fine
            }
            var prefs = Preferences.userNodeForPackage(App.class);
            var bus = new CaptionBus();
            var session = new Session();
            var pipeline = new Pipeline(session, bus, new ModelStore(ModelStore.defaultRoot(), ModelStore.VOSK_MODELS));
            var overlay = new CaptionOverlay(prefs.node("overlay"));
            bus.subscribe(overlay::onEvent);
            new ControlWindow(session, pipeline, overlay, prefs).setVisible(true);
        });
    }
}
