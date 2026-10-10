package captions;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The state of the start button: idle, loading, listening or error. */
public final class Session {

    public enum State { IDLE, LOADING, LISTENING, ERROR }

    public enum Event { START, READY, FAIL, STOP, END }

    public interface Listener {
        /** {@code detail} is the source name, or the error message. */
        void changed(State from, State to, String detail);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private State state = State.IDLE;

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public synchronized State state() {
        return state;
    }

    /** Moves to the next state. Returns false and changes nothing if the event does not apply now. */
    public boolean fire(Event event, String detail) {
        State from;
        State to;
        synchronized (this) {
            from = state;
            to = next(from, event);
            if (to == null) {
                return false;
            }
            state = to;
        }
        for (var listener : listeners) {
            listener.changed(from, to, detail);
        }
        return true;
    }

    static State next(State state, Event event) {
        return switch (state) {
            case IDLE, ERROR -> event == Event.START ? State.LOADING : null;
            case LOADING -> switch (event) {
                case READY -> State.LISTENING;
                case FAIL -> State.ERROR;
                case STOP -> State.IDLE;
                default -> null;
            };
            case LISTENING -> switch (event) {
                case STOP, END -> State.IDLE;
                case FAIL -> State.ERROR;
                default -> null;
            };
        };
    }
}
