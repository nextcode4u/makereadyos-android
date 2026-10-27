package com.makereadyos.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.*;
import android.provider.DocumentsContract;
import android.webkit.WebView;
import androidx.core.content.FileProvider;
import androidx.webkit.*;
import org.json.JSONObject;
import java.io.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Narrow, origin-bound PDF handoff. No URL fetching, credentials, arbitrary paths or JS evaluation. */
final class ReportPdfDelivery {
    static final int SAVE_REQUEST = 742;
    private final Activity activity;
    private final Consumer<String> notice;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final File root;
    private volatile int generation;
    private volatile boolean destroyed;
    private boolean busy;
    private File report;
    private int reportPages;
    private File saving;
    private int saveGeneration;
    private AlertDialog dialog;

    ReportPdfDelivery(Activity activity, WebView web, String origin, Consumer<String> notice) {
        this.activity = activity; this.notice = notice;
        root = new File(activity.getCacheDir(), "reports");
        // The report is not an offline work record. Clear prior-process private copies on opening a workspace.
        remove(root); root.mkdirs();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web, "MakeReadyOSReports", Set.of(origin), (view, message, source, mainFrame, reply) -> {
                if (!mainFrame || !origin.equals(source.toString()) || destroyed) return;
                String url = view.getUrl();
                if (url == null || !(url.equals(origin + "/") || url.startsWith(origin + "/?"))) return;
                if (message.getType() != WebMessageCompat.TYPE_STRING) return;
                receive(message.getData(), reply);
            });
        }
    }
    private void receive(String raw, JavaScriptReplyProxy reply) {
        String id = "";
        try {
            if (raw == null || raw.length() > ReportPdfPolicy.MAX_BASE64 + 2048) throw new IllegalArgumentException();
            JSONObject input = new JSONObject(raw);
            if (input.getInt("version") != 1) throw new IllegalArgumentException();
            if (input.getString("action").equals("clear")) { clear(); return; }
            id = input.getString("id");
            if (!id.matches("[a-fA-F0-9-]{36}") || !input.getString("action").equals("report") || busy || saving != null) throw new IllegalArgumentException();
            String kind = input.getString("kind");
            String name = ReportPdfPolicy.filename(input.getString("filename"), kind);
            String encoded = input.getString("pdfBase64");
            clear(); busy = true;
            int ticket = generation;
            String requestId = id;
            worker.execute(() -> {
                File folder = new File(root, UUID.randomUUID().toString());
                File prepared = new File(folder, name);
                try {
                    byte[] pdf = ReportPdfPolicy.decode(encoded);
                    if (!folder.mkdirs()) throw new IOException();
                    try (FileOutputStream out = new FileOutputStream(prepared)) { out.write(pdf); }
                    final int pageCount;
                    try (ParcelFileDescriptor fd = ParcelFileDescriptor.open(prepared, ParcelFileDescriptor.MODE_READ_ONLY); PdfRenderer renderer = new PdfRenderer(fd)) {
                        pageCount = renderer.getPageCount();
                        if (pageCount < 1 || pageCount > 200) throw new IOException();
                    }
                    activity.runOnUiThread(() -> {
                        if (destroyed || ticket != generation) { remove(folder); return; }
                        busy = false; report = prepared; reportPages = pageCount;
                        respond(reply, requestId, "ready");
                        showActions(prepared, kind);
                    });
                } catch (Exception invalid) {
                    remove(folder);
                    activity.runOnUiThread(() -> {
                        if (!destroyed && ticket == generation) {
                            busy = false; respond(reply, requestId, "error");
                            notice.accept("Could not prepare this PDF. Nothing was exported.");
                        }
                    });
                }
            });
        } catch (Exception invalid) { respond(reply, id, "error"); }
    }
    private void respond(JavaScriptReplyProxy reply, String id, String status) {
        try { reply.postMessage(new JSONObject().put("id", id).put("status", status).toString()); }
        catch (Exception unavailable) { /* The originating page may have closed. */ }
    }
    private void showActions(File file, String kind) {
        String title = kind.equals("draft") ? "DRAFT PDF - not the resident copy" : "Resident final-walk PDF";
        dialog = new AlertDialog.Builder(activity).setTitle(title)
            .setItems(new String[]{"Save a copy...", "Open in PDF app...", "Share...", "Print..."}, (d, which) -> {
                if (which == 0) save(file);
                else if (which == 3) print(file);
                else confirmExternal(file, which == 2);
            }).setNegativeButton("Cancel", (d, which) -> clear())
            .setOnCancelListener(d -> clear()).show();
    }
    private void confirmExternal(File file, boolean share) {
        dialog = new AlertDialog.Builder(activity).setTitle(share ? "Share this report?" : "Open this report in another app?")
            .setMessage("This PDF may contain resident information and access codes. Only choose an authorized recipient or a trusted app. Exported copies cannot be recalled by MakeReadyOS.")
            .setPositiveButton("Continue", (d, which) -> external(file, share))
            .setNegativeButton("Cancel", (d, which) -> clear()).setOnCancelListener(d -> clear()).show();
    }
    private Uri uri(File file) { return FileProvider.getUriForFile(activity, activity.getPackageName() + ".files", file); }
    private void external(File file, boolean share) {
        try {
            Uri uri = uri(file);
            Intent intent = new Intent(share ? Intent.ACTION_SEND : Intent.ACTION_VIEW);
            if (share) intent.setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri);
            else intent.setDataAndType(uri, "application/pdf");
            intent.setClipData(ClipData.newRawUri(file.getName(), uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, share ? "Share PDF with" : "Open PDF with"));
            notice.accept("PDF handed to Android. Confirm the recipient or action in the selected app.");
        } catch (RuntimeException unavailable) {
            notice.accept("No compatible PDF app is available. Choose Save a copy instead.");
            showActions(file, file.getName().startsWith("DRAFT-") ? "draft" : "resident");
        }
    }
    @SuppressWarnings("deprecation")
    private void save(File file) {
        saving = file; saveGeneration = generation;
        try {
            activity.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/pdf").putExtra(Intent.EXTRA_TITLE, file.getName()), SAVE_REQUEST);
        } catch (RuntimeException unavailable) { saving = null; notice.accept("Could not open the save dialog. Nothing was exported."); }
    }
    void result(int resultCode, Intent data) {
        File file = saving; int ticket = saveGeneration; saving = null;
        if (file == null || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            clear();
            notice.accept(file == null && resultCode == Activity.RESULT_OK
                ? "The report closed before saving. Remove any empty destination file and generate the report again."
                : "Save cancelled. No copy was exported by MakeReadyOS.");
            return;
        }
        Uri destination = data.getData();
        if (!"content".equals(destination.getScheme()) || destination.getAuthority() == null
            || destination.getAuthority().replaceFirst("^.*@", "").equals(activity.getPackageName() + ".files")) {
            notice.accept("Invalid save destination. Nothing was exported."); return;
        }
        worker.execute(() -> {
            boolean success = false;
            try {
                if (destroyed || ticket != generation) throw new IOException();
                try (InputStream in = new FileInputStream(file); OutputStream out = activity.getContentResolver().openOutputStream(destination, "wt")) {
                    if (out == null) throw new IOException();
                    byte[] buffer = new byte[32768]; int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (destroyed || ticket != generation) throw new IOException();
                        out.write(buffer, 0, read);
                    }
                }
                success = true;
            } catch (Exception failure) {
                try { DocumentsContract.deleteDocument(activity.getContentResolver(), destination); } catch (Exception ignored) { }
            }
            boolean saved = success;
            activity.runOnUiThread(() -> {
                if (!destroyed) notice.accept(saved ? "PDF saved to the location you selected." : "PDF was not saved completely. Remove any incomplete destination file before retrying.");
            });
        });
    }
    private void print(File file) {
        try {
            int ticket = generation;
            int pageCount = reportPages;
            PrintManager manager = (PrintManager) activity.getSystemService(Activity.PRINT_SERVICE);
            notice.accept("Print options opened. Confirm the pages and printer in Android.");
            manager.print(file.getName(), new PrintDocumentAdapter() {
                @Override public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes, CancellationSignal cancel, LayoutResultCallback result, android.os.Bundle extras) {
                    if (cancel.isCanceled()) result.onLayoutCancelled();
                    else result.onLayoutFinished(new PrintDocumentInfo.Builder(file.getName()).setPageCount(pageCount).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(), !newAttributes.equals(oldAttributes));
                }
                @Override public void onWrite(PageRange[] pages, ParcelFileDescriptor destination, CancellationSignal cancel, WriteResultCallback result) {
                    if (destroyed) { result.onWriteFailed("Report closed."); return; }
                    worker.execute(() -> {
                        try (InputStream in = new FileInputStream(file); OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(destination)) {
                            byte[] buffer = new byte[32768]; int read;
                            while ((read = in.read(buffer)) != -1) {
                                if (cancel.isCanceled() || destroyed || ticket != generation) throw new IOException();
                                out.write(buffer, 0, read);
                            }
                        } catch (Exception failure) {
                            activity.runOnUiThread(() -> { if (cancel.isCanceled()) result.onWriteCancelled(); else result.onWriteFailed("Could not prepare the PDF for printing."); });
                            return;
                        }
                        // Declare the actual supplied range so Android can select the user's requested subset.
                        activity.runOnUiThread(() -> result.onWriteFinished(new PageRange[]{new PageRange(0, pageCount - 1)}));
                    });
                }
            }, null);
        } catch (RuntimeException unavailable) { notice.accept("Printing is unavailable. Save the PDF and print from a trusted PDF app."); }
    }
    void clear() {
        generation++; busy = false;
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        if (report != null) {
            activity.revokeUriPermission(uri(report), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            remove(report.getParentFile()); report = null;
        }
    }
    void destroy() { destroyed = true; clear(); worker.shutdownNow(); }
    private static void remove(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) remove(child);
        file.delete();
    }
}
