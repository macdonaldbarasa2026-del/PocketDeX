package com.pocketdex.launcher.runtime;

/** Human-readable runtime availability; callers can explain why launch is unavailable. */
public final class SupportReport {
    public enum Status {
        READY,
        NEEDS_SETUP,
        UNSUPPORTED,
        UNAVAILABLE
    }

    private final Status status;
    private final String message;

    public SupportReport(Status status, String message) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
        this.message = message == null ? "" : message;
    }

    public Status status() {
        return status;
    }

    public String message() {
        return message;
    }

    public boolean isReady() {
        return status == Status.READY;
    }
}
