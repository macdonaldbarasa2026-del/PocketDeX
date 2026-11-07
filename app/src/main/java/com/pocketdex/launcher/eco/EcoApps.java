package com.pocketdex.launcher.eco;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.widget.Toast;

import com.pocketdex.launcher.R;
import com.pocketdex.launcher.runtime.AppDescriptor;
import com.pocketdex.launcher.runtime.DeviceCapabilityProfile;
import com.pocketdex.launcher.runtime.GuacamoleWorkspaceConfig;
import com.pocketdex.launcher.runtime.RuntimeProvider;
import com.pocketdex.launcher.runtime.RuntimeRegistry;
import com.pocketdex.launcher.runtime.SupportReport;
import com.pocketdex.launcher.runtime.WinlatorRuntime;

import java.util.ArrayList;
import java.util.List;

public final class EcoApps {
    public static final String FILES = "eco:files";
    public static final String EDITOR = "eco:editor";
    public static final String TERMINAL = "eco:terminal";
    public static final String CALCULATOR = "eco:calculator";
    public static final String REMOTE_PC = "eco:remote_pc";
    public static final String LOCAL_WINDOWS = "eco:local_windows";
    public static final String SETTINGS = "eco:settings";

    private EcoApps() {
    }

    public static final class Entry implements com.pocketdex.launcher.EcoItem {
        public final String id;
        public final String name;
        public final int iconRes;
        private final Class<?> activity;

        Entry(String id, String name, int iconRes, Class<?> activity) {
            this.id = id;
            this.name = name;
            this.iconRes = iconRes;
            this.activity = activity;
        }

        @Override
        public String key() {
            return id;
        }

        @Override
        public String label() {
            return name;
        }

        @Override
        public Drawable icon(Context c) {
            try {
                return c.getDrawable(iconRes);
            } catch (Exception e) {
                return null;
            }
        }

        @Override
        public void open(Context c) {
            if (REMOTE_PC.equals(id) || LOCAL_WINDOWS.equals(id)) {
                openRuntimeItem(c);
                return;
            }
            Intent intent = activity != null
                    ? new Intent(c, activity)
                    : new Intent(android.provider.Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                c.startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(c, "Unable to open " + name, Toast.LENGTH_SHORT).show();
            }
        }

        private void openRuntimeItem(Context c) {
            boolean localWindows = LOCAL_WINDOWS.equals(id);
            String runtimeId = localWindows
                    ? WinlatorRuntime.RUNTIME_ID : GuacamoleWorkspaceConfig.RUNTIME_ID;
            String appId = localWindows
                    ? WinlatorRuntime.APP_ID : GuacamoleWorkspaceConfig.APP_ID;
            AppDescriptor app = new AppDescriptor(appId, name, runtimeId,
                    localWindows ? "winlator-home" : "workspace");
            RuntimeProvider runtime = RuntimeRegistry.getDefault()
                    .find(runtimeId);
            if (runtime == null) {
                Toast.makeText(c, name + " runtime is unavailable",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                DeviceCapabilityProfile capabilities = DeviceCapabilityProfile.capture(c);
                SupportReport report = runtime.inspect(c, app, capabilities);
                if (!report.isReady()) {
                    Toast.makeText(c, report.message(), Toast.LENGTH_LONG).show();
                    return;
                }
                runtime.launch(c, app, capabilities);
            } catch (Exception e) {
                Toast.makeText(c, "Unable to open " + name,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static final List<Entry> ENTRIES;
    public static final List<String> DEFAULT_DOCK_KEYS;

    static {
        List<Entry> list = new ArrayList<>();
        list.add(new Entry(FILES, "Files", R.drawable.ic_files, FilesActivity.class));
        list.add(new Entry(EDITOR, "Editor", R.drawable.ic_editor, EditorActivity.class));
        list.add(new Entry(TERMINAL, "Terminal", R.drawable.ic_terminal, TerminalActivity.class));
        list.add(new Entry(CALCULATOR, "Calculator", R.drawable.ic_calc, CalculatorActivity.class));
        list.add(new Entry(REMOTE_PC, "Remote PC", R.drawable.ic_remote_pc,
                RemoteWorkspaceActivity.class));
        list.add(new Entry(LOCAL_WINDOWS, "Local Windows", R.drawable.ic_windows,
                null));
        list.add(new Entry(SETTINGS, "Settings", R.drawable.ic_settings, null));
        ENTRIES = list;

        List<String> dock = new ArrayList<>();
        dock.add(FILES);
        dock.add(EDITOR);
        dock.add(TERMINAL);
        dock.add(CALCULATOR);
        DEFAULT_DOCK_KEYS = dock;
    }

    public static Entry byId(String key) {
        if (key == null) {
            return null;
        }
        for (Entry entry : ENTRIES) {
            if (entry.id.equals(key)) {
                return entry;
            }
        }
        return null;
    }
}
