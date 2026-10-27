package com.makereadyos.android;

import org.junit.Test;
import static org.junit.Assert.*;

public class ServerAddressTest {
    @Test public void normalizesRootDomains() {
        assertEquals("https://demo.example", ServerAddress.parse(" Demo.Example ").toString());
        assertEquals("https://demo.example", ServerAddress.parse("https://demo.example:443/").toString());
    }
    @Test public void rejectsAmbiguousOrUnsafeAddresses() {
        for (String value : new String[]{"", "http://demo.example", "https://u:p@demo.example",
                "https://demo.example/path", "https://demo.example?token=x", "https://demo.example#x",
                "https://demo.example:8443", "https://demo.example\\@other.example",
                "https://demo.example/%2e%2e", "https://demo..example", "javascript:alert(1)",
                "https://demo.ex\nample", "https://[::1]"}) {
            assertThrows(value, IllegalArgumentException.class, () -> ServerAddress.parse(value));
        }
    }
}
