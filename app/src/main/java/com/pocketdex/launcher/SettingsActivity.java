package com.pocketdex.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.health.SystemHealthManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.pocketdex.launcher.eco.EcoApps;
import com.pocketdex.launcher.runtime.DeviceCapabilityProfile;
import com.pocketdex.launcher.runtime.GuacamoleWorkspaceConfig;
import com.pocketdex.launcher.runtime.RuntimeSession;
import com.pocketdex.launcher.runtime.RuntimeSessionStore;
import com.pocketdex.launcher.runtime.VulkanDeviceProbe;
import com.pocketdex.launcher.runtime.WinlatorRuntime;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {

    private static final int BG = 0xFF0B0D12;
    private static final int CARD = 0xFF171A24;
    private static final int TEXT = 0xFFF4F6FB;
    private static final int DIM = 0xFF9AA3B5;
    private static final int ACCENT = 0xFF4C8DFF;
    private static final int DIVIDER = 0xFF2A3040;
    private static final ExecutorService RESOURCE_HEADROOM_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "PocketDeX-resource-headroom");
                thread.setDaemon(true);
                return thread;
            });

    private interface BoolConsumer {
        void accept(boolean value);
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private LinearLayout content;
    private ScrollView settingsScroll;
    private TextView capabilityDetails;
    private TextView remoteSessionDetails;
    private EditText remoteEndpoint;
    private int resourceHeadroomGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildSettingsScreen();
    }

    private void buildSettingsScreen() {
        settingsScroll = new ScrollView(this);
        settingsScroll.setBackgroundColor(BG);
        settingsScroll.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(48), dp(18), dp(40));
        settingsScroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(settingsScroll);

        buildHeader();
        buildAppearance();
        buildEcosystem();
        buildRemoteWorkspace();
        buildRemoteSessionStatus();
        buildLocalWindowsRuntime();
        buildLinuxRuntime();
        buildCapabilities();
        buildBehavior();
        buildHome();
        buildAbout();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        int oldScrollRange = settingsScroll == null || settingsScroll.getChildAt(0) == null
                ? 0 : Math.max(0, settingsScroll.getChildAt(0).getHeight()
                - settingsScroll.getHeight());
        float scrollFraction = oldScrollRange == 0 ? 0f
                : (float) settingsScroll.getScrollY() / oldScrollRange;
        String endpointText = remoteEndpoint == null
                ? "" : remoteEndpoint.getText().toString();
        int endpointSelection = remoteEndpoint == null
                ? 0 : remoteEndpoint.getSelectionStart();
        boolean endpointFocused = remoteEndpoint != null && remoteEndpoint.hasFocus();
        super.onConfigurationChanged(newConfig);
        buildSettingsScreen();
        remoteEndpoint.setText(endpointText);
        remoteEndpoint.setSelection(Math.max(0,
                Math.min(endpointSelection, remoteEndpoint.length())));
        if (endpointFocused) {
            remoteEndpoint.requestFocus();
        }
        settingsScroll.post(() -> {
            int newScrollRange = settingsScroll.getChildAt(0) == null ? 0
                    : Math.max(0, settingsScroll.getChildAt(0).getHeight()
                    - settingsScroll.getHeight());
            settingsScroll.scrollTo(0, Math.round(scrollFraction * newScrollRange));
        });
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private float sp(int value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics());
    }

    private void buildHeader() {
        TextView title = new TextView(this);
        title.setText("PocketDeX Settings");
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Turn your phone or tablet into a PC-style desktop");
        subtitle.setTextColor(DIM);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        content.addView(subtitle);
    }

    private void section(String label) {
        TextView view = new TextView(this);
        view.setText(label.toUpperCase());
        view.setTextColor(ACCENT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(dp(4), dp(18), dp(4), dp(8));
        content.addView(view);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundedStroke(this, CARD, 14, DIVIDER, 1));
        card.setPadding(dp(14), dp(4), dp(14), dp(4));
        content.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(DIVIDER);
        parent.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private TextView titleText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return view;
    }

    private TextView subtitleText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(DIM);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        view.setPadding(0, dp(2), 0, 0);
        return view;
    }

    private void switchRow(LinearLayout parent, String title, String subtitle,
                           boolean value, BoolConsumer onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(titleText(title));
        if (subtitle != null && !subtitle.isEmpty()) {
            texts.addView(subtitleText(subtitle));
        }
        row.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(value);
        toggle.setOnCheckedChangeListener((button, checked) -> onChange.accept(checked));
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (parent.getChildCount() > 0) {
            divider(parent);
        }
        parent.addView(row);
    }

    private SeekBar seekRow(LinearLayout parent, String title, int min, int max,
                            int value, boolean enabled, String suffix,
                            IntConsumer onChange) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, dp(12), 0, dp(12));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        labels.addView(titleText(title), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView valueLabel = new TextView(this);
        valueLabel.setTextColor(ACCENT);
        valueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valueLabel.setTypeface(Typeface.DEFAULT_BOLD);
        valueLabel.setText(value + suffix);
        labels.addView(valueLabel);
        block.addView(labels);

        SeekBar bar = new SeekBar(this);
        bar.setMax(Math.max(1, max - min));
        bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
        bar.setEnabled(enabled);
        bar.setPadding(0, dp(6), 0, dp(2));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int actual = min + progress;
                valueLabel.setText(actual + suffix);
                if (fromUser) {
                    onChange.accept(actual);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        block.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        if (parent.getChildCount() > 0) {
            divider(parent);
        }
        parent.addView(block);
        return bar;
    }

    private Button actionButton(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setBackground(Ui.ripple(this,
                Ui.rounded(this, 0x33232838, 10), 0x44FFFFFF));
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        parent.addView(button, params);
        return button;
    }

    private void buildAppearance() {
        section("Appearance");
        LinearLayout card = card();

        int iconSize = Math.round(Prefs.iconSize(this));
        seekRow(card, "Icon size", 36, 80, iconSize, true, " dp",
                value -> Prefs.setIconSize(this, value));

        boolean auto = Prefs.columns(this) == 0;
        int columns = auto ? 5 : Prefs.columns(this);
        SeekBar[] columnsBar = new SeekBar[1];
        columnsBar[0] = seekRow(card, "Desktop columns", 3, 10, columns, !auto, "",
                value -> Prefs.setColumns(this, value));
        switchRow(card, "Automatic grid", "Fit columns to the screen width", auto,
                checked -> {
                    if (checked) {
                        Prefs.setColumns(this, 0);
                    } else {
                        Prefs.setColumns(this, 5);
                    }
                    if (columnsBar[0] != null) {
                        columnsBar[0].setEnabled(!checked);
                    }
                });

        switchRow(card, "Show app labels", "Display names under desktop icons",
                Prefs.showLabels(this),
                checked -> Prefs.setBoolean(this, "labels", checked));

        seekRow(card, "Wallpaper dim", 0, 70, Prefs.dim(this), true, "%",
                value -> Prefs.setInt(this, "dim", value));
    }

    private void buildBehavior() {
        section("Taskbar & behavior");
        LinearLayout card = card();

        switchRow(card, "Open Android apps in windows by default",
                "Ask Android for movable windows; unsupported devices open apps normally",
                Prefs.windowedApps(this),
                checked -> Prefs.setBoolean(this, "windowed_apps", checked));

        switchRow(card, "Allow installed Android apps",
                "When off, PocketDeX shows and launches only its built-in apps. Android still controls app windows when enabled.",
                Prefs.allowAndroidApps(this),
                checked -> {
                    Prefs.setBoolean(this, "allow_android_apps", checked);
                    AppRepo.invalidate();
                });

        switchRow(card, "Double-tap to open", "Require two taps on desktop icons",
                Prefs.doubleClick(this),
                checked -> Prefs.setBoolean(this, "double_click", checked));

        switchRow(card, "Clock seconds", "Show seconds in the taskbar clock",
                Prefs.showSeconds(this),
                checked -> Prefs.setBoolean(this, "show_seconds", checked));

        switchRow(card, "Fade taskbar", "Dim the taskbar until the pointer hovers it",
                Prefs.autoHideTaskbar(this),
                checked -> Prefs.setBoolean(this, "autohide", checked));

        switchRow(card, "Fullscreen desktop", "Hide status and navigation bars",
                Prefs.fullscreen(this),
                checked -> Prefs.setBoolean(this, "fullscreen", checked));

        switchRow(card, "Desktop on external display",
                "Show a second desktop on HDMI or USB-C monitors",
                Prefs.externalDisplay(this),
                checked -> Prefs.setBoolean(this, "external_display", checked));

        switchRow(card, "Ecosystem on desktop",
                "Pin built-in PocketDeX apps on the desktop grid",
                Prefs.showEcoOnDesktop(this),
                checked -> Prefs.setBoolean(this, "eco_desktop", checked));
    }

    private void buildEcosystem() {
        section("PocketDeX ecosystem");
        LinearLayout card = card();
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setPadding(0, dp(4), 0, dp(4));
        card.addView(buttons);

        actionButton(buttons, "Open Files", () ->
                openEco("com.pocketdex.launcher.eco.FilesActivity"));
        actionButton(buttons, "Open Editor", () ->
                openEco("com.pocketdex.launcher.eco.EditorActivity"));
        actionButton(buttons, "Open Terminal", () ->
                openEco("com.pocketdex.launcher.eco.TerminalActivity"));
        actionButton(buttons, "Open Calculator", () ->
                openEco("com.pocketdex.launcher.eco.CalculatorActivity"));
    }

    private void buildCapabilities() {
        section("Device capabilities");
        LinearLayout capabilityCard = card();

        capabilityDetails = new TextView(this);
        capabilityDetails.setTextColor(DIM);
        capabilityDetails.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        capabilityDetails.setLineSpacing(dp(3), 1f);
        capabilityDetails.setPadding(0, dp(12), 0, dp(8));
        capabilityCard.addView(capabilityDetails);
        updateCapabilityDetails();

        actionButton(capabilityCard, "Refresh device details", this::updateCapabilityDetails);
    }

    private void buildRemoteWorkspace() {
        section("Remote PC workspace");
        LinearLayout remoteCard = card();
        TextView explanation = subtitleText("Connect to your own Apache Guacamole server over HTTPS. "
                + "RDP and VNC hosts are configured on that server; PocketDeX does not provide cloud hosting. "
                + "To send an .exe to a remote Windows PC, use Guacamole's file-transfer menu while "
                + "connected; the server administrator must enable uploads. PocketDeX's local apps "
                + "continue to work without a server.");
        explanation.setPadding(0, dp(10), 0, dp(8));
        remoteCard.addView(explanation);

        remoteEndpoint = new EditText(this);
        remoteEndpoint.setSingleLine(true);
        remoteEndpoint.setTextColor(TEXT);
        remoteEndpoint.setHintTextColor(DIM);
        remoteEndpoint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        remoteEndpoint.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        remoteEndpoint.setHint("https://your-server/guacamole/");
        remoteEndpoint.setText(getSharedPreferences(GuacamoleWorkspaceConfig.PREFS, MODE_PRIVATE)
                .getString(GuacamoleWorkspaceConfig.KEY_ENDPOINT, ""));
        remoteCard.addView(remoteEndpoint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        switchRow(remoteCard, "Open Remote PC on an external display",
                "Use a connected, allowed HDMI or USB-C display when available",
                GuacamoleWorkspaceConfig.launchOnExternalDisplay(this),
                checked -> GuacamoleWorkspaceConfig.setLaunchOnExternalDisplay(this, checked));

        actionButton(remoteCard, "Save and connect", () -> {
            android.net.Uri normalized = GuacamoleWorkspaceConfig
                    .normalizeEndpoint(remoteEndpoint.getText().toString());
            if (normalized == null) {
                android.widget.Toast.makeText(this,
                        "Enter a valid HTTPS Guacamole server address",
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            GuacamoleWorkspaceConfig.saveEndpoint(this, normalized);
            EcoApps.byId(EcoApps.REMOTE_PC).open(this);
        });
    }

    private void buildLocalWindowsRuntime() {
        section("Local Windows runtime");
        LinearLayout runtimeCard = card();
        TextView description = subtitleText("Winlator can run some x86-64 Windows apps locally "
                + "using Wine and Box64. It requires a 64-bit ARM device and is installed separately. "
                + "Compatibility varies by app, device and graphics backend; PocketDeX records the "
                + "installed version and launch dispatch, not container or app health. Winlator's "
                + "current app interface exposes its launcher, not direct Windows app launch.");
        description.setPadding(0, dp(10), 0, dp(8));
        runtimeCard.addView(description);
        actionButton(runtimeCard, "Open official Winlator releases", () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse(WinlatorRuntime.RELEASES_URL)));
            } catch (Exception e) {
                android.widget.Toast.makeText(this, "Unable to open Winlator releases",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void buildLinuxRuntime() {
        section("Linux desktop and terminal options");
        LinearLayout linuxCard = card();
        TextView description = subtitleText("Optional user-managed Linux desktop using Termux, "
                + "PRoot-Distro and Termux:X11. PocketDeX does not install or control this environment. "
                + "Install Termux and Termux:X11 from compatible signing sources, then install the "
                + "Termux:X11 companion package and a Linux userland. On select devices, Android's "
                + "separate Linux Terminal provides Debian in an OS-managed VM for command-line use, "
                + "not a graphical desktop.");
        description.setPadding(0, dp(10), 0, dp(8));
        linuxCard.addView(description);

        TextView steps = subtitleText("In Termux:\n"
                + "1. pkg install x11-repo, then pkg install termux-x11-nightly proot-distro\n"
                + "2. proot-distro install debian\n"
                + "3. Start the X server: termux-x11 :1 &\n"
                + "4. Enter Debian: proot-distro login debian --shared-tmp\n"
                + "5. In Debian: apt update && apt install xfce4 dbus-x11\n"
                + "6. In Debian: DISPLAY=:1 dbus-launch --exit-with-session xfce4-session\n\n"
                + "This is a user-space Linux environment, not a VM: it shares Android's kernel and "
                + "device resources. PocketDeX's built-in Terminal is separate from Termux.");
        steps.setTextColor(DIM);
        steps.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        steps.setLineSpacing(dp(3), 1f);
        steps.setPadding(0, dp(4), 0, dp(8));
        linuxCard.addView(steps);

        actionButton(linuxCard, "Termux:X11 setup and usage", () -> openExternalUrl(
                "https://github.com/termux/termux-x11"));
        actionButton(linuxCard, "PRoot-Distro documentation", () -> openExternalUrl(
                "https://github.com/termux/proot-distro"));
        actionButton(linuxCard, "Android Linux development environment", () -> openExternalUrl(
                "https://source.android.com/docs/core/virtualization/usecases"));
        actionButton(linuxCard, "Android background and battery guidance", () -> openExternalUrl(
                "https://developer.android.com/topic/performance/power/power-details"));
        TextView backgroundNote = subtitleText("If Android stops Termux or Termux:X11 when they are "
                + "in the background, review their per-app battery settings. Unrestricted mode may "
                + "use more battery and does not remove the separate-UID CPU scheduling slowdown "
                + "described by Termux:X11.");
        backgroundNote.setPadding(0, dp(8), 0, dp(4));
        linuxCard.addView(backgroundNote);
    }

    private void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Unable to open documentation",
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void buildRemoteSessionStatus() {
        section("Runtime session status");
        LinearLayout sessionCard = card();
        remoteSessionDetails = subtitleText("");
        remoteSessionDetails.setPadding(0, dp(10), 0, dp(8));
        sessionCard.addView(remoteSessionDetails);
        actionButton(sessionCard, "Refresh session status", this::updateRuntimeSessionDetails);
        updateRuntimeSessionDetails();
    }

    private void updateRuntimeSessionDetails() {
        if (remoteSessionDetails == null) {
            return;
        }
        RuntimeSession session = RuntimeSessionStore.current(this);
        if (session == null) {
            remoteSessionDetails.setText("No runtime launch has been recorded yet.");
            return;
        }
        StringBuilder details = new StringBuilder()
                .append("App: ").append(session.appId()).append(" (")
                .append(session.runtimeId()).append(")\n")
                .append("State: ").append(session.state()).append("\n")
                .append(session.detail());
        if (session.gatewayResponseMillis() >= 0) {
            details.append("\nGateway response: ")
                    .append(session.gatewayResponseMillis()).append(" ms (HTTP ")
                    .append(session.gatewayHttpStatus()).append(")");
        }
        remoteSessionDetails.setText(details.toString());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateRuntimeSessionDetails();
        updateCapabilityDetails();
    }

    private void updateCapabilityDetails() {
        DeviceCapabilityProfile profile = DeviceCapabilityProfile.capture(this);
        String abis = profile.supportedAbis().isEmpty()
                ? "Unknown" : String.join(", ", profile.supportedAbis());
        String totalMemory = android.text.format.Formatter.formatFileSize(
                this, profile.totalMemoryBytes());
        String advertisedMemory = profile.advertisedMemoryBytes() > 0
                ? android.text.format.Formatter.formatFileSize(
                        this, profile.advertisedMemoryBytes()) : "not reported";
        String availableMemory = android.text.format.Formatter.formatFileSize(
                this, profile.availableMemoryBytes());
        String appMemoryClass = profile.appMemoryClassMb() > 0
                ? profile.appMemoryClassMb() + " MB (approximate)" : "not reported";
        String desktopSetup = (profile.hasPhysicalKeyboardConnected()
                ? "Keyboard connected" : "Connect a hardware keyboard") + " · "
                + (profile.hasPointerConnected()
                ? "pointer or stylus detected" : "connect a mouse or trackpad") + " · "
                + (profile.presentationDisplayCount() > 0
                ? "presentation display available"
                : "connect a supported external display");
        StringBuilder details = new StringBuilder("Device: ")
                .append(profile.manufacturer()).append(' ').append(profile.model()).append("\n")
                .append("Desktop setup: ").append(desktopSetup).append("\n")
                .append("Android API: ").append(profile.androidApi()).append("\n")
                .append("Supported app ABIs: ").append(abis).append("\n")
                .append("Vulkan GPU (system driver): checking…\n")
                .append("Advertised device memory: ").append(advertisedMemory).append("\n")
                .append("Kernel-accessible RAM: ").append(totalMemory).append("\n")
                .append("Available to system: ").append(availableMemory)
                .append(" (snapshot; includes reclaimable RAM)\n")
                .append("PocketDeX app heap class: ").append(appMemoryClass)
                .append("; not device RAM\n")
                .append("External presentation displays (physical mode, max app area, density): ")
                .append(profile.presentationDisplayCount()).append("\n")
                .append("Activities on secondary displays: ")
                .append(profile.supportsActivitiesOnSecondaryDisplays()
                        ? "advertised; device policy can still reject a launch"
                        : "not advertised").append("\n")
                .append("Freeform window management: ")
                .append(profile.supportsFreeformWindowManagement()
                        ? "advertised" : "not advertised").append("\n")
                .append("OS thermal status (snapshot): ")
                .append(thermalStatusLabel(profile.thermalStatus())).append("\n")
                .append("10-second thermal headroom forecast: ")
                .append(thermalHeadroomLabel(profile.thermalHeadroom())).append("\n")
                .append("PocketDeX process CPU headroom: ")
                .append(Build.VERSION.SDK_INT >= 36 ? "checking…"
                        : "not available (Android 16+)").append("\n")
                .append("Device GPU headroom: ")
                .append(Build.VERSION.SDK_INT >= 36 ? "checking…"
                        : "not available (Android 16+)").append("\n")
                .append("Thermal status is not a temperature reading; estimates and device "
                        + "support may vary. CPU/GPU values are capacity estimates, not "
                        + "benchmark scores. "
                        + "Per-display policy may still ignore window bounds. "
                        + "App-level performance is not measured.\n"
                        + "Vulkan reports the system driver visible to PocketDeX; it does not "
                        + "identify the driver selected inside Winlator.");
        for (String displaySummary : profile.externalDisplaySummaries()) {
            details.append('\n').append(displaySummary);
        }
        capabilityDetails.setText(details.toString());
        int generation = ++resourceHeadroomGeneration;
        requestVulkanSnapshot(generation);
        requestResourceHeadroomSnapshot(generation);
    }

    private void requestVulkanSnapshot(int generation) {
        RESOURCE_HEADROOM_EXECUTOR.execute(() -> {
            String vulkan = VulkanDeviceProbe.query();
            runOnUiThread(() -> {
                if (generation != resourceHeadroomGeneration || capabilityDetails == null
                        || isFinishing()) {
                    return;
                }
                capabilityDetails.setText(capabilityDetails.getText().toString()
                        .replace("Vulkan GPU (system driver): checking…",
                                "Vulkan GPU (system driver): " + vulkan));
            });
        });
    }

    private void requestResourceHeadroomSnapshot(int generation) {
        if (Build.VERSION.SDK_INT < 36) {
            return;
        }
        RESOURCE_HEADROOM_EXECUTOR.execute(() -> {
            float cpuHeadroom = Float.NaN;
            float gpuHeadroom = Float.NaN;
            try {
                SystemHealthManager healthManager =
                        getSystemService(SystemHealthManager.class);
                if (healthManager != null) {
                    cpuHeadroom = healthManager.getCpuHeadroom(null);
                    gpuHeadroom = healthManager.getGpuHeadroom(null);
                }
            } catch (RuntimeException ignored) {
                // Capacity estimates are optional and unsupported on some devices.
            }
            final float cpu = cpuHeadroom;
            final float gpu = gpuHeadroom;
            runOnUiThread(() -> {
                if (generation != resourceHeadroomGeneration || capabilityDetails == null
                        || isFinishing()) {
                    return;
                }
                String details = capabilityDetails.getText().toString()
                        .replace("PocketDeX process CPU headroom: checking…",
                                "PocketDeX process CPU headroom: " + resourceHeadroomLabel(cpu))
                        .replace("Device GPU headroom: checking…",
                                "Device GPU headroom: " + resourceHeadroomLabel(gpu));
                capabilityDetails.setText(details);
            });
        });
    }

    private String resourceHeadroomLabel(float headroom) {
        if (Float.isNaN(headroom) || Float.isInfinite(headroom)
                || headroom < 0f || headroom > 100f) {
            return "not reported";
        }
        return String.format(java.util.Locale.ROOT, "%.0f%% estimated capacity available",
                headroom);
    }

    private String thermalHeadroomLabel(float headroom) {
        if (Float.isNaN(headroom) || Float.isInfinite(headroom) || headroom < 0f) {
            return "not reported";
        }
        return String.format(java.util.Locale.ROOT, "%.2f (closer to 1 means less headroom)",
                headroom);
    }

    private String thermalStatusLabel(int status) {
        switch (status) {
            case android.os.PowerManager.THERMAL_STATUS_NONE:
                return "none reported";
            case android.os.PowerManager.THERMAL_STATUS_LIGHT:
                return "light mitigation";
            case android.os.PowerManager.THERMAL_STATUS_MODERATE:
                return "moderate mitigation";
            case android.os.PowerManager.THERMAL_STATUS_SEVERE:
                return "severe mitigation";
            case android.os.PowerManager.THERMAL_STATUS_CRITICAL:
                return "critical";
            case android.os.PowerManager.THERMAL_STATUS_EMERGENCY:
                return "emergency";
            case android.os.PowerManager.THERMAL_STATUS_SHUTDOWN:
                return "shutdown status";
            default:
                return "not reported";
        }
    }

    private void openEco(String className) {
        try {
            startActivity(new Intent().setClassName(getPackageName(), className));
        } catch (Exception ignored) {
        }
    }

    private void buildHome() {
        section("Home screen");
        LinearLayout card = card();
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setPadding(0, dp(4), 0, dp(4));
        card.addView(buttons);

        actionButton(buttons, "Set PocketDeX as default home", () -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS));
            } catch (Exception ignored) {
            }
        });
        actionButton(buttons, "Change wallpaper", () -> {
            try {
                startActivity(Intent.createChooser(
                        new Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper"));
            } catch (Exception ignored) {
            }
        });
        actionButton(buttons, "Reload app list", () -> {
            AppRepo.invalidate();
            android.widget.Toast.makeText(this, "App list refreshed",
                    android.widget.Toast.LENGTH_SHORT).show();
        });
        actionButton(buttons, "Reset all settings", () -> {
            Prefs.get(this).edit().clear().apply();
            android.widget.Toast.makeText(this, "Settings reset",
                    android.widget.Toast.LENGTH_SHORT).show();
            recreate();
        });
    }

    private void buildAbout() {
        section("About");
        LinearLayout card = card();

        TextView about = new TextView(this);
        about.setText("PocketDeX is a PC-style desktop launcher for Android. "
                + "It replaces your home screen with a desktop, start menu and taskbar.\n\n"
                + "PocketDeX's built-in apps use in-desktop windows. Other Android apps remain "
                + "Android-managed and use freeform windows when the device supports them.\n\n"
                + "Windowed apps and the external-display desktop depend on your "
                + "device's multi-window support. If the device does not support "
                + "freeform windows, apps open fullscreen instead.\n\n"
                + "Keyboard & mouse:\n"
                + "• Start / Meta key: open Start menu\n"
                + "• Esc: close Start menu\n"
                + "• Arrow keys + Enter: navigate and open\n"
                + "• Right-click: context menu\n"
                + "• Alt+Tab: switch recent apps\n"
                + "• F5: reload app list\n"
                + "• Hold arrow keys to move focus; Start, menu and open actions run once per press");
        about.setTextColor(DIM);
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        about.setLineSpacing(dp(2), 1f);
        about.setPadding(0, dp(12), 0, dp(12));
        card.addView(about);

        TextView version = new TextView(this);
        version.setText("Version " + BuildConfig.VERSION_NAME);
        version.setTextColor(DIM);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        version.setPadding(0, dp(4), 0, dp(10));
        card.addView(version);
    }
}
