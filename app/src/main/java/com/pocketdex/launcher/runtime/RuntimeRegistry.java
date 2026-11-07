package com.pocketdex.launcher.runtime;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Resolves app descriptors to their runtime without coupling the shell to a backend. */
public final class RuntimeRegistry {
    public static final String ANDROID = "android";

    private static final RuntimeRegistry DEFAULT = new RuntimeRegistry();
    private final ConcurrentMap<String, RuntimeProvider> providers =
            new ConcurrentHashMap<>();

    public RuntimeRegistry() {
        register(new AndroidAppRuntime());
        register(new GuacamoleRuntime());
    }

    public static RuntimeRegistry getDefault() {
        return DEFAULT;
    }

    public RuntimeProvider find(String runtimeId) {
        return providers.get(runtimeId);
    }

    /** Adds a backend; duplicate ids must be resolved explicitly by the owner. */
    public void register(RuntimeProvider provider) {
        if (provider == null || provider.id() == null || provider.id().trim().isEmpty()) {
            throw new IllegalArgumentException("runtime provider must have an id");
        }
        RuntimeProvider previous = providers.putIfAbsent(provider.id(), provider);
        if (previous != null) {
            throw new IllegalStateException("runtime already registered: " + provider.id());
        }
    }
}
