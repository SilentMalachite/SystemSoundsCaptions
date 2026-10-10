package captions.audio;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Finds the ffmpeg command. Apps started from Finder do not get the Homebrew PATH, so common folders are also checked. */
public final class Ffmpeg {

    private static final Pattern DURATION = Pattern.compile("Duration:\\s*(\\d+):(\\d{2}):(\\d{2}(?:\\.\\d+)?)");

    private Ffmpeg() {
    }

    public static Optional<Path> find() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
        var extra = new ArrayList<Path>();
        if (windows) {
            env("LOCALAPPDATA").ifPresent(d -> extra.add(Path.of(d, "Microsoft", "WinGet", "Links")));
            env("USERPROFILE").ifPresent(d -> extra.add(Path.of(d, "scoop", "shims")));
            extra.add(Path.of("C:\\ProgramData\\chocolatey\\bin"));
            extra.add(Path.of("C:\\ffmpeg\\bin"));
        } else {
            extra.add(Path.of("/opt/homebrew/bin"));
            extra.add(Path.of("/usr/local/bin"));
            extra.add(Path.of("/opt/local/bin"));
            extra.add(Path.of("/usr/bin"));
        }
        // getenv(String) ignores case; on Windows the variable is "Path"
        return find(Objects.requireNonNullElse(System.getenv("PATH"), ""), extra, windows);
    }

    static Optional<Path> find(String pathVariable, List<Path> extra, boolean windows) {
        var dirs = new ArrayList<Path>();
        for (String part : pathVariable.split(File.pathSeparator)) {
            String dir = part.strip();
            if (dir.length() >= 2 && dir.startsWith("\"") && dir.endsWith("\"")) {
                dir = dir.substring(1, dir.length() - 1);
            }
            if (dir.isBlank()) {
                continue;
            }
            try {
                dirs.add(Path.of(dir));
            } catch (InvalidPathException e) {
                // skip an entry Windows cannot use
            }
        }
        dirs.addAll(extra);
        String name = windows ? "ffmpeg.exe" : "ffmpeg";
        return dirs.stream()
                .map(d -> d.resolve(name))
                .filter(p -> Files.isRegularFile(p) && Files.isExecutable(p))
                .findFirst();
    }

    /** Reads "Duration: 00:14:30.12" from an ffmpeg log line. */
    static Optional<Duration> parseDuration(String line) {
        var m = DURATION.matcher(line);
        if (!m.find()) {
            return Optional.empty();
        }
        long millis = Long.parseLong(m.group(1)) * 3_600_000
                + Long.parseLong(m.group(2)) * 60_000
                + Math.round(Double.parseDouble(m.group(3)) * 1000);
        return Optional.of(Duration.ofMillis(millis));
    }

    private static Optional<String> env(String name) {
        return Optional.ofNullable(System.getenv(name)).filter(s -> !s.isBlank());
    }
}
