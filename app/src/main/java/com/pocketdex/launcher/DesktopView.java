package com.pocketdex.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.pocketdex.launcher.eco.EcoApps;
import com.pocketdex.launcher.eco.Panels;
import com.pocketdex.launcher.runtime.DisplayWindowMetrics;

import java.util.Locale;

public class DesktopView extends FrameLayout {

    public interface Host {
        Context hostContext();

        Display hostDisplay();

        int hostDisplayId();

        boolean hasExternalDisplay();

        void launchApp(AppRepo.AppEntry app, boolean windowed);

        void launchAppOnExternal(AppRepo.AppEntry app);

        void openSettings();

        void openWallpaper();

        void openHomeSettings();

        void openAppInfo(AppRepo.AppEntry app);

        void uninstallApp(AppRepo.AppEntry app);

        void goHome();

        default void applyFullscreen() {
        }

        default void pickFilesRoot() {
        }
    }

    private static final int COLOR_TEXT = 0xFFF4F6FB;
    private static final int COLOR_TEXT_DIM = 0xFF9AA3B5;
    private static final int COLOR_ACCENT = 0xFF4C8DFF;
    private static final int COLOR_PANEL = 0xF2171A24;
    private static final int COLOR_DIVIDER = 0xFF2A3040;

    private static final int TASKBAR_HEIGHT_DP = 54;
    private static final int TASKBAR_MARGIN_DP = 10;

    private final Context ctx;
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ImageView wallpaperView;
    private View dimView;
    private LinearLayout taskbar;
    private LinearLayout dockApps;
    private ImageButton startButton;
    private TextView clock;
    private TextView date;
    private TextView battery;
    private TextView network;
    private TextView deskButton;
    private TextView defaultHint;

    private ScrollView desktopScroll;
    private GridLayout desktopGrid;

    private FrameLayout panelLayer;
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private Panels.Panel editorPanel;
    private Panels.Panel filesPanel;
    private Panels.Env panelEnv;

    private FrameLayout startOverlay;
    private LinearLayout startPanel;
    private GridLayout startGrid;
    private EditText search;
    private TextView startEmpty;
    private int startGridWidth;

    private boolean startOpen;
    private boolean batteryRegistered;
    private int batteryPercent = -1;
    private boolean charging;
    private int recentIndex = -1;
    private View lastClickedView;
    private long lastClickTime;

