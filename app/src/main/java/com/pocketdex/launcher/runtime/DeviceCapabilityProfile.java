package com.pocketdex.launcher.runtime;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.InputDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A point-in-time, on-device capability snapshot for runtime selection. */
public final class DeviceCapabilityProfile {
    private final int androidApi;
    private final String manufacturer;
    private final String model;
    private final List<String> supportedAbis;
    private final long advertisedMemoryBytes;
    private final long totalMemoryBytes;
    private final long availableMemoryBytes;
    private final boolean lowMemory;
    private final int presentationDisplayCount;
    private final List<String> externalDisplaySummaries;
    private final boolean secondaryDisplayActivitiesSupported;
    private final boolean freeformWindowManagementSupported;
    private final boolean physicalKeyboardConnected;
    private final boolean pointerConnected;

    private DeviceCapabilityProfile(int androidApi, String manufacturer, String model,
                                    List<String> supportedAbis, long advertisedMemoryBytes,
                                    long totalMemoryBytes,
                                    long availableMemoryBytes, boolean lowMemory,
                                    int presentationDisplayCount,
                                    List<String> externalDisplaySummaries,
                                    boolean secondaryDisplayActivitiesSupported,
                                    boolean freeformWindowManagementSupported,
                                    boolean physicalKeyboardConnected,
                                    boolean pointerConnected) {
        this.androidApi = androidApi;
        this.manufacturer = manufacturer;
        this.model = model;
        this.supportedAbis = Collections.unmodifiableList(
                new ArrayList<>(supportedAbis));
        this.advertisedMemoryBytes = advertisedMemoryBytes;
        this.totalMemoryBytes = totalMemoryBytes;
        this.availableMemoryBytes = availableMemoryBytes;
        this.lowMemory = lowMemory;
        this.presentationDisplayCount = presentationDisplayCount;
        this.externalDisplaySummaries = Collections.unmodifiableList(
                new ArrayList<>(externalDisplaySummaries));
        this.secondaryDisplayActivitiesSupported = secondaryDisplayActivitiesSupported;
        this.freeformWindowManagementSupported = freeformWindowManagementSupported;
        this.physicalKeyboardConnected = physicalKeyboardConnected;
        this.pointerConnected = pointerConnected;
    }

    public static DeviceCapabilityProfile capture(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        long advertisedMemory = 0L;
        long totalMemory = 0L;
        long availableMemory = 0L;
        boolean lowMemory = false;
        ActivityManager activityManager = (ActivityManager)
                context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memory);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                advertisedMemory = memory.advertisedMem;
            }
            totalMemory = memory.totalMem;
            availableMemory = memory.availMem;
            lowMemory = memory.lowMemory;
        }

        int presentationDisplays = 0;
        List<String> externalDisplaySummaries = new ArrayList<>();
        DisplayManager displayManager = (DisplayManager)
                context.getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager != null) {
            Display[] displays = displayManager.getDisplays(
                    DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            if (displays != null) {
                for (Display display : displays) {
                    if (DisplayUtils.isUsablePresentationDisplay(display)) {
                        presentationDisplays++;
                        externalDisplaySummaries.add(
                                summarizeExternalDisplay(context, display, presentationDisplays));
                    }
                }
            }
        }

        List<String> abis = new ArrayList<>();
        Collections.addAll(abis, Build.SUPPORTED_ABIS);
        boolean secondaryDisplayActivities = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS);
        boolean freeformWindowManagement = context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT);
        boolean physicalKeyboard = false;
        boolean pointer = false;
        for (int deviceId : InputDevice.getDeviceIds()) {
            InputDevice input = InputDevice.getDevice(deviceId);
            if (input == null || input.isVirtual()) {
                continue;
            }
            physicalKeyboard |= input.supportsSource(InputDevice.SOURCE_KEYBOARD);
            pointer |= input.supportsSource(InputDevice.SOURCE_MOUSE)
                    || input.supportsSource(InputDevice.SOURCE_STYLUS);
        }
        return new DeviceCapabilityProfile(Build.VERSION.SDK_INT, Build.MANUFACTURER,
                Build.MODEL, abis, advertisedMemory, totalMemory, availableMemory, lowMemory,
                presentationDisplays, externalDisplaySummaries,
                secondaryDisplayActivities,
                freeformWindowManagement, physicalKeyboard, pointer);
    }

    private static String summarizeExternalDisplay(Context context, Display display,
                                                   int index) {
        StringBuilder summary = new StringBuilder("External display ").append(index).append(": ");
        Display.Mode mode = display.getMode();
        if (mode != null && mode.getPhysicalWidth() > 0 && mode.getPhysicalHeight() > 0) {
            summary.append(mode.getPhysicalWidth()).append(" × ")
                    .append(mode.getPhysicalHeight()).append(" px");
        } else {
            summary.append("resolution unavailable");
        }
        float refreshRate = mode == null ? display.getRefreshRate() : mode.getRefreshRate();
        if (refreshRate > 0f) {
            summary.append(", ").append(Math.round(refreshRate)).append(" Hz");
        }
        try {
            int densityDpi = context.createDisplayContext(display)
                    .getResources().getConfiguration().densityDpi;
            if (densityDpi > 0) {
                summary.append(", ").append(densityDpi).append(" dpi");
            }
        } catch (Exception ignored) {
            // Resolution and refresh rate remain useful if display resources are unavailable.
        }
        return summary.toString();
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

    /** Retail-advertised device memory when Android 14+ reports it, otherwise zero. */
    public long advertisedMemoryBytes() {
        return advertisedMemoryBytes;
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

    /** Active mode and logical density for each eligible external presentation display. */
    public List<String> externalDisplaySummaries() {
        return externalDisplaySummaries;
    }

    public boolean supportsActivitiesOnSecondaryDisplays() {
        return secondaryDisplayActivitiesSupported;
    }

    /** Whether Android advertises support for launching activities with freeform bounds. */
    public boolean supportsFreeformWindowManagement() {
        return freeformWindowManagementSupported;
    }

    /** Whether Android currently reports a connected, non-virtual keyboard device. */
    public boolean hasPhysicalKeyboardConnected() {
        return physicalKeyboardConnected;
    }

    /** Whether Android currently reports a connected, non-virtual mouse or stylus. */
    public boolean hasPointerConnected() {
        return pointerConnected;
    }
}
