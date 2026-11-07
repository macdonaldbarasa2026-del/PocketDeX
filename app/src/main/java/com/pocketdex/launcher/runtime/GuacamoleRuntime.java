package com.pocketdex.launcher.runtime;

import android.content.Context;
import android.content.Intent;

import com.pocketdex.launcher.eco.RemoteWorkspaceActivity;

/** Runtime provider for a user-managed Apache Guacamole remote workspace. */
public final class GuacamoleRuntime implements RuntimeProvider {
    @Override
    public String id() {
        return GuacamoleWorkspaceConfig.RUNTIME_ID;
    }

    @Override
    public SupportReport inspect(Context context, AppDescriptor app,
                                 DeviceCapabilityProfile capabilities) {
        if (context == null || app == null || capabilities == null
                || !id().equals(app.runtimeId())
                || !GuacamoleWorkspaceConfig.APP_ID.equals(app.id())) {
            return new SupportReport(SupportReport.Status.UNSUPPORTED,
                    "This app is not a valid remote workspace entry.");
        }
        if (GuacamoleWorkspaceConfig.endpoint(context) == null) {
            return new SupportReport(SupportReport.Status.NEEDS_SETUP,
                    "Set a valid HTTPS Guacamole server in Settings.");
        }
        return new SupportReport(SupportReport.Status.READY,
                "Remote workspace gateway is configured.");
    }

    @Override
    public void launch(Context context, AppDescriptor app,
                       DeviceCapabilityProfile capabilities) throws Exception {
        SupportReport report = inspect(context, app, capabilities);
        if (!report.isReady()) {
            throw new IllegalStateException(report.message());
        }
        Intent intent = new Intent(context, RemoteWorkspaceActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
