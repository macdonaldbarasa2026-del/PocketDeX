package com.pocketdex.launcher.eco;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.pocketdex.launcher.Prefs;
import com.pocketdex.launcher.Ui;
import com.pocketdex.launcher.runtime.WindowsExecutableFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class Panels {
    private Panels() {
    }

    private static final int BG = 0xFF0B0D12;
    private static final int CARD = 0xFF171A24;
    private static final int KEY = 0xFF232838;
    private static final int TEXT = 0xFFF4F6FB;
    private static final int DIM = 0xFF9AA3B5;
    private static final int ACCENT = 0xFF4C8DFF;
    private static final int DIVIDER = 0xFF2A3040;

    public interface Env {
        Context context();

        Activity activity();

        void runOnUi(Runnable action);

        void refreshHome();

        void applyFullscreen();

        void wallpaper();

        void homeSettings();

        void openEditor(Uri uri, String name);

        void pickFilesRoot();

        void openWithSystem(Uri uri, String mime);

        void stageExecutableForWinlator(Uri uri, String name);

        void toast(String message);
    }

    public interface Panel {
        View view();

        default void destroy() {
        }
    }

    public static Panel create(String key, Env env) {
        switch (key) {
            case EcoApps.CALCULATOR:
                return new CalculatorWidget(env);
            case EcoApps.TERMINAL:
                return new TerminalWidget(env);
            case EcoApps.FILES:
                return new FilesWidget(env);
            case EcoApps.SETTINGS:
                return new SettingsWidget(env);
            default:
                return null;
        }
    }

    public static Panel createEditor(Env env, Uri uri, String name) {
        return new EditorWidget(env, uri, name);
    }

    static int dp(Context c, float value) {
        return Ui.dp(c, value);
    }

    static TextView miniButton(Context c, String label, View.OnClickListener listener) {
        TextView button = new TextView(c);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        button.setMinWidth(dp(c, 26));
        button.setBackground(Ui.ripple(c, Ui.rounded(c, KEY, 8), 0x44FFFFFF));
        button.setOnClickListener(listener);
        return button;
    }

    static TextView heading(Context c, String label, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(c);
        view.setText(label);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private static void promptInput(Activity activity, String heading, String initial,
                                    Consumer<String> onOk) {
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setText(initial);
        input.setTextColor(TEXT);
        input.setSelection(initial.length());
        input.setBackground(Ui.roundedStroke(activity, KEY, 8, DIVIDER, 1));
        input.setPadding(dp(activity, 10), dp(activity, 8),
                dp(activity, 10), dp(activity, 8));
        new AlertDialog.Builder(activity)
                .setTitle(heading)
                .setView(input)
                .setPositiveButton("OK", (dialog, which) ->
                        onOk.accept(input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024f);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f);
        }
        return String.format(Locale.US, "%.2f GB", bytes / 1024f / 1024f / 1024f);
    }

    // ------------------------------------------------------------------
    // TERMINAL — real /system/bin/sh execution behind a window
    // ------------------------------------------------------------------
    public static final class TerminalWidget implements Panel, ShellEngine.Listener {
        private final Env env;
        private final LinearLayout root;
        private final TextView output;
        private final TextView promptText;
        private final EditText input;
        private final ScrollView scroll;
        private final ShellEngine engine;

        private TerminalWidget(Env env) {
            this.env = env;
            engine = new ShellEngine(this);
            Context c = env.context();
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(BG);
            root.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));

            LinearLayout header = new LinearLayout(c);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.addView(miniButton(c, "Stop", v -> engine.stop()));
            header.addView(spacer(c, 6));
            header.addView(miniButton(c, "Clear", v -> clear()));
            header.addView(spacer(c, 6));
            header.addView(miniButton(c, "ls", v -> run("ls")));
            header.addView(spacer(c, 6));
            header.addView(miniButton(c, "exit", v -> engine.run("exit")));
            TextView spacer = new TextView(c);
            spacer.setText("");
            header.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
            header.addView(miniButton(c, "Help", v -> showHelp()));
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 28));
            root.addView(header, headerParams);

            output = new TextView(c);
            output.setTextColor(TEXT);
            output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            output.setTypeface(Typeface.MONOSPACE);
            output.setPadding(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 4));
            output.setTextIsSelectable(true);

            scroll = new ScrollView(c);
            scroll.addView(output, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            scrollParams.topMargin = dp(c, 6);
            scrollParams.bottomMargin = dp(c, 6);
            root.addView(scroll, scrollParams);

            LinearLayout runRow = new LinearLayout(c);
            runRow.setOrientation(LinearLayout.HORIZONTAL);
            runRow.setGravity(Gravity.CENTER_VERTICAL);

            promptText = new TextView(c);
            promptText.setTextColor(ACCENT);
            promptText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            promptText.setTypeface(Typeface.MONOSPACE);
            promptText.setPadding(0, 0, dp(c, 4), 0);
            promptText.setSingleLine(true);
            promptText.setEllipsize(TextUtils.TruncateAt.START);
            runRow.addView(promptText, new LinearLayout.LayoutParams(
                    ViewGroup.MarginLayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            input = new EditText(c);
            input.setTextColor(TEXT);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            input.setTypeface(Typeface.MONOSPACE);
            input.setSingleLine(true);
            input.setBackgroundColor(Color.TRANSPARENT);
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setHint("command");
            input.setHintTextColor(DIM);
            input.setOnEditorActionListener((v, actionId, event) -> {
                runInput();
                return true;
            });
            runRow.addView(input, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            runRow.addView(miniButton(c, "Run", v -> runInput()));
            root.addView(runRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 34)));

            clear();
        }

        private static TextView spacer(Context c, int widthDp) {
            TextView view = new TextView(c);
            view.setText("");
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(c, widthDp), 1);
            view.setLayoutParams(params);
            return view;
        }

        private void showHelp() {
            output.append("help: type a command and press Run or Enter.\n"
                    + "cd          go to /storage/emulated/0\n"
                    + "cd ../..    relative paths work\n"
                    + "ls, pwd, whoami, uname -a   try them\n"
                    + "Stop        kill the running command\n"
                    + "exit        end the shell\n");
        }

        private void clear() {
            output.setText("PocketDeX Terminal — /system/bin/sh (per-command mode)\n");
            updatePrompt();
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        }

        private void runInput() {
            String command = input.getText().toString();
            input.setText("");
            run(command);
        }

        private void run(String command) {
            String trimmed = command == null ? "" : command.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            if (engine.busy()) {
                env.toast("A command is running — Stop it first");
                return;
            }
            output.append(promptText.getText().toString() + " " + trimmed + "\n");
            engine.run(trimmed);
        }

        private void updatePrompt() {
            String path = engine.cwd();
            String shortPath;
            if (path.equals("/")) {
                shortPath = "/";
            } else {
                int cut = path.lastIndexOf('/');
                shortPath = path.substring(cut + 1) + " >";
            }
            promptText.setText(shortPath + " $");
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        }

        @Override
        public void onOutput(String text) {
            env.runOnUi(() -> {
                output.append(text);
                scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
            });
        }

        @Override
        public void onPrompt(String cwd) {
            env.runOnUi(this::updatePrompt);
        }

        @Override
        public void onDone(String command, int exitCode) {
            env.runOnUi(() -> {
                output.append("[exit " + exitCode + "]\n");
                updatePrompt();
            });
        }

        @Override
        public void onError(String message) {
            env.runOnUi(() -> output.append(message + "\n"));
        }

        @Override
        public View view() {
            return root;
        }

        @Override
        public void destroy() {
            engine.shutdown();
        }
    }

    // ------------------------------------------------------------------
    // CALCULATOR
    // ------------------------------------------------------------------
    public static final class CalculatorWidget implements Panel {
        private final Env env;
        private final LinearLayout root;
        private final TextView display;
        private final TextView preview;
        private final TextView history;
        private final StringBuilder expr = new StringBuilder();
        private double memory = 0d;
        private String lastResult = "";

        private static final String[][] ROWS = {
                {"C", "^", "(", ")"},
                {"7", "8", "9", "÷"},
                {"4", "5", "6", "×"},
                {"1", "2", "3", "−"},
                {"0", ".", "=", "+"},
                {"⌫", "%", "M+", "MR"},
        };

        private CalculatorWidget(Env env) {
            this.env = env;
            Context c = env.context();
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(BG);
            root.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));

            LinearLayout top = new LinearLayout(c);
            top.setOrientation(LinearLayout.VERTICAL);
            top.setBackground(Ui.roundedStroke(c, CARD, 12, DIVIDER, 1));
            top.setPadding(dp(c, 10), dp(c, 6), dp(c, 10), dp(c, 6));

            history = heading(c, "prev:", 9, DIM, false);
            history.setGravity(Gravity.END);
            history.setSingleLine(true);
            history.setPadding(0, dp(c, 1), 0, dp(c, 1));
            top.addView(history);

            display = heading(c, "0", 20, TEXT, true);
            display.setGravity(Gravity.END);
            display.setSingleLine(true);
            history.setOnClickListener(v -> {
                if (!lastResult.isEmpty()) {
                    expr.append(lastResult);
                    display.setText(expr.toString());
                    refreshPreview();
                }
            });
            top.addView(display);

            preview = heading(c, "", 12, DIM, false);
            preview.setGravity(Gravity.END);
            preview.setSingleLine(true);
            top.addView(preview);

            root.addView(top, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            for (String[] rowLabels : ROWS) {
                LinearLayout row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                for (String label : rowLabels) {
                    TextView button = key(c, label, this);
                    row.addView(button, new LinearLayout.LayoutParams(
                            0, dp(c, 38), 1f));
                }
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = dp(c, 6);
                root.addView(row, rowParams);
            }
        }

        private static TextView key(Context c, String label, CalculatorWidget self) {
            TextView button = new TextView(c);
            button.setText(label);
            button.setTextColor(TEXT);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            button.setTypeface(Typeface.DEFAULT_BOLD);
            button.setGravity(Gravity.CENTER);
            button.setBackground(Ui.ripple(c,
                    Ui.rounded(c, KEY, 10), 0x44FFFFFF));
            button.setOnClickListener(v -> self.onKey(label));
            return button;
        }

        private void onKey(String label) {
            switch (label) {
                case "C":
                    expr.setLength(0);
                    preview.setText("");
                    break;
                case "⌫":
                    if (expr.length() > 0) {
                        expr.setLength(expr.length() - 1);
                    }
                    break;
                case "=":
                    calculate();
                    break;
                case "÷":
                    expr.append('/');
                    break;
                case "×":
                    expr.append('*');
                    break;
                case "−":
                    expr.append('-');
                    break;
                case "M+":
                    try {
                        memory = ExpEval.evaluate(expr.toString());
                        preview.setText("M = " + format(memory));
                    } catch (Exception e) {
                        env.toast("nothing to store");
                    }
                    break;
                case "MR":
                    expr.append(format(memory));
                    break;
                case "^":
                    expr.append('^');
                    break;
                default:
                    expr.append(label);
                    break;
            }
            display.setText(expr.length() == 0 ? "0" : expr.toString());
            refreshPreview();
        }

        private void calculate() {
            try {
                double value = ExpEval.evaluate(expr.toString());
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    preview.setText("Math error");
                    return;
                }
                lastResult = format(value);
                history.setText("prev: " + lastResult);
                expr.setLength(0);
                expr.append(lastResult);
                display.setText(lastResult);
                preview.setText("");
            } catch (Exception e) {
                preview.setText("Error");
            }
        }

        private void refreshPreview() {
            String source = expr.toString();
            if (source.isEmpty()) {
                preview.setText(memory != 0 ? "M = " + format(memory) : "");
                return;
            }
            try {
                double value = ExpEval.evaluate(source);
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    return;
                }
                preview.setText("= " + format(value));
            } catch (Exception ignored) {
            }
        }

        private static String format(double value) {
            if (value == Math.rint(value) && Math.abs(value) < 1e15d) {
                return String.valueOf((long) value);
            }
            return String.valueOf(value);
        }

        @Override
        public View view() {
            return root;
        }
    }

    // ------------------------------------------------------------------
    // EDITOR
    // ------------------------------------------------------------------
    public static final class EditorWidget implements Panel {
        private final Env env;
        private final LinearLayout root;
        private final TextView title;
        private final TextView status;
        private final EditText editor;
        private Uri uri;
        private String name = "Untitled";
        private boolean wrap = true;

        private EditorWidget(Env env, Uri uri, String name) {
            this.env = env;
            Context c = env.context();
            if (name != null && !name.isEmpty()) {
                this.name = name;
            }
            this.uri = uri;
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(BG);

            LinearLayout header = new LinearLayout(c);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));

            title = heading(c, this.name, 12, TEXT, true);
            title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.START);
            title.setPadding(dp(c, 2), 0, dp(c, 6), 0);
            header.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            header.addView(miniButton(c, "Wrap", v -> toggleWrap()));
            header.addView(spacerView(c, 6));
            header.addView(miniButton(c, "Save", v -> save()));
            root.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 30)));

            View divider = new View(c);
            divider.setBackgroundColor(DIVIDER);
            root.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1));

            editor = new EditText(c);
            editor.setTextColor(TEXT);
            editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            editor.setTypeface(Typeface.MONOSPACE);
            editor.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setBackgroundColor(Color.TRANSPARENT);
            editor.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
            editor.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    updateStatus();
                }
            });
            root.addView(editor, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            status = heading(c, "Untitled", 9, DIM, false);
            status.setGravity(Gravity.END);
            status.setPadding(0, dp(c, 2), dp(c, 8), dp(c, 4));
            root.addView(status, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            updateTitle();
            if (uri != null) {
                load(uri, name);
            } else {
                updateStatus();
            }
        }

        private static TextView spacerView(Context c, int widthDp) {
            TextView view = new TextView(c);
            view.setText("");
            view.setLayoutParams(new LinearLayout.LayoutParams(dp(c, widthDp), 1));
            return view;
        }

        public void load(Uri target, String fileName) {
            if (target == null) {
                return;
            }
            uri = target;
            if (fileName != null && !fileName.isEmpty()) {
                name = fileName;
            }
            updateTitle();
            status.setText("Loading…");
            Context c = env.context();
            new Thread(() -> {
                byte[] bytes = null;
                boolean truncated = false;
                Exception error = null;
                try (InputStream in = c.getContentResolver().openInputStream(target)) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    long total = 0;
                    while ((read = in.read(buffer)) > 0) {
                        total += read;
                        if (total > 2L * 1024 * 1024) {
                            truncated = true;
                            break;
                        }
                        out.write(buffer, 0, read);
                    }
                    bytes = out.toByteArray();
                } catch (Exception e) {
                    error = e;
                }
                byte[] finalBytes = bytes;
                boolean finalTruncated = truncated;
                boolean failed = error != null || finalBytes == null;
                env.runOnUi(() -> {
                    if (failed) {
                        status.setText("Cannot read file");
                        return;
                    }
                    editor.setText(new String(finalBytes, StandardCharsets.UTF_8));
                    editor.setSelection(0);
                    updateStatus();
                    if (finalTruncated) {
                        status.setText("File truncated at 2 MB");
                    }
                });
            }).start();
        }

        private void toggleWrap() {
            wrap = !wrap;
            editor.setHorizontallyScrolling(!wrap);
            env.toast(wrap ? "Wrap on" : "Wrap off");
        }

        private void save() {
            if (uri == null) {
                env.toast("Open a file from Files, or use the full Editor");
                return;
            }
            Context c = env.context();
            try (OutputStream out = c.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) {
                    env.toast("Cannot write file");
                    return;
                }
                out.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
                env.toast("Saved");
            } catch (Exception e) {
                env.toast("Save failed");
            }
        }

        private void updateTitle() {
            title.setText(name);
        }

        private void updateStatus() {
            String text = editor.getText().toString();
            int caret = Math.max(0, Math.min(editor.getSelectionStart(), text.length()));
            int line = 1;
            int col = 1;
            for (int i = 0; i < caret; i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            status.setText("Ln " + line + ", Col " + col
                    + "   " + text.length() + " chars");
        }

        @Override
        public View view() {
            return root;
        }
    }

    // ------------------------------------------------------------------
    // FILES
    // ------------------------------------------------------------------
    public static final class FilesWidget implements Panel {
        private final Env env;
        private final LinearLayout root;
        private final TextView title;
        private final TextView upButton;
        private final TextView empty;
        private final TextView pickButton;
        private final ListView list;
        private final WidgetAdapter adapter;
        private Uri treeUri;
        private String currentDocId;
        private final List<String> parentIds = new ArrayList<>();
        private final List<String> parentNames = new ArrayList<>();
        private final List<Doc> shown = new ArrayList<>();

        private FilesWidget(Env env) {
            this.env = env;
            Context c = env.context();
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(BG);
            root.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));

            LinearLayout header = new LinearLayout(c);
            header.setOrientation(LinearLayout.HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            upButton = miniButton(c, "‹", v -> goUp());
            header.addView(upButton);
            header.addView(spacerKey(c, 6));
            header.addView(miniButton(c, "Dir", v -> createNew(true)));
            header.addView(spacerKey(c, 6));
            header.addView(miniButton(c, "File", v -> createNew(false)));
            header.addView(spacerKey(c, 6));
            header.addView(miniButton(c, "Help", v -> showHelp()));

            title = heading(c, "Files", 12, TEXT, true);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            title.setPadding(dp(c, 6), 0, dp(c, 6), 0);
            header.addView(title, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            header.addView(miniButton(c, "Root", v -> env.pickFilesRoot()));
            root.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 30)));

            empty = heading(c, "", 11, DIM, false);
            empty.setGravity(Gravity.CENTER);
            root.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 64)));

            pickButton = miniButton(c, "Pick storage root", v -> env.pickFilesRoot());
            root.addView(pickButton, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 30)));

            adapter = new WidgetAdapter(c, shown);
            list = new ListView(c);
            list.setAdapter(adapter);
            list.setDivider(new android.graphics.drawable.ColorDrawable(DIVIDER));
            list.setDividerHeight(dp(c, 1));
            root.addView(list, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            list.setOnItemClickListener((parent, view, position, id) -> {
                if (position < 0 || position >= shown.size()) {
                    return;
                }
                Doc doc = shown.get(position);
                if (doc.isDir()) {
                    parentIds.add(currentDocId);
                    parentNames.add(title.getText().toString());
                    loadDirectory(doc.docId, doc.name);
                } else {
                    showFileMenu(doc, view);
                }
            });

            refreshRoot();
        }

        private static TextView spacerKey(Context c, int widthDp) {
            TextView view = new TextView(c);
            view.setText("");
            view.setLayoutParams(new LinearLayout.LayoutParams(dp(c, widthDp), 1));
            return view;
        }

        private void showHelp() {
            env.toast("Root picks storage, Dir/File create, ‹ goes up");
        }

        public void reload() {
            refreshRoot();
        }

        private void refreshRoot() {
            String saved = null;
            try {
                saved = Prefs.get(env.context()).getString("files_root", null);
            } catch (Exception ignored) {
            }
            if (saved == null || saved.isEmpty()) {
                treeUri = null;
                title.setText("Files");
                parentIds.clear();
                parentNames.clear();
                shown.clear();
                adapter.notifyDataSetChanged();
                empty.setText("Pick a storage root to browse files");
                empty.setVisibility(View.VISIBLE);
                pickButton.setVisibility(View.VISIBLE);
                return;
            }
            Uri parsed;
            try {
                parsed = Uri.parse(saved);
                DocumentsContract.getTreeDocumentId(parsed);
                treeUri = parsed;
            } catch (Exception e) {
                treeUri = null;
                env.toast("Storage root invalid — pick again");
                return;
            }
            pickButton.setVisibility(View.GONE);
            empty.setVisibility(View.GONE);
            parentIds.clear();
            parentNames.clear();
            loadDirectory(DocumentsContract.getTreeDocumentId(treeUri), "Storage");
        }

        private void loadDirectory(String docId, String name) {
            if (treeUri == null) {
                return;
            }
            currentDocId = docId;
            title.setText(name);
            shown.clear();
            try {
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                        treeUri, docId);
                Cursor cursor = env.context().getContentResolver().query(children,
                        new String[]{
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_MIME_TYPE,
                                DocumentsContract.Document.COLUMN_SIZE
                        }, null, null, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        Doc doc = new Doc();
                        doc.name = cursor.getString(0);
                        doc.docId = cursor.getString(1);
                        doc.mime = cursor.getString(2);
                        doc.size = cursor.getLong(3);
                        if (doc.name != null && !doc.name.startsWith(".")) {
                            shown.add(doc);
                        }
                    }
                    cursor.close();
                }
            } catch (Exception e) {
                env.toast("Cannot open this folder");
                return;
            }
            Collections.sort(shown, (a, b) -> {
                if (a.isDir() != b.isDir()) {
                    return a.isDir() ? -1 : 1;
                }
                return a.name.toLowerCase(Locale.ROOT)
                        .compareTo(b.name.toLowerCase(Locale.ROOT));
            });
            adapter.notifyDataSetChanged();
            upButton.setTextColor(parentIds.isEmpty() ? DIM : TEXT);
            if (shown.isEmpty()) {
                empty.setText("Empty folder");
                empty.setVisibility(View.VISIBLE);
            } else {
                empty.setVisibility(View.GONE);
            }
        }

        private void goUp() {
            if (parentIds.isEmpty()) {
                return;
            }
            String previous = parentIds.remove(parentIds.size() - 1);
            String name = parentNames.remove(parentNames.size() - 1);
            loadDirectory(previous, name);
        }

        private Uri docUri(Doc doc) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.docId);
        }

        private void showFileMenu(Doc doc, View anchor) {
            PopupMenu menu = new PopupMenu(env.context(), anchor);
            menu.getMenu().add(0, 1, 0, "Open in Editor window");
            if (Prefs.allowAndroidApps(env.context())) {
                menu.getMenu().add(0, 2, 1, "Open with app");
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && WindowsExecutableFile.isExecutable(doc.name)) {
                menu.getMenu().add(0, 6, 2, "Copy to Winlator and open");
            }
            menu.getMenu().add(0, 3, 3, "Rename");
            menu.getMenu().add(0, 4, 4, "Delete");
            menu.getMenu().add(0, 5, 5, humanSize(doc.size));
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        env.openEditor(docUri(doc), doc.name);
                        return true;
                    case 2:
                        env.openWithSystem(docUri(doc), doc.mime);
                        return true;
                    case 6:
                        env.stageExecutableForWinlator(docUri(doc), doc.name);
                        return true;
                    case 3:
                        renameDocument(doc);
                        return true;
                    case 4:
                        deleteDocument(doc);
                        return true;
                    default:
                        return false;
                }
            });
            menu.show();
        }

        private void renameDocument(Doc doc) {
            Activity activity = env.activity();
            if (activity == null) {
                env.toast("Use the full Files app to rename");
                return;
            }
            promptInput(activity, "Rename " + doc.name, doc.name, value -> {
                if (value == null || value.trim().isEmpty()) {
                    env.toast("Cancel");
                    return;
                }
                try {
                    DocumentsContract.renameDocument(
                            env.context().getContentResolver(),
                            docUri(doc), value.trim());
                    loadDirectory(currentDocId, title.getText().toString());
                } catch (Exception e) {
                    env.toast("Rename failed");
                }
            });
        }

        private void deleteDocument(Doc doc) {
            Activity activity = env.activity();
            if (activity == null) {
                env.toast("Use the full Files app to delete");
                return;
            }
            new AlertDialog.Builder(activity)
                    .setTitle("Delete " + doc.name)
                    .setMessage("This cannot be undone.")
                    .setPositiveButton("Delete", (d, w) -> {
                        try {
                            DocumentsContract.deleteDocument(
                                    env.context().getContentResolver(),
                                    docUri(doc));
                            loadDirectory(currentDocId, title.getText().toString());
                        } catch (Exception e) {
                            env.toast("Delete failed");
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void createNew(boolean folder) {
            Activity activity = env.activity();
            if (activity == null) {
                env.toast("Use the full Files app to create");
                return;
            }
            if (treeUri == null || currentDocId == null) {
                env.pickFilesRoot();
                return;
            }
            promptInput(activity, folder ? "New folder" : "New file", "", value -> {
                if (value == null || value.trim().isEmpty()) {
                    env.toast("Cancel");
                    return;
                }
                try {
                    Uri created = DocumentsContract.createDocument(
                            env.context().getContentResolver(),
                            DocumentsContract.buildDocumentUriUsingTree(
                                    treeUri, currentDocId),
                            folder ? DocumentsContract.Document.MIME_TYPE_DIR
                                    : "text/plain",
                            value.trim());
                    if (!folder && created != null) {
                        env.openEditor(created, value.trim());
                    }
                    loadDirectory(currentDocId, title.getText().toString());
                } catch (Exception e) {
                    env.toast("Create failed");
                }
            });
        }

        @Override
        public View view() {
            return root;
        }
    }

    static final class Doc {
        String name;
        String docId;
        String mime;
        long size;

        boolean isDir() {
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
        }
    }

    static final class WidgetAdapter extends BaseAdapter {
        private final List<Doc> data;
        private final Context c;

        WidgetAdapter(Context c, List<Doc> data) {
            this.c = c;
            this.data = data;
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Doc getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Doc doc = getItem(position);
            LinearLayout row = convert instanceof LinearLayout
                    ? (LinearLayout) convert : newRow(c);
            TextView line1 = (TextView) row.getChildAt(0);
            TextView line2 = (TextView) row.getChildAt(1);
            line1.setText(doc.name);
            line2.setText(doc.isDir() ? "Folder" : humanSize(doc.size));
            return row;
        }

        private static LinearLayout newRow(Context c) {
            int pad = dp(c, 10);
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            row.setBackground(Ui.ripple(c,
                    Ui.rounded(c, Color.TRANSPARENT, 8), 0x2FFFFFFF));

            TextView line1 = new TextView(c);
            line1.setTextColor(TEXT);
            line1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            line1.setSingleLine(true);
            line1.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(line1, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView line2 = new TextView(c);
            line2.setTextColor(DIM);
            line2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            line2.setSingleLine(true);
            row.addView(line2, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }
    }

    // ------------------------------------------------------------------
    // SETTINGS
    // ------------------------------------------------------------------
    public static final class SettingsWidget implements Panel {
        private final Env env;
        private final LinearLayout root;
        private boolean building = true;

        private SettingsWidget(Env env) {
            this.env = env;
            Context c = env.context();
            root = new LinearLayout(c);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(BG);

            ScrollView scroll = new ScrollView(c);
            LinearLayout content = new LinearLayout(c);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(c, 10), dp(c, 10), dp(c, 10), dp(c, 24));
            scroll.addView(content, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            cardTitle(c, content, "Desktop");
            LinearLayout card1 = card(c, content);
            seekRow(c, card1, "Icon size", 36, 80,
                    Math.round(Prefs.iconSize(c)), true, " dp", value -> {
                        Prefs.setIconSize(c, value);
                        env.refreshHome();
                    });
            SeekBar columnsBar = seekRow(c, card1, "Columns", 3, 10,
                    Prefs.columns(c) == 0 ? 5 : Prefs.columns(c),
                    Prefs.columns(c) != 0, "", value -> {
                        Prefs.setColumns(c, value);
                        env.refreshHome();
                    });
            switchRow(c, card1, "Automatic grid",
                    Prefs.columns(c) == 0, checked -> {
                        if (building) {
                            return;
                        }
                        Prefs.setColumns(c, checked ? 0 : 5);
                        columnsBar.setEnabled(!checked);
                        env.refreshHome();
                    });
            switchRow(c, card1, "Show app labels", Prefs.showLabels(c), checked -> {
                if (building) return;
                Prefs.setBoolean(c, "labels", checked);
                env.refreshHome();
            });
            seekRow(c, card1, "Wallpaper dim", 0, 70, Prefs.dim(c), true, "%",
                    value -> Prefs.setInt(c, "dim", value));
            building = false;

            cardTitle(c, content, "Behavior");
            LinearLayout card2 = card(c, content);
            switchRow(c, card2, "Double-tap to open", Prefs.doubleClick(c),
                    checked -> Prefs.setBoolean(c, "double_click", checked));
            switchRow(c, card2, "Clock seconds", Prefs.showSeconds(c),
                    checked -> Prefs.setBoolean(c, "show_seconds", checked));
            switchRow(c, card2, "Fade taskbar", Prefs.autoHideTaskbar(c),
                    checked -> Prefs.setBoolean(c, "autohide", checked));
            switchRow(c, card2, "Fullscreen desktop", Prefs.fullscreen(c),
                    checked -> {
                        Prefs.setBoolean(c, "fullscreen", checked);
                        env.applyFullscreen();
                    });
            switchRow(c, card2, "External display desktop", Prefs.externalDisplay(c),
                    checked -> Prefs.setBoolean(c, "external_display", checked));
            switchRow(c, card2, "Ecosystem on desktop", Prefs.showEcoOnDesktop(c),
                    checked -> {
                        Prefs.setBoolean(c, "eco_desktop", checked);
                        env.refreshHome();
                    });

            cardTitle(c, content, "Home screen");
            LinearLayout card3 = card(c, content);
            cardButton(c, card3, "Change wallpaper", env::wallpaper);
            cardButton(c, card3, "Set as default home", env::homeSettings);
        }

        private static void cardTitle(Context c, LinearLayout parent, String label) {
            parent.addView(heading(c, label.toUpperCase(), 10, ACCENT, true));
        }

        private static LinearLayout card(Context c, LinearLayout parent) {
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(Ui.roundedStroke(c, CARD, 12, DIVIDER, 1));
            card.setPadding(dp(c, 10), dp(c, 2), dp(c, 10), dp(c, 2));
            parent.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return card;
        }

        private static void cardButton(Context c, LinearLayout parent,
                                       String label, Runnable action) {
            parent.addView(miniButton(c, label, v -> action.run()),
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 30)));
        }

        private static void switchRow(Context c, LinearLayout parent, String label,
                                      boolean value, Consumer<Boolean> onChange) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(c, 2), dp(c, 8), dp(c, 2), dp(c, 8));

            TextView text = heading(c, label, 12, TEXT, false);
            row.addView(text, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            Switch toggle = new Switch(c);
            toggle.setChecked(value);
            toggle.setShowText(true);
            toggle.setOnCheckedChangeListener((b, checked) -> onChange.accept(checked));
            row.addView(toggle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            parent.addView(row);
        }

        private static SeekBar seekRow(Context c, LinearLayout parent, String label,
                                       int min, int max, int value, boolean enabled,
                                       String suffix, Consumer<Integer> onChange) {
            LinearLayout block = new LinearLayout(c);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(dp(c, 2), dp(c, 8), dp(c, 2), dp(c, 8));

            LinearLayout titles = new LinearLayout(c);
            titles.setOrientation(LinearLayout.HORIZONTAL);
            titles.setGravity(Gravity.CENTER_VERTICAL);
            titles.addView(heading(c, label, 12, TEXT, false),
                    new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView valueLabel = heading(c, value + suffix, 11, ACCENT, true);
            titles.addView(valueLabel);
            block.addView(titles);

            SeekBar bar = new SeekBar(c);
            bar.setMax(Math.max(1, max - min));
            bar.setProgress(Math.max(0, Math.min(max - min, value - min)));
            bar.setEnabled(enabled);
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                    int actual = min + progress;
                    valueLabel.setText(actual + suffix);
                    if (fromUser) {
                        onChange.accept(actual);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            block.addView(bar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            parent.addView(block);
            return bar;
        }

        @Override
        public View view() {
            return root;
        }
    }
}
