package captions.caption;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Sends caption events from the recognizer thread to every subscriber. */
public final class CaptionBus {

    private final List<Consumer<CaptionEvent>> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<CaptionEvent> subscriber) {
        subscribers.add(subscriber);
    }

    public void publish(CaptionEvent event) {
        for (var subscriber : subscribers) {
            subscriber.accept(event);
        }
    }
}
