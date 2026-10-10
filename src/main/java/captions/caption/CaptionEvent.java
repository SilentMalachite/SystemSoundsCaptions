package captions.caption;

import java.time.Instant;

/** What the recognizer says. The overlay draws these. */
public sealed interface CaptionEvent {

    /** Text that can still change. Drawn in gray. */
    record Partial(String text) implements CaptionEvent {
    }

    /** Text that will not change. Drawn in white. */
    record Final(String text, Instant at) implements CaptionEvent {
    }

    /** A problem the user must see, for example a microphone that gives no sound. */
    record Error(String message) implements CaptionEvent {
    }
}
