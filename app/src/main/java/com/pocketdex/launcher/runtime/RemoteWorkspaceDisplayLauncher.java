package com.pocketdex.launcher.runtime;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;

/** Targets a connected, eligible external display when the user opts in. */
public final class RemoteWorkspaceDisplayLauncher {
    private RemoteWorkspaceDisplayLauncher() {
    }

    public static boolean launchOnExternalDisplay(Context context, Intent intent) {
        if (context == null || intent == null
                || !GuacamoleWorkspaceConfig.launchOnExternalDisplay(context)
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !context.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)) {
            return false;
        }

        DisplayManager displays = (DisplayManager)
                context.getSystemService(Context.DISPLAY_SERVICE);
        if (displays == null) {
            return false;
        }
        for (Display display : displays.getDisplays(
                DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            try {
                if (!isEligible(context, display, intent)) {
                    continue;
                }
                ActivityOptions options = ActivityOptions.makeBasic()
                        .setLaunchDisplayId(display.getDisplayId());
                if (context.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
                    Rect bounds = nearFullScreenBounds(display);
                    if (bounds != null) {
                        options.setLaunchBounds(bounds);
                    }
                }
                context.startActivity(intent, options.toBundle());
                return true;
            } catch (Exception ignored) {
                // Device policy can still reject a display after capability checks.
            }
        }
        return false;
    }

    private static Rect nearFullScreenBounds(Display display) {
        DisplayMetrics metrics = new DisplayMetrics();
        display.getRealMetrics(metrics);
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            return null;
        }
        int marginX = Math.round(metrics.widthPixels * 0.03f);
        int marginY = Math.round(metrics.heightPixels * 0.04f);
        return new Rect(marginX, marginY,
                metrics.widthPixels - marginX, metrics.heightPixels - marginY);
    }

    private static boolean isEligible(Context context, Display display, Intent intent) {
        if (!DisplayUtils.isExternal(display)
                || display.getState() != Display.STATE_ON
                || (display.getFlags() & Display.FLAG_PRIVATE) != 0) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true;
        }
        ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        return activityManager != null && activityManager.isActivityStartAllowedOnDisplay(
                context, display.getDisplayId(), intent);
    }
}
