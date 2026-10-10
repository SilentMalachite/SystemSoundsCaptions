package captions;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineTest {

    @Test
    void explainsNetworkFailuresInJapanese() {
        assertTrue(Pipeline.message(new UnknownHostException("alphacephei.com")).contains("ネット接続"));
    }

    @Test
    void keepsOwnMessages() {
        assertEquals("入力デバイスを開けません: X", Pipeline.message(new IOException("入力デバイスを開けません: X")));
    }

    @Test
    void namesExceptionsWithoutMessage() {
        assertEquals("IllegalStateException", Pipeline.message(new IllegalStateException()));
    }
}
