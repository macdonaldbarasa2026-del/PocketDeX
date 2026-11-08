package com.pocketdex.launcher.runtime;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Copies a user-selected executable into shared Downloads, which Winlator maps as D:. */
public final class WinlatorExecutableTransfer {
    private static final String SUBDIRECTORY = "PocketDeX";
    private static final String MIME_TYPE = "application/vnd.microsoft.portable-executable";

    private WinlatorExecutableTransfer() {
    }

    /** Copies the content URI and returns its shared Downloads URI. Requires Android 10+. */
    public static Uri copyToSharedDownloads(Context context, Uri source, String sourceName)
            throws IOException {
        if (context == null || source == null || !WindowsExecutableFile.isExecutable(sourceName)) {
            throw new IllegalArgumentException("A context, source URI and .exe file are required.");
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw new IOException("Shared Downloads transfer requires Android 10 or later.");
        }

        ContentResolver resolver = context.getContentResolver();
        String displayName = uniqueDisplayName(resolver,
                WindowsExecutableFile.safeFileName(sourceName));
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIRECTORY);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri target = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (target == null) {
            throw new IOException("Android could not create the shared Downloads file.");
        }
        try {
            try (InputStream input = resolver.openInputStream(source);
                 OutputStream output = resolver.openOutputStream(target, "w")) {
                if (input == null || output == null) {
                    throw new IOException("Android could not open the selected file.");
                }
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            if (resolver.update(target, ready, null, null) <= 0) {
                throw new IOException("Android could not publish the copied file.");
            }
            return target;
        } catch (Exception e) {
            resolver.delete(target, null, null);
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Could not copy the executable to shared Downloads.", e);
        }
    }

    /** Copies an EXE into the embedded runtime's app-private D: drive. */
    public static File copyToPocketDeXRuntime(Context context, Uri source, String sourceName)
            throws IOException {
        if (context == null || source == null || !WindowsExecutableFile.isExecutable(sourceName)) {
            throw new IllegalArgumentException("A context, source URI and .exe file are required.");
        }
        File downloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloads == null) {
            downloads = new File(context.getFilesDir(), "Winlator/Downloads");
        }
        File targetDirectory = new File(downloads, SUBDIRECTORY);
        if (!targetDirectory.isDirectory() && !targetDirectory.mkdirs()) {
            throw new IOException("Could not create PocketDeX's private Windows drive folder.");
        }

        String displayName = uniquePrivateDisplayName(targetDirectory,
                WindowsExecutableFile.safeFileName(sourceName));
        File target = new File(targetDirectory, displayName);
        File temporary = File.createTempFile("pocketdex-exe-", ".part", targetDirectory);
        try {
            try (InputStream input = context.getContentResolver().openInputStream(source);
                 OutputStream output = new FileOutputStream(temporary)) {
                if (input == null) {
                    throw new IOException("Android could not open the selected file.");
                }
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
            if (!temporary.renameTo(target)) {
                throw new IOException("Could not finish copying the executable.");
            }
            return target;
        } catch (Exception e) {
            temporary.delete();
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Could not copy the executable into PocketDeX storage.", e);
        }
    }

    private static String uniquePrivateDisplayName(File directory, String requestedName) {
        String base = requestedName.substring(0, requestedName.length() - 4);
        String candidate = requestedName;
        int suffix = 2;
        while (new File(directory, candidate).exists()) {
            candidate = base + " (" + suffix++ + ").exe";
        }
        return candidate;
    }

    private static String uniqueDisplayName(ContentResolver resolver, String requestedName) {
        String base = requestedName.substring(0, requestedName.length() - 4);
        String candidate = requestedName;
        int suffix = 2;
        while (exists(resolver, candidate)) {
            candidate = base + " (" + suffix++ + ").exe";
        }
        return candidate;
    }

    private static boolean exists(ContentResolver resolver, String displayName) {
        String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + SUBDIRECTORY + "/";
        try (Cursor cursor = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                        + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                new String[]{displayName, relativePath}, null)) {
            return cursor != null && cursor.moveToFirst();
        } catch (Exception ignored) {
            return false;
        }
    }
}
