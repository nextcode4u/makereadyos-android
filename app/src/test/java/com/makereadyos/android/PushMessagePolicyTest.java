package com.makereadyos.android;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class PushMessagePolicyTest {
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
