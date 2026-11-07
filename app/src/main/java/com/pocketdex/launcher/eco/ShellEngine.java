package com.pocketdex.launcher.eco;

import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShellEngine {

    public interface Listener {
        void onOutput(String text);

        void onPrompt(String cwd);

        void onDone(String command, int exitCode);

        void onError(String message);
    }

    private static final String HOME_DIR = "/storage/emulated/0";
    private static final String SHELL = "/system/bin/sh";

    private final Handler ui;
    private final Listener listener;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    private volatile File cwd = new File("/");
    private volatile Process current;
    private volatile boolean destroyed;
    private final AtomicBoolean running = new AtomicBoolean();

    public ShellEngine(Listener listener) {
        this.ui = new Handler(Looper.getMainLooper());
        this.listener = listener;
    }

    public String cwd() {
        return cwd.getAbsolutePath();
    }

    public boolean busy() {
        return running.get();
    }

    public void run(final String command) {
        if (destroyed) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            post(() -> listener.onError("a command is still running — stop it first"));
            return;
        }
        final String trimmed = command == null ? "" : command.trim();
        if (trimmed.isEmpty()) {
            running.set(false);
            return;
        }
        try {
            pool.execute(() -> executeCommand(trimmed));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            running.set(false);
            post(() -> listener.onError("terminal is closed"));
        }
    }

    private void executeCommand(String command) {
        try {
            if (command.equals("cd") || command.startsWith("cd ")
                    || command.startsWith("cd\t")) {
                String target = command.length() > 2 ? command.substring(2).trim() : "";
                changeDirectory(expandHome(target), command);
                return;
            }
            ProcessBuilder builder = new ProcessBuilder(SHELL, "-c", command);
            builder.directory(cwd);
            builder.redirectErrorStream(true);
            java.util.Map<String, String> env = builder.environment();
            env.put("PWD", cwd.getAbsolutePath());
            java.io.File home = new File(HOME_DIR);
            if (home.isDirectory()) {
                env.put("HOME", home.getAbsolutePath());
            }
            String path = env.get("PATH");
            if (path == null || path.isEmpty()) {
                env.put("PATH", "/system/bin:/system/xbin:/vendor/bin:/product/bin");
            }
            Process process = builder.start();
            current = process;

            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    final String chunk = new String(buffer, 0, read,
                            java.nio.charset.StandardCharsets.UTF_8);
                    post(() -> listener.onOutput(chunk));
                }
            }
            int exit = process.waitFor();
            current = null;
            post(() -> listener.onDone(command, exit));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            final String message = e.getClass().getSimpleName() + ": "
                    + (e.getMessage() == null ? "failed" : e.getMessage());
            post(() -> listener.onError(message));
            post(() -> listener.onDone(command, 1));
        } finally {
            current = null;
            running.set(false);
        }
    }

    private void changeDirectory(String target, String command) {
        File next;
        if (target.isEmpty()) {
            next = new File(HOME_DIR);
        } else if (target.startsWith("/")) {
            next = new File(target);
        } else if (target.equals("~") || target.startsWith("~/")) {
            File home = new File(HOME_DIR);
            String remainder = target.equals("~") ? "" : target.substring(2);
            next = remainder.isEmpty() ? home : new File(home, remainder);
        } else {
            next = new File(cwd, target);
        }
        File canonical;
        try {
            canonical = next.getCanonicalFile();
        } catch (Exception e) {
            canonical = next;
        }
        if (canonical.isDirectory()) {
            cwd = canonical;
            String path = canonical.getAbsolutePath();
            post(() -> listener.onPrompt(path));
            post(() -> listener.onDone(command, 0));
        } else {
            final String message = "cd: no such directory: " + target;
            post(() -> listener.onOutput(message + "\n"));
            post(() -> listener.onDone(command, 1));
        }
    }

    private String expandHome(String target) {
        if (target.equals("~")) {
            return HOME_DIR;
        }
        if (target.startsWith("~/")) {
            return HOME_DIR + target.substring(1);
        }
        return target;
    }

    public void stop() {
        Process running = current;
        if (running != null) {
            running.destroy();
            if (running.isAlive()) {
                running.destroyForcibly();
            }
        }
    }

    public void shutdown() {
        destroyed = true;
        stop();
        pool.shutdownNow();
    }

    private void post(Runnable action) {
        if (destroyed) {
            return;
        }
        ui.post(action);
    }
}
