package com.pocketdex.launcher.runtime;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.PowerManager;
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
    private final int appMemoryClassMb;
    private final boolean lowMemory;
    private final int presentationDisplayCount;
    private final List<String> externalDisplaySummaries;
    private final boolean secondaryDisplayActivitiesSupported;
    private final boolean freeformWindowManagementSupported;
    private final boolean physicalKeyboardConnected;
    private final boolean pointerConnected;
    private final int thermalStatus;
    private final float thermalHeadroom;

    private DeviceCapabilityProfile(int androidApi, String manufacturer, String model,
                                    List<String> supportedAbis, long advertisedMemoryBytes,
                                    long totalMemoryBytes,
                                    long availableMemoryBytes, int appMemoryClassMb,
                                    boolean lowMemory,
                                    int presentationDisplayCount,
                                    List<String> externalDisplaySummaries,
                                    boolean secondaryDisplayActivitiesSupported,
                                    boolean freeformWindowManagementSupported,
                                    boolean physicalKeyboardConnected,
                                    boolean pointerConnected,
                                    int thermalStatus,
                                    float thermalHeadroom) {
        this.androidApi = androidApi;
        this.manufacturer = manufacturer;
        this.model = model;
        this.supportedAbis = Collections.unmodifiableList(
                new ArrayList<>(supportedAbis));
        this.advertisedMemoryBytes = advertisedMemoryBytes;
        this.totalMemoryBytes = totalMemoryBytes;
        this.availableMemoryBytes = availableMemoryBytes;
        this.appMemoryClassMb = appMemoryClassMb;
        this.lowMemory = lowMemory;
        this.presentationDisplayCount = presentationDisplayCount;
        this.externalDisplaySummaries = Collections.unmodifiableList(
                new ArrayList<>(externalDisplaySummaries));
        this.secondaryDisplayActivitiesSupported = secondaryDisplayActivitiesSupported;
        this.freeformWindowManagementSupported = freeformWindowManagementSupported;
        this.physicalKeyboardConnected = physicalKeyboardConnected;
        this.pointerConnected = pointerConnected;
        this.thermalStatus = thermalStatus;
        this.thermalHeadroom = thermalHeadroom;
    }

    public static DeviceCapabilityProfile capture(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }

        long advertisedMemory = 0L;
        long totalMemory = 0L;
        long availableMemory = 0L;
        int appMemoryClassMb = 0;
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
            appMemoryClassMb = activityManager.getMemoryClass();
            lowMemory = memory.lowMemory;
        }

        int thermalStatus = -1;
        float thermalHeadroom = Float.NaN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            PowerManager powerManager = (PowerManager)
                    context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                thermalStatus = powerManager.getCurrentThermalStatus();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        thermalHeadroom = powerManager.getThermalHeadroom(10);
                    } catch (RuntimeException ignored) {
                        // Some devices do not expose a usable thermal headroom estimate.
                    }
                }
            }
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
                Build.MODEL, abis, advertisedMemory, totalMemory, availableMemory,
                appMemoryClassMb, lowMemory,
                presentationDisplays, externalDisplaySummaries,
                secondaryDisplayActivities,
                freeformWindowManagement, physicalKeyboard, pointer, thermalStatus,
                thermalHeadroom);
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
        DisplayWindowMetrics appMetrics = DisplayWindowMetrics.forDisplay(context, display);
        if (appMetrics.widthPixels > 0 && appMetrics.heightPixels > 0) {
            summary.append(", max app area ").append(appMetrics.widthPixels)
                    .append(" × ").append(appMetrics.heightPixels).append(" px");
        }
        int densityDpi = Math.round(appMetrics.density * 160f);
        if (densityDpi > 0) {
            summary.append(", ").append(densityDpi).append(" dpi");
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

    /** Approximate Android per-app heap class for PocketDeX, in MB. */
    public int appMemoryClassMb() {
        return appMemoryClassMb;
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

    /** Android OS thermal severity snapshot, or -1 when the API is unavailable. */
    public int thermalStatus() {
        return thermalStatus;
    }

    /** Ten-second Android thermal headroom estimate, or NaN when unavailable. */
    public float thermalHeadroom() {
        return thermalHeadroom;
    }
}
