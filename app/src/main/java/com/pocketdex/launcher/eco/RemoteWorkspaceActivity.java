package com.pocketdex.launcher.eco;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.webkit.URLUtil;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pocketdex.launcher.Ui;
import com.pocketdex.launcher.runtime.GuacamoleWorkspaceConfig;
import com.pocketdex.launcher.runtime.RuntimeSession;
import com.pocketdex.launcher.runtime.RuntimeSessionStore;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;


/** Remote desktop viewer for a user-managed HTTPS Apache Guacamole server. */
public final class RemoteWorkspaceActivity extends Activity {
    private static final int REQUEST_FILE_CHOOSER = 41;
    private static final int REQUEST_DOWNLOAD_TARGET = 42;
    private static final int BG = 0xFF0B0D12;
    private static final int TEXT = 0xFFF4F6FB;
    private static final int DIM = 0xFF9AA3B5;

    private WebView webView;
    private TextView status;
    private TextView gatewayMetric;
    private Uri trustedOrigin;
    private String sessionId;
    private int gatewayProbeGeneration;
    private boolean mainFrameHttpError;
    private ValueCallback<Uri[]> pendingFileCallback;
    private String pendingDownloadUrl;
    private String pendingDownloadName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();
        sessionId = getIntent().getStringExtra(RuntimeSessionStore.EXTRA_SESSION_ID);

