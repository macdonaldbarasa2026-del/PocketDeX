package com.pocketdex.launcher.runtime;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

/** Display-specific launch dimensions and density using modern window metrics when available. */
public final class DisplayWindowMetrics {
    public final int widthPixels;
    public final int heightPixels;
    public final float density;

    private DisplayWindowMetrics(int widthPixels, int heightPixels, float density) {
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.density = density;
    }

    public static DisplayWindowMetrics forDisplay(Context context, Display display) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        float density = context.getResources().getDisplayMetrics().density;
        if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Context displayContext = context.createDisplayContext(display);
                int densityDpi = displayContext.getResources()
                        .getConfiguration().densityDpi;
                if (densityDpi > 0) {
                    density = densityDpi / 160f;
                }
                Context windowContext = displayContext.createWindowContext(
                        WindowManager.LayoutParams.TYPE_APPLICATION, null);
                WindowManager windowManager = windowContext.getSystemService(
                        WindowManager.class);
                if (windowManager != null) {
                    Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        return new DisplayWindowMetrics(
                                bounds.width(), bounds.height(), density);
                    }
                }
            } catch (RuntimeException ignored) {
                // Use the legacy display metrics fallback on devices without usable window data.
            }
        }

        DisplayMetrics legacyMetrics = new DisplayMetrics();
        if (display != null) {
            try {
                display.getRealMetrics(legacyMetrics);
                if (legacyMetrics.widthPixels > 0 && legacyMetrics.heightPixels > 0) {
                    if (legacyMetrics.density > 0f) {
                        density = legacyMetrics.density;
                    }
                    return new DisplayWindowMetrics(legacyMetrics.widthPixels,
                            legacyMetrics.heightPixels, density);
                }
            } catch (RuntimeException ignored) {
                // Use this context's dimensions below if the display is no longer available.
            }
        }

        DisplayMetrics fallback = context.getResources().getDisplayMetrics();
        return new DisplayWindowMetrics(fallback.widthPixels, fallback.heightPixels,
                density > 0f ? density : 1f);
    }
}
