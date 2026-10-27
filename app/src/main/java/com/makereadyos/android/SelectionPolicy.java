package com.makereadyos.android;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** Bounds apply to bytes read, not untrusted document-provider metadata. */
final class SelectionPolicy {
    static final int MAX_FILES = 10;
    static final long MAX_FILE_BYTES = 25L * 1024 * 1024;
    static final long MAX_BATCH_BYTES = 100L * 1024 * 1024;
    static long copy(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Selection cancelled");
            total += read;
            if (total > limit) throw new IOException("Selection is too large");
            out.write(buffer, 0, read);
        }
        if (total == 0) throw new IOException("Selected file is empty");
        return total;
    }
    static String safeName(String name) {
        String clean = name == null ? "attachment" : name.replaceAll("[^a-zA-Z0-9._ -]", "_");
        if (clean.isBlank() || clean.equals(".") || clean.equals("..")) clean = "attachment";
        return clean.length() > 160 ? clean.substring(clean.length() - 160) : clean;
    }
    static boolean supportsPhoto(String[] types) {
        if (types.length == 0) return true;
        for (String entry : types) for (String type : entry.split(",")) {
            String value = type.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty() || value.equals("*/*") || value.equals("image/*") || value.equals("image/jpeg")
                || value.equals(".jpg") || value.equals(".jpeg")) return true;
        }
        return false;
    }
}
