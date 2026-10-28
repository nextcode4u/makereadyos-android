package com.makereadyos.android;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PushMessagePolicyTest {
    @Test public void displaysEventAndUnitInsteadOfGenericPrompt() {
        Map<String, String> summary = Map.of("title", "MakeReadyOS - DG 102", "body", "Final walk corrections requested. Tap to view details.");
        assertEquals("MakeReadyOS - DG 102", PushMessagePolicy.title(summary));
        assertEquals(summary.get("body"), PushMessagePolicy.body(summary, false));
        assertEquals(summary.get("body"), PushMessagePolicy.body(summary, true));
    }
    @Test public void supportsOlderServersAndBoundsUntrustedText() {
        assertEquals("MakeReadyOS", PushMessagePolicy.title(Map.of()));
        assertTrue(PushMessagePolicy.body(Map.of(), true).contains("Alertas"));
        assertTrue(PushMessagePolicy.body(Map.of("body", " \n\t"), false).contains("Open Alerts"));
        assertEquals("Final walk", PushMessagePolicy.body(Map.of("body", "Final\nwalk"), false));
        assertEquals(240, PushMessagePolicy.body(Map.of("body", "x".repeat(1000)), false).length());
        assertEquals(100, PushMessagePolicy.title(Map.of("title", "x".repeat(1000))).length());
    }
    private final Map<String, String> data = Map.of("bindingId", "current-binding", "notificationId", "notice-1", "expiresAt", "2000");
    @Test public void acceptsCurrentBindingOnly() {
        assertTrue(PushMessagePolicy.accepts(data, "current-binding", true, 5000, 1000));
        assertFalse(PushMessagePolicy.accepts(data, "previous-account", true, 5000, 1000));
        assertFalse(PushMessagePolicy.accepts(data, "", true, 5000, 1000));
        assertFalse(PushMessagePolicy.accepts(data, "current-binding", false, 5000, 1000));
    }
    @Test public void dropsExpiredMalformedAndUnboundedMessages() {
        assertFalse(PushMessagePolicy.accepts(data, "current-binding", true, 1000, 1000));
        assertFalse(PushMessagePolicy.accepts(data, "current-binding", true, 5000, 2000));
        assertFalse(PushMessagePolicy.accepts(Map.of(), "current-binding", true, 5000, 1000));
        assertFalse(PushMessagePolicy.accepts(Map.of("bindingId", "current-binding", "notificationId", "https://evil.test", "expiresAt", "2000"), "current-binding", true, 5000, 1000));
        assertFalse(PushMessagePolicy.accepts(Map.of("bindingId", "current-binding", "notificationId", "notice", "expiresAt", "999999999"), "current-binding", true, 5000, 1000));
    }
}
