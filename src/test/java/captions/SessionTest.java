package captions;

import captions.Session.Event;
import captions.Session.State;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {

    @Test
    void followsTheMainPath() {
        var session = new Session();
        var seen = new ArrayList<String>();
        session.addListener((from, to, detail) -> seen.add(from + ">" + to + ":" + detail));

        assertTrue(session.fire(Event.START, "マイク"));
        assertTrue(session.fire(Event.READY, "マイク"));
        assertTrue(session.fire(Event.STOP, ""));

        assertEquals(State.IDLE, session.state());
        assertEquals(java.util.List.of(
                "IDLE>LOADING:マイク", "LOADING>LISTENING:マイク", "LISTENING>IDLE:"), seen);
    }

    @Test
    void ignoresEventsThatDoNotApply() {
        var session = new Session();
        assertFalse(session.fire(Event.READY, ""));
        assertFalse(session.fire(Event.STOP, ""));
        assertEquals(State.IDLE, session.state());
    }

    @Test
    void fileEndReturnsToIdle() {
        assertEquals(State.IDLE, Session.next(State.LISTENING, Event.END));
    }

    @Test
    void failureFromLoadingOrListeningGoesToError() {
        assertEquals(State.ERROR, Session.next(State.LOADING, Event.FAIL));
        assertEquals(State.ERROR, Session.next(State.LISTENING, Event.FAIL));
    }

    @Test
    void errorCanStartAgain() {
        assertEquals(State.LOADING, Session.next(State.ERROR, Event.START));
        assertNull(Session.next(State.ERROR, Event.READY));
    }

    @Test
    void stopCancelsLoading() {
        assertEquals(State.IDLE, Session.next(State.LOADING, Event.STOP));
    }
}
