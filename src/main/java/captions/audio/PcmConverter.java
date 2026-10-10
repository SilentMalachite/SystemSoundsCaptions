package captions.audio;

import javax.sound.sampled.AudioFormat;

/**
 * Turns 16-bit signed PCM of any rate and channel count into {@link AudioSource#FORMAT}.
 * It keeps state between calls, so a stream cut into chunks gives the same result as one piece.
 */
public final class PcmConverter {

    private static final double TARGET_RATE = 16_000.0;

    private final int channels;
    private final int frameSize;
    private final boolean bigEndian;
    private final double step;
    private final boolean passThrough;

    // windowed-sinc low-pass before downsampling, so sound above 8 kHz does not fold into speech
    private static final int TAPS = 63;
    private final double[] taps;     // null when no filter is needed
    private final double[] history;
    private int head;

    // resampler position: index into [previous sample, this chunk...]
    private double pos = 1.0;
    private double previous;

    public PcmConverter(AudioFormat source) {
        if (source.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || source.getSampleSizeInBits() != 16) {
            throw new IllegalArgumentException("16-bit signed PCM only: " + source);
        }
        this.channels = source.getChannels();
        this.frameSize = 2 * channels;
        this.bigEndian = source.isBigEndian();
        this.step = source.getSampleRate() / TARGET_RATE;
        this.passThrough = channels == 1 && !bigEndian && source.getSampleRate() == (float) TARGET_RATE;
        this.taps = step > 1 ? lowPassTaps(0.45 * TARGET_RATE / source.getSampleRate()) : null;
        this.history = new double[taps == null ? 1 : TAPS];
    }

    /** Blackman-windowed sinc. {@code cutoff} is in cycles per input sample. */
    static double[] lowPassTaps(double cutoff) {
        double[] h = new double[TAPS];
        int mid = TAPS / 2;
        double sum = 0;
        for (int n = 0; n < TAPS; n++) {
            int k = n - mid;
            double sinc = k == 0 ? 2 * cutoff : Math.sin(2 * Math.PI * cutoff * k) / (Math.PI * k);
            double window = 0.42 - 0.5 * Math.cos(2 * Math.PI * n / (TAPS - 1))
                    + 0.08 * Math.cos(4 * Math.PI * n / (TAPS - 1));
            h[n] = sinc * window;
            sum += h[n];
        }
        for (int n = 0; n < TAPS; n++) {
            h[n] /= sum; // a constant stays the same
        }
        return h;
    }

    public byte[] convert(byte[] buf, int length) {
        int frames = length / frameSize;
        if (passThrough) {
            byte[] copy = new byte[frames * 2];
            System.arraycopy(buf, 0, copy, 0, copy.length);
            return copy;
        }
        if (frames == 0) {
            return new byte[0];
        }
        double[] mono = new double[frames + 1];
        mono[0] = previous;
        for (int f = 0; f < frames; f++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += sample(buf, f * frameSize + c * 2);
            }
            mono[f + 1] = lowPass(sum / channels);
        }

        byte[] out = new byte[((int) ((frames + 1 - pos) / step) + 2) * 2];
        int n = 0;
        while (pos <= frames) {
            int i = (int) pos;
            double frac = pos - i;
            double v = frac == 0 ? mono[i] : mono[i] * (1 - frac) + mono[i + 1] * frac;
            int s = (int) Math.round(Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v)));
            out[n++] = (byte) s;
            out[n++] = (byte) (s >> 8);
            pos += step;
        }
        pos -= frames;
        previous = mono[frames];

        if (n == out.length) {
            return out;
        }
        byte[] trimmed = new byte[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    private double sample(byte[] buf, int i) {
        int lo = bigEndian ? buf[i + 1] : buf[i];
        int hi = bigEndian ? buf[i] : buf[i + 1];
        return (short) ((hi << 8) | (lo & 0xff));
    }

    private double lowPass(double v) {
        if (taps == null) {
            return v;
        }
        history[head] = v;
        double acc = 0;
        int i = head;
        for (double tap : taps) {
            acc += tap * history[i];
            i = i == 0 ? history.length - 1 : i - 1;
        }
        head = (head + 1) % history.length;
        return acc;
    }

    /** The largest absolute sample in 16-bit little-endian mono PCM. */
    public static int peak(byte[] pcm) {
        int max = 0;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int s = Math.abs((short) ((pcm[i + 1] << 8) | (pcm[i] & 0xff)));
            if (s > max) {
                max = s;
            }
        }
        return max;
    }
}
