package com.pocketdex.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PackageChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        String packageName = intent.getData().getSchemeSpecificPart();
        if (context.getPackageName().equals(packageName)
                && Intent.ACTION_PACKAGE_CHANGED.equals(intent.getAction())) {
            return;
        }
        AppRepo.invalidate();
    }
}
