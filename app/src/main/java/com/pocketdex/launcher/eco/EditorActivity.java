package com.pocketdex.launcher.eco;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pocketdex.launcher.R;
import com.pocketdex.launcher.Ui;

import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class EditorActivity extends Activity {

    private static final int BG = 0xFF0B0D12;
    private static final int CARD = 0xFF171A24;
    private static final int TEXT = 0xFFF4F6FB;
    private static final int DIM = 0xFF9AA3B5;
    private static final int ACCENT = 0xFF4C8DFF;
    private static final int DIVIDER = 0xFF2A3040;
    private static final int RC_CREATE = 51;
    private static final long MAX_BYTES = 3L * 1024 * 1024;

    private Uri uri;
    private String fileName;
    private TextView title;
    private TextView status;
    private EditText editor;
    private boolean switchWrap = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());

        String saved = getIntent().getStringExtra("uri");
        if (saved != null) {
            try {
                uri = Uri.parse(saved);
            } catch (Exception e) {
                uri = null;
            }
        }
        fileName = getIntent().getStringExtra("name");
        updateTitle();
        if (uri != null) {
            loadFile(uri);
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView back = toolbarButton("‹", 20, v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(28), dp(34)));

        title = new TextView(this);
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.START);
        title.setPadding(dp(8), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(toolbarButton("Wrap", 11, v -> toggleWrap()),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        header.addView(toolbarButton("Save As", 11, v -> saveAs()),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        TextView save = toolbarButton("Save", 11, v -> save());
        save.setTextColor(Color.argb(0xFF, 0x0B, 0x0D, 0x12));
        save.setBackground(Ui.ripple(this,
                Ui.rounded(this, ACCENT, 12), 0x66FFFFFF));
        header.addView(save, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

        root.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        editor = new EditText(this);
        editor.setTextColor(TEXT);
        editor.setHintTextColor(DIM);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        editor.setTypeface(Typeface.MONOSPACE);
        editor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setBackgroundColor(Color.TRANSPARENT);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(editor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextColor(DIM);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        status.setGravity(Gravity.END);
        status.setPadding(0, dp(6), dp(12), dp(6));
        status.setText("Ln 1, Col 1");
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return root;
    }

    private TextView toolbarButton(String label, int sizeSp,
                                   View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(Ui.ripple(this,
                Ui.rounded(this, 0x33232838, 12), 0x44FFFFFF));
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private void updateTitle() {
        String label = fileName != null ? fileName
                : uri != null ? Uri.parse(uri.toString()).getLastPathSegment()
                : "Untitled";
        if (label == null || label.isEmpty()) {
            label = "Untitled";
        }
        title.setText(label);
    }

    private void updateStatus() {
        String text = editor.getText().toString();
        int caret = editor.getSelectionStart();
        int line = 1;
        int col = 1;
        for (int i = 0; i < Math.min(caret, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        status.setText("Ln " + line + ", Col " + col
                + "  ·  " + text.length() + " chars");
    }

    private void toggleWrap() {
        switchWrap = !switchWrap;
        editor.setHorizontallyScrolling(!switchWrap);
        Toast.makeText(this, switchWrap ? "Wrap on" : "Wrap off",
                Toast.LENGTH_SHORT).show();
    }

    private void loadFile(Uri target) {
        status.setText("Loading…");
        new Thread(() -> {
            byte[] bytes = null;
            Exception error = null;
            try (InputStream in = getContentResolver().openInputStream(target)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_BYTES) {
                        break;
                    }
                    out.write(buffer, 0, read);
                }
                bytes = out.toByteArray();
            } catch (Exception e) {
                error = e;
            }
            byte[] finalBytes = bytes;
            Exception finalError = error;
            runOnUiThread(() -> {
                if (finalError != null || finalBytes == null) {
                    status.setText("Cannot read file");
                    return;
                }
                String content = new String(finalBytes, StandardCharsets.UTF_8);
                editor.setText(content);
                editor.setSelection(0);
                updateStatus();
            });
        }).start();
    }

    private void save() {
        if (uri == null) {
            saveAs();
            return;
        }
        try {
            OutputStream out = getContentResolver().openOutputStream(uri, "wt");
            if (out == null) {
                Toast.makeText(this, "Cannot write file", Toast.LENGTH_SHORT).show();
                return;
            }
            out.write(editor.getText().toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            updateStatus();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAs() {
        try {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            String suggested = fileName != null ? fileName : "notes.txt";
            intent.putExtra(Intent.EXTRA_TITLE, suggested);
            startActivityForResult(intent, RC_CREATE);
        } catch (Exception e) {
            Toast.makeText(this, "No storage picker", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_CREATE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            uri = data.getData();
            fileName = uri.getLastPathSegment();
            updateTitle();
            save();
        }
    }

    public static void open(Context context, Uri target, String name) {
        Intent intent = new Intent(context, EditorActivity.class);
        intent.putExtra("uri", target.toString());
        intent.putExtra("name", name);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }
}
