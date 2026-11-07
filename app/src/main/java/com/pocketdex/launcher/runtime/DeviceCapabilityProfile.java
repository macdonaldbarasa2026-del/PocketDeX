package com.pocketdex.launcher.runtime;

import android.app.ActivityManager;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A point-in-time, on-device capability snapshot for runtime selection. */
public final class DeviceCapabilityProfile {
    private final int androidApi;
    private final String manufacturer;
    private final String model;
    private final List<String> supportedAbis;
    private final long totalMemoryBytes;
    private final long availableMemoryBytes;
    private final boolean lowMemory;
    private final int presentationDisplayCount;

    private DeviceCapabilityProfile(int androidApi, String manufacturer, String model,
                                    List<String> supportedAbis, long totalMemoryBytes,
                                    long availableMemoryBytes, boolean lowMemory,
                                    int presentationDisplayCount) {
        this.androidApi = androidApi;
        this.manufacturer = manufacturer;
        this.model = model;
        this.supportedAbis = Collections.unmodifiableList(
                new ArrayList<>(supportedAbis));
        this.totalMemoryBytes = totalMemoryBytes;
        this.availableMemoryBytes = availableMemoryBytes;
        this.lowMemory = lowMemory;
        this.presentationDisplayCount = presentationDisplayCount;
    }

    public static DeviceCapabilityProfile capture(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        long totalMemory = 0L;
        long availableMemory = 0L;
        boolean lowMemory = false;
        ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memory);
            totalMemory = memory.totalMem;
            availableMemory = memory.availMem;
            lowMemory = memory.lowMemory;
        }

        int presentationDisplays = 0;
        DisplayManager displayManager = (DisplayManager)
                context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            Display[] displays = displayManager.getDisplays(
                    DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            presentationDisplays = displays == null ? 0 : displays.length;
        }

        List<String> abis = new ArrayList<>();
        Collections.addAll(abis, Build.SUPPORTED_ABIS);
        return new DeviceCapabilityProfile(Build.VERSION.SDK_INT, Build.MANUFACTURER,
                Build.MODEL, abis, totalMemory, availableMemory, lowMemory,
                presentationDisplays);
    }

    public int androidApi() {
        return androidApi;
    }

    public String manufacturer() {
        return manufacturer;
    }

    public String model() {
        return model;
    }

    public List<String> supportedAbis() {
        return supportedAbis;
    }

    public long totalMemoryBytes() {
        return totalMemoryBytes;
    }

    public long availableMemoryBytes() {
        return availableMemoryBytes;
    }

    public boolean isLowMemory() {
        return lowMemory;
    }

    public int presentationDisplayCount() {
        return presentationDisplayCount;
    }
}
