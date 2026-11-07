package com.pocketdex.launcher.runtime;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Stores a short local history of runtime state snapshots, never credentials or file data. */
public final class RuntimeSessionStore {
    public static final String EXTRA_SESSION_ID =
            "com.pocketdex.launcher.extra.RUNTIME_SESSION_ID";
    private static final String PREFS = "pocketdex_runtime_session";
    private static final String SESSION_IDS = "session_ids";
    private static final String LATEST_ID = "latest_id";
    private static final int MAX_SESSIONS = 20;

    private RuntimeSessionStore() {
    }

    public static synchronized RuntimeSession begin(Context context, AppDescriptor app) {
        if (context == null || app == null) {
            throw new IllegalArgumentException("context and app are required");
        }
        long now = System.currentTimeMillis();
        RuntimeSession session = new RuntimeSession(UUID.randomUUID().toString(),
                app.id(), app.runtimeId(), RuntimeSession.State.REQUESTED,
                now, now, -1L, -1, "Launch requested.");
        SharedPreferences prefs = preferences(context);
        Set<String> ids = new LinkedHashSet<>(prefs.getStringSet(
                SESSION_IDS, Collections.emptySet()));
        ids.add(session.id());
        SharedPreferences.Editor editor = prefs.edit();
        while (ids.size() > MAX_SESSIONS) {
            String oldestId = oldestSessionId(prefs, ids, session);
            if (oldestId == null) {
                break;
            }
            ids.remove(oldestId);
            remove(editor, oldestId);
        }
        editor.putStringSet(SESSION_IDS, ids);
        editor.putString(LATEST_ID, session.id());
        write(editor, session);
        editor.apply();
        return session;
    }

    public static synchronized boolean transition(Context context, String sessionId,
                                                 RuntimeSession.State state, String detail) {
        if (context == null || sessionId == null) {
            return false;
        }
        SharedPreferences prefs = preferences(context);
        RuntimeSession current = load(prefs, sessionId);
        if (current == null) {
            return false;
        }
        try {
            write(prefs.edit(), current.transition(state, detail, System.currentTimeMillis()))
                    .apply();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public static synchronized void recordGatewayResponse(Context context, String sessionId,
                                                          long elapsedMillis, int httpStatus,
                                                          String detail) {
        if (context == null || sessionId == null) {
            return;
        }
        SharedPreferences prefs = preferences(context);
        RuntimeSession current = load(prefs, sessionId);
        if (current == null || current.state() == RuntimeSession.State.STOPPED
                || current.state() == RuntimeSession.State.FAILED) {
            return;
        }
        write(prefs.edit(), current.withGatewayResponse(elapsedMillis, httpStatus,
                detail, System.currentTimeMillis())).apply();
    }

    public static synchronized RuntimeSession current(Context context) {
        if (context == null) {
            return null;
        }
        SharedPreferences prefs = preferences(context);
        return load(prefs, prefs.getString(LATEST_ID, null));
    }

    public static synchronized RuntimeSession find(Context context, String sessionId) {
        return context == null ? null : load(preferences(context), sessionId);
    }

    public static synchronized RuntimeSession latestForRuntime(Context context,
                                                               String runtimeId) {
        if (context == null || runtimeId == null) {
            return null;
        }
        SharedPreferences prefs = preferences(context);
        Set<String> ids = prefs.getStringSet(SESSION_IDS, Collections.emptySet());
        RuntimeSession latest = null;
        for (String id : ids) {
            RuntimeSession session = load(prefs, id);
            if (session != null && runtimeId.equals(session.runtimeId())
                    && (latest == null || session.createdAtMillis() > latest.createdAtMillis())) {
                latest = session;
            }
        }
        return latest;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static RuntimeSession load(SharedPreferences prefs, String id) {
        if (id == null) {
            return null;
        }
        String prefix = key(id, "");
        String appId = prefs.getString(prefix + "app_id", null);
        String runtimeId = prefs.getString(prefix + "runtime_id", null);
        String stateName = prefs.getString(prefix + "state", null);
        if (appId == null || runtimeId == null || stateName == null) {
            return null;
        }
        try {
            return new RuntimeSession(id, appId, runtimeId,
                    RuntimeSession.State.valueOf(stateName),
                    prefs.getLong(prefix + "created", 0L),
                    prefs.getLong(prefix + "updated", 0L),
                    prefs.getLong(prefix + "gateway_ms", -1L),
                    prefs.getInt(prefix + "gateway_status", -1),
                    prefs.getString(prefix + "detail", ""));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String oldestSessionId(SharedPreferences prefs, Set<String> ids,
                                         RuntimeSession pendingSession) {
        String oldestId = null;
        long oldestCreatedAt = Long.MAX_VALUE;
        for (String id : ids) {
            RuntimeSession session = id.equals(pendingSession.id())
                    ? pendingSession : load(prefs, id);
            long createdAt = session == null ? Long.MIN_VALUE : session.createdAtMillis();
            if (oldestId == null || createdAt < oldestCreatedAt) {
                oldestId = id;
                oldestCreatedAt = createdAt;
            }
        }
        return oldestId;
    }

    private static SharedPreferences.Editor write(SharedPreferences.Editor editor,
                                                  RuntimeSession session) {
        String prefix = key(session.id(), "");
        return editor
                .putString(prefix + "app_id", session.appId())
                .putString(prefix + "runtime_id", session.runtimeId())
                .putString(prefix + "state", session.state().name())
                .putLong(prefix + "created", session.createdAtMillis())
                .putLong(prefix + "updated", session.updatedAtMillis())
                .putLong(prefix + "gateway_ms", session.gatewayResponseMillis())
                .putInt(prefix + "gateway_status", session.gatewayHttpStatus())
                .putString(prefix + "detail", session.detail());
    }

    private static void remove(SharedPreferences.Editor editor, String id) {
        String prefix = key(id, "");
        editor.remove(prefix + "app_id").remove(prefix + "runtime_id")
                .remove(prefix + "state").remove(prefix + "created")
                .remove(prefix + "updated").remove(prefix + "gateway_ms")
                .remove(prefix + "gateway_status").remove(prefix + "detail");
    }

    private static String key(String id, String suffix) {
        return "session." + id + "." + suffix;
    }
}
