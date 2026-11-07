package com.pocketdex.launcher.eco;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.pocketdex.launcher.Ui;

public class TerminalActivity extends Activity implements ShellEngine.Listener {

    private static final int BG = 0xFF0B0D12;
    private static final int TEXT = 0xFFF4F6FB;
    private static final int DIM = 0xFF9AA3B5;
    private static final int ACCENT = 0xFF4C8DFF;

    private static final String BANNER =
            "PocketDeX Terminal — /system/bin/sh (per-command mode)\n";

    private LinearLayout root;
    private ScrollView scroll;
    private TextView output;
    private TextView prompt;
    private EditText input;
    private ShellEngine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        engine = new ShellEngine(this);
        appendBanner();
        updatePrompt();
    }

    private View buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(10), dp(10), dp(10));

        header.addView(quickButton("Back", v -> finish()));
        header.addView(quickButton("Stop", v -> {
            if (engine.busy()) {
                engine.stop();
                appendText("[stopped]\n");
            } else {
                Toast.makeText(this, "Nothing running", Toast.LENGTH_SHORT).show();
            }
        }));
        header.addView(quickButton("Clear", v -> clearOutput()));
        header.addView(quickButton("Help", v -> showHelp()));
        root.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(ACCENT);
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        output = new TextView(this);
        output.setTextColor(TEXT);
        output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        output.setTypeface(Typeface.MONOSPACE);
        output.setPadding(dp(10), dp(10), dp(10), dp(16));
        output.setTextIsSelectable(true);

        scroll = new ScrollView(this);
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) ->
                scroll.fullScroll(ScrollView.FOCUS_DOWN));
        scroll.addView(output, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(10), dp(6), dp(10), dp(6));

        prompt = new TextView(this);
        prompt.setTextColor(ACCENT);
        prompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        prompt.setTypeface(Typeface.MONOSPACE);
        prompt.setSingleLine(true);
        prompt.setPadding(0, 0, dp(4), 0);
        inputRow.addView(prompt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        input = new EditText(this);
        input.setTextColor(TEXT);
        input.setHintTextColor(DIM);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        input.setTypeface(Typeface.MONOSPACE);
        input.setSingleLine(true);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        input.setHint("command");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setOnEditorActionListener((v, actionId, event) -> {
            runInput();
            return true;
        });
        input.setOnKeyListener((view, keyCode, event) -> {
            return event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getRepeatCount() == 0
                    && handleStopShortcut(event);
        });
        inputRow.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        inputRow.addView(quickButton("Run", v -> runInput()));
        root.addView(inputRow);

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setGravity(Gravity.CENTER);
        quickRow.setPadding(dp(8), dp(2), dp(8), dp(8));
        quickRow.addView(quickButton("ls", v -> type("ls")));
        quickRow.addView(quickButton("pwd", v -> type("pwd")));
        quickRow.addView(quickButton("cd ~", v -> type("cd ~")));
        quickRow.addView(quickButton("whoami", v -> type("whoami")));
        quickRow.addView(quickButton("uname -a", v -> type("uname -a")));
        root.addView(quickRow);

        return root;
    }

    @Override
    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        if (isStopShortcut(event) && event.getRepeatCount() > 0) {
            return true;
        }
        if (handleStopShortcut(event)) {
            return true;
        }
        return super.dispatchKeyShortcutEvent(event);
    }

    private boolean isStopShortcut(KeyEvent event) {
        return event != null && event.getKeyCode() == KeyEvent.KEYCODE_C
                && event.isCtrlPressed() && !event.isShiftPressed()
                && !event.isAltPressed() && !event.isMetaPressed();
    }

    private boolean handleStopShortcut(KeyEvent event) {
        if (!isStopShortcut(event) || engine == null || !engine.busy()
                || input == null || input.length() != 0
                || getCurrentFocus() != input) {
            return false;
        }
        engine.stop();
        appendText("^C\n[stopped]\n");
        return true;
    }

    private TextView quickButton(String label, View.OnClickListener action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        button.setTypeface(Typeface.MONOSPACE);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(Ui.ripple(this,
                Ui.rounded(this, 0x33232838, 8), 0x44FFFFFF));
        button.setOnClickListener(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        params.rightMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private void appendBanner() {
        output.setText(BANNER);
        scroll.fullScroll(ScrollView.FOCUS_DOWN);
    }

    private void showHelp() {
        appendText("\nhelp: type a command and press Run or Enter.\n"
                + "cd           go to /storage/emulated/0\n"
                + "cd ../..     relative paths work\n"
                + "Ctrl+C       stop the running command when the command field is empty\n"
                + "Stop         kill the running command\n");
    }

    private void runInput() {
        String command = input.getText().toString();
        input.setText("");
        type(command);
    }

    private void type(String command) {
        String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (engine.busy()) {
            Toast.makeText(this, "A command is running — Stop it first",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        appendText(promptText() + " " + trimmed + "\n");
        engine.run(trimmed);
        scroll.fullScroll(ScrollView.FOCUS_DOWN);
    }

    private void appendText(String text) {
        output.append(text);
        scroll.fullScroll(ScrollView.FOCUS_DOWN);
    }

    private void clearOutput() {
        appendBanner();
        updatePrompt();
    }

    private String promptText() {
        String path = engine.cwd();
        String shortPath;
        if (path.equals("/")) {
            shortPath = "/";
        } else {
            int cut = path.lastIndexOf('/');
            shortPath = path.substring(cut + 1) + " >";
        }
        return shortPath + " $";
    }

    private void updatePrompt() {
        prompt.setText(promptText());
    }

    @Override
    public void onOutput(String text) {
        appendText(text);
    }

    @Override
    public void onPrompt(String cwd) {
        updatePrompt();
    }

    @Override
    public void onDone(String command, int exitCode) {
        appendText("[exit " + exitCode + "]\n");
        updatePrompt();
    }

    @Override
    public void onError(String message) {
        appendText(message + "\n");
    }

    @Override
    protected void onDestroy() {
        if (engine != null) {
            engine.shutdown();
            engine = null;
        }
        super.onDestroy();
    }
}
