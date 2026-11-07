package com.pocketdex.launcher.eco;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.pocketdex.launcher.Ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

public class TerminalActivity extends Activity {

    private static final int BG = 0xFF0B0D12;
    private static final int CARD = 0xFF171A24;
    private static final int TEXT = 0xFFF4F6FB;
    private static final int DIM = 0xFF9AA3B5;
    private static final int ACCENT = 0xFF4C8DFF;

    private static final String BANNER =
            "PocketDeX Terminal — /system/bin/sh\n$ ";

    private LinearLayout root;
    private ScrollView scroll;
    private TextView output;
    private EditText input;
    private TextView prompt;
    private TextView runButton;

    private Process process;
    private OutputStreamWriter stdin;
    private final ReentrantLock stdinLock = new ReentrantLock();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        startShell();
    }

    private View buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(10), dp(10), dp(10));

        header.addView(quickButton("Back", v -> finish()),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        header.addView(quickButton("Ctrl+C", v -> sendControlC()),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        header.addView(quickButton("Clear", v -> clearOutput()),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        header.addView(quickButton("Kill", v -> killShell()),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        root.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(ACCENT);
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        output = new TextView(this);
        output.setTextColor(TEXT);
        output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        output.setTypeface(Typeface.MONOSPACE);
        output.setText(BANNER);
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
        prompt.setText("$ ");
        prompt.setTextColor(ACCENT);
        prompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        prompt.setTypeface(Typeface.MONOSPACE);
        prompt.setPadding(0, 0, dp(4), 0);
        inputRow.addView(prompt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        input = new EditText(this);
        input.setTextColor(TEXT);
        input.setHintTextColor(DIM);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
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
        inputRow.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        runButton = quickButton("Run", v -> runInput());
        inputRow.addView(runButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        root.addView(inputRow);

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setGravity(Gravity.CENTER);
        quickRow.setPadding(dp(8), dp(2), dp(8), dp(8));
        quickRow.addView(quickButton("ls", v -> type("ls")));
        quickRow.addView(quickButton("pwd", v -> type("pwd")));
        quickRow.addView(quickButton("cd ~", v -> type("cd ~")));
        quickRow.addView(quickButton("whoami", v -> type("whoami")));
        quickRow.addView(quickButton("top -n 1", v -> type("top -n 1")));
        root.addView(quickRow);

        return root;
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

    private void startShell() {
        try {
            ProcessBuilder builder = new ProcessBuilder(Arrays.asList("/system/bin/sh"));
            builder.redirectErrorStream(true);
            builder.directory(getFilesDir());
            process = builder.start();
            stdin = new OutputStreamWriter(process.getOutputStream());
        } catch (IOException e) {
            appendText("Unable to start shell: " + e.getMessage() + "\n");
            return;
        }

        Thread reader = new Thread(() -> {
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    final String chunk = new String(buffer, 0, read);
                    runOnUiThread(() -> appendText(chunk));
                }
            } catch (IOException ignored) {
            }
        }, "PocketDeX-shell-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void runInput() {
        String command = input.getText().toString();
        if (process == null) {
            startShell();
            if (process == null) {
                return;
            }
        }
        appendText(command + "\n");
        try {
            stdinLock.lock();
            try {
                stdin.write(command + "\n");
                stdin.flush();
            } finally {
                stdinLock.unlock();
            }
        } catch (IOException | NullPointerException e) {
            appendText("shell is not running\n");
            return;
        }
        input.setText("");
    }

    private void type(String command) {
        input.setText(command);
        input.setSelection(command.length());
        runInput();
    }

    private void appendText(String text) {
        String current = output.getText().toString();
        output.setText(current + text);
    }

    private void clearOutput() {
        output.setText(BANNER);
        scroll.fullScroll(ScrollView.FOCUS_DOWN);
    }

    private void sendControlC() {
        try {
            stdinLock.lock();
            try {
                stdin.write('\u0003');
                stdin.flush();
            } finally {
                stdinLock.unlock();
            }
        } catch (Exception e) {
            appendText("\n(no running process)\n");
        }
    }

    private void killShell() {
        if (process != null) {
            process.destroy();
            process = null;
            stdin = null;
        }
        appendText("shell killed\n");
        Toast.makeText(this, "Shell closed", Toast.LENGTH_SHORT).show();
        startShell();
    }

    @Override
    protected void onDestroy() {
        if (process != null) {
            process.destroy();
            process = null;
        }
        stdin = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (killRequestedOnBack()) {
            return;
        }
        super.onBackPressed();
    }

    private boolean killRequestedOnBack() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        return false;
    }
}
