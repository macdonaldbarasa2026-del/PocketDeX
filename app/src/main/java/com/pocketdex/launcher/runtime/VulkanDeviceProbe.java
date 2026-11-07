package com.pocketdex.launcher.runtime;

/** Reads GPU identity from Android's Vulkan loader for local compatibility research. */
public final class VulkanDeviceProbe {
    private static final boolean NATIVE_AVAILABLE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("pocketdex_vulkan_probe");
            loaded = true;
        } catch (UnsatisfiedLinkError | SecurityException ignored) {
            loaded = false;
        }
        NATIVE_AVAILABLE = loaded;
    }

    private VulkanDeviceProbe() {
    }

    /**
     * Returns the GPU identity reported to this Android process, not a runtime-specific
     * driver selection such as Winlator's Turnip build.
     */
    public static String query() {
        if (!NATIVE_AVAILABLE) {
            return "Vulkan probe unavailable for this app ABI.";
        }
        try {
            String result = queryNative();
            return result == null || result.isEmpty() ? "No Vulkan details reported." : result;
        } catch (RuntimeException | LinkageError ignored) {
            return "Vulkan query unavailable on this device.";
        }
    }

    private static native String queryNative();
}
