package com.pocketdex.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import com.pocketdex.launcher.runtime.AppDescriptor;
import com.pocketdex.launcher.runtime.RuntimeProvider;
import com.pocketdex.launcher.runtime.RuntimeRegistry;

public final class AppRepo {
    private static final String PREFS = "pocketdex";
    private static final String KEY_PINNED = "pinned";
    private static final String KEY_RECENT = "recent";

    private static List<AppEntry> cache;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private AppRepo() {
    }

    public static final class AppEntry implements com.pocketdex.launcher.EcoItem {
        public final String label;
        public final String packageName;
        public final ComponentName component;
        private Drawable icon;

        AppEntry(String label, String packageName, ComponentName component) {
            this.label = label;
            this.packageName = packageName;
            this.component = component;
        }

        public String key() {
            return component.flattenToString();
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public Drawable icon(Context c) {
            if (icon == null) {
                try {
                    icon = c.getPackageManager().getActivityIcon(component);
                } catch (Exception ignored) {
                    try {
                        icon = c.getPackageManager().getApplicationIcon(packageName);
                    } catch (Exception ignoredToo) {
                        icon = null;
                    }
                }
            }
            return icon;
        }

        @Override
        public void open(Context c) {
            try {
                RuntimeProvider runtime = RuntimeRegistry.getDefault()
                        .find(RuntimeRegistry.ANDROID);
                if (runtime == null) {
                    return;
                }
                runtime.launch(c, new AppDescriptor(key(), label,
                        RuntimeRegistry.ANDROID, component.flattenToString()));
            } catch (Exception ignored) {
            }
        }
    }

    public static synchronized List<AppEntry> apps(Context c) {
        if (cache == null) {
            cache = load(c);
        }
        return cache;
    }

    public static AppEntry find(Context c, String key) {
        if (key == null) {
            return null;
        }
        for (AppEntry entry : apps(c)) {
            if (entry.key().equals(key)) {
                return entry;
            }
        }
        return null;
    }

    private static List<AppEntry> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        LinkedHashMap<String, AppEntry> byComponent = new LinkedHashMap<>();
        List<ResolveInfo> resolved;
        try {
            resolved = pm.queryIntentActivities(intent, 0);
        } catch (Exception e) {
            resolved = Collections.emptyList();
        }

        String ownDesktop = new ComponentName(c, MainActivity.class).flattenToString();
        for (ResolveInfo info : resolved) {
            ActivityInfo activity = info.activityInfo;
            if (activity == null) {
                continue;
            }
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            String key = component.flattenToString();
            if (key.equals(ownDesktop) || byComponent.containsKey(key)) {
                continue;
            }
            CharSequence loaded = info.loadLabel(pm);
            String label = loaded == null ? activity.packageName : loaded.toString().trim();
            if (label.isEmpty()) {
                label = activity.packageName;
            }
            byComponent.put(key, new AppEntry(label, activity.packageName, component));
        }

        List<AppEntry> list = new ArrayList<>(byComponent.values());
        list.sort((a, b) -> a.label.toLowerCase(Locale.getDefault())
                .compareTo(b.label.toLowerCase(Locale.getDefault())));
        return list;
    }

    public static void invalidate() {
        synchronized (AppRepo.class) {
            cache = null;
        }
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception ignored) {
            }
        }
    }

    public static void addListener(Runnable listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static LinkedHashSet<String> readSet(Context c, String key) {
        String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(key, "");
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split("\\|")) {
                if (!part.isEmpty()) {
                    set.add(part);
                }
            }
        }
        return set;
    }

    private static void writeSet(Context c, String key, Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String value : set) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(value);
        }
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(key, sb.toString()).apply();
    }

    public static LinkedHashSet<String> pinned(Context c) {
        return readSet(c, KEY_PINNED);
    }

    public static boolean isPinned(Context c, String key) {
        return pinned(c).contains(key);
    }

    public static void togglePinned(Context c, String key) {
        LinkedHashSet<String> set = readSet(c, KEY_PINNED);
        if (!set.remove(key)) {
            set.add(key);
        }
        writeSet(c, KEY_PINNED, set);
    }

    public static void addRecent(Context c, String key) {
        if (key == null) {
            return;
        }
        LinkedHashSet<String> set = readSet(c, KEY_RECENT);
        set.remove(key);
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add(key);
        for (String value : set) {
            if (ordered.size() >= 8) {
                break;
            }
            ordered.add(value);
        }
        writeSet(c, KEY_RECENT, ordered);
    }

    public static List<String> recent(Context c) {
        return new ArrayList<>(readSet(c, KEY_RECENT));
    }
}
