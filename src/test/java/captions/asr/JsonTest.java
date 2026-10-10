package captions.asr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTest {

    @Test
    void readsVoskResult() {
        assertEquals("今日 は 晴れ", Json.field("{\n  \"text\" : \"今日 は 晴れ\"\n}", "text"));
        assertEquals("こん", Json.field("{\n  \"partial\" : \"こん\"\n}", "partial"));
    }

    @Test
    void handlesEscapes() {
        assertEquals("a\"b\\cあ", Json.field("{\"text\":\"a\\\"b\\\\c\\u3042\"}", "text"));
    }

    @Test
    void missingFieldIsEmpty() {
        assertEquals("", Json.field("{\"partial\":\"x\"}", "text"));
    }
}
