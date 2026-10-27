package com.makereadyos.android;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.webkit.WebView;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public final class WorkspaceMessagingService extends FirebaseMessagingService {
    @Override public void onMessageReceived(RemoteMessage message) {
        SharedPreferences p = NativePushBridge.prefs(this);
        Map<String, String> data = message.getData();
        long now = System.currentTimeMillis();
        String id = data.get("notificationId");
        if (message.getNotification() != null || !PushMessagePolicy.accepts(data, p.getString("bindingId", ""),
            p.getBoolean("optedIn", false), p.getLong("expiresAt", 0), now)) return;
        String seen = p.getString("seen", "");
        if (seen.contains("|" + id + "|")) return;
        seen += "|" + id + "|";
        p.edit().putString("seen", seen.substring(Math.max(0, seen.length() - 8192))).commit();
        WebView web = NativePushBridge.foreground.get();
        if (web != null) {
            web.post(() -> web.evaluateJavascript("window.dispatchEvent(new Event('makereadyos-native-alert'))", null));
            return;
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return;
        boolean es = p.getString("language", "en").equals("es");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("work-alerts", es ? "Alertas de trabajo" : "Work alerts", NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent = new Intent(this, WebWorkspaceActivity.class).putExtra("server", p.getString("origin", ""))
            .putExtra("openAlerts", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap = PendingIntent.getActivity(this, 912, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.notify(id.hashCode(), new NotificationCompat.Builder(this, "work-alerts")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("MakeReadyOS")
            .setContentText(es ? "Tienes una alerta de trabajo. Abre Alertas para revisarla." : "You have a work alert. Open Alerts to review it.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(tap).build());
    }
}
