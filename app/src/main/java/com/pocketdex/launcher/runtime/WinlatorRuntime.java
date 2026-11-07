package com.pocketdex.launcher.runtime;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** Launch bridge to the separately installed Winlator x86-64 Windows runtime. */
public final class WinlatorRuntime implements RuntimeProvider {
    public static final String RUNTIME_ID = "winlator";
    public static final String PACKAGE_NAME = "com.winlator";
    public static final String APP_ID = "windows:winlator";
    public static final String RELEASES_URL =
            "https://github.com/brunodev85/winlator/releases/latest";

    @Override
    public String id() {
        return RUNTIME_ID;
    }

    @Override
    public SupportReport inspect(Context context, AppDescriptor app,
                                 DeviceCapabilityProfile capabilities) {
        if (context == null || app == null || capabilities == null
                || !id().equals(app.runtimeId()) || !APP_ID.equals(app.id())) {
            return new SupportReport(SupportReport.Status.UNSUPPORTED,
                    "This app is not a valid Winlator runtime entry.");
        }
        if (!capabilities.supportedAbis().contains("arm64-v8a")) {
            return new SupportReport(SupportReport.Status.UNSUPPORTED,
                    "Winlator requires a 64-bit ARM Android device.");
        }
        try {
            if (!context.getPackageManager().getApplicationInfo(PACKAGE_NAME, 0).enabled) {
                return new SupportReport(SupportReport.Status.UNAVAILABLE,
                        "Winlator is installed but disabled.");
            }
            return new SupportReport(SupportReport.Status.READY,
                    "Winlator is installed. Windows app compatibility varies by app and device.");
        } catch (PackageManager.NameNotFoundException e) {
            return new SupportReport(SupportReport.Status.NEEDS_SETUP,
                    "Install Winlator from Settings → Local Windows runtime, then return here.");
        }
    }

    @Override
    public void launch(Context context, AppDescriptor app,
                       DeviceCapabilityProfile capabilities) throws Exception {
        SupportReport report = inspect(context, app, capabilities);
        if (!report.isReady()) {
            throw new IllegalStateException(report.message());
        }
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(PACKAGE_NAME);
        if (intent == null) {
            throw new IllegalStateException("Winlator has no launchable activity.");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
