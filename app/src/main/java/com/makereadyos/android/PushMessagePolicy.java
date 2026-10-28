package com.makereadyos.android;

import java.util.Map;

final class PushMessagePolicy {
    static String title(Map<String, String> data) {
        return text(data.get("title"), "MakeReadyOS", 100);
    }

    static String body(Map<String, String> data, boolean spanish) {
        return text(data.get("body"), spanish
            ? "Tienes una alerta de trabajo. Abre Alertas para revisarla."
            : "You have a work alert. Open Alerts to review it.", 240);
    }

    private static String text(String value, String fallback, int limit) {
        if (value == null) return fallback;
        String clean = value.replaceAll("[\\p{Cc}\\p{Cf}]", " ").trim();
        if (clean.isEmpty()) return fallback;
        int end = clean.offsetByCodePoints(0, Math.min(limit, clean.codePointCount(0, clean.length())));
        return clean.substring(0, end);
    }

    static boolean accepts(Map<String, String> data, String binding, boolean optedIn, long sessionExpiry, long now) {
        long expiry;
        try { expiry = Long.parseLong(data.get("expiresAt")); } catch (Exception invalid) { return false; }
        String id = data.get("notificationId");
        return optedIn && sessionExpiry > now && expiry > now && expiry <= now + 310000
            && binding != null && !binding.isEmpty() && binding.equals(data.get("bindingId"))
            && id != null && id.matches("[A-Za-z0-9_-]{1,128}");
    }
}
