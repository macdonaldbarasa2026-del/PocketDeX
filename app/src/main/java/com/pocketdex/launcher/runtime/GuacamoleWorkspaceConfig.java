package com.pocketdex.launcher.runtime;

import android.content.Context;
import android.net.Uri;

import java.net.URI;

/** Local configuration for the user's Guacamole web gateway. */
public final class GuacamoleWorkspaceConfig {
    public static final String RUNTIME_ID = "guacamole";
    public static final String APP_ID = "remote:workspace";
    public static final String PREFS = "pocketdex_remote";
    public static final String KEY_ENDPOINT = "guacamole_url";

    private GuacamoleWorkspaceConfig() {
    }

    public static Uri endpoint(Context context) {
        if (context == null) {
            return null;
        }
        String configured = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ENDPOINT, "");
        return normalizeEndpoint(configured);
    }

    public static void saveEndpoint(Context context, Uri endpoint) {
        if (context == null || endpoint == null) {
            throw new IllegalArgumentException("context and endpoint are required");
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ENDPOINT, endpoint.toString()).apply();
    }

    public static Uri normalizeEndpoint(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            URI parsed = new URI(value.trim());
            if (!"https".equalsIgnoreCase(parsed.getScheme())
                    || parsed.getHost() == null || parsed.getHost().isEmpty()
                    || parsed.getUserInfo() != null || parsed.getQuery() != null
                    || parsed.getPort() == 0 || parsed.getPort() > 65535) {
                return null;
            }
            String path = parsed.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (!path.endsWith("/")) {
                path += "/";
            }
            return new Uri.Builder().scheme("https")
                    .encodedAuthority(parsed.getRawAuthority())
                    .encodedPath(path)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
