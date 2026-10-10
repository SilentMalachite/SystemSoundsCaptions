package captions.asr;

import captions.audio.PcmConverter;
import captions.caption.CaptionBus;
import captions.caption.CaptionEvent;

/**
 * When macOS or Windows refuses the microphone, the app gets exact zeros, not an error.
 * After 3 seconds of exact zeros, this tells the user once where to allow it.
 */
public final class SilenceGuard {

    static final long LIMIT_BYTES = 3L * 32_000; // 3 s of 16 kHz 16-bit mono

    private final CaptionBus bus;
    private final String message;
    private long silentBytes;
    private boolean warned;

    public SilenceGuard(CaptionBus bus, String message) {
        this.bus = bus;
        this.message = message;
    }

    public static String defaultMessage() {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        return mac
                ? "マイクの音が届きません。システム設定 › プライバシーとセキュリティ › マイク で、Java または起動に使ったアプリを許可してください。"
                : "マイクの音が届きません。設定 › プライバシーとセキュリティ › マイク で、デスクトップ アプリのアクセスを許可してください。";
    }

    public void check(byte[] chunk) {
        if (PcmConverter.peak(chunk) != 0) {
            silentBytes = 0;
            return;
        }
        silentBytes += chunk.length;
        if (!warned && silentBytes >= LIMIT_BYTES) {
            warned = true;
            bus.publish(new CaptionEvent.Error(message));
        }
    }
}
