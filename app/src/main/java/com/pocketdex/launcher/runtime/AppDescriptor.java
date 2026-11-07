package com.pocketdex.launcher.runtime;

/** Stable catalog record passed from the desktop shell to a runtime provider. */
public final class AppDescriptor {
    private final String id;
    private final String label;
    private final String runtimeId;
    private final String entryPoint;

    public AppDescriptor(String id, String label, String runtimeId, String entryPoint) {
        this.id = requireText(id, "id");
        this.label = requireText(label, "label");
        this.runtimeId = requireText(runtimeId, "runtimeId");
        this.entryPoint = requireText(entryPoint, "entryPoint");
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String runtimeId() {
        return runtimeId;
    }

    public String entryPoint() {
        return entryPoint;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return value;
    }
}
