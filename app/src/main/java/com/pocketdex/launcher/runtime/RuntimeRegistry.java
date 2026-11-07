package com.pocketdex.launcher.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Resolves app descriptors to their runtime without coupling the shell to a backend. */
public final class RuntimeRegistry {
    public static final String ANDROID = "android";

    private static final RuntimeRegistry DEFAULT = createDefault();
    private final Map<String, RuntimeProvider> providers;

    private RuntimeRegistry(Map<String, RuntimeProvider> providers) {
        this.providers = Collections.unmodifiableMap(providers);
    }

    public static RuntimeRegistry getDefault() {
        return DEFAULT;
    }

    public RuntimeProvider find(String runtimeId) {
        return providers.get(runtimeId);
    }

    private static RuntimeRegistry createDefault() {
        Map<String, RuntimeProvider> providers = new LinkedHashMap<>();
        RuntimeProvider android = new AndroidAppRuntime();
        providers.put(android.id(), android);
        return new RuntimeRegistry(providers);
    }
}
