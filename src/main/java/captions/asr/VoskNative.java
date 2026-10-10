package captions.asr;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Calls libvosk through the Java FFM API. The native libraries come from the vosk jar.
 * The Java wrapper of vosk 0.3.45 is not used: on macOS it asks for a symbol the library does not have.
 */
final class VoskNative {

    private static final String VERSION = "0.3.45";
    private static VoskNative instance;

    private final MethodHandle setLogLevel;
    private final MethodHandle modelNew;
    private final MethodHandle modelFree;
    private final MethodHandle recognizerNew;
    private final MethodHandle acceptWaveform;
    private final MethodHandle result;
    private final MethodHandle partialResult;
    private final MethodHandle finalResult;
    private final MethodHandle recognizerFree;

    private VoskNative(List<Path> libraries) {
        SymbolLookup lookup = null;
        for (Path library : libraries) { // dependencies first, libvosk last
            lookup = SymbolLookup.libraryLookup(library, Arena.global());
        }
        Linker linker = Linker.nativeLinker();
        setLogLevel = linker.downcallHandle(lookup.findOrThrow("vosk_set_log_level"), FunctionDescriptor.ofVoid(JAVA_INT));
        modelNew = linker.downcallHandle(lookup.findOrThrow("vosk_model_new"), FunctionDescriptor.of(ADDRESS, ADDRESS));
        modelFree = linker.downcallHandle(lookup.findOrThrow("vosk_model_free"), FunctionDescriptor.ofVoid(ADDRESS));
        recognizerNew = linker.downcallHandle(lookup.findOrThrow("vosk_recognizer_new"),
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_FLOAT));
        acceptWaveform = linker.downcallHandle(lookup.findOrThrow("vosk_recognizer_accept_waveform"),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
        result = linker.downcallHandle(lookup.findOrThrow("vosk_recognizer_result"), FunctionDescriptor.of(ADDRESS, ADDRESS));
        partialResult = linker.downcallHandle(lookup.findOrThrow("vosk_recognizer_partial_result"),
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        finalResult = linker.downcallHandle(lookup.findOrThrow("vosk_recognizer_final_result"),
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        recognizerFree = linker.downcallHandle(lookup.findOrThrow("vosk_recognizer_free"), FunctionDescriptor.ofVoid(ADDRESS));
    }

    static synchronized VoskNative get() throws IOException {
        if (instance == null) {
            instance = new VoskNative(extract());
        }
        return instance;
    }

    /** The libraries for this OS inside the vosk jar, in load order. */
    static List<String> resources(String osName, String arch) {
        String os = osName.toLowerCase(Locale.ROOT);
        boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
        if (os.contains("mac")) {
            return List.of("darwin/libvosk.dylib"); // universal: arm64 and x86_64
        }
        if (os.startsWith("windows") && x64) {
            return List.of("win32-x86-64/libwinpthread-1.dll", "win32-x86-64/libgcc_s_seh-1.dll",
                    "win32-x86-64/libstdc++-6.dll", "win32-x86-64/libvosk.dll");
        }
        if (os.contains("linux") && x64) {
            return List.of("linux-x86-64/libvosk.so");
        }
        return List.of();
    }

    private static List<Path> extract() throws IOException {
        String os = System.getProperty("os.name", "");
        String arch = System.getProperty("os.arch", "");
        List<String> names = resources(os, arch);
        if (names.isEmpty()) {
            throw new IOException("この OS には対応していません: " + os + " " + arch);
        }
        Path dir = Path.of(System.getProperty("user.home"), ".systemsoundscaptions", "native", VERSION);
        Files.createDirectories(dir);
        var paths = new ArrayList<Path>();
        for (String name : names) {
            Path out = dir.resolve(name.substring(name.indexOf('/') + 1));
            try (InputStream in = VoskNative.class.getResourceAsStream("/" + name)) {
                if (in == null) {
                    throw new IOException("jar に音声認識ライブラリがありません: " + name);
                }
                byte[] bytes = in.readAllBytes();
                if (!Files.exists(out) || Files.size(out) != bytes.length) {
                    Path tmp = Files.createTempFile(dir, "lib", ".part");
                    try {
                        Files.write(tmp, bytes);
                        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        Files.deleteIfExists(tmp);
                    }
                }
            }
            paths.add(out);
        }
        return paths;
    }

    void setLogLevel(int level) {
        try {
            setLogLevel.invokeExact(level);
        } catch (Throwable t) {
            throw failure(t);
        }
    }

    /** Returns NULL if the model cannot be loaded. */
    MemorySegment modelNew(Path dir) {
        try (Arena arena = Arena.ofConfined()) {
            return (MemorySegment) modelNew.invokeExact(nativePath(arena, dir));
        } catch (Throwable t) {
            throw failure(t);
        }
    }

    void modelFree(MemorySegment model) {
        try {
            modelFree.invokeExact(model);
        } catch (Throwable t) {
            throw failure(t);
        }
    }

    MemorySegment recognizerNew(MemorySegment model, float sampleRate) {
        try {
            return (MemorySegment) recognizerNew.invokeExact(model, sampleRate);
        } catch (Throwable t) {
            throw failure(t);
        }
    }

    /** 1 when an utterance ended, 0 when it goes on, -1 on an error. */
    int acceptWaveform(MemorySegment recognizer, MemorySegment data, int length) {
        try {
            return (int) acceptWaveform.invokeExact(recognizer, data, length);
        } catch (Throwable t) {
            throw failure(t);
        }
    }

    String result(MemorySegment recognizer) {
        try {
            return utf8((MemorySegment) result.invokeExact(recognizer));
        } catch (Throwable t) {
            throw failure(t);
        }
    }

    String partialResult(MemorySegment recognizer) {
        try {
            return utf8((MemorySegment) partialResult.invokeExact(recognizer));
        } catch (Throwable t) {
            throw failure(t);
        }
    }

    String finalResult(MemorySegment recognizer) {
        try {
            return utf8((MemorySegment) finalResult.invokeExact(recognizer));
        } catch (Throwable t) {
            throw failure(t);
        }
    }

    void recognizerFree(MemorySegment recognizer) {
        try {
            recognizerFree.invokeExact(recognizer);
        } catch (Throwable t) {
            throw failure(t);
        }
    }

    /**
     * Vosk opens the model with a narrow-char path. On Windows that is the ANSI code page,
     * so a Japanese user name must be encoded in it, not in UTF-8.
     */
    static MemorySegment nativePath(Arena arena, Path dir) {
        String path = dir.toAbsolutePath().toString();
        if (!canPass(path)) {
            throw new IllegalArgumentException("音声モデルのフォルダ名に使えない文字があります: " + path);
        }
        byte[] bytes = path.getBytes(nativeCharset());
        MemorySegment segment = arena.allocate(bytes.length + 1L);
        MemorySegment.copy(bytes, 0, segment, JAVA_BYTE, 0, bytes.length);
        segment.set(JAVA_BYTE, bytes.length, (byte) 0);
        return segment;
    }

    /** True if libvosk can open this path: every character fits the native narrow encoding. */
    static boolean canPass(String path) {
        return nativeCharset().newEncoder().canEncode(path);
    }

    private static Charset nativeCharset() {
        try {
            return Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8"));
        } catch (IllegalArgumentException e) {
            return StandardCharsets.UTF_8;
        }
    }

    private static String utf8(MemorySegment text) {
        if (text.equals(MemorySegment.NULL)) {
            return "";
        }
        return text.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
    }

    private static RuntimeException failure(Throwable t) {
        if (t instanceof RuntimeException r) {
            return r;
        }
        if (t instanceof Error e) {
            throw e;
        }
        return new IllegalStateException(t);
    }
}
