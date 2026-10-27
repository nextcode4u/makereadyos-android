package com.makereadyos.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLException;

/** First-run server selection. Authentication remains in the shared web workspace. */
public final class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        String mode = WorkspaceTheme.saved(this);
        WorkspaceTheme.style(this, mode);
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        String saved = getPreferences(MODE_PRIVATE).getString("server", "");
        if (!saved.isEmpty()) {
            try { open(ServerAddress.parse(saved).toString()); return; }
            catch (IllegalArgumentException invalid) {
                getPreferences(MODE_PRIVATE).edit().remove("server").apply();
            }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(24), dp(36), dp(24), dp(24));
        scroll.addView(page);
        TextView brand = label(page, R.string.app_name, 16, mode);
        brand.setTextColor(WorkspaceTheme.accent(mode));
        label(page, R.string.connect_heading, 30, mode);
        label(page, R.string.connect_help, 16, mode);
        TextView caption = label(page, R.string.domain, 14, mode);
        EditText domain = new EditText(this);
        domain.setId(View.generateViewId());
        caption.setLabelFor(domain.getId());
        domain.setSingleLine(true);
        domain.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        domain.setHint("example.com");
        domain.setTextColor(WorkspaceTheme.text(mode));
        domain.setMinHeight(dp(52));
        domain.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        page.addView(domain);
        label(page, R.string.domain_help, 13, mode);
        label(page, R.string.verify_domain, 13, mode);
        TextView message = label(page, R.string.working, 14, mode);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        message.setVisibility(View.GONE);
        Button connect = new Button(this);
        connect.setText(R.string.check_server);
        connect.setAllCaps(false);
        connect.setMinHeight(dp(48));
        page.addView(connect);
        connect.setOnClickListener(view -> {
            if (busy) return;
            final WorkspaceApi api;
            try { api = new WorkspaceApi(ServerAddress.parse(domain.getText().toString())); }
            catch (IllegalArgumentException invalid) {
                message.setText(R.string.error_domain);
                message.setVisibility(View.VISIBLE);
                return;
            }
            busy = true;
            domain.setEnabled(false);
            connect.setEnabled(false);
            message.setText(R.string.working);
            message.setVisibility(View.VISIBLE);
            executor.execute(() -> {
                try {
                    api.verify();
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        getPreferences(MODE_PRIVATE).edit().putString("server", api.origin()).apply();
                        open(api.origin());
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        busy = false;
                        domain.setEnabled(true);
                        connect.setEnabled(true);
                        message.setText(errorString(error));
                    });
                }
            });
        });
        domain.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        domain.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_GO) return false;
            connect.performClick();
            return true;
        });
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        WorkspaceTheme.bars(this, scroll, mode);
        scroll.requestApplyInsets();
    }

    private void open(String origin) {
        startActivity(new Intent(this, WebWorkspaceActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("server", origin));
        finish();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(LinearLayout page, int resource, int size, String mode) {
        TextView view = new TextView(this);
        view.setText(resource);
        view.setTextSize(size);
        view.setTextColor(WorkspaceTheme.text(mode));
        view.setPadding(0, dp(8), 0, dp(8));
        page.addView(view);
        return view;
    }
    private int errorString(Exception error) {
        if (error instanceof SSLException) return R.string.error_tls;
        if (error instanceof WorkspaceApi.Failure failure) return switch (failure.kind) {
            case "forbidden" -> R.string.error_forbidden;
            case "redirect" -> R.string.error_redirect;
            case "rate_limit" -> R.string.error_rate;
            case "server" -> R.string.error_server;
            case "too_large" -> R.string.error_large;
            default -> R.string.error_compatibility;
        };
        if (error instanceof org.json.JSONException) return R.string.error_compatibility;
        return R.string.error_connection;
    }
    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
