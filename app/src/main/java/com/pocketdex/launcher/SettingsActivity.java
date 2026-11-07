package com.pocketdex.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
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

import com.pocketdex.launcher.eco.RemoteWorkspaceActivity;
import com.pocketdex.launcher.runtime.DeviceCapabilityProfile;

public class SettingsActivity extends Activity {

    private static final int BG = 0xFF0B0D12;
    private static final int CARD = 0xFF171A24;
    private static final int TEXT = 0xFFF4F6FB;
    private static final int DIM = 0xFF9AA3B5;
    private static final int ACCENT = 0xFF4C8DFF;
    private static final int DIVIDER = 0xFF2A3040;

    private interface BoolConsumer {
        void accept(boolean value);
    }

    private interface IntConsumer {
        void accept(int value);
    }

    private LinearLayout content;
    private TextView capabilityDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(48), dp(18), dp(40));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        buildHeader();
        buildAppearance();
        buildEcosystem();
        buildRemoteWorkspace();
        buildCapabilities();
        buildBehavior();
        buildHome();
        buildAbout();
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
                + "RDP and VNC hosts are configured on that server; PocketDeX does not provide cloud hosting.");
        explanation.setPadding(0, dp(10), 0, dp(8));
        remoteCard.addView(explanation);

        EditText endpoint = new EditText(this);
        endpoint.setSingleLine(true);
        endpoint.setTextColor(TEXT);
        endpoint.setHintTextColor(DIM);
        endpoint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        endpoint.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setHint("https://your-server/guacamole/");
        endpoint.setText(getSharedPreferences(RemoteWorkspaceActivity.PREFS, MODE_PRIVATE)
                .getString(RemoteWorkspaceActivity.KEY_GUACAMOLE_URL, ""));
        remoteCard.addView(endpoint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        actionButton(remoteCard, "Save and connect", () -> {
            android.net.Uri normalized = RemoteWorkspaceActivity
                    .normalizeEndpoint(endpoint.getText().toString());
            if (normalized == null) {
                android.widget.Toast.makeText(this,
                        "Enter a valid HTTPS Guacamole server address",
                        android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            getSharedPreferences(RemoteWorkspaceActivity.PREFS, MODE_PRIVATE)
                    .edit().putString(RemoteWorkspaceActivity.KEY_GUACAMOLE_URL,
                            normalized.toString()).apply();
            startActivity(new Intent(this, RemoteWorkspaceActivity.class));
        });
    }

    private void updateCapabilityDetails() {
        DeviceCapabilityProfile profile = DeviceCapabilityProfile.capture(this);
        String abis = profile.supportedAbis().isEmpty()
                ? "Unknown" : String.join(", ", profile.supportedAbis());
        String totalMemory = android.text.format.Formatter.formatFileSize(
                this, profile.totalMemoryBytes());
        String availableMemory = android.text.format.Formatter.formatFileSize(
                this, profile.availableMemoryBytes());
        capabilityDetails.setText("Device: " + profile.manufacturer() + " "
                + profile.model() + "\n"
                + "Android API: " + profile.androidApi() + "\n"
                + "Supported app ABIs: " + abis + "\n"
                + "System RAM: " + totalMemory + " total; " + availableMemory
                + " currently available\n"
                + "Presentation displays: " + profile.presentationDisplayCount() + "\n"
                + "GPU performance is not measured by this report.");
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
                + "Windowed apps and the external-display desktop depend on your "
                + "device's multi-window support. If the device does not support "
                + "freeform windows, apps open fullscreen instead.\n\n"
                + "Keyboard & mouse:\n"
                + "• Start / Meta key: open Start menu\n"
                + "• Esc: close Start menu\n"
                + "• Arrow keys + Enter: navigate and open\n"
                + "• Right-click: context menu\n"
                + "• Alt+Tab: switch recent apps\n"
                + "• F5: reload app list");
        about.setTextColor(DIM);
        about.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        about.setLineSpacing(dp(2), 1f);
        about.setPadding(0, dp(12), 0, dp(12));
        card.addView(about);

        TextView version = new TextView(this);
        version.setText("Version 2.0.0");
        version.setTextColor(DIM);
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        version.setPadding(0, dp(4), 0, dp(10));
        card.addView(version);
    }
}
