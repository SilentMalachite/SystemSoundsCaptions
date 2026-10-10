package captions.audio;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PcmConverterTest {

    static byte[] pcm(boolean bigEndian, int... samples) {
        byte[] out = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            byte lo = (byte) samples[i];
            byte hi = (byte) (samples[i] >> 8);
            out[i * 2] = bigEndian ? hi : lo;
            out[i * 2 + 1] = bigEndian ? lo : hi;
        }
        return out;
    }

    static short[] samples(byte[] pcm) {
        short[] out = new short[pcm.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) ((pcm[i * 2 + 1] << 8) | (pcm[i * 2] & 0xff));
        }
        return out;
    }

    @Test
    void passesTargetFormatThrough() {
        var converter = new PcmConverter(AudioSource.FORMAT);
        byte[] in = pcm(false, 1, -2, 300, -32768, 32767);
        assertArrayEquals(in, converter.convert(in, in.length));
    }

    @Test
    void averagesStereoToMono() {
        var converter = new PcmConverter(new AudioFormat(16_000f, 16, 2, true, false));
        short[] out = samples(converter.convert(pcm(false, 1000, 3000, -400, 400), 8));
        assertArrayEquals(new short[]{2000, 0}, out);
    }

    @Test
    void readsBigEndian() {
        var converter = new PcmConverter(new AudioFormat(16_000f, 16, 1, true, true));
        short[] out = samples(converter.convert(pcm(true, 1234, -5678), 4));
        assertArrayEquals(new short[]{1234, -5678}, out);
    }

    @Test
    void downsamples48kToOneThird() {
        var converter = new PcmConverter(new AudioFormat(48_000f, 16, 1, true, false));
        int[] tone = new int[4800]; // 0.1 s
        for (int i = 0; i < tone.length; i++) {
            tone[i] = 5000;
        }
        short[] out = samples(converter.convert(pcm(false, tone), tone.length * 2));
        assertEquals(1600, out.length);
        // after the filter settles, a constant stays constant
        assertEquals(5000, out[out.length - 1]);
    }

    @Test
    void chunksGiveTheSameResultAsOnePiece() {
        var format = new AudioFormat(44_100f, 16, 2, true, false);
        int[] wave = new int[44_100 * 2];
        for (int i = 0; i < wave.length; i++) {
            wave[i] = (int) (8000 * Math.sin(i / 2 * 2 * Math.PI * 440 / 44_100));
        }
        byte[] all = pcm(false, wave);

        byte[] whole = new PcmConverter(format).convert(all, all.length);

        var chunked = new PcmConverter(format);
        var out = new ByteArrayOutputStream();
        for (int off = 0; off < all.length; off += 1764) { // 441 frames
            int len = Math.min(1764, all.length - off);
            byte[] part = new byte[len];
            System.arraycopy(all, off, part, 0, len);
            out.writeBytes(chunked.convert(part, len));
        }
        short[] a = samples(whole);
        short[] b = samples(out.toByteArray());
        assertTrue(Math.abs(a.length - b.length) <= 1);
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            assertTrue(Math.abs(a[i] - b[i]) <= 1, "sample " + i); // only float rounding may differ
        }
        assertTrue(Math.abs(a.length - 16_000) <= 1);
    }

    private static double rmsOfTone(double hz) {
        var converter = new PcmConverter(new AudioFormat(48_000f, 16, 1, true, false));
        int[] tone = new int[48_000];
        for (int i = 0; i < tone.length; i++) {
            tone[i] = (int) (10_000 * Math.sin(2 * Math.PI * hz * i / 48_000));
        }
        short[] out = samples(converter.convert(pcm(false, tone), tone.length * 2));
        double sum = 0;
        for (int i = 1000; i < out.length; i++) {  // skip the filter start
            sum += (double) out[i] * out[i];
        }
        return Math.sqrt(sum / (out.length - 1000)) / (10_000 / Math.sqrt(2));
    }

    @Test
    void keepsSpeechAndRemovesSoundAbove8kHz() {
        assertTrue(rmsOfTone(1_000) > 0.95, "1 kHz must pass");
        assertTrue(rmsOfTone(12_000) < 0.02, "12 kHz must not fold back into speech");
    }

    @Test
    void refusesOtherEncodings() {
        assertThrows(IllegalArgumentException.class,
                () -> new PcmConverter(new AudioFormat(16_000f, 8, 1, true, false)));
    }

    @Test
    void findsThePeak() {
        assertEquals(0, PcmConverter.peak(new byte[3200]));
        assertEquals(32768, PcmConverter.peak(pcm(false, 5, -32768, 100)));
    }
}
