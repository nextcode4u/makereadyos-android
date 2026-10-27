package com.makereadyos.android;

import java.net.URI;
import java.util.Locale;

/** Root-hosted HTTPS only. Never infer an API origin from a response or redirect. */
public final class ServerAddress {
    private ServerAddress() {}

    public static URI parse(String input) {
        try {
            String value = input.trim();
            if (!value.contains("://")) value = "https://" + value;
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !(uri.getRawPath().isEmpty() || uri.getRawPath().equals("/"))
                    || !host.matches("(?i)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")
                    || !host.contains(".") || host.contains("..")) {
                throw new IllegalArgumentException();
            }
            return new URI("https://" + host.toLowerCase(Locale.ROOT));
        } catch (Exception error) {
            throw new IllegalArgumentException("Use an HTTPS domain without a path, port, or credentials.");
        }
    }
}
