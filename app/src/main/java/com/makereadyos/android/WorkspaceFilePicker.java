package com.makereadyos.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** User-selected content only. Never forward third-party result URIs directly to WebView. */
final class WorkspaceFilePicker {
    static final int REQUEST = 741;
    private final Activity activity;
    private final Consumer<String> notice;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ValueCallback<Uri[]> callback;
    private File directory;
    private File cameraFile;
    private Uri cameraUri;
    private boolean multiple;
    private boolean camera;
    private boolean destroyed;
    private AlertDialog dialog;

    WorkspaceFilePicker(Activity activity, Consumer<String> notice) {
        this.activity = activity;
        this.notice = notice;
        worker.execute(() -> {
            File[] old = new File(activity.getCacheDir(), "picker").listFiles();
            if (old != null) for (File file : old) if (System.currentTimeMillis() - file.lastModified() > 86400000L) remove(file);
        });
    }
    boolean show(ValueCallback<Uri[]> next, WebChromeClient.FileChooserParams params) {
        if (callback != null || destroyed || (params.getMode() != WebChromeClient.FileChooserParams.MODE_OPEN
            && params.getMode() != WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)) {
            next.onReceiveValue(null); return true;
        }
        callback = next;
        multiple = params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
        directory = new File(activity.getCacheDir(), "picker/" + UUID.randomUUID());
        if (!directory.mkdirs()) { fail(); return true; }
        boolean canCamera = SelectionPolicy.supportsPhoto(params.getAcceptTypes())
            && new Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(activity.getPackageManager()) != null;
        if (params.isCaptureEnabled() && canCamera) capture();
        else if (canCamera) {
            dialog = new AlertDialog.Builder(activity).setTitle("Add attachment")
                .setItems(new String[]{"Take photo", "Choose photos or files"}, (d, which) -> {
                    if (which == 0) capture(); else choose(params.getAcceptTypes());
                }).setOnCancelListener(d -> complete(null)).show();
        } else choose(params.getAcceptTypes());
        return true;
    }
    private void choose(String[] accepted) {
        camera = false;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        Set<String> types = new LinkedHashSet<>();
        for (String entry : accepted) for (String raw : entry.split(",")) {
            String type = raw.trim().toLowerCase(Locale.ROOT);
            if (type.startsWith(".")) type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(type.substring(1));
            if (type != null && type.matches("[a-z0-9*.+-]+/[a-z0-9*.+-]+")) types.add(type);
        }
        intent.setType(types.size() == 1 ? types.iterator().next() : "*/*");
        if (types.size() > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        launch(intent);
    }
    private void capture() {
        camera = true;
        cameraFile = new File(directory, "photo-" + System.currentTimeMillis() + ".jpg");
        cameraUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", cameraFile);
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri("Photo", cameraUri));
        launch(intent);
    }
    @SuppressWarnings("deprecation")
    private void launch(Intent intent) {
        try { activity.startActivityForResult(intent, REQUEST); }
        catch (RuntimeException unavailable) { fail(); }
    }
    void result(int resultCode, Intent result) {
        if (callback == null) return;
        if (cameraUri != null) activity.revokeUriPermission(cameraUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (resultCode != Activity.RESULT_OK) { complete(null); return; }
        List<Uri> selected = new ArrayList<>();
        if (!camera && result != null) {
            if (result.getClipData() != null) {
                if (result.getClipData().getItemCount() > (multiple ? SelectionPolicy.MAX_FILES : 1)) { fail(); return; }
                for (int i = 0; i < result.getClipData().getItemCount(); i++) selected.add(result.getClipData().getItemAt(i).getUri());
            } else if (result.getData() != null) selected.add(result.getData());
        }
        final File batch = directory;
        final File captured = cameraFile;
        final boolean takingPhoto = camera;
        notice.accept("Preparing selected files on this device...");
        worker.execute(() -> {
            try {
                List<Uri> ready = new ArrayList<>();
                if (takingPhoto) {
                    if (captured == null || captured.length() == 0 || captured.length() > SelectionPolicy.MAX_FILE_BYTES) throw new IOException();
                    ready.add(FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", captured));
                } else {
                    if (selected.isEmpty()) throw new IOException();
                    long total = 0;
                    for (Uri uri : new LinkedHashSet<>(selected)) {
                        if (uri == null || !"content".equals(uri.getScheme()) || uri.getAuthority() == null
                            || uri.getAuthority().replaceFirst("^.*@", "").equals(activity.getPackageName() + ".files")) throw new IOException();
                        String name = "attachment";
                        try (Cursor cursor = activity.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                        }
                        File folder = new File(batch, UUID.randomUUID().toString());
                        if (!folder.mkdir()) throw new IOException();
                        File copy = new File(folder, SelectionPolicy.safeName(name));
                        try (InputStream in = activity.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(copy)) {
                            if (in == null) throw new IOException();
                            total += SelectionPolicy.copy(in, out, Math.min(SelectionPolicy.MAX_FILE_BYTES, SelectionPolicy.MAX_BATCH_BYTES - total));
                        }
                        ready.add(FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", copy));
                    }
                }
                activity.runOnUiThread(() -> {
                    if (destroyed) { remove(batch); return; }
                    notice.accept("Files selected. Wait for the work item to confirm Saved or Pending upload.");
                    complete(ready.toArray(new Uri[0]));
                });
            } catch (Exception invalid) {
                remove(batch);
                activity.runOnUiThread(() -> { if (!destroyed) fail(); });
            }
        });
    }
    private void fail() {
        notice.accept("Could not select files. Use up to 10 files, 25 MB each (100 MB total). Try a locally available file or check device storage.");
        complete(null);
    }
    private void complete(Uri[] uris) {
        ValueCallback<Uri[]> done = callback;
        callback = null;
        if (uris == null && directory != null) remove(directory);
        cameraFile = null; cameraUri = null; directory = null;
        if (done != null) done.onReceiveValue(uris);
    }
    void destroy() {
        destroyed = true;
        if (dialog != null) dialog.dismiss();
        if (cameraUri != null) activity.revokeUriPermission(cameraUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        complete(null);
        worker.shutdownNow();
    }
    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        file.delete();
    }
}
