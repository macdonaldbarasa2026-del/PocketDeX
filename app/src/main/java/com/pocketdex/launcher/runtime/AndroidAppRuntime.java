package com.pocketdex.launcher.runtime;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;

/** Runtime provider for activities installed in Android's package manager. */
public final class AndroidAppRuntime implements RuntimeProvider {
    @Override
    public String id() {
        return RuntimeRegistry.ANDROID;
    }

    @Override
    public SupportReport inspect(Context context, AppDescriptor app,
                                 DeviceCapabilityProfile capabilities) {
        if (context == null || app == null || capabilities == null
                || !id().equals(app.runtimeId())) {
            return new SupportReport(SupportReport.Status.UNSUPPORTED,
                    "This app is not a valid Android runtime entry.");
        }
        try {
            ActivityInfo activity = context.getPackageManager()
                    .getActivityInfo(component(app), 0);
            if (!activity.enabled || !activity.applicationInfo.enabled) {
                return new SupportReport(SupportReport.Status.UNAVAILABLE,
                        "The Android app is disabled.");
            }
            return new SupportReport(SupportReport.Status.READY,
                    "Installed Android app.");
        } catch (PackageManager.NameNotFoundException e) {
            return new SupportReport(SupportReport.Status.UNAVAILABLE,
                    "The Android app is no longer installed.");
        } catch (IllegalArgumentException e) {
            return new SupportReport(SupportReport.Status.UNSUPPORTED,
                    "The Android app entry is invalid.");
        }
    }

    @Override
    public void launch(Context context, AppDescriptor app,
                       DeviceCapabilityProfile capabilities) throws Exception {
        SupportReport report = inspect(context, app, capabilities);
        if (!report.isReady()) {
            throw new IllegalStateException(report.message());
        }
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(component(app));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private ComponentName component(AppDescriptor app) {
        ComponentName component = ComponentName.unflattenFromString(app.entryPoint());
        if (component == null) {
            throw new IllegalArgumentException("Invalid Android component");
        }
        return component;
    }
}
