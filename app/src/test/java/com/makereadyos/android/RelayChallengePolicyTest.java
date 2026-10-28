package com.makereadyos.android;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class RelayChallengePolicyTest {
    private final Map<String, String> data = Map.of("kind", "relay-challenge", "origin", "https://example.test", "nonce", "nonce-a",
        "bindingId", "binding-a", "challengeId", "d673a280-1ae9-4f6d-8455-464921bc3431", "proof", "a".repeat(64), "expiresAt", "2000");
    @Test public void requiresCurrentOriginNonceBindingAndExpiry() {
        assertTrue(RelayChallengePolicy.accepts(data, "https://example.test", "nonce-a", "binding-a", 3000, 1000));
        assertFalse(RelayChallengePolicy.accepts(data, "https://other.test", "nonce-a", "binding-a", 3000, 1000));
        assertFalse(RelayChallengePolicy.accepts(data, "https://example.test", "wrong", "binding-a", 3000, 1000));
        assertFalse(RelayChallengePolicy.accepts(data, "https://example.test", "nonce-a", "old-account", 3000, 1000));
        assertFalse(RelayChallengePolicy.accepts(data, "https://example.test", "nonce-a", "binding-a", 1000, 1000));
        assertFalse(RelayChallengePolicy.accepts(data, "https://example.test", "nonce-a", "binding-a", 3000, 2000));
        assertFalse(RelayChallengePolicy.accepts(Map.of(), "https://example.test", "nonce-a", "binding-a", 3000, 1000));
    }
}
