package captions;

import javax.swing.JOptionPane;
import java.awt.GraphicsEnvironment;

/**
 * Entry point of the jar. Compiled with --release 8 so that an old JVM can load it
 * and tell the user which Java is needed, instead of failing with UnsupportedClassVersionError.
 */
public final class Launcher {

    static final int REQUIRED = 25;

    private Launcher() {
    }

    public static void main(String[] args) throws Exception {
        String spec = System.getProperty("java.specification.version"); // "1.8", "21", "25"
        if (major(spec) < REQUIRED) {
            String message = "Java " + REQUIRED + " 以上が必要です。\n現在の Java: " + spec
                    + "\n\nhttps://adoptium.net/ から Java " + REQUIRED + " 以上を入れてください。";
            if (GraphicsEnvironment.isHeadless()) {
                System.err.println(message);
            } else {
                JOptionPane.showMessageDialog(null, message, "System Sounds Captions",
                        JOptionPane.ERROR_MESSAGE);
            }
            System.exit(1);
        }
        Class.forName("captions.App")
                .getMethod("main", String[].class)
                .invoke(null, (Object) args);
    }

    static int major(String spec) {
        if (spec.startsWith("1.")) {
            return Integer.parseInt(spec.substring(2));
        }
        int dot = spec.indexOf('.');
        return Integer.parseInt(dot < 0 ? spec : spec.substring(0, dot));
    }
}
