package com.makereadyos.android;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.WebView;
import androidx.core.app.NotificationManagerCompat;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.UUID;
import org.json.JSONObject;

final class NativePushBridge {
    static WeakReference<WebView> foreground = new WeakReference<>(null);
    private final WebView web;
    static SharedPreferences prefs(Context context) { return context.getSharedPreferences("native-push", Context.MODE_PRIVATE); }
    static void clear(Context context) {
        prefs(context).edit().clear().commit();
        context.getSystemService(NotificationManager.class).cancelAll();
    }
    NativePushBridge(Activity activity, WebView view, String origin) {
        web = view;
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return;
        WebViewCompat.addWebMessageListener(web, "MakeReadyOSPush", Set.of(origin), (v, message, source, main, reply) -> {
            if (!main || !origin.equals(source.toString()) || message.getType() != WebMessageCompat.TYPE_STRING) return;
            String raw = message.getData();
            if (raw == null || raw.length() > 8192) return;
            JSONObject result = new JSONObject();
            try {
                JSONObject input = new JSONObject(raw);
                result.put("id", input.getString("id"));
                String action = input.getString("action");
                SharedPreferences p = prefs(activity);
                if (action.equals("clear")) { clear(activity); reply.postMessage(result.toString()); return; }
                String owner = input.getString("owner");
                if (owner.length() > 256 || owner.isEmpty()) throw new Exception();
                if (!owner.equals(p.getString("owner", "")) || !origin.equals(p.getString("origin", ""))) {
                    if (!action.equals("status")) throw new Exception();
                    clear(activity);
                    p.edit().putString("owner", owner).putString("origin", origin).commit();
                }
                p.edit().putString("language", input.optString("language", p.getString("language", "en"))).apply();
                result.put("configured", true).put("projectId", FirebaseApp.getInstance().getOptions().getProjectId());
                result.put("relaySupported", true);
                result.put("optedIn", p.getBoolean("optedIn", false));
                result.put("enabled", p.getLong("expiresAt", 0) > System.currentTimeMillis() && NotificationManagerCompat.from(activity).areNotificationsEnabled());
                result.put("bindingId", p.getString("bindingId", ""));
                if (action.equals("status")) { reply.postMessage(result.toString()); return; }
                if (action.equals("relayProof")) {
                    if (p.getLong("relayExpires", 0) > System.currentTimeMillis()
                        && input.getString("challengeId").equals(p.getString("relayChallenge", "")))
                        result.put("proof", p.getString("relayProof", ""));
                    reply.postMessage(result.toString()); return;
                }
                if (action.equals("activate")) {
                    if (!input.getString("bindingId").equals(p.getString("pendingBinding", ""))) throw new Exception();
                    p.edit().putString("bindingId", input.getString("bindingId")).putLong("expiresAt", input.getLong("expiresAt"))
                        .putString("boundToken", p.getString("preparedToken", "")).putBoolean("optedIn", true).commit();
                    p.edit().remove("relayProof").remove("relayChallenge").remove("relayNonce").remove("relayExpires").commit();
                    reply.postMessage(result.toString()); return;
                }
                if (!action.equals("prepare") || (!input.optBoolean("interactive") && !p.getBoolean("optedIn", false))) throw new Exception();
                if (!NotificationManagerCompat.from(activity).areNotificationsEnabled()) {
                    if (input.optBoolean("interactive") && Build.VERSION.SDK_INT >= 33)
                        activity.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 912);
                    result.put("error", "Allow notifications in Android settings, then tap Enable again.");
                    reply.postMessage(result.toString()); return;
                }
                FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                    try {
                        if (!task.isSuccessful() || !owner.equals(p.getString("owner", "")) || !origin.equals(p.getString("origin", ""))) {
                            result.put("error", "Device registration unavailable. Please try again.");
                        } else {
                            String token = task.getResult();
                            String binding = p.getString("pendingBinding", "");
                            if (binding.isEmpty()) binding = UUID.randomUUID().toString();
                            p.edit().putString("pendingBinding", binding).putString("preparedToken", token).commit();
                            result.put("token", token).put("bindingId", binding);
                            result.put("registeredToken", token.equals(p.getString("boundToken", "")));
                            if (input.optBoolean("relay")) {
                                String nonce = UUID.randomUUID().toString();
                                p.edit().putString("relayNonce", nonce).putLong("relayExpires", System.currentTimeMillis() + 120000)
                                    .remove("relayProof").remove("relayChallenge").commit();
                                result.put("nonce", nonce);
                            }
                        }
                        reply.postMessage(result.toString());
                    } catch (Exception ignored) { /* A closed WebView cannot receive a reply. */ }
                });
            } catch (Exception invalid) {
                try { result.put("error", "Device registration changed. Please check notifications again."); reply.postMessage(result.toString()); }
                catch (Exception ignored) {}
            }
        });
    }
    void resume() {
        foreground = new WeakReference<>(web);
        web.evaluateJavascript("window.dispatchEvent(new Event('makereadyos-native-push-refresh'))", null);
    }
    void pause() { if (foreground.get() == web) foreground.clear(); }
}
