package com.makereadyos.android;

import java.util.Map;

final class PushMessagePolicy {
    static boolean accepts(Map<String, String> data, String binding, boolean optedIn, long sessionExpiry, long now) {
        long expiry;
        try { expiry = Long.parseLong(data.get("expiresAt")); } catch (Exception invalid) { return false; }
        String id = data.get("notificationId");
        return optedIn && sessionExpiry > now && expiry > now && expiry <= now + 310000
            && binding != null && !binding.isEmpty() && binding.equals(data.get("bindingId"))
            && id != null && id.matches("[A-Za-z0-9_-]{1,128}");
    }
}
