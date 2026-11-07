package com.pocketdex.launcher.runtime;

/** Persistable snapshot of a runtime launch and its observed lifecycle. */
public final class RuntimeSession {
    public enum State {
        REQUESTED,
        DISPATCHED,
        CONNECTING,
        GATEWAY_READY,
        REMOTE_CONNECTED,
        DEGRADED,
        STOPPED,
        FAILED
    }

    private final String id;
    private final String appId;
    private final String runtimeId;
    private final State state;
    private final long createdAtMillis;
    private final long updatedAtMillis;
    private final long gatewayResponseMillis;
    private final int gatewayHttpStatus;
    private final String detail;

    RuntimeSession(String id, String appId, String runtimeId, State state,
                   long createdAtMillis, long updatedAtMillis,
                   long gatewayResponseMillis, int gatewayHttpStatus, String detail) {
        this.id = id;
        this.appId = appId;
        this.runtimeId = runtimeId;
        this.state = state;
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.gatewayResponseMillis = gatewayResponseMillis;
        this.gatewayHttpStatus = gatewayHttpStatus;
        this.detail = detail == null ? "" : detail;
    }

    public String id() {
        return id;
    }

    public String appId() {
        return appId;
    }

    public String runtimeId() {
        return runtimeId;
    }

    public State state() {
        return state;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }

    /** Returns -1 until a gateway response has been measured. */
    public long gatewayResponseMillis() {
        return gatewayResponseMillis;
    }

    /** Returns -1 until a gateway response has been measured. */
    public int gatewayHttpStatus() {
        return gatewayHttpStatus;
    }

    public String detail() {
        return detail;
    }

    RuntimeSession transition(State next, String nextDetail, long nowMillis) {
        if (next == null || !canTransition(state, next)) {
            throw new IllegalStateException("Invalid runtime session transition: "
                    + state + " -> " + next);
        }
        return new RuntimeSession(id, appId, runtimeId, next, createdAtMillis,
                nowMillis, gatewayResponseMillis, gatewayHttpStatus, nextDetail);
    }

    RuntimeSession withGatewayResponse(long elapsedMillis, int httpStatus,
                                       String nextDetail, long nowMillis) {
        return new RuntimeSession(id, appId, runtimeId, state, createdAtMillis,
                nowMillis, elapsedMillis, httpStatus, nextDetail);
    }

    static boolean canTransition(State current, State next) {
        if (current == next && current != State.STOPPED && current != State.FAILED) {
            return true;
        }
        switch (current) {
            case REQUESTED:
                return next == State.DISPATCHED || next == State.FAILED;
            case DISPATCHED:
                return next == State.CONNECTING || next == State.GATEWAY_READY
                        || next == State.DEGRADED || next == State.STOPPED
                        || next == State.FAILED;
            case CONNECTING:
                return next == State.GATEWAY_READY || next == State.DEGRADED
                        || next == State.REMOTE_CONNECTED || next == State.STOPPED;
            case GATEWAY_READY:
                return next == State.CONNECTING || next == State.DEGRADED
                        || next == State.REMOTE_CONNECTED || next == State.STOPPED;
            case REMOTE_CONNECTED:
                return next == State.CONNECTING || next == State.GATEWAY_READY
                        || next == State.DEGRADED || next == State.STOPPED;
            case DEGRADED:
                return next == State.CONNECTING || next == State.GATEWAY_READY
                        || next == State.REMOTE_CONNECTED || next == State.STOPPED;
            case STOPPED:
            case FAILED:
            default:
                return false;
        }
    }
}
