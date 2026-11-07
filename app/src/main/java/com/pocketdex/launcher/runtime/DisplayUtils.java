package com.pocketdex.launcher.runtime;

import android.view.Display;

import java.lang.reflect.Method;

/** Display helpers that remain compatible with Android releases before Display.isInternal(). */
public final class DisplayUtils {
    private static final Method IS_INTERNAL_METHOD = resolveIsInternalMethod();

    private DisplayUtils() {
    }

    /**
     * Returns whether a display is a non-default external display. On platform versions exposing
     * Display.isInternal(), built-in displays are excluded even if they support presentations.
     */
    public static boolean isExternal(Display display) {
        if (display == null || display.getDisplayId() == Display.DEFAULT_DISPLAY) {
            return false;
        }
        if (IS_INTERNAL_METHOD != null) {
            try {
                return !((Boolean) IS_INTERNAL_METHOD.invoke(display));
            } catch (Exception ignored) {
                // Fall back to the pre-isInternal() display-id convention.
            }
        }
        return true;
    }

    /** Returns whether the display can currently host a public presentation window. */
    public static boolean isUsablePresentationDisplay(Display display) {
        return isExternal(display)
                && display.getState() == Display.STATE_ON
                && (display.getFlags() & Display.FLAG_PRIVATE) == 0
                && (display.getFlags() & Display.FLAG_PRESENTATION) != 0;
    }

    private static Method resolveIsInternalMethod() {
        try {
            return Display.class.getMethod("isInternal");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
