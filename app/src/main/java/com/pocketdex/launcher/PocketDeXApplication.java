package com.pocketdex.launcher;

import android.app.Application;
import android.content.Context;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Performs early setup for the optional in-process Winlator runtime flavor. */
public final class PocketDeXApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (!BuildConfig.EMBEDDED_WINLATOR) {
            return;
        }
        try {
            Class<?> appUtils = Class.forName("com.winlator.core.AppUtils");
            Method configure = appUtils.getMethod("configurePocketDeXStorage", Context.class);
            configure.invoke(null, this);
        } catch (ClassNotFoundException | NoSuchMethodException
                 | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException(
                    "Embedded Winlator private storage could not be initialized.", e);
        }
    }
}
