package captions.audio;

import javax.sound.sampled.Mixer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Finds an input device that carries what the computer plays. */
public final class LoopbackFinder {

    static final List<String> HINTS = List.of(
            "blackhole",                       // macOS
            "loopback audio",                  // macOS, Rogue Amoeba
            "soundflower",                     // macOS, old
            "stereo mix", "ステレオ ミキサー", "ステレオミキサー", // Windows
            "what u hear", "wave out mix",     // Windows, some sound cards
            "cable output");                   // Windows, VB-CABLE

    private LoopbackFinder() {
    }

    public static boolean looksLikeLoopback(String deviceName) {
        String name = deviceName.toLowerCase(Locale.ROOT);
        return HINTS.stream().anyMatch(name::contains);
    }

    public static Optional<Mixer.Info> find(List<Mixer.Info> inputs) {
        return inputs.stream().filter(i -> looksLikeLoopback(i.getName())).findFirst();
    }
}
