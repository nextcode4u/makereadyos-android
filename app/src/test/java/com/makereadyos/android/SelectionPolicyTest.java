package com.makereadyos.android;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class SelectionPolicyTest {
    @Test public void acceptsExactByteLimitAndRejectsOversize() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertEquals(3, SelectionPolicy.copy(new ByteArrayInputStream(new byte[]{1,2,3}), output, 3));
        assertArrayEquals(new byte[]{1,2,3}, output.toByteArray());
        assertThrows(IOException.class, () -> SelectionPolicy.copy(new ByteArrayInputStream(new byte[4]), new ByteArrayOutputStream(), 3));
        assertThrows(IOException.class, () -> SelectionPolicy.copy(new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), 3));
    }
    @Test public void cleansPathsWithoutLosingExtension() {
        assertEquals(".._.._test.jpg", SelectionPolicy.safeName("../../test.jpg"));
        assertEquals("attachment", SelectionPolicy.safeName(".."));
        String name = SelectionPolicy.safeName("a".repeat(200) + ".png");
        assertEquals(160, name.length());
        assertTrue(name.endsWith(".png"));
    }
    @Test public void onlyOffersJpegCameraForCompatibleInputs() {
        assertTrue(SelectionPolicy.supportsPhoto(new String[]{"image/*"}));
        assertTrue(SelectionPolicy.supportsPhoto(new String[]{".pdf,.jpg,.png"}));
        assertTrue(SelectionPolicy.supportsPhoto(new String[]{}));
        assertFalse(SelectionPolicy.supportsPhoto(new String[]{"application/pdf"}));
        assertFalse(SelectionPolicy.supportsPhoto(new String[]{"image/png"}));
    }
}
