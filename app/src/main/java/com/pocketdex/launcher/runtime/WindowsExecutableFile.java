package com.pocketdex.launcher.runtime;

import java.util.Locale;

/** Safe filename and guest path helpers for executables staged for Winlator. */
public final class WindowsExecutableFile {
    private WindowsExecutableFile() {
    }

    public static boolean isExecutable(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".exe");
    }

    public static String safeFileName(String name) {
        String value = name == null ? "" : name;
        int separator = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        if (separator >= 0) {
            value = value.substring(separator + 1);
        }
        value = value.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").trim();
        while (value.endsWith(".") || value.endsWith(" ")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return "program.exe";
        }
        if (!isExecutable(value)) {
            value += ".exe";
        }
        if (value.equalsIgnoreCase(".exe")) {
            return "program.exe";
        }
        if (value.length() > 180) {
            value = value.substring(0, 176) + ".exe";
        }
        String stem = value.substring(0, value.length() - 4);
        if (stem.matches("(?i)CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) {
            value = "_" + value;
        }
        return value;
    }

    public static String guestPath(String name) {
        return "D:\\PocketDeX\\" + safeFileName(name);
    }
}
