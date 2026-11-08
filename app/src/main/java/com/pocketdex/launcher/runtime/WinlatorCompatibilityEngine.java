package com.pocketdex.launcher.runtime;

import java.util.Collections;
import java.util.List;

/**
 * Hardware-only preflight for the separately installed Winlator runtime.
 * This does not probe a Wine container or select its graphics driver.
 */
public final class WinlatorCompatibilityEngine {
    public static final long BASELINE_MEMORY_BYTES = 6L * 1024L * 1024L * 1024L;

    public enum CpuSupport {
        ARM64_AVAILABLE,
        ARM64_UNAVAILABLE,
        UNKNOWN
    }

    public enum MemoryTier {
        MEETS_REFERENCE,
        BELOW_REFERENCE,
        UNKNOWN
    }

    public enum VulkanVisibility {
        SYSTEM_DEVICE_REPORTED,
        NO_SYSTEM_DEVICE_REPORTED,
        UNKNOWN
    }

    public static final class Assessment {
        private final CpuSupport cpuSupport;
        private final MemoryTier memoryTier;
        private final VulkanVisibility vulkanVisibility;

        private Assessment(CpuSupport cpuSupport, MemoryTier memoryTier,
                           VulkanVisibility vulkanVisibility) {
            this.cpuSupport = cpuSupport;
            this.memoryTier = memoryTier;
            this.vulkanVisibility = vulkanVisibility;
        }

        public CpuSupport cpuSupport() {
            return cpuSupport;
        }

        public MemoryTier memoryTier() {
            return memoryTier;
        }

        public VulkanVisibility vulkanVisibility() {
            return vulkanVisibility;
        }

        public boolean mayAttemptWinlator() {
            return cpuSupport == CpuSupport.ARM64_AVAILABLE;
        }

        public String summary() {
            StringBuilder result = new StringBuilder();
            switch (cpuSupport) {
                case ARM64_AVAILABLE:
                    result.append("ARM64 app ABI is available");
                    break;
                case ARM64_UNAVAILABLE:
                    result.append("ARM64 app ABI is not available; Winlator cannot be launched here");
                    break;
                case UNKNOWN:
                default:
                    result.append("ARM64 app ABI support is unknown");
                    break;
            }

            result.append(". ");
            switch (memoryTier) {
                case MEETS_REFERENCE:
                    result.append("RAM meets the 6 GiB planning reference");
                    break;
                case BELOW_REFERENCE:
                    result.append("RAM is below the 6 GiB planning reference");
                    break;
                case UNKNOWN:
                default:
                    result.append("RAM total is not reported");
                    break;
            }
            result.append("; this is advisory and does not gate launch. ");

            switch (vulkanVisibility) {
                case SYSTEM_DEVICE_REPORTED:
                    result.append("Android reports a Vulkan device to PocketDeX");
                    break;
                case NO_SYSTEM_DEVICE_REPORTED:
                    result.append("Android reports no Vulkan physical device to PocketDeX");
                    break;
                case UNKNOWN:
                default:
                    result.append("Vulkan availability could not be verified");
                    break;
            }
            result.append(". This does not identify or validate Winlator's selected driver.");
            return result.toString();
        }
    }

    private WinlatorCompatibilityEngine() {
    }

    public static Assessment assess(DeviceCapabilityProfile capabilities,
                                    String systemVulkanSummary) {
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities must not be null");
        }
        return assess(capabilities.supportedAbis(), capabilities.totalMemoryBytes(),
                systemVulkanSummary);
    }

    /** Pure policy function kept independent of Android APIs for unit testing. */
    public static Assessment assess(List<String> supportedAbis, long totalMemoryBytes,
                                    String systemVulkanSummary) {
        List<String> abis = supportedAbis == null
                ? Collections.emptyList() : supportedAbis;
        CpuSupport cpuSupport;
        if (abis.contains("arm64-v8a")) {
            cpuSupport = CpuSupport.ARM64_AVAILABLE;
        } else if (abis.isEmpty()) {
            cpuSupport = CpuSupport.UNKNOWN;
        } else {
            cpuSupport = CpuSupport.ARM64_UNAVAILABLE;
        }

        MemoryTier memoryTier = totalMemoryBytes <= 0L
                ? MemoryTier.UNKNOWN
                : totalMemoryBytes >= BASELINE_MEMORY_BYTES
                ? MemoryTier.MEETS_REFERENCE : MemoryTier.BELOW_REFERENCE;

        VulkanVisibility vulkanVisibility = classifyVulkan(systemVulkanSummary);
        return new Assessment(cpuSupport, memoryTier, vulkanVisibility);
    }

    private static VulkanVisibility classifyVulkan(String summary) {
        if (summary == null || summary.trim().isEmpty()) {
            return VulkanVisibility.UNKNOWN;
        }
        String normalized = summary.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("no vulkan physical device")) {
            return VulkanVisibility.NO_SYSTEM_DEVICE_REPORTED;
        }
        if (normalized.startsWith("vulkan probe unavailable")
                || normalized.startsWith("vulkan query unavailable")
                || normalized.startsWith("vulkan instance creation failed")
                || normalized.startsWith("vulkan device enumeration failed")) {
            return VulkanVisibility.UNKNOWN;
        }
        return VulkanVisibility.SYSTEM_DEVICE_REPORTED;
    }
}
