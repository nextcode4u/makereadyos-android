package com.makereadyos.android;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ReportPdfPolicyTest {
    private String encoded(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.US_ASCII)); }
    @Test public void acceptsPdfEnvelopeForSubsequentRendererValidation() {
        String text = "%PDF-1.7\nexample\n%%EOF\n";
        assertArrayEquals(text.getBytes(StandardCharsets.US_ASCII), ReportPdfPolicy.decode(encoded(text)));
    }
    @Test public void rejectsErrorsTruncationAndExcessiveSize() {
        assertThrows(IllegalArgumentException.class, () -> ReportPdfPolicy.decode(encoded("<html>error</html>")));
        assertThrows(IllegalArgumentException.class, () -> ReportPdfPolicy.decode(encoded("%PDF-1.7\ntruncated")));
        assertThrows(IllegalArgumentException.class, () -> ReportPdfPolicy.decode("not base64"));
        assertThrows(IllegalArgumentException.class, () -> ReportPdfPolicy.decode("a".repeat(ReportPdfPolicy.MAX_BASE64 + 1)));
    }
    @Test public void labelsReportsAndRejectsOtherFileTypes() {
        assertEquals("DRAFT-.._report.pdf", ReportPdfPolicy.filename("../report.pdf", "draft"));
        assertEquals("RESIDENT-report.pdf", ReportPdfPolicy.filename("report.pdf", "resident"));
        assertThrows(IllegalArgumentException.class, () -> ReportPdfPolicy.filename("report.html", "resident"));
        assertThrows(IllegalArgumentException.class, () -> ReportPdfPolicy.filename("report.pdf", "other"));
    }
}
