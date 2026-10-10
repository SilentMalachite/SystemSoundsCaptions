package captions.audio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DecodersTest {

    @TempDir
    Path tmp;

    private Path wav(AudioFormat format, int frames) throws IOException {
        byte[] data = new byte[frames * format.getFrameSize()];
        Path file = tmp.resolve("test.wav");
        try (var in = new AudioInputStream(new ByteArrayInputStream(data), format, frames)) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, file.toFile());
        }
        return file;
    }

    @Test
    void opensWavAsPcm16LittleEndian() throws IOException {
        Path file = wav(new AudioFormat(22_050f, 16, 1, true, false), 22_050);
        try (var decoded = Decoders.open(file)) {
            AudioFormat f = decoded.stream().getFormat();
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, f.getEncoding());
            assertEquals(16, f.getSampleSizeInBits());
            assertFalse(f.isBigEndian());
            assertEquals(Duration.ofSeconds(1), decoded.total());
        }
    }

    @Test
    void converts8BitWav() throws IOException {
        Path file = wav(new AudioFormat(8_000f, 8, 2, false, false), 800);
        try (var decoded = Decoders.open(file)) {
            AudioFormat f = decoded.stream().getFormat();
            assertEquals(16, f.getSampleSizeInBits());
            assertEquals(2, f.getChannels());
            assertEquals(8_000f, f.getSampleRate());
        }
    }
}
