package com.makereadyos.android;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class ReportPdfPolicy {
    static final int MAX_BYTES = 16 * 1024 * 1024;
    static final int MAX_BASE64 = 22369624;
    static byte[] decode(String encoded) {
        if (encoded == null || encoded.length() > MAX_BASE64) throw new IllegalArgumentException("PDF size");
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length < 12 || bytes.length > MAX_BYTES
            || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw new IllegalArgumentException("PDF header");
        String tail = new String(bytes, Math.max(0, bytes.length - 1024), Math.min(1024, bytes.length), StandardCharsets.US_ASCII);
        if (!tail.contains("%%EOF")) throw new IllegalArgumentException("Incomplete PDF");
        return bytes;
    }
    static String filename(String name, String kind) {
        if (!(kind.equals("draft") || kind.equals("resident")) || name == null || !name.endsWith(".pdf")) throw new IllegalArgumentException("Report type");
        return (kind.equals("draft") ? "DRAFT-" : "RESIDENT-") + SelectionPolicy.safeName(name);
    }
}
