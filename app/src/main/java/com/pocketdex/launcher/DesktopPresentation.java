package com.pocketdex.launcher;

import android.app.Presentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.Window;

public class DesktopPresentation extends Presentation implements DesktopView.Host {

    private final MainActivity owner;
    private DesktopView desktop;

    public DesktopPresentation(Context outerContext, Display display, MainActivity owner) {
        super(outerContext, display);
        this.owner = owner;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0xFF0B0D12));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
            }
        }
        desktop = new DesktopView(getContext(), this);
        setContentView(desktop);
    }

    @Override
    public void onDisplayChanged() {
        super.onDisplayChanged();
        if (desktop != null) {
            desktop.onConfigurationChanged();
        }
    }

    @Override
    public Context hostContext() {
        return getContext();
    }

    @Override
    public Display hostDisplay() {
        return getDisplay();
    }

    @Override
    public int hostDisplayId() {
        Display display = getDisplay();
        return display == null ? Display.DEFAULT_DISPLAY : display.getDisplayId();
    }

    @Override
    public boolean hasExternalDisplay() {
        return false;
    }

    @Override
    public void launchApp(AppRepo.AppEntry app, boolean windowed) {
        owner.launchOnDisplay(app, hostDisplayId(), windowed);
        if (desktop != null) {
            desktop.onAppLaunched(app);
        }
    }

    @Override
    public void launchAppOnExternal(AppRepo.AppEntry app) {
        owner.launchApp(app, false);
    }

    @Override
    public void openSettings() {
        owner.openSettings();
    }

    @Override
    public void openWallpaper() {
        owner.openWallpaper();
    }

    @Override
    public void openHomeSettings() {
        owner.openHomeSettings();
    }

    @Override
    public void openAppInfo(AppRepo.AppEntry app) {
        owner.openAppInfo(app);
    }

    @Override
    public void uninstallApp(AppRepo.AppEntry app) {
        owner.uninstallApp(app);
    }

    @Override
    public void goHome() {
        owner.goHome();
    }
}
