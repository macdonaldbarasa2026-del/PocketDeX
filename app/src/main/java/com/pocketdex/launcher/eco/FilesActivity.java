package com.pocketdex.launcher.eco;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.pocketdex.launcher.Prefs;
import com.pocketdex.launcher.R;
import com.pocketdex.launcher.Ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FilesActivity extends Activity {

    private static final int BG = 0xFF0B0D12;
    private static final int CARD = 0xFF171A24;
    private static final int TEXT = 0xFFF4F6FB;
    private static final int DIM = 0xFF9AA3B5;
    private static final int ACCENT = 0xFF4C8DFF;
    private static final int DIVIDER = 0xFF2A3040;
    private static final int RC_TREE = 41;

    private static final class Doc {
        String name;
        String docId;
        String mime;
        long size;
        long modified;

        boolean isDir() {
            return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
        }
    }

    private TextView title;
    private TextView upButton;
    private ListView list;
    private TextView empty;

    private Uri treeUri;
    private String currentDocId;
    private final List<String> parentDocIds = new ArrayList<>();
    private final List<String> parentNames = new ArrayList<>();
    private final List<Doc> shown = new ArrayList<>();
    private final DocAdapter adapter = new DocAdapter(shown);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setContentView(buildUi());
        restoreOrPickRoot();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(10), dp(12), dp(10));
        header.setBackground(Ui.rounded(this, CARD, 0));

        upButton = smallButton("‹", 20, v -> goUp());
        header.addView(upButton, new LinearLayout.LayoutParams(dp(28), dp(34)));

        title = new TextView(this);
        title.setTextColor(TEXT);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.START);
        title.setPadding(dp(8), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(smallButton("New Folder", 11, v -> createNew(true)),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        header.addView(smallButton("New File", 11, v -> createNew(false)),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        header.addView(smallButton("Root", 11, v -> pickRoot(this, RC_TREE)),
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

        LinearLayout headerHolder = new LinearLayout(this);
        headerHolder.setPadding(dp(10), dp(10), dp(10), 0);
        headerHolder.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        root.addView(headerHolder);

        empty = new TextView(this);
        empty.setText("This folder is empty");
        empty.setTextColor(DIM);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        empty.setGravity(Gravity.CENTER);
        root.addView(empty, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(120)));

        list = new ListView(this);
        list.setAdapter(adapter);
        list.setDivider(new android.graphics.drawable.ColorDrawable(DIVIDER));
        list.setDividerHeight(dp(1));
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        list.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= shown.size()) {
                return;
            }
            Doc doc = shown.get(position);
            if (doc.isDir()) {
                parentDocIds.add(currentDocId);
                parentNames.add(title.getText().toString());
                loadDirectory(doc.docId, doc.name);
            } else {
                showFileMenu(doc, view);
            }
        });
        return root;
    }

    private TextView smallButton(String label, int sizeSp,
                                  View.OnClickListener listener) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(Ui.ripple(this,
                Ui.rounded(this, 0x33232838, 8), 0x44FFFFFF));
        button.setOnClickListener(listener);
        return button;
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private void restoreOrPickRoot() {
        String saved = Prefs.get(this).getString("files_root", null);
        if (saved == null || saved.isEmpty()) {
            pickRoot(this, RC_TREE);
            return;
        }
        try {
            treeUri = Uri.parse(saved);
            DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception e) {
            treeUri = null;
            pickRoot(this, RC_TREE);
            return;
        }
        parentDocIds.clear();
        parentNames.clear();
        loadDirectory(DocumentsContract.getTreeDocumentId(treeUri), "Storage");
    }

    public static void pickRoot(Activity activity, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            Toast.makeText(activity, "Storage picker unavailable",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_TREE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException ignored) {
            }
            treeUri = uri;
            Prefs.get(this).edit()
                    .putString("files_root", uri.toString()).apply();
            parentDocIds.clear();
            parentNames.clear();
            loadDirectory(DocumentsContract.getTreeDocumentId(treeUri), "Storage");
        }
    }

    private void loadDirectory(String docId, String name) {
        currentDocId = docId;
        title.setText(name);
        shown.clear();
        Cursor cursor = null;
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri, docId);
            cursor = getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
            }, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    Doc doc = new Doc();
                    doc.name = cursor.getString(0);
                    doc.docId = cursor.getString(1);
                    doc.mime = cursor.getString(2);
                    doc.size = cursor.getLong(3);
                    doc.modified = cursor.getLong(4);
                    if (doc.name != null && !doc.name.startsWith(".")) {
                        shown.add(doc);
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open this folder", Toast.LENGTH_SHORT).show();
            if (currentDocId == null) {
                pickRoot(this, RC_TREE);
            }
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception ignored) {
                }
            }
        }
        Collections.sort(shown, (a, b) -> {
            if (a.isDir() != b.isDir()) {
                return a.isDir() ? -1 : 1;
            }
            return a.name.toLowerCase(Locale.getDefault())
                    .compareTo(b.name.toLowerCase(Locale.getDefault()));
        });
        adapter.notifyDataSetChanged();
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        upButton.setEnabled(!parentDocIds.isEmpty());
        upButton.setTextColor(parentDocIds.isEmpty() ? DIM : TEXT);
    }

    private boolean goUp() {
        if (parentDocIds.isEmpty()) {
            finish();
            return true;
        }
        String previous = parentDocIds.remove(parentDocIds.size() - 1);
        String name = parentNames.remove(parentNames.size() - 1);
        loadDirectory(previous, name);
        return true;
    }

    private Uri documentUri(Doc doc) {
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, doc.docId);
    }

    private boolean startFileDrag(Doc doc, View source) {
        if (doc == null || doc.isDir() || treeUri == null) {
            return false;
        }
        Uri uri = documentUri(doc);
        String mimeType = doc.mime == null || doc.mime.trim().isEmpty()
                ? "application/octet-stream" : doc.mime;
        ClipData clip = new ClipData(doc.name,
                new String[]{mimeType}, new ClipData.Item(uri));
        int flags = View.DRAG_FLAG_GLOBAL | View.DRAG_FLAG_GLOBAL_URI_READ;
        try {
            return source.startDragAndDrop(clip,
                    new View.DragShadowBuilder(source), null, flags);
        } catch (Exception e) {
            Toast.makeText(this, "Could not share this file with the drop target.",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void showFileMenu(Doc doc, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Open in Editor");
        menu.getMenu().add(0, 2, 1, "Open with app");
        menu.getMenu().add(0, 3, 2, "Rename");
        menu.getMenu().add(0, 4, 3, "Delete");
        menu.getMenu().add(0, 5, 4, "Info");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    EditorActivity.open(this, documentUri(doc), doc.name);
                    return true;
                case 2:
                    openWithSystem(doc);
                    return true;
                case 3:
                    renameDocument(doc);
                    return true;
                case 4:
                    deleteDocument(doc);
                    return true;
                case 5:
                    docInfo(doc);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void openWithSystem(Doc doc) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(documentUri(doc), doc.mime == null
                    ? "application/octet-stream" : doc.mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent("android.intent.action.VIEW_DOCUMENT");
                fallback.setDataAndType(documentUri(doc), doc.mime);
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(fallback);
            } catch (Exception ignored) {
                Toast.makeText(this, "No app can open this file",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void renameDocument(Doc doc) {
        promptInput("Rename " + doc.name, doc.name, value -> {
            if (value == null || value.trim().isEmpty()) {
                return;
            }
            try {
                DocumentsContract.renameDocument(getContentResolver(),
                        documentUri(doc), value.trim());
                loadDirectory(currentDocId,
                        parentNames.isEmpty() ? "Storage"
                                : parentNames.get(parentNames.size() - 1));
            } catch (Exception e) {
                Toast.makeText(this, "Rename failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteDocument(Doc doc) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + doc.name)
                .setMessage("This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        DocumentsContract.deleteDocument(getContentResolver(),
                                documentUri(doc));
                        loadDirectory(currentDocId, title.getText().toString());
                    } catch (Exception e) {
                        Toast.makeText(this, "Delete failed", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void docInfo(Doc doc) {
        SimpleDateFormat format = new SimpleDateFormat(
                "EEE d MMM yyyy HH:mm", Locale.getDefault());
        new AlertDialog.Builder(this)
                .setTitle(doc.name)
                .setMessage("Type: " + (doc.isDir() ? "Folder" : doc.mime)
                        + "\nSize: " + (doc.isDir() ? "—" : humanSize(doc.size))
                        + "\nModified: " + format.format(new Date(doc.modified))
                        + "\nID: " + doc.docId)
                .setPositiveButton("Close", null)
                .show();
    }

    private void createNew(boolean folder) {
        promptInput(folder ? "New folder" : "New file", "", value -> {
            if (value == null || value.trim().isEmpty()) {
                return;
            }
            String name = value.trim();
            try {
                Uri created = DocumentsContract.createDocument(
                        getContentResolver(),
                        DocumentsContract.buildDocumentUriUsingTree(
                                treeUri, currentDocId),
                        folder ? DocumentsContract.Document.MIME_TYPE_DIR
                                : "text/plain",
                        name);
                if (!folder && created != null) {
                    EditorActivity.open(this, created, name);
                }
                loadDirectory(currentDocId, title.getText().toString());
            } catch (Exception e) {
                Toast.makeText(this, "Create failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void promptInput(String heading, String initial,
                             java.util.function.Consumer<String> onOk) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(initial);
        input.setTextColor(TEXT);
        input.setHintTextColor(DIM);
        input.setHint("Name");
        input.setPadding(dp(12), dp(8), dp(12), dp(8));
        input.setBackground(Ui.roundedStroke(this, 0xFF232838, 8, DIVIDER, 1));
        input.setSelection(initial.length());
        new AlertDialog.Builder(this)
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

    private final class DocAdapter extends BaseAdapter {
        private final List<Doc> data;

        DocAdapter(List<Doc> data) {
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
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = parent.getContext();
            Doc doc = getItem(position);

            LinearLayout row = convertView instanceof LinearLayout
                    ? (LinearLayout) convertView : newRow(context);
            TextView line1 = (TextView) row.getChildAt(0);
            TextView line2 = (TextView) row.getChildAt(1);

            line1.setText(doc.name);
            line2.setText(doc.isDir() ? "Folder" : humanSize(doc.size)
                    + (doc.modified > 0
                    ? "  ·  " + new SimpleDateFormat("d MMM yy", Locale.US)
                    .format(new Date(doc.modified))
                    : ""));
            row.setLongClickable(!doc.isDir());
            row.setTooltipText(doc.isDir() ? doc.name : doc.name + " — hold and drag to share");
            row.setOnLongClickListener(doc.isDir()
                    ? null : view -> startFileDrag(doc, view));
            return row;
        }

        private LinearLayout newRow(Context context) {
            int pad = Ui.dp(context, 12);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            row.setClickable(true);
            row.setBackground(Ui.ripple(context,
                    Ui.rounded(context, Color.TRANSPARENT, 8), 0x33FFFFFF));

            TextView line1 = new TextView(context);
            line1.setTextColor(TEXT);
            line1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            line1.setSingleLine(true);
            line1.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(line1, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView line2 = new TextView(context);
            line2.setTextColor(DIM);
            line2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            line2.setSingleLine(true);
            line2.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(line2, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }
    }

    public static void handleTreeResult(Context context, Intent data) {
        if (data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        Prefs.get(context).edit().putString("files_root", uri.toString()).apply();
    }
}