    private final Runnable appListener = this::refresh;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            long delay = Prefs.showSeconds(ctx)
                    ? 1000L : 60_000L - (System.currentTimeMillis() % 60_000L);
            handler.postDelayed(this, Math.max(250L, delay));
        }
    };

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            batteryPercent = (level >= 0 && scale > 0)
                    ? Math.round(level * 100f / scale) : -1;
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            updateBattery();
        }
    };

    public DesktopView(Context context, Host host) {
        super(context);
        this.ctx = context;
        this.host = host;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackground(Ui.desktopGradient());
        buildUi();
    }

    private int dp(float value) {
        return Ui.dp(ctx, value);
    }

    private String str(int resId) {
        return ctx.getString(resId);
    }

    private DisplayMetrics metrics() {
        DisplayMetrics out = new DisplayMetrics();
        Display display = host.hostDisplay();
        out.setTo(getResources().getDisplayMetrics());
        if (display != null) {
            DisplayWindowMetrics windowMetrics = DisplayWindowMetrics.forDisplay(ctx, display);
            out.widthPixels = windowMetrics.widthPixels;
            out.heightPixels = windowMetrics.heightPixels;
            out.density = windowMetrics.density;
            out.densityDpi = Math.round(windowMetrics.density * 160f);
        }
        return out;
    }

    private void buildUi() {
        buildWallpaper();
        buildDesktop();
        buildPanelLayer();
        buildTaskbar();
        buildStartMenu();
        buildDefaultHint();
        applySettings();
    }

    private void buildWallpaper() {
        wallpaperView = new ImageView(ctx);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(wallpaperView, match());

        dimView = new View(ctx);
        addView(dimView, match());

        loadWallpaper();
        updateDim();
    }

    private void loadWallpaper() {
        Drawable wallpaper = null;
        try {
            wallpaper = android.app.WallpaperManager.getInstance(ctx).getDrawable();
        } catch (Exception ignored) {
        }
        if (wallpaper != null) {
            wallpaperView.setImageDrawable(wallpaper);
            wallpaperView.setVisibility(VISIBLE);
        } else {
            wallpaperView.setVisibility(GONE);
        }
    }

    private void updateDim() {
        float amount = Math.max(0, Math.min(90, Prefs.dim(ctx))) / 100f;
        dimView.setBackgroundColor(Ui.alpha(Color.BLACK, amount));
    }

    private void buildDesktop() {
        desktopScroll = new ScrollView(ctx);
        desktopScroll.setFillViewport(true);
        desktopScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        desktopScroll.setVerticalScrollBarEnabled(false);

        desktopGrid = new GridLayout(ctx);
        desktopGrid.setUseDefaultMargins(false);
        desktopScroll.addView(desktopGrid,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        int paddingH = dp(16);
        int paddingTop = dp(18);
        int paddingBottom = dp(TASKBAR_HEIGHT_DP + TASKBAR_MARGIN_DP + 22);
        desktopScroll.setPadding(paddingH, paddingTop, paddingH, paddingBottom);
        addView(desktopScroll, match());

        rebuildDesktop();
    }

    private void buildPanelLayer() {
        panelLayer = new FrameLayout(ctx);
        panelLayer.setClipChildren(false);
        addView(panelLayer, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        updatePanelLayerPadding();
    }

    private void updatePanelLayerPadding() {
        if (panelLayer == null) {
            return;
        }
        LayoutParams params = (LayoutParams) panelLayer.getLayoutParams();
        params.topMargin = dp(10);
        params.leftMargin = dp(6);
        params.rightMargin = dp(6);
        params.bottomMargin = dp(TASKBAR_HEIGHT_DP + TASKBAR_MARGIN_DP + 8);
        panelLayer.setLayoutParams(params);
    }

    private Panels.Env panelEnv() {
        if (panelEnv != null) {
            return panelEnv;
        }
        panelEnv = new Panels.Env() {
            @Override
            public Context context() {
                return ctx;
            }

            @Override
            public Activity activity() {
                return host.hostContext() instanceof Activity
                        ? (Activity) host.hostContext() : null;
            }

            @Override
            public void runOnUi(Runnable action) {
                handler.post(action);
            }

            @Override
            public void refreshHome() {
                refresh();
            }

            @Override
            public void applyFullscreen() {
                host.applyFullscreen();
                updatePanelLayerPadding();
            }

            @Override
            public void wallpaper() {
                host.openWallpaper();
            }

            @Override
            public void homeSettings() {
                host.openHomeSettings();
            }

            @Override
            public void openEditor(Uri uri, String name) {
                openEditorWindow(uri, name);
            }

            @Override
            public void pickFilesRoot() {
                host.pickFilesRoot();
            }

            @Override
            public void openWithSystem(Uri uri, String mime) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, mime == null ? "*/*" : mime);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    ctx.startActivity(Intent.createChooser(intent, "Open"));
                } catch (Exception e) {
                    Toast.makeText(ctx, "No app can open this", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void toast(String message) {
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
            }
        };
        return panelEnv;
    }

    private boolean isEcoWindowKey(String key) {
        return EcoApps.FILES.equals(key) || EcoApps.TERMINAL.equals(key)
                || EcoApps.CALCULATOR.equals(key) || EcoApps.SETTINGS.equals(key)
                || EcoApps.EDITOR.equals(key);
    }

    private void openWindow(String key) {
        if (!isEcoWindowKey(key)) {
            return;
        }
        if (EcoApps.EDITOR.equals(key)) {
            openEditorWindow(null, null);
            return;
        }
        Window existing = windows.get(key);
        if (existing != null) {
            existing.setVisibility(VISIBLE);
            existing.bringToFront();
            existing.requestLayout();
            return;
        }
        Panels.Panel panel = Panels.create(key, panelEnv());
        if (panel == null) {
            EcoApps.Entry entry = EcoApps.byId(key);
            if (entry != null) {
                entry.open(host.hostContext());
            }
            return;
        }
        if (EcoApps.FILES.equals(key)) {
            filesPanel = panel;
        }
        EcoApps.Entry entry = EcoApps.byId(key);
        String title = entry != null ? entry.label() : key;
        addWindow(key, title, panel);
    }

    private void openEditorWindow(Uri uri, String name) {
        if (editorPanel != null && editorPanel instanceof Panels.EditorWidget) {
            ((Panels.EditorWidget) editorPanel).load(uri, name);
            Window existing = windows.get(EcoApps.EDITOR);
            if (existing != null) {
                existing.setVisibility(VISIBLE);
                existing.bringToFront();
            }
            return;
        }
        Panels.Panel panel = Panels.createEditor(panelEnv(), uri, name);
        editorPanel = panel;
        String title = name == null || name.isEmpty() ? "Editor" : name;
        addWindow(EcoApps.EDITOR, title, panel);
    }

    private void addWindow(String key, String title, Panels.Panel panel) {
        if (panelLayer == null) {
            return;
        }
        DisplayMetrics m = metrics();
        int maxWidth = Math.max(dp(280), Math.round(m.widthPixels * 0.72f));
        int maxHeight = Math.max(dp(220), Math.round(m.heightPixels * 0.62f));
        int width = Math.min(maxWidth, dp(560));
        int height = Math.min(maxHeight, dp(400));
        int offset = (windows.size() % 5) * dp(26);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = Math.max(dp(4), dp(10) + offset);
        params.topMargin = dp(10) + offset;
        panelsTopWindowsMarginLimit(params, m);

        final Window window = new Window(key, title, panel);
        window.setLayoutParams(params);
        panelLayer.addView(window);
        windows.put(key, window);
        window.bringToFront();
        window.setTranslationZ(dp(4));
    }

    private void panelsTopWindowsMarginLimit(FrameLayout.LayoutParams params,
                                             DisplayMetrics m) {
        int maxLeft = Math.max(dp(4), m.widthPixels - params.width - dp(20));
        int maxTop = Math.max(dp(4), m.heightPixels / 2);
        params.leftMargin = Math.min(params.leftMargin, maxLeft);
        params.topMargin = Math.min(params.topMargin, maxTop);
    }

    public void onFilesRootChanged() {
        handler.post(() -> {
            if (filesPanel != null && filesPanel instanceof Panels.FilesWidget) {
                ((Panels.FilesWidget) filesPanel).reload();
            }
        });
    }

    private void closeWindow(Window window) {
        String key = window.key;
        windows.remove(key);
        panelLayer.removeView(window);
        try {
            window.panel.destroy();
        } catch (Exception ignored) {
        }
        if (EcoApps.FILES.equals(key)) {
            filesPanel = null;
        } else if (EcoApps.EDITOR.equals(key)) {
            editorPanel = null;
        }
    }

    private void closeAllWindows() {
        for (Window window : new ArrayList<>(windows.values())) {
            closeWindow(window);
        }
    }

    private final class Window extends LinearLayout {
        final String key;
        final Panels.Panel panel;
        final TextView title;
        final View body;
        boolean maximized;
        private FrameLayout.LayoutParams savedParams;
        private float downRawX;
        private float downRawY;
        private int startLeft;
        private int startTop;

        Window(String key, String titleText, Panels.Panel panel) {
            super(ctx);
            this.key = key;
            this.panel = panel;
            setOrientation(LinearLayout.VERTICAL);
            setBackground(Ui.roundedStroke(ctx, COLOR_PANEL, 14,
                    COLOR_ACCENT, 1));
            setElevation(dp(10));
            setClickable(true);

            LinearLayout titleBar = new LinearLayout(ctx);
            titleBar.setOrientation(LinearLayout.HORIZONTAL);
            titleBar.setGravity(Gravity.CENTER_VERTICAL);
            titleBar.setPadding(dp(10), dp(4), dp(4), dp(4));
            titleBar.setBackground(Ui.rounded(ctx, 0xFF232838, 14));

            title = new TextView(ctx);
            title.setText(titleText);
            title.setTextColor(COLOR_TEXT);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(dp(2), 0, dp(6), 0);
            titleBar.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            titleBar.addView(windowButton("—", "Minimize " + titleText,
                    v -> minimize()));
            titleBar.addView(windowButton("□", "Maximize " + titleText,
                    v -> toggleMaximize()));
            titleBar.addView(windowButton("✕", v -> closeWindow(Window.this)));

            titleBar.setOnTouchListener((v, event) -> {
                if (maximized) {
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        FrameLayout.LayoutParams p =
                                (FrameLayout.LayoutParams) getLayoutParams();
                        startLeft = p.leftMargin;
                        startTop = p.topMargin;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        FrameLayout.LayoutParams q =
                                (FrameLayout.LayoutParams) getLayoutParams();
                        int newLeft = startLeft + Math.round(event.getRawX() - downRawX);
                        int newTop = startTop + Math.round(event.getRawY() - downRawY);
                        q.leftMargin = Math.max(0,
                                Math.min(panelLayer.getWidth() - dp(70), newLeft));
                        q.topMargin = Math.max(0,
                                Math.min(panelLayer.getHeight() - dp(36), newTop));
                        setLayoutParams(q);
                        return true;
                    default:
                        return false;
                }
            });
            titleBar.setOnClickListener(v -> {
                long now = SystemClock.uptimeMillis();
                if (lastClickTime > 0 && now - lastClickTime <= 350L) {
                    lastClickTime = 0L;
                    toggleMaximize();
                } else {
                    lastClickTime = now;
                }
            });

            body = panel.view();
            addView(titleBar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        private View windowButton(String label, OnClickListener listener) {
            return windowButton(label, label, listener);
        }

        private View windowButton(String label, String description,
                                  OnClickListener listener) {
            TextView button = new TextView(ctx);
            button.setText(label);
            button.setContentDescription(description);
            button.setTooltipText(description);
            button.setTextColor(COLOR_TEXT);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(9), dp(4), dp(9), dp(4));
            button.setBackground(Ui.ripple(ctx,
                    Ui.rounded(ctx, Color.TRANSPARENT, 8), 0x44FFFFFF));
            button.setOnClickListener(listener);
            return button;
        }

        void minimize() {
            setVisibility(GONE);
        }

        void toggleMaximize() {
            FrameLayout.LayoutParams current =
                    (FrameLayout.LayoutParams) getLayoutParams();
            if (maximized) {
                if (savedParams != null) {
                    setLayoutParams(savedParams);
                }
                maximized = false;
            } else {
                savedParams = new FrameLayout.LayoutParams(current.width,
                        current.height);
                savedParams.gravity = current.gravity;
                savedParams.leftMargin = current.leftMargin;
                savedParams.topMargin = current.topMargin;
                current.width = ViewGroup.LayoutParams.MATCH_PARENT;
                current.height = ViewGroup.LayoutParams.MATCH_PARENT;
                current.leftMargin = 0;
                current.topMargin = 0;
                setLayoutParams(current);
                maximized = true;
            }
            requestLayout();
        }
    }

    private int autoColumns() {
        int width = metrics().widthPixels;
        int columns = width / Math.max(1, dp(84));
        return Math.max(3, Math.min(12, columns));
    }
    private void rebuildDesktop() {
        if (desktopGrid == null) {
            return;
        }
        int columns = Prefs.columns(ctx) > 0 ? Prefs.columns(ctx) : autoColumns();
        desktopGrid.setColumnCount(columns);

        int available = metrics().widthPixels - dp(32);
        int cell = Math.max(dp(64), available / columns);

        LinkedHashSet<String> pins = AppRepo.pinned(ctx);
        List<EcoItem> shown = new ArrayList<>();

        if (!pins.isEmpty()) {
            for (String key : pins) {
                EcoItem entry = findItem(key);
                if (entry != null && !containsItem(shown, entry)) {
                    shown.add(entry);
                }
            }
        }
        if (shown.isEmpty()) {
            if (Prefs.showEcoOnDesktop(ctx)) {
                for (EcoApps.Entry entry : EcoApps.ENTRIES) {
                    shown.add(entry);
                }
            }
            shown.addAll(AppRepo.apps(ctx));
        }

        desktopGrid.removeAllViews();
        int iconSize = Math.round(Prefs.iconSize(ctx));
        boolean labels = Prefs.showLabels(ctx);
        for (EcoItem item : shown) {
            View icon = buildIcon(item, iconSize, labels, cell);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cell;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            desktopGrid.addView(icon, params);
        }
    }

    private View buildIcon(EcoItem item, int iconSizeDp, boolean showLabel,
                           int cellWidth) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(4), dp(7), dp(4), dp(7));
        box.setFocusable(true);
        box.setClickable(true);
        box.setTag(item);

        Drawable normal = Ui.rounded(ctx, Color.TRANSPARENT, 12);
        Drawable background = Ui.ripple(ctx, normal, 0x33FFFFFF);
        box.setBackground(background);

        ImageView image = new ImageView(ctx);
        Drawable icon = item.icon(ctx);
        if (icon != null) {
            image.setImageDrawable(icon);
        }
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int iconPx = dp(iconSizeDp);
        LinearLayout.LayoutParams iconParams =
                new LinearLayout.LayoutParams(iconPx, iconPx);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(image, iconParams);

        if (showLabel) {
            TextView label = new TextView(ctx);
            label.setText(item.label());
            label.setTextColor(COLOR_TEXT);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setShadowLayer(2f, 0f, 1f, 0xCC000000);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(6);
            box.addView(label, labelParams);
        }

        box.setTooltipText(item.label());
        box.setOnClickListener(v -> handleIconClick(item, v));
        box.setOnLongClickListener(v -> {
            showAppMenu(item, v);
            return true;
        });
        box.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS
                    && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                showAppMenu(item, v);
                return true;
            }
            return false;
        });
        box.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setBackground(Ui.roundedStroke(
                        ctx, 0x22FFFFFF, 12, COLOR_ACCENT, 1.5f));
            } else {
                v.setBackground(background);
            }
        });
        return box;
    }

    private void handleIconClick(EcoItem item, View view) {
        if (!Prefs.doubleClick(ctx)) {
            launchFromUi(item);
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (lastClickedView == view && now - lastClickTime <= 420L) {
            lastClickedView = null;
            lastClickTime = 0L;
            launchFromUi(item);
            return;
        }
        lastClickedView = view;
        lastClickTime = now;
        view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70L)
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(90L).start())
                .start();
    }

    private void buildTaskbar() {
        taskbar = new LinearLayout(ctx);
        taskbar.setOrientation(LinearLayout.HORIZONTAL);
        taskbar.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.setPadding(dp(6), dp(4), dp(8), dp(4));
        taskbar.setBackground(Ui.roundedStroke(ctx, COLOR_PANEL, 16,
                COLOR_DIVIDER, 1));
        taskbar.setElevation(dp(12));
        taskbar.setOnHoverListener((v, event) -> {
            if (!Prefs.autoHideTaskbar(ctx)) {
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER) {
                taskbar.animate().alpha(1f).setDuration(120L).start();
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                taskbar.animate().alpha(0.55f).setDuration(220L).start();
            }
            return false;
        });

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TASKBAR_HEIGHT_DP));
        barParams.gravity = Gravity.BOTTOM;
        barParams.leftMargin = dp(TASKBAR_MARGIN_DP);
        barParams.rightMargin = dp(TASKBAR_MARGIN_DP);
        barParams.bottomMargin = dp(TASKBAR_MARGIN_DP);
        addView(taskbar, barParams);

        startButton = new ImageButton(ctx);
        startButton.setImageResource(R.drawable.ic_start);
        startButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        startButton.setPadding(dp(9), dp(9), dp(9), dp(9));
        startButton.setBackground(Ui.ripple(ctx,
                Ui.rounded(ctx, Color.TRANSPARENT, 12), 0x44FFFFFF));
        startButton.setContentDescription(str(R.string.start));
        startButton.setTooltipText(str(R.string.start));
        startButton.setOnClickListener(v -> toggleStart());
        taskbar.addView(startButton, new LinearLayout.LayoutParams(dp(40), dp(40)));

        View divider = new View(ctx);
        divider.setBackgroundColor(COLOR_DIVIDER);
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(dp(1), dp(26));
        dividerParams.leftMargin = dp(6);
        dividerParams.rightMargin = dp(6);
        taskbar.addView(divider, dividerParams);

        dockApps = new LinearLayout(ctx);
        dockApps.setOrientation(LinearLayout.HORIZONTAL);
        dockApps.setGravity(Gravity.CENTER_VERTICAL);
        taskbar.addView(dockApps, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View spacer = new View(ctx);
        taskbar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout status = new LinearLayout(ctx);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.rightMargin = dp(10);
        taskbar.addView(status, statusParams);

        clock = new TextView(ctx);
        clock.setTextColor(COLOR_TEXT);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        clock.setTypeface(Typeface.DEFAULT_BOLD);
        clock.setGravity(Gravity.END);
        status.addView(clock);

        date = new TextView(ctx);
        date.setTextColor(COLOR_TEXT_DIM);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        date.setGravity(Gravity.END);
        status.addView(date);

        battery = statusText(11);
        network = statusText(11);

        LinearLayout.LayoutParams batteryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        batteryParams.rightMargin = dp(8);
        taskbar.addView(battery, batteryParams);

        LinearLayout.LayoutParams networkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        networkParams.rightMargin = dp(6);
        taskbar.addView(network, networkParams);

        deskButton = statusText(11);
        deskButton.setText("Desk");
        deskButton.setContentDescription(str(R.string.show_desktop));
        deskButton.setTooltipText(str(R.string.show_desktop));
        deskButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        deskButton.setBackground(Ui.ripple(ctx,
                Ui.rounded(ctx, Color.TRANSPARENT, 10), 0x33FFFFFF));
        deskButton.setOnClickListener(v -> host.goHome());
        taskbar.addView(deskButton);

        ImageButton settingsButton = new ImageButton(ctx);
        settingsButton.setImageResource(R.drawable.ic_settings);
        settingsButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        settingsButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        settingsButton.setBackground(Ui.ripple(ctx,
                Ui.rounded(ctx, Color.TRANSPARENT, 10), 0x33FFFFFF));
        settingsButton.setContentDescription(str(R.string.settings_title));
        settingsButton.setTooltipText(str(R.string.settings_title));
        settingsButton.setOnClickListener(v -> host.openSettings());
        taskbar.addView(settingsButton, new LinearLayout.LayoutParams(dp(36), dp(36)));

        rebuildDock();
    }

    private TextView statusText(int sizeSp) {
        TextView text = new TextView(ctx);
        text.setTextColor(COLOR_TEXT_DIM);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private void rebuildDock() {
        if (dockApps == null) {
            return;
        }
        dockApps.removeAllViews();

        int widthDp = Math.round(metrics().widthPixels
                / getResources().getDisplayMetrics().density);
        int maxItems = Math.max(3, Math.min(8, (widthDp - 320) / 46));

        LinkedHashSet<String> pins = AppRepo.pinned(ctx);
        List<String> ordered = new ArrayList<>();
        for (String key : pins) {
            if (ordered.size() >= maxItems) {
                break;
            }
            if (findItem(key) != null) {
                ordered.add(key);
            }
        }

        for (String key : AppRepo.recent(ctx)) {
            if (ordered.size() >= maxItems) {
                break;
            }
            if (!ordered.contains(key) && findItem(key) != null) {
                ordered.add(key);
            }
        }

        if (ordered.isEmpty()) {
            for (String key : EcoApps.DEFAULT_DOCK_KEYS) {
                if (ordered.size() >= maxItems) {
                    break;
                }
                ordered.add(key);
            }
            List<AppRepo.AppEntry> apps = AppRepo.apps(ctx);
            for (AppRepo.AppEntry app : apps) {
                if (ordered.size() >= maxItems) {
                    break;
                }
                if (!ordered.contains(app.key())) {
                    ordered.add(app.key());
                }
            }
        }

        for (String key : ordered) {
            EcoItem item = findItem(key);
            if (item == null) {
                continue;
            }
            View icon = buildDockIcon(item);
            dockApps.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(40)));
        }
    }

    private View buildDockIcon(EcoItem item) {
        LinearLayout box = new LinearLayout(ctx);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(5), dp(4), dp(5), dp(4));
        box.setTag(item);
        box.setClickable(true);
        box.setFocusable(true);
        Drawable background = Ui.ripple(ctx,
                Ui.rounded(ctx, Color.TRANSPARENT, 10), 0x44FFFFFF);
        box.setBackground(background);

        ImageView image = new ImageView(ctx);
        Drawable icon = item.icon(ctx);
        if (icon != null) {
            image.setImageDrawable(icon);
        }
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(image, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        box.setTooltipText(item.label());
        box.setOnClickListener(v -> launchFromUi(item));
        box.setOnLongClickListener(v -> {
            showAppMenu(item, v);
            return true;
        });
        box.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS
                    && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
                showAppMenu(item, v);
                return true;
            }
            return false;
        });
        box.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setBackground(Ui.roundedStroke(
                        ctx, 0x22FFFFFF, 10, COLOR_ACCENT, 1.5f));
            } else {
                v.setBackground(background);
            }
        });
        return box;
    }

    private void buildStartMenu() {
        startOverlay = new FrameLayout(ctx);
        startOverlay.setBackgroundColor(0x66000000);
        startOverlay.setVisibility(GONE);
        startOverlay.setClickable(true);
        startOverlay.setOnClickListener(v -> closeStart());
        addView(startOverlay, match());

        DisplayMetrics m = metrics();
        int panelWidth = Math.min(Math.max(dp(380), (int) (m.widthPixels * 0.32f)),
                (int) (m.widthPixels * 0.92f));
        int panelHeight = Math.min(Math.max(dp(460), (int) (m.heightPixels * 0.66f)),
                (int) (m.heightPixels * 0.86f));

        startPanel = new LinearLayout(ctx);
        startPanel.setOrientation(LinearLayout.VERTICAL);
        startPanel.setPadding(dp(10), dp(10), dp(10), dp(10));
        startPanel.setBackground(Ui.roundedStroke(ctx, COLOR_PANEL, 18,
                COLOR_DIVIDER, 1));
        startPanel.setElevation(dp(20));
        startPanel.setClickable(true);
        startPanel.setOnClickListener(v -> {
        });

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                panelWidth, panelHeight);
        panelParams.gravity = Gravity.BOTTOM | Gravity.START;
        panelParams.leftMargin = dp(TASKBAR_MARGIN_DP + 2);
        panelParams.bottomMargin = dp(TASKBAR_HEIGHT_DP + TASKBAR_MARGIN_DP + 8);
        startOverlay.addView(startPanel, panelParams);

        startGridWidth = panelWidth - dp(20);

        startPanel.addView(buildStartHeader());

        LinearLayout ecoRow = buildEcoRow();
        LinearLayout.LayoutParams ecoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ecoParams.topMargin = dp(6);
        startPanel.addView(ecoRow, ecoParams);

        search = new EditText(ctx);
        search.setSingleLine(true);
        search.setHint(str(R.string.search_apps));
        search.setHintTextColor(COLOR_TEXT_DIM);
        search.setTextColor(COLOR_TEXT);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.setBackground(Ui.roundedStroke(ctx, 0xFF232838, 10,
                0x332A3040, 1));
        search.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        searchParams.topMargin = dp(8);
        searchParams.bottomMargin = dp(6);
        startPanel.addView(search, searchParams);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                rebuildStartGrid(s == null ? "" : s.toString());
            }
        });

        TextView allApps = new TextView(ctx);
        allApps.setText(str(R.string.all_apps));
        allApps.setTextColor(COLOR_TEXT_DIM);
        allApps.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        allApps.setPadding(dp(4), dp(2), dp(4), dp(6));
        startPanel.addView(allApps);

        ScrollView startScroll = new ScrollView(ctx);
        startScroll.setOverScrollMode(OVER_SCROLL_NEVER);
        startScroll.setVerticalScrollBarEnabled(false);
        startPanel.addView(startScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout gridHolder = new FrameLayout(ctx);
        startScroll.addView(gridHolder, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        startGrid = new GridLayout(ctx);
        startGrid.setUseDefaultMargins(false);
        gridHolder.addView(startGrid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        startEmpty = new TextView(ctx);
        startEmpty.setText(str(R.string.no_apps));
        startEmpty.setTextColor(COLOR_TEXT_DIM);
        startEmpty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        startEmpty.setGravity(Gravity.CENTER);
        startEmpty.setPadding(dp(8), dp(24), dp(8), dp(8));
        gridHolder.addView(startEmpty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        startPanel.addView(buildStartFooter());

        rebuildStartGrid("");
    }

    private LinearLayout buildEcoRow() {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int count = Math.min(EcoApps.ENTRIES.size(), 4);
        for (int index = 0; index < count; index++) {
            final EcoApps.Entry entry = EcoApps.ENTRIES.get(index);
            LinearLayout tile = new LinearLayout(ctx);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(6), dp(10), dp(6), dp(10));
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setTag(entry);

            Drawable background = Ui.ripple(ctx,
                    Ui.rounded(ctx, 0xFF232838, 12), 0x44FFFFFF);
            tile.setBackground(background);
            tile.setOnFocusChangeListener((view, hasFocus) -> {
                view.setBackground(hasFocus
                        ? Ui.roundedStroke(ctx, 0x44232838, 12, COLOR_ACCENT, 1.5f)
                        : background);
            });

            ImageView icon = new ImageView(ctx);
            Drawable drawable = entry.icon(ctx);
            if (drawable != null) {
                icon.setImageDrawable(drawable);
            }
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(dp(26), dp(26));
            iconParams.gravity = Gravity.CENTER_HORIZONTAL;
            tile.addView(icon, iconParams);

            TextView label = new TextView(ctx);
            label.setText(entry.label());
            label.setTextColor(COLOR_TEXT);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0);
            tile.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            tile.setTooltipText(entry.label());
            tile.setOnClickListener(v -> launchFromUi(entry));
            tile.setOnLongClickListener(v -> {
                AppRepo.togglePinned(ctx, entry.key());
                refresh();
                return true;
            });
            row.addView(tile, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private View buildStartHeader() {
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), 0, dp(2), 0);

        TextView title = new TextView(ctx);
        title.setText(str(R.string.app_name));
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton settingsButton = new ImageButton(ctx);
        settingsButton.setImageResource(R.drawable.ic_settings);
        settingsButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        settingsButton.setPadding(dp(7), dp(7), dp(7), dp(7));
        settingsButton.setBackground(Ui.ripple(ctx,
                Ui.rounded(ctx, Color.TRANSPARENT, 10), 0x33FFFFFF));
        settingsButton.setContentDescription(str(R.string.settings_title));
        settingsButton.setOnClickListener(v -> {
            closeStart();
            host.openSettings();
        });
        header.addView(settingsButton, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView close = new TextView(ctx);
        close.setText("✕");
        close.setTextColor(COLOR_TEXT_DIM);
        close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        close.setGravity(Gravity.CENTER);
        close.setPadding(dp(8), dp(4), dp(8), dp(4));
        close.setBackground(Ui.ripple(ctx,
                Ui.rounded(ctx, Color.TRANSPARENT, 10), 0x33FFFFFF));
        close.setOnClickListener(v -> closeStart());
        header.addView(close, new LinearLayout.LayoutParams(dp(30), dp(30)));

        return header;
    }

    private View buildStartFooter() {
        LinearLayout footer = new LinearLayout(ctx);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(6), 0, 0);

        footer.addView(textButton(str(R.string.wallpaper), v -> {
            closeStart();
            host.openWallpaper();
        }), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        footer.addView(textButton(str(R.string.default_home), v -> {
            closeStart();
            host.openHomeSettings();
        }), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View spacer = new View(ctx);
        footer.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        footer.addView(textButton(str(R.string.settings_title), v -> {
            closeStart();
            host.openSettings();
        }), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return footer;
    }

    private TextView textButton(String label, OnClickListener listener) {
        TextView button = new TextView(ctx);
        button.setText(label);
        button.setTextColor(COLOR_TEXT_DIM);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setBackground(Ui.ripple(ctx,
                Ui.rounded(ctx, Color.TRANSPARENT, 10), 0x33FFFFFF));
        button.setOnClickListener(listener);
        return button;
    }

    private void rebuildStartGrid(String query) {
        if (startGrid == null) {
            return;
        }
        startGrid.removeAllViews();
        List<EcoItem> matches = filteredApps(query);
        int columns = Math.max(3, Math.min(8, startGridWidth / Math.max(1, dp(84))));
        startGrid.setColumnCount(columns);
        int cell = Math.max(dp(64), startGridWidth / columns);

        for (EcoItem item : matches) {
            View icon = buildIcon(item, 38, true, cell);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cell;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            startGrid.addView(icon, params);
        }
        if (startEmpty != null) {
            startEmpty.setVisibility(matches.isEmpty() ? VISIBLE : GONE);
        }
    }

    private List<EcoItem> filteredApps(String query) {
        List<EcoItem> result = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        for (EcoApps.Entry entry : EcoApps.ENTRIES) {
            if (needle.isEmpty()
                    || entry.name.toLowerCase(Locale.getDefault()).contains(needle)) {
                result.add(entry);
            }
        }
        for (AppRepo.AppEntry app : AppRepo.apps(ctx)) {
            if (needle.isEmpty()
                    || app.label.toLowerCase(Locale.getDefault()).contains(needle)
                    || app.packageName.toLowerCase(Locale.getDefault()).contains(needle)) {
                result.add(app);
            }
        }
        return result;
    }

    private void buildDefaultHint() {
        defaultHint = new TextView(ctx);
        defaultHint.setText(str(R.string.not_default_home));
        defaultHint.setTextColor(COLOR_TEXT);
        defaultHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        defaultHint.setGravity(Gravity.CENTER);
        defaultHint.setPadding(dp(14), dp(8), dp(14), dp(8));
        defaultHint.setBackground(Ui.ripple(ctx,
                Ui.roundedStroke(ctx, 0xE64C8DFF, 18, 0x664C8DFF, 1),
                0x44FFFFFF));
        defaultHint.setOnClickListener(v -> host.openHomeSettings());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.topMargin = dp(14);
        addView(defaultHint, params);
        updateDefaultHint();
    }

    private boolean isDefaultHome() {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            ResolveInfo info = ctx.getPackageManager().resolveActivity(
                    intent, PackageManager.MATCH_DEFAULT_ONLY);
            return info != null && info.activityInfo != null
                    && ctx.getPackageName().equals(info.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private void updateDefaultHint() {
        if (defaultHint == null) {
            return;
        }
        boolean show = host.hostDisplayId() == Display.DEFAULT_DISPLAY
                && !isDefaultHome();
        defaultHint.setVisibility(show ? VISIBLE : GONE);
    }

    private void showAppMenu(EcoItem target, View anchor) {
        PopupMenu menu = new PopupMenu(ctx, anchor);
        menu.getMenu().add(0, 1, 0, str(R.string.open));
        menu.getMenu().add(0, 2, 1, str(R.string.open_window));
        if (target instanceof AppRepo.AppEntry && host.hasExternalDisplay()) {
            menu.getMenu().add(0, 3, 2, str(R.string.open_external));
        }
        boolean pinned = AppRepo.isPinned(ctx, target.key());
        menu.getMenu().add(0, 4, 3,
                str(pinned ? R.string.unpin_desktop : R.string.pin_desktop));
        menu.getMenu().add(0, 5, 4, str(R.string.app_info));
        menu.getMenu().add(0, 6, 5, str(R.string.uninstall));
        if (target instanceof EcoApps.Entry || target instanceof AppRepo.AppEntry) {
            menu.getMenu().add(0, 7, 6, str(R.string.open_fullscreen));
        }

        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    launchFromUi(target);
                    return true;
                case 2:
                    launchWindowed(target);
                    return true;
                case 3:
                    closeStart();
                    host.launchAppOnExternal((AppRepo.AppEntry) target);
                    return true;
                case 4:
                    AppRepo.togglePinned(ctx, target.key());
                    refresh();
                    return true;
                case 5:
                    host.openAppInfo((AppRepo.AppEntry) target);
                    return true;
                case 6:
                    host.uninstallApp((AppRepo.AppEntry) target);
                    return true;
                case 7:
                    closeStart();
                    if (target instanceof AppRepo.AppEntry) {
                        host.launchApp((AppRepo.AppEntry) target, false);
                    } else {
                        ((EcoApps.Entry) target).open(host.hostContext());
                    }
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void showDesktopMenu() {
        View anchor = startButton != null ? startButton : this;
        PopupMenu menu = new PopupMenu(ctx, anchor);
        menu.getMenu().add(0, 1, 0, str(R.string.start));
        menu.getMenu().add(0, 2, 1, str(R.string.reload_apps));
        menu.getMenu().add(0, 3, 2, str(R.string.files_title));
        menu.getMenu().add(0, 4, 3, str(R.string.terminal_title));
        menu.getMenu().add(0, 5, 4, str(R.string.wallpaper));
        menu.getMenu().add(0, 6, 5, str(R.string.default_home));
        menu.getMenu().add(0, 7, 6, str(R.string.settings_title));
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    openStart();
                    return true;
                case 2:
                    reloadApps();
                    return true;
                case 3:
                    AppRepo.addRecent(ctx, EcoApps.FILES);
                    openWindow(EcoApps.FILES);
                    return true;
                case 4:
                    AppRepo.addRecent(ctx, EcoApps.TERMINAL);
                    openWindow(EcoApps.TERMINAL);
                    return true;
                case 5:
                    host.openWallpaper();
                    return true;
                case 6:
                    host.openHomeSettings();
                    return true;
                case 7:
                    host.openSettings();
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void launchFromUi(EcoItem item) {
        recentIndex = -1;
        closeStart();
        if (item instanceof EcoApps.Entry) {
            AppRepo.addRecent(ctx, item.key());
            openWindow(item.key());
        } else if (item instanceof AppRepo.AppEntry) {
            host.launchApp((AppRepo.AppEntry) item, Prefs.windowedApps(ctx));
        }
    }

    private void launchWindowed(EcoItem item) {
        recentIndex = -1;
        closeStart();
        if (item instanceof EcoApps.Entry) {
            AppRepo.addRecent(ctx, item.key());
            openWindow(item.key());
        } else if (item instanceof AppRepo.AppEntry) {
            host.launchApp((AppRepo.AppEntry) item, true);
        }
    }

    public void onAppLaunched(AppRepo.AppEntry app) {
        rebuildDock();
    }

    public void reloadApps() {
        AppRepo.invalidate();
        refresh();
        Toast.makeText(ctx, str(R.string.reload_apps), Toast.LENGTH_SHORT).show();
    }

    public void refresh() {
        if (wallpaperView == null) {
            return;
        }
        updateDim();
        applySettings();
        rebuildDesktop();
        rebuildDock();
        rebuildStartGrid(search == null ? "" : search.getText().toString());
        updateDefaultHint();
        updateStatus();
    }

    private void applySettings() {
        if (taskbar != null) {
            taskbar.setAlpha(Prefs.autoHideTaskbar(ctx) ? 0.55f : 1f);
        }
        int widthDp = Math.round(metrics().widthPixels
                / getResources().getDisplayMetrics().density);
        if (network != null) {
            network.setVisibility(widthDp < 480 ? GONE : VISIBLE);
        }
        if (deskButton != null) {
            deskButton.setVisibility(widthDp < 480 ? GONE : VISIBLE);
        }
        if (date != null) {
            date.setVisibility(widthDp < 380 ? GONE : VISIBLE);
        }
        updateDim();
    }

    public void onResume() {
        loadWallpaper();
        refresh();
    }

    public void onConfigurationChanged() {
        refresh();
    }

    public void onDestroy() {
        AppRepo.removeListener(appListener);
        unregisterBattery();
        handler.removeCallbacksAndMessages(null);
        closeAllWindows();
        panelEnv = null;
        filesPanel = null;
        editorPanel = null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        AppRepo.addListener(appListener);
        registerBattery();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        requestFocus();
    }

    @Override
    protected void onDetachedFromWindow() {
        AppRepo.removeListener(appListener);
        unregisterBattery();
        handler.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    private void registerBattery() {
        if (batteryRegistered) {
            return;
        }
        try {
            ctx.registerReceiver(batteryReceiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            batteryRegistered = true;
        } catch (Exception ignored) {
        }
    }

    private void unregisterBattery() {
        if (!batteryRegistered) {
            return;
        }
        try {
            ctx.unregisterReceiver(batteryReceiver);
        } catch (Exception ignored) {
        }
        batteryRegistered = false;
    }

    private void updateBattery() {
        if (battery == null) {
            return;
        }
        if (batteryPercent < 0) {
            battery.setText("–");
        } else {
            battery.setText(batteryPercent + "%" + (charging ? " ⚡" : ""));
        }
    }

    private void updateStatus() {
        if (clock == null) {
            return;
        }
        Date now = new Date();
        boolean seconds = Prefs.showSeconds(ctx);
        SimpleDateFormat timeFormat = new SimpleDateFormat(
                seconds ? "HH:mm:ss" : "HH:mm", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "EEE d MMM", Locale.getDefault());
        clock.setText(timeFormat.format(now));
        date.setText(dateFormat.format(now));
        if (network != null) {
            network.setText(networkLabel());
        }
        updateBattery();
    }

    private String networkLabel() {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return "";
            }
            Network active = cm.getActiveNetwork();
            if (active == null) {
                return "Offline";
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            if (caps == null) {
                return "Offline";
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return "Wi-Fi";
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return "Mobile";
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return "Ethernet";
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                return "VPN";
            }
            return "Online";
        } catch (Exception e) {
            return "";
        }
    }

    public void toggleStart() {
        if (startOpen) {
            closeStart();
        } else {
            openStart();
        }
    }

    public void openStart() {
        if (startPanel == null) {
            return;
        }
        if (startOpen) {
            search.requestFocus();
            return;
        }
        startOpen = true;
        search.setText("");
        rebuildStartGrid("");
        if (defaultHint != null) {
            defaultHint.setVisibility(GONE);
        }
        startOverlay.setVisibility(VISIBLE);
        startOverlay.setAlpha(0f);
        startOverlay.animate().alpha(1f).setDuration(140L).start();
        startPanel.setTranslationY(dp(18));
        startPanel.animate().translationY(0f).setDuration(170L).start();
        search.requestFocus();

        Configuration configuration = getResources().getConfiguration();
        if (configuration.keyboard == Configuration.KEYBOARD_NOKEYS) {
            InputMethodManager imm = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            }
        }
    }

    public void closeStart() {
        if (!startOpen || startOverlay == null) {
            return;
        }
        startOpen = false;
        InputMethodManager imm = (InputMethodManager)
                ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && search != null) {
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
        startOverlay.animate().alpha(0f).setDuration(120L)
                .withEndAction(() -> startOverlay.setVisibility(GONE))
                .start();
        if (search != null) {
            search.clearFocus();
        }
        updateDefaultHint();
        requestFocus();
    }

    public boolean handleBack() {
        if (startOpen) {
            closeStart();
            return true;
        }
        return false;
    }

    public boolean handleKeyDown(int keyCode, KeyEvent event) {
        if (event.getRepeatCount() > 0
                && (keyCode == KeyEvent.KEYCODE_SEARCH
                || keyCode == KeyEvent.KEYCODE_MENU
                || keyCode == KeyEvent.KEYCODE_F5
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (startOpen) {
                closeStart();
                return true;
            }
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            toggleStart();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showDesktopMenu();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_F5) {
            reloadApps();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            if (startOpen) {
                View focus = findFocus();
                if (focus != null && focus != search
                        && focus.getTag() instanceof EcoItem) {
                    launchFromUi((EcoItem) focus.getTag());
                    return true;
                }
                if (focus == search) {
                    List<EcoItem> matches = filteredApps(search.getText().toString());
                    if (!matches.isEmpty()) {
                        launchFromUi(matches.get(0));
                        return true;
                    }
                }
                return false;
            }
            View focus = findFocus();
            if (focus != null && focus.getTag() instanceof EcoItem) {
                launchFromUi((EcoItem) focus.getTag());
                return true;
            }
        }
        return false;
    }

    public boolean handleDispatchKey(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return false;
        }
        int keyCode = event.getKeyCode();
        if ((keyCode == KeyEvent.KEYCODE_META_LEFT
                || keyCode == KeyEvent.KEYCODE_META_RIGHT)
                && event.getRepeatCount() == 0) {
            toggleStart();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_TAB && event.isAltPressed()) {
            cycleRecent();
            return true;
        }
        return false;
    }

    private void cycleRecent() {
        List<String> recent = AppRepo.recent(ctx);
        if (recent.size() < 2) {
            Toast.makeText(ctx, str(R.string.recent_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        if (recentIndex < 0 || recentIndex >= recent.size()) {
            recentIndex = 1;
        } else {
            recentIndex = (recentIndex + 1) % recent.size();
        }
        EcoItem item = findItem(recent.get(recentIndex));
        if (item != null) {
            launchFromUi(item);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_BUTTON_PRESS
                && (event.getButtonState() & MotionEvent.BUTTON_SECONDARY) != 0) {
            if (startOpen) {
                closeStart();
            } else {
                showDesktopMenu();
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private EcoItem findItem(String key) {
        if (key == null) {
            return null;
        }
        EcoApps.Entry entry = EcoApps.byId(key);
        if (entry != null) {
            return entry;
        }
        return AppRepo.find(ctx, key);
    }

    private boolean containsItem(List<EcoItem> list, EcoItem item) {
        for (EcoItem candidate : list) {
            if (candidate.key().equals(item.key())) {
                return true;
            }
        }
        return false;
    }

    private LayoutParams match() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