        Uri endpoint = GuacamoleWorkspaceConfig.endpoint(this);
        if (endpoint == null) {
            status.setText("Set an HTTPS Guacamole server in PocketDeX Settings.");
            gatewayMetric.setText("Gateway response time unavailable: no server configured.");
            updateSession(RuntimeSession.State.DEGRADED,
                    "No valid HTTPS gateway is configured.");
            return;
        }
        trustedOrigin = endpoint;
        configureWebView();
        webView.loadUrl(endpoint.toString());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(8), dp(6), dp(8), dp(6));
        toolbar.addView(toolbarButton("Back", v -> finish()));
        toolbar.addView(toolbarButton("Reconnect", v -> {
            if (webView != null && trustedOrigin != null) {
                status.setText("Reconnecting to the Guacamole gateway…");
                webView.loadUrl(trustedOrigin.toString());
            }
        }));
        toolbar.addView(toolbarButton("Disconnect", v -> disconnect()));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setTextColor(DIM);
        status.setTextSize(12);
        status.setPadding(dp(12), dp(6), dp(12), dp(6));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        gatewayMetric = new TextView(this);
        gatewayMetric.setText("Gateway response time: waiting for server…");
        gatewayMetric.setTextColor(DIM);
        gatewayMetric.setTextSize(11);
        gatewayMetric.setPadding(dp(12), 0, dp(12), dp(6));
        root.addView(gatewayMetric, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress < 100) {
                    status.setText("Loading Guacamole gateway… " + progress + "%");
                } else {
                    status.setText("Gateway page loaded. Sign in and select a "
                            + "remote connection.");
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = callback;
                if (!isTrustedPage(view.getUrl())) {
                    finishFileChooser(null);
                    status.setText("File access is available only on the configured gateway.");
                    return true;
                }
                if (params.getMode() != FileChooserParams.MODE_OPEN
                        && params.getMode() != FileChooserParams.MODE_OPEN_MULTIPLE) {
                    finishFileChooser(null);
                    status.setText("This remote file request is not an upload selection.");
                    return true;
                }
                try {
                    Intent chooser = params.createIntent();
                    startActivityForResult(chooser, REQUEST_FILE_CHOOSER);
                    status.setText("Choose a file to upload to the remote session.");
                } catch (Exception e) {
                    finishFileChooser(null);
                    status.setText("No compatible file picker is available.");
                }
                return true;
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, size) -> {
            Uri downloadUri = Uri.parse(url);
            if (!isTrustedOrigin(downloadUri)) {
                status.setText("Blocked download outside the configured HTTPS server.");
                return;
            }
            try {
                pendingDownloadUrl = url;
                pendingDownloadName = safeDownloadName(
                        URLUtil.guessFileName(url, contentDisposition, mimeType));
                Intent create = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                create.addCategory(Intent.CATEGORY_OPENABLE);
                create.setType(mimeType == null ? "application/octet-stream" : mimeType);
                create.putExtra(Intent.EXTRA_TITLE, pendingDownloadName);
                startActivityForResult(create, REQUEST_DOWNLOAD_TARGET);
                status.setText("Choose where to save the remote file.");
            } catch (Exception e) {
                clearPendingDownload();
                status.setText("Could not open Android's save dialog.");
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                mainFrameHttpError = false;
                gatewayProbeGeneration++;
                gatewayMetric.setText("Gateway response time: checking…");
                status.setText("Loading Guacamole gateway…");
                updateSession(RuntimeSession.State.CONNECTING, "Loading gateway page.");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!mainFrameHttpError) {
                    status.setText("Gateway page loaded. Sign in and select a "
                            + "remote connection.");
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                if (!mainFrameHttpError) {
                    updateSession(RuntimeSession.State.GATEWAY_READY,
                            "Gateway page loaded; remote desktop connection state is managed by Guacamole.");
                }
                probeGatewayResponseTime();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri destination = request.getUrl();
                if (isTrustedOrigin(destination)) {
                    return false;
                }
                status.setText("Blocked navigation outside the configured HTTPS server.");
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    status.setText("Gateway connection failed. Check the address "
                            + "or network, then Reconnect.");
                    updateSession(RuntimeSession.State.DEGRADED,
                            "Gateway page request failed.");
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            android.webkit.WebResourceResponse response) {
                if (request.isForMainFrame()) {
                    mainFrameHttpError = true;
                    status.setText("Gateway returned HTTP " + response.getStatusCode()
                            + ". Check the server URL and access policy.");
                    updateSession(RuntimeSession.State.DEGRADED,
                            "Gateway returned HTTP " + response.getStatusCode() + ".");
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           android.net.http.SslError error) {
                handler.cancel();
                status.setText("TLS certificate validation failed. Connection cancelled.");
                updateSession(RuntimeSession.State.DEGRADED,
                        "TLS certificate validation failed.");
            }
        });
    }

    private boolean isTrustedOrigin(Uri candidate) {
        return trustedOrigin != null
                && "https".equalsIgnoreCase(candidate.getScheme())
                && candidate.getUserInfo() == null
                && trustedOrigin.getHost() != null
                && trustedOrigin.getHost().equalsIgnoreCase(candidate.getHost())
                && effectivePort(trustedOrigin) == effectivePort(candidate);
    }

    private boolean isTrustedPage(String url) {
        if (url == null) {
            return false;
        }
        try {
            return isTrustedOrigin(Uri.parse(url));
        } catch (Exception e) {
            return false;
        }
    }

    private void probeGatewayResponseTime() {
        if (trustedOrigin == null || gatewayMetric == null) {
            return;
        }
        final int generation = ++gatewayProbeGeneration;
        final String endpoint = trustedOrigin.toString();
        gatewayMetric.setText("Gateway response time: measuring…");
        new Thread(() -> {
            HttpURLConnection connection = null;
            String outcome = "Gateway response time unavailable. Check the server or network.";
            long start = SystemClock.elapsedRealtime();
            try {
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("HEAD");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                int responseCode = connection.getResponseCode();
                long elapsed = SystemClock.elapsedRealtime() - start;
                RuntimeSessionStore.recordGatewayResponse(RemoteWorkspaceActivity.this,
                        sessionId, elapsed, responseCode,
                        "HTTPS gateway response; this is not remote desktop input latency.");
                outcome = "Gateway response: " + elapsed + " ms (HTTP "
                        + responseCode + "). This does not measure the remote desktop session.";
            } catch (Exception ignored) {
                RuntimeSessionStore.recordGatewayResponse(RemoteWorkspaceActivity.this,
                        sessionId, -1L, -1,
                        "Gateway response probe failed; remote session health is unknown.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final String result = outcome;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed() && gatewayMetric != null
                        && generation == gatewayProbeGeneration) {
                    gatewayMetric.setText(result);
                }
            });
        }, "pocketdex-gateway-probe").start();
    }

    private String safeDownloadName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "remote-file";
        }
        String safe = name.replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        return safe.isEmpty() ? "remote-file" : safe;
    }

    private void clearPendingDownload() {
        pendingDownloadUrl = null;
        pendingDownloadName = null;
    }

    private void updateSession(RuntimeSession.State state, String detail) {
        if (sessionId != null) {
            RuntimeSessionStore.transition(this, sessionId, state, detail);
        }
    }

    private void downloadTo(Uri target) {
        final String source = pendingDownloadUrl;
        final String userAgent = webView == null ? null : webView.getSettings().getUserAgentString();
        final String cookie = source == null ? null : CookieManager.getInstance().getCookie(source);
        clearPendingDownload();
        status.setText("Downloading remote file…");
        new Thread(() -> {
            HttpURLConnection connection = null;
            String outcome;
            try {
                URL current = new URL(source);
                for (int redirects = 0; ; redirects++) {
                    if (!isTrustedOrigin(Uri.parse(current.toString()))) {
                        throw new IllegalStateException("Download redirect left the configured server.");
                    }
                    connection = (HttpURLConnection) current.openConnection();
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(30000);
                    if (userAgent != null) {
                        connection.setRequestProperty("User-Agent", userAgent);
                    }
                    if (cookie != null && !cookie.isEmpty()) {
                        connection.setRequestProperty("Cookie", cookie);
                    }
                    int response = connection.getResponseCode();
                    if (response == HttpURLConnection.HTTP_MOVED_PERM
                            || response == HttpURLConnection.HTTP_MOVED_TEMP
                            || response == HttpURLConnection.HTTP_SEE_OTHER
                            || response == 307 || response == 308) {
                        if (redirects >= 5) {
                            throw new IllegalStateException("Too many download redirects.");
                        }
                        String location = connection.getHeaderField("Location");
                        if (location == null || location.trim().isEmpty()) {
                            throw new IllegalStateException("The server returned an invalid redirect.");
                        }
                        URL next = new URL(current, location);
                        connection.disconnect();
                        connection = null;
                        current = next;
                        continue;
                    }
                    if (response < 200 || response >= 300) {
                        throw new IllegalStateException("Remote server returned HTTP " + response + ".");
                    }
                    try (InputStream input = connection.getInputStream();
                         OutputStream output = getContentResolver().openOutputStream(target, "w")) {
                        if (output == null) {
                            throw new IllegalStateException("Android could not open the selected destination.");
                        }
                        byte[] buffer = new byte[16384];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                        }
                    }
                    break;
                }
                outcome = "Remote file saved.";
            } catch (Exception e) {
                outcome = "Remote download failed: " + e.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final String message = outcome;
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed() && status != null) {
                    status.setText(message);
                }
            });
        }, "pocketdex-remote-download").start();
    }

    private void finishFileChooser(Uri[] result) {
        ValueCallback<Uri[]> callback = pendingFileCallback;
        pendingFileCallback = null;
        if (callback != null) {
            callback.onReceiveValue(result);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DOWNLOAD_TARGET) {
            Uri target = resultCode == RESULT_OK && data != null ? data.getData() : null;
            if (target != null && "content".equalsIgnoreCase(target.getScheme())
                    && pendingDownloadUrl != null) {
                downloadTo(target);
            } else {
                clearPendingDownload();
                status.setText("Remote file save cancelled.");
            }
            return;
        }
        if (requestCode != REQUEST_FILE_CHOOSER) {
            return;
        }
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            Uri[] candidates = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            if (candidates != null && candidates.length > 0) {
                java.util.ArrayList<Uri> accepted = new java.util.ArrayList<>();
                for (Uri candidate : candidates) {
                    if (isSelectableUploadUri(candidate, data)) {
                        accepted.add(candidate);
                    }
                }
                if (!accepted.isEmpty()) {
                    results = accepted.toArray(new Uri[0]);
                }
            }
        }
        finishFileChooser(results);
        if (results == null) {
            status.setText("File selection cancelled or returned an unsupported location.");
        }
    }

    private boolean isSelectableUploadUri(Uri uri, Intent result) {
        if (uri == null || result == null
                || !"content".equalsIgnoreCase(uri.getScheme())
                || uri.getAuthority() == null) {
            return false;
        }
        boolean granted = (result.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
                || checkUriPermission(uri, android.os.Process.myPid(),
                android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            return false;
        }
        ProviderInfo provider = getPackageManager().resolveContentProvider(uri.getAuthority(), 0);
        return provider != null && !getPackageName().equals(provider.packageName);
    }

    private int effectivePort(Uri uri) {
        return uri.getPort() < 0 ? 443 : uri.getPort();
    }

    private TextView toolbarButton(String label, View.OnClickListener action) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextColor(TEXT);
        button.setTextSize(12);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(Ui.ripple(this,
                Ui.rounded(this, 0x33232838, 8), 0x44FFFFFF));
        button.setOnClickListener(action);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        params.rightMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (webView != null) {
            webView.requestLayout();
        }
    }

    private void disconnect() {
        updateSession(RuntimeSession.State.STOPPED, "Remote workspace was disconnected by the user.");
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
        }
        CookieManager.getInstance().removeAllCookies(ignored -> finish());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        gatewayProbeGeneration++;
        if (isFinishing()) {
            RuntimeSession current = RuntimeSessionStore.find(this, sessionId);
            if (current != null && sessionId != null && sessionId.equals(current.id())
                    && current.state() != RuntimeSession.State.STOPPED
                    && current.state() != RuntimeSession.State.FAILED) {
                updateSession(RuntimeSession.State.STOPPED,
                        "Remote workspace activity closed.");
            }
        }
        clearPendingDownload();
        finishFileChooser(null);
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
