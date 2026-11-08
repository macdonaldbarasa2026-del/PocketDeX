package com.pocketdex.launcher.runtime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class RuntimeSessionTest {
    @Test
    public void requestedSessionCanDispatchOrFail() {
        assertTrue(RuntimeSession.canTransition(RuntimeSession.State.REQUESTED,
                RuntimeSession.State.DISPATCHED));
        assertTrue(RuntimeSession.canTransition(RuntimeSession.State.REQUESTED,
                RuntimeSession.State.FAILED));
        assertFalse(RuntimeSession.canTransition(RuntimeSession.State.REQUESTED,
                RuntimeSession.State.REMOTE_CONNECTED));
    }

    @Test
    public void dispatchedSessionCanEnterRuntimeLifecycle() {
        assertTrue(RuntimeSession.canTransition(RuntimeSession.State.DISPATCHED,
                RuntimeSession.State.CONNECTING));
        assertTrue(RuntimeSession.canTransition(RuntimeSession.State.DISPATCHED,
                RuntimeSession.State.GATEWAY_READY));
        assertTrue(RuntimeSession.canTransition(RuntimeSession.State.DISPATCHED,
                RuntimeSession.State.FAILED));
    }

    @Test
    public void terminalStatesCannotRestart() {
        for (RuntimeSession.State next : RuntimeSession.State.values()) {
            assertFalse(RuntimeSession.canTransition(RuntimeSession.State.STOPPED, next));
            assertFalse(RuntimeSession.canTransition(RuntimeSession.State.FAILED, next));
        }
    }

    @Test
    public void activeSessionMayRepeatStateForProgressUpdates() {
        assertTrue(RuntimeSession.canTransition(RuntimeSession.State.CONNECTING,
                RuntimeSession.State.CONNECTING));
        assertFalse(RuntimeSession.canTransition(RuntimeSession.State.STOPPED,
                RuntimeSession.State.STOPPED));
    }
}
