package com.makereadyos.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.webkit.*;
import android.view.WindowManager;
import android.widget.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebMessageCompat;

/** Packaged, trusted UI at the selected HTTPS origin, with an origin-bound PDF-only message channel. */
public final class WebWorkspaceActivity extends Activity {
    private WebView web;
    private String origin;
    private TextView status;
    private ProgressBar loading;
    private WorkspaceFilePicker picker;
    private ReportPdfDelivery reports;
    private static String currentProcessOrigin;
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
        + "img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self'; "
        + "frame-src 'self' data: blob:; object-src 'none'; base-uri 'none'; form-action 'self'";

    @Override @android.annotation.SuppressLint("SetJavaScriptEnabled")
    public void onCreate(Bundle saved) {
        WorkspaceTheme.style(this, WorkspaceTheme.saved(this));
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        try { origin = ServerAddress.parse(getIntent().getStringExtra("server")).toString(); }
        catch (Exception invalid) { finish(); return; }
        // Until server/account switching and pending-work review are implemented, keep one origin per process.
        if (currentProcessOrigin != null && !currentProcessOrigin.equals(origin)) {
            new AlertDialog.Builder(this).setMessage(R.string.web_switch_notice)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> finish()).setCancelable(false).show();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.WHITE);
        status = new TextView(this);
        status.setText(R.string.web_opening);
        status.setTextColor(android.graphics.Color.rgb(24,47,48));
        status.setTextSize(12);
        int padding = Math.round(8 * getResources().getDisplayMetrics().density);
        status.setPadding(padding,padding,padding,padding);
        root.addView(status);
        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setIndeterminate(true);
        root.addView(loading, new LinearLayout.LayoutParams(-1, padding));
        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(-1,0,1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        applyTheme(root, WorkspaceTheme.saved(this));
        root.requestApplyInsets();
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        picker = new WorkspaceFilePicker(this, this::showStatus);
        reports = new ReportPdfDelivery(this, web, origin, this::showStatus);
        nativePush = new NativePushBridge(this, web, origin);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "MakeReadyOSTheme", Set.of(origin), (view, message, source, mainFrame, reply) -> {
                if (!mainFrame || !origin.equals(source.toString()) || message.getType() != WebMessageCompat.TYPE_STRING) return;
                String mode = message.getData();
                if (!WorkspaceTheme.valid(mode)) return;
                WorkspaceTheme.save(this, mode);
                applyTheme(root, mode);
            });
        }
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<android.net.Uri[]> callback, FileChooserParams params) {
                String url = view.getUrl();
                if (url == null || !(url.equals(origin + "/") || url.startsWith(origin + "/?"))) {
                    callback.onReceiveValue(null); return true;
                }
                return picker.show(callback, params);
            }
        });
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                loading.setVisibility(android.view.View.GONE);
                status.setVisibility(android.view.View.GONE);
                if (url.equals(origin + "/") || url.startsWith(origin + "/?")) {
                    // Observe the existing web preference, rather than introducing a second theme control.
                    view.evaluateJavascript("(() => { if (!window.MakeReadyOSTheme) return; "
                        + "window.__mrosThemeObserver?.disconnect(); "
                        + "const send = () => { const mode = document.documentElement.dataset.theme; "
                        + "if (['light','dark','default'].includes(mode)) window.MakeReadyOSTheme.postMessage(mode); }; "
                        + "window.__mrosThemeObserver = new MutationObserver(send); "
                        + "window.__mrosThemeObserver.observe(document.documentElement, {attributes:true,attributeFilter:['data-theme']}); send(); })()", null);
                }
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return route(request);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request.getUrl().toString().equals(origin + "/") || request.getUrl().toString().startsWith(origin + "/?")) return false;
                // Do not load third-party HTML in a workspace with stored auth and offline drafts.
                showStatus(getString(R.string.web_external_notice));
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showLocalFallback();
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) showLocalFallback();
            }
            @Override public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                handler.cancel();
                showStatus(getString(R.string.error_tls));
            }
        });
        // Defensive: no website service worker may bypass the packaged-resource routing.
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) { return route(request); }
        });
        if (android.os.Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
        // Retain the server-issued session; the web app verifies /auth/me before loading live work.
        // Cookies are scoped to the selected host and never copied into native preferences.
        currentProcessOrigin = origin;
        web.loadUrl(origin + (getIntent().getBooleanExtra("openAlerts", false) ? "/?notifications=1" : "/"));
    }

    private void applyTheme(LinearLayout root, String mode) {
        WorkspaceTheme.style(this, mode);
        WorkspaceTheme.bars(this, root, mode);
        status.setBackgroundColor(WorkspaceTheme.surface(mode));
        status.setTextColor(WorkspaceTheme.text(mode));
        web.setBackgroundColor(WorkspaceTheme.background(mode));
        loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(WorkspaceTheme.accent(mode)));
    }

    private WebResourceResponse route(WebResourceRequest request) {
        android.net.Uri url = request.getUrl();
        if (!"https".equals(url.getScheme()) || !url.getAuthority().equals(android.net.Uri.parse(origin).getAuthority()))
            return response(403, "Forbidden", "text/plain", "External resource blocked");
        String path = url.getPath();
        if (path == null || path.contains("..") || path.contains("\\") || path.contains("%"))
            return response(400, "Bad Request", "text/plain", "Invalid path");
        if (path.startsWith("/api/")) return null; // Actual server responses are never substituted with cached successes.
        if (!request.getMethod().equals("GET")) return response(405,"Method Not Allowed","text/plain","");
        if (path.equals("/sw.js")) return response(404,"Not Found","text/plain","");
        if (path.equals("/")) path = "/index.html";
        try {
            String mime = mime(path);
            return new WebResourceResponse(mime, "UTF-8", 200, "OK",
                Map.of("Content-Security-Policy", CSP, "Cache-Control", "no-store", "X-Content-Type-Options", "nosniff"),
                getAssets().open("web" + path));
        } catch (IOException missing) {
            return response(404,"Not Found","text/plain","Resource not included in this app build");
        }
    }
    private String mime(String path) {
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".webmanifest") || path.endsWith(".json")) return "application/json";
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
        return type == null ? "application/octet-stream" : type;
    }
    private WebResourceResponse response(int code, String reason, String mime, String body) {
        return new WebResourceResponse(mime,"UTF-8",code,reason,Map.of("Cache-Control","no-store"),
            new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }
    private void showLocalFallback() {
        // Only a top-level failure reaches here; API errors must leave the current React screen intact.
        showStatus(getString(R.string.web_load_failed));
        web.setVisibility(android.view.View.GONE);
        new AlertDialog.Builder(this).setMessage(R.string.web_load_failed)
            .setPositiveButton(R.string.retry, (dialog, which) -> { web.setVisibility(android.view.View.VISIBLE); web.loadUrl(origin + "/"); })
            .setNegativeButton(android.R.string.cancel, null).show();
    }
    private void back() {
        if (web != null && web.canGoBack()) { web.goBack(); return; }
        moveTaskToBack(true);
    }
    private void showStatus(String message) {
        status.setText(message);
        status.setVisibility(android.view.View.VISIBLE);
        status.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { back(); }
    private NativePushBridge nativePush;
    @Override protected void onResume() {
        super.onResume();
        if (nativePush != null) nativePush.resume();
    }
    @Override protected void onPause() {
        if (nativePush != null) nativePush.pause();
        // Persist login and logout cookie changes before Android can reclaim the process.
        CookieManager.getInstance().flush();
        super.onPause();
    }
    @Override protected void onDestroy() {
        if (reports != null) reports.destroy();
        if (picker != null) picker.destroy();
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == WorkspaceFilePicker.REQUEST && picker != null) picker.result(resultCode, data);
        if (requestCode == ReportPdfDelivery.SAVE_REQUEST && reports != null) reports.result(resultCode, data);
    }
}
