package com.pocketdex.launcher.runtime;

import android.content.Context;

/** A local or remote execution backend that can inspect and launch catalog entries. */
public interface RuntimeProvider {
    String id();

    SupportReport inspect(Context context, AppDescriptor app);

    void launch(Context context, AppDescriptor app) throws Exception;
}
