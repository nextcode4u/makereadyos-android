package com.makereadyos.android;

import java.util.Map;

final class RelayChallengePolicy {
    static boolean accepts(Map<String, String> data, String origin, String nonce, String binding, long pendingExpires, long now) {
        try {
            long expires = Long.parseLong(data.getOrDefault("expiresAt", "0"));
            return "relay-challenge".equals(data.get("kind")) && !origin.isEmpty() && origin.equals(data.get("origin"))
                && !nonce.isEmpty() && nonce.equals(data.get("nonce")) && !binding.isEmpty() && binding.equals(data.get("bindingId"))
                && pendingExpires > now && expires > now && expires <= now + 125000
                && data.getOrDefault("proof", "").matches("[a-f0-9]{64}")
                && data.getOrDefault("challengeId", "").matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
        } catch (NumberFormatException invalid) { return false; }
    }
}
