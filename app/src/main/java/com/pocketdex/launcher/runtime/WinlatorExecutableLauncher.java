package com.pocketdex.launcher.runtime;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import android.widget.Toast;

import com.pocketdex.launcher.BuildConfig;

/** Stages a selected Windows installer and opens the supported Winlator entry point. */
public final class WinlatorExecutableLauncher {
    private WinlatorExecutableLauncher() {
    }

    public static void stage(Context context, Uri source, String name) {
        if (!BuildConfig.EMBEDDED_WINLATOR
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            show(context, "Winlator file transfer needs Android 10 or later.");
            return;
        }
        if (!WindowsExecutableFile.isExecutable(name)) {
            show(context, "Choose a Windows .exe file.");
            return;
        }

        AppDescriptor app = new AppDescriptor(WinlatorRuntime.APP_ID, "Local Windows",
                WinlatorRuntime.RUNTIME_ID, "winlator-home");
        RuntimeProvider runtime = RuntimeRegistry.getDefault()
                .find(WinlatorRuntime.RUNTIME_ID);
        if (runtime == null) {
            show(context, "Local Windows runtime is unavailable.");
            return;
        }
        DeviceCapabilityProfile capabilities = DeviceCapabilityProfile.capture(context);
        SupportReport report = runtime.inspect(context, app, capabilities);
        if (!report.isReady()) {
            show(context, report.message());
            return;
        }

        Context appContext = context.getApplicationContext();
        show(context, BuildConfig.EMBEDDED_WINLATOR
                ? "Copying into PocketDeX's private Windows drive…"
                : "Copying to Downloads/PocketDeX…");
        new Thread(() -> {
            try {
                if (BuildConfig.EMBEDDED_WINLATOR) {
                    WinlatorExecutableTransfer.copyToPocketDeXRuntime(appContext, source, name);
                } else {
                    WinlatorExecutableTransfer.copyToSharedDownloads(appContext, source, name);
                }
                try {
                    runtime.launch(appContext, app, capabilities);
                    show(appContext, "In Winlator, open "
                            + WindowsExecutableFile.guestPath(name));
                } catch (Exception e) {
                    show(appContext,
                            "Copied to Downloads/PocketDeX, but Winlator could not open.");
                }
            } catch (Exception e) {
                show(appContext, "Could not copy this executable to shared Downloads.");
            }
        }, "PocketDeX-exe-transfer").start();
    }

    private static void show(Context context, String message) {
        Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(appContext,
                message, Toast.LENGTH_LONG).show());
    }
}
