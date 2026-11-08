package com.pocketdex.launcher.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public final class WinlatorCompatibilityEngineTest {
    private static final long GIB = 1024L * 1024L * 1024L;

    @Test
    public void arm64AndSixGigabytesMeetsPlanningReference() {
        WinlatorCompatibilityEngine.Assessment assessment =
                WinlatorCompatibilityEngine.assess(Arrays.asList("arm64-v8a", "armeabi-v7a"),
                        6L * GIB, "Adreno (integrated GPU), Vulkan API 1.3.0");

        assertEquals(WinlatorCompatibilityEngine.CpuSupport.ARM64_AVAILABLE,
                assessment.cpuSupport());
        assertEquals(WinlatorCompatibilityEngine.MemoryTier.MEETS_REFERENCE,
                assessment.memoryTier());
        assertEquals(WinlatorCompatibilityEngine.VulkanVisibility.SYSTEM_DEVICE_REPORTED,
                assessment.vulkanVisibility());
        assertTrue(assessment.mayAttemptWinlator());
    }

    @Test
    public void lowerMemoryIsAdvisoryAndDoesNotBlockArm64Launch() {
        WinlatorCompatibilityEngine.Assessment assessment =
                WinlatorCompatibilityEngine.assess(Collections.singletonList("arm64-v8a"),
                        4L * GIB, "No Vulkan physical device reported by the system driver.");

        assertEquals(WinlatorCompatibilityEngine.MemoryTier.BELOW_REFERENCE,
                assessment.memoryTier());
        assertEquals(WinlatorCompatibilityEngine.VulkanVisibility.NO_SYSTEM_DEVICE_REPORTED,
                assessment.vulkanVisibility());
        assertTrue(assessment.mayAttemptWinlator());
        assertTrue(assessment.summary().contains("does not gate launch"));
    }

    @Test
    public void doesNotAssumeGpuVendorForVulkanVisibility() {
        WinlatorCompatibilityEngine.Assessment assessment =
                WinlatorCompatibilityEngine.assess(Collections.singletonList("arm64-v8a"),
                        8L * GIB, "Mali-G78 (integrated GPU), Vulkan API 1.2.0");

        assertEquals(WinlatorCompatibilityEngine.VulkanVisibility.SYSTEM_DEVICE_REPORTED,
                assessment.vulkanVisibility());
        assertTrue(assessment.mayAttemptWinlator());
    }

    @Test
    public void nonArm64DeviceIsRejectedAndMissingDataStaysUnknown() {
        WinlatorCompatibilityEngine.Assessment unsupported =
                WinlatorCompatibilityEngine.assess(Collections.singletonList("x86_64"),
                        8L * GIB, "");
        WinlatorCompatibilityEngine.Assessment unknown =
                WinlatorCompatibilityEngine.assess(null, 0L, "Vulkan probe unavailable for this app ABI.");

        assertEquals(WinlatorCompatibilityEngine.CpuSupport.ARM64_UNAVAILABLE,
                unsupported.cpuSupport());
        assertFalse(unsupported.mayAttemptWinlator());
        assertEquals(WinlatorCompatibilityEngine.MemoryTier.UNKNOWN, unknown.memoryTier());
        assertEquals(WinlatorCompatibilityEngine.CpuSupport.UNKNOWN, unknown.cpuSupport());
        assertEquals(WinlatorCompatibilityEngine.VulkanVisibility.UNKNOWN,
                unknown.vulkanVisibility());
    }
}
