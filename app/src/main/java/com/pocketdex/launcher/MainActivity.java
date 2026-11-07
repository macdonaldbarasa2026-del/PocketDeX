package com.pocketdex.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Toast;

import com.pocketdex.launcher.runtime.DisplayUtils;

public class MainActivity extends Activity implements DesktopView.Host {

    private DesktopView desktop;
    private DesktopPresentation presentation;
    private DisplayManager displayManager;
    private int cascade;
    private static final int RC_ECO_FILES_ROOT = 71;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable updateDisplayTask = this::updateExternalDisplay;

    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    scheduleDisplayUpdate();
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                    scheduleDisplayUpdate();
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    scheduleDisplayUpdate();
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        displayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        desktop = new DesktopView(this, this);
        setContentView(desktop);
        applySystemUi();
        displayManager.registerDisplayListener(displayListener, handler);
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySystemUi();
        if (desktop != null) {
            desktop.onResume();
        }
        updateExternalDisplay();
    }

    @Override
    protected void onDestroy() {
        displayManager.unregisterDisplayListener(displayListener);
        dismissPresentation();
        if (desktop != null) {
            desktop.onDestroy();
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemUi();
        if (desktop != null) {
            desktop.onConfigurationChanged();
        }
        scheduleDisplayUpdate();
    }

    private void scheduleDisplayUpdate() {
        handler.removeCallbacks(updateDisplayTask);
        handler.postDelayed(updateDisplayTask, 500L);
    }

    private void applySystemUi() {
        Window window = getWindow();
        boolean fullscreen = Prefs.fullscreen(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(!fullscreen);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                if (fullscreen) {
                    controller.hide(WindowInsets.Type.statusBars()
                            | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                } else {
                    controller.show(WindowInsets.Type.statusBars()
                            | WindowInsets.Type.navigationBars());
                }
            }
        } else {
            View decor = window.getDecorView();
            if (fullscreen) {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            } else {
                decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (desktop != null && desktop.handleKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (desktop != null && desktop.handleDispatchKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (desktop != null && desktop.handleBack()) {
            return;
        }
        moveTaskToBack(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_ECO_FILES_ROOT && resultCode == RESULT_OK
                && data != null) {
            com.pocketdex.launcher.eco.FilesActivity.handleTreeResult(this, data);
            if (desktop != null) {
                desktop.onFilesRootChanged();
            }
        }
    }

    @Override
    public void applyFullscreen() {
        applySystemUi();
    }

    @Override
    public void pickFilesRoot() {
        com.pocketdex.launcher.eco.FilesActivity.pickRoot(this, RC_ECO_FILES_ROOT);
    }

    @Override
    public Context hostContext() {
        return this;
    }

    @Override
    public Display hostDisplay() {
        return getWindowManager().getDefaultDisplay();
    }

    @Override
    public int hostDisplayId() {
        return Display.DEFAULT_DISPLAY;
    }

    @Override
    public boolean hasExternalDisplay() {
        return findExternalDisplay() != null;
    }

    @Override
    public void launchApp(AppRepo.AppEntry app, boolean windowed) {
        launchOnDisplay(app, Display.DEFAULT_DISPLAY, windowed);
        if (desktop != null) {
            desktop.onAppLaunched(app);
        }
    }

    @Override
    public void launchAppOnExternal(AppRepo.AppEntry app) {
        Display target = findExternalDisplay();
        if (target == null) {
            Toast.makeText(this, getString(R.string.external_unsupported),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        launchOnDisplay(app, target.getDisplayId(), true);
        if (desktop != null) {
            desktop.onAppLaunched(app);
        }
    }

    void launchOnDisplay(AppRepo.AppEntry app, int displayId, boolean windowed) {
        if (app == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(app.component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            boolean hasOptions = false;
            if (windowed) {
                if (getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
                    Rect bounds = nextWindowBounds(displayId);
                    options.setLaunchBounds(bounds);
                    hasOptions = true;
                } else {
                    Toast.makeText(this,
                            "Android does not advertise freeform windows; opening normally.",
                            Toast.LENGTH_SHORT).show();
                }
            }
            if (displayId != Display.DEFAULT_DISPLAY
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                options.setLaunchDisplayId(displayId);
                hasOptions = true;
            }
            if (hasOptions) {
                startActivity(intent, options.toBundle());
            } else {
                startActivity(intent);
            }
        } catch (Exception first) {
            try {
                startActivity(intent);
                if (displayId != Display.DEFAULT_DISPLAY) {
                    Toast.makeText(this,
                            "Android could not open " + app.label
                                    + " on that display; opened it on the phone instead.",
                            Toast.LENGTH_LONG).show();
                }
            } catch (Exception second) {
                Toast.makeText(this, "Unable to open " + app.label,
                        Toast.LENGTH_SHORT).show();
            }
        }
        AppRepo.addRecent(this, app.key());
    }

    private Rect nextWindowBounds(int displayId) {
        Display display = displayId == Display.DEFAULT_DISPLAY
                ? getWindowManager().getDefaultDisplay()
                : findDisplayById(displayId);
        DisplayMetrics metrics = new DisplayMetrics();
        if (display != null) {
            display.getRealMetrics(metrics);
        } else {
            metrics.setTo(getResources().getDisplayMetrics());
        }
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            metrics.setTo(getResources().getDisplayMetrics());
        }
        float density = metrics.density > 0f
                ? metrics.density : getResources().getDisplayMetrics().density;
        int minWidth = Math.round(320f * density);
        int minHeight = Math.round(240f * density);
        int width = Math.min(metrics.widthPixels,
                Math.max(minWidth, Math.round(metrics.widthPixels * 0.72f)));
        int height = Math.min(metrics.heightPixels,
                Math.max(minHeight, Math.round(metrics.heightPixels * 0.70f)));
        int step = Math.round(22f * density) * (cascade % 5);
        int desiredX = Math.round((metrics.widthPixels - width) / 2f)
                - Math.round(90f * density) + step;
        int desiredY = Math.round(metrics.heightPixels * 0.08f) + step;
        int x = Math.max(0, Math.min(metrics.widthPixels - width, desiredX));
        int y = Math.max(0, Math.min(metrics.heightPixels - height, desiredY));
        cascade++;
        return new Rect(x, y, x + width, y + height);
    }

    private Display findDisplayById(int displayId) {
        for (Display display : displayManager.getDisplays()) {
            if (display.getDisplayId() == displayId) {
                return display;
            }
        }
        return null;
    }

    private Display findExternalDisplay() {
        if (displayManager == null) {
            return null;
        }
        for (Display display : displayManager.getDisplays(
                DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (isUsableExternal(display)) {
                return display;
            }
        }
        for (Display display : displayManager.getDisplays()) {
            if (isUsableExternal(display)) {
                return display;
            }
        }
        return null;
    }

    private boolean isUsableExternal(Display display) {
        if (!DisplayUtils.isExternal(display)) {
            return false;
        }
        if (display.getState() != Display.STATE_ON) {
            return false;
        }
        return (display.getFlags() & Display.FLAG_PRIVATE) == 0;
    }

    private void updateExternalDisplay() {
        if (!Prefs.externalDisplay(this)) {
            dismissPresentation();
            return;
        }
        Display target = findPresentationDisplay();
        if (target == null) {
            dismissPresentation();
            return;
        }
        if (presentation != null) {
            Display current = presentation.getDisplay();
            if (current != null
                    && current.getDisplayId() == target.getDisplayId()
                    && presentation.isShowing()) {
                return;
            }
        }
        dismissPresentation();
        try {
            DesktopPresentation next = new DesktopPresentation(this, target, this);
            next.setOnDismissListener(dialog -> {
                if (presentation == next) {
                    presentation = null;
                }
            });
            next.show();
            presentation = next;
        } catch (Exception ignored) {
            presentation = null;
        }
    }

    private Display findPresentationDisplay() {
        if (displayManager == null) {
            return null;
        }
        for (Display display : displayManager.getDisplays(
                DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (DisplayUtils.isUsablePresentationDisplay(display)) {
                return display;
            }
        }
        return null;
    }

    private void dismissPresentation() {
        if (presentation != null) {
            try {
                presentation.dismiss();
            } catch (Exception ignored) {
            }
            presentation = null;
        }
    }

    @Override
    public void openSettings() {
        try {
            startActivity(new Intent(this, SettingsActivity.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void openWallpaper() {
        try {
            Intent intent = new Intent(Intent.ACTION_SET_WALLPAPER);
            startActivity(Intent.createChooser(intent, getString(R.string.wallpaper)));
        } catch (Exception e) {
            openSettings();
        }
    }

    @Override
    public void openHomeSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void openAppInfo(AppRepo.AppEntry app) {
        try {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + app.packageName));
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void uninstallApp(AppRepo.AppEntry app) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + app.packageName));
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void goHome() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
