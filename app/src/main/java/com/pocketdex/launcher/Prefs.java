package com.pocketdex.launcher;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    private static final String FILE = "pocketdex";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static float iconSize(Context c) {
        return get(c).getFloat("icon_size", 52f);
    }

    public static void setIconSize(Context c, float value) {
        get(c).edit().putFloat("icon_size", value).apply();
    }

    public static int columns(Context c) {
        return get(c).getInt("columns", 0);
    }

    public static void setColumns(Context c, int value) {
        get(c).edit().putInt("columns", value).apply();
    }

    public static int dim(Context c) {
        return get(c).getInt("dim", 22);
    }

    public static void setDim(Context c, int value) {
        get(c).edit().putInt("dim", value).apply();
    }

    public static boolean showLabels(Context c) {
        return get(c).getBoolean("labels", true);
    }

    public static boolean doubleClick(Context c) {
        return get(c).getBoolean("double_click", false);
    }

    public static boolean showSeconds(Context c) {
        return get(c).getBoolean("show_seconds", false);
    }

    public static boolean autoHideTaskbar(Context c) {
        return get(c).getBoolean("autohide", false);
    }

    public static boolean externalDisplay(Context c) {
        return get(c).getBoolean("external_display", true);
    }

    public static boolean windowedApps(Context c) {
        return get(c).getBoolean("windowed_apps", true);
    }

    /** Whether the launcher may dispatch activities owned by other Android packages. */
    public static boolean allowAndroidApps(Context c) {
        return get(c).getBoolean("allow_android_apps", false);
    }

    public static boolean showEcoOnDesktop(Context c) {
        return get(c).getBoolean("eco_desktop", true);
    }

    public static boolean fullscreen(Context c) {
        return get(c).getBoolean("fullscreen", true);
    }

    public static void setBoolean(Context c, String key, boolean value) {
        get(c).edit().putBoolean(key, value).apply();
    }

    public static void setInt(Context c, String key, int value) {
        get(c).edit().putInt(key, value).apply();
    }
}
