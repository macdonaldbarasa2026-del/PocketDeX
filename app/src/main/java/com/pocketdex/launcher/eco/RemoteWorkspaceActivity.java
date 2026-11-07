package com.pocketdex.launcher.eco;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.pocketdex.launcher.R;
import com.pocketdex.launcher.Ui;

/** Remote desktop viewer for a user-managed HTTPS Apache Guacamole server. */
public final class RemoteWorkspaceActivity extends Activity {
    public static final String PREFS = "pocketdex_remote";
    public static final String KEY_GUACAMOLE_URL = "guacamole_url";

    private static final int BG = 0xFF0B0D12;
    private static final int TEXT = 0xFFF4F6FB;
    private static final int DIM = 0xFF9AA3B5;

    private WebView webView;
    private TextView status;
    private Uri trustedOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUi();

        String savedUrl = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_GUACAMOLE_URL, "");
        Uri endpoint = normalizeEndpoint(savedUrl);
        if (endpoint == null) {
            status.setText("Set an HTTPS Guacamole server in PocketDeX Settings.");
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
        toolbar.addView(toolbarButton("Reload", v -> {
            if (webView != null) {
                webView.reload();
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
                    status.setText("Connecting to remote workspace… " + progress + "%");
                } else {
                    status.setText("Remote workspace · keyboard and pointer input enabled");
                }
            }
        });
        webView.setWebViewClient(new WebViewClient() {
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
                    status.setText("Connection failed. Check the server address and network, then Reload.");
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           android.net.http.SslError error) {
                handler.cancel();
                status.setText("TLS certificate validation failed. Connection cancelled.");
            }
        });
    }

    private boolean isTrustedOrigin(Uri candidate) {
        return trustedOrigin != null
                && "https".equalsIgnoreCase(candidate.getScheme())
                && trustedOrigin.getHost() != null
                && trustedOrigin.getHost().equalsIgnoreCase(candidate.getHost())
                && effectivePort(trustedOrigin) == effectivePort(candidate);
    }

    /** Accept only an HTTPS base URL and remove fragments or query data. */
    public static Uri normalizeEndpoint(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            Uri input = Uri.parse(value.trim());
            if (!"https".equalsIgnoreCase(input.getScheme())
                    || input.getHost() == null || input.getHost().isEmpty()
                    || input.getUserInfo() != null || input.getQuery() != null) {
                return null;
            }
            String path = input.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (!path.endsWith("/")) {
                path += "/";
            }
            Uri.Builder builder = new Uri.Builder().scheme("https").encodedAuthority(
                    input.getEncodedAuthority()).encodedPath(path);
            return builder.build();
        } catch (Exception e) {
            return null;
        }
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
        return buttonWithParams(button, params);
    }

    private TextView buttonWithParams(TextView button, LinearLayout.LayoutParams params) {
        button.setLayoutParams(params);
        return button;
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }

    private void disconnect() {
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
