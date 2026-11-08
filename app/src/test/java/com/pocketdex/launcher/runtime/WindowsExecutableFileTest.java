package com.pocketdex.launcher.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WindowsExecutableFileTest {
    @Test
    public void recognizesExeSuffixWithoutCaseSensitivity() {
        assertTrue(WindowsExecutableFile.isExecutable("setup.exe"));
        assertTrue(WindowsExecutableFile.isExecutable("SETUP.ExE"));
        assertFalse(WindowsExecutableFile.isExecutable("setup.exe.txt"));
        assertFalse(WindowsExecutableFile.isExecutable(null));
    }

    @Test
    public void sanitizesSourceNameToOneSafeFileName() {
        assertEquals("installer.exe",
                WindowsExecutableFile.safeFileName("../../installer.exe"));
        assertEquals("bad_name.exe",
                WindowsExecutableFile.safeFileName("bad:name.exe"));
        assertEquals("_CON.exe", WindowsExecutableFile.safeFileName("CON.exe"));
        assertEquals("notes.txt.exe", WindowsExecutableFile.safeFileName("notes.txt"));
        assertEquals("program.exe", WindowsExecutableFile.safeFileName(null));
    }

    @Test
    public void keepsLongGuestPathsWithinClassicWindowsPathLimit() {
        String name = new String(new char[220]).replace('\0', 'a') + ".exe";
        assertEquals(180, WindowsExecutableFile.safeFileName(name).length());
        assertTrue(WindowsExecutableFile.guestPath(name).length() < 260);
    }

    @Test
    public void resolvesIntoWinlatorDownloadsDriveFolder() {
        assertEquals("D:\\PocketDeX\\setup.exe",
                WindowsExecutableFile.guestPath("setup.exe"));
    }
}
