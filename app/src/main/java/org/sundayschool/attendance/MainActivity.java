package org.sundayschool.attendance;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;

import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private static final String TAG = "SS_APP";
    private static final String JS_TAG = "SS_APP_JS";
    private static final int FILE_CHOOSER_REQUEST = 2401;
    private static final int SAVE_FILE_REQUEST = 2402;
    private static final int DOWNLOAD_NOTIFICATION_PERMISSION_REQUEST = 2403;
    private static final String DOWNLOAD_CHANNEL_ID = "ss_downloads";

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingContent;
    private byte[] pendingBytes;
    private String pendingDownloadCallbackId;

    private static final String GITHUB_RELEASES_API =
        "https://api.github.com/repos/bekiye1319-hue/SundaySchoolAttendance/releases/latest";
    private static final String CURRENT_VERSION = "1.0";
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "App started");
        getWindow().setStatusBarColor(0xFF174EA6);
        getWindow().setNavigationBarColor(0xFF174EA6);
        WebView.setWebContentsDebuggingEnabled(false);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFFF4F7FB);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true); // Required for bundled file:///android_asset/index.html
        settings.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        Log.d(TAG, "WebView settings configured");
        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.d(TAG, "Page started: " + url);
            }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "Page finished: " + url);
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) Log.e(TAG, "MAIN PAGE ERROR: " + error.getDescription() + " | Code: " + error.getErrorCode() + " | URL: " + request.getUrl());
                else Log.e(TAG, "RESOURCE ERROR: " + error.getDescription() + " | URL: " + request.getUrl());
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                super.onReceivedHttpError(view, request, response);
                Log.e(TAG, "HTTP ERROR: " + response.getStatusCode() + " | URL: " + request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                // Keep the Android-JS bridge inside the bundled local app. Open web links
                // in the external browser so untrusted pages never share this WebView.
                if ("file".equalsIgnoreCase(scheme)
                        && uri.getPath() != null
                        && uri.getPath().startsWith("/android_asset/")) return false;
                if ("https".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception e) {
                        Log.e(TAG, "Unable to open external link", e);
                        Toast.makeText(MainActivity.this, "Unable to open link", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                Toast.makeText(MainActivity.this, "Blocked unsupported or insecure link", Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                Log.e(JS_TAG, message.message() + " | Line: " + message.lineNumber() + " | Source: " + message.sourceId() + " | Level: " + message.messageLevel());
                return true;
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "File chooser error", e);
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Unable to open file picker", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        setContentView(webView);
        ensureDownloadNotificationPermission();
        Log.d(TAG, "Loading local HTML");
        webView.loadUrl("file:///android_asset/index.html");
        checkForUpdate();
    }

    private class AndroidBridge {
        // Backward-compatible entry point for older HTML calls.
        @JavascriptInterface public void saveTextFile(String filename, String content, String mimeType) {
            saveDownload(filename, content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8), mimeType, null);
        }
        @JavascriptInterface public void saveBase64File(String filename, String base64, String mimeType) {
            try { saveDownload(filename, Base64.decode(base64, Base64.DEFAULT), mimeType, null); }
            catch (Exception e) { Log.e(TAG, "Base64 decode failed", e); showSaveResult(null, false, "Invalid Base64 file data"); }
        }
        // Promise-aware methods used by the updated HTML. Success is reported only after bytes are written.
        @JavascriptInterface public void saveTextFileWithCallback(String filename, String content, String mimeType, String callbackId) {
            saveDownload(filename, content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8), mimeType, callbackId);
        }
        @JavascriptInterface public void saveBase64FileWithCallback(String filename, String base64, String mimeType, String callbackId) {
            try { saveDownload(filename, Base64.decode(base64, Base64.DEFAULT), mimeType, callbackId); }
            catch (Exception e) { Log.e(TAG, "Base64 decode failed", e); showSaveResult(callbackId, false, "Invalid Base64 file data"); }
        }
        @JavascriptInterface public void printPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager manager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                    PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter("Sunday_School_Attendance");
                    manager.print("Sunday School Attendance", adapter, null);
                } catch (Exception e) {
                    Log.e(TAG, "Print error", e);
                    Toast.makeText(MainActivity.this, "Unable to print this page", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void saveDownload(String filename, byte[] bytes, String mimeType, String callbackId) {
        final String safeName = (filename == null || filename.trim().isEmpty()) ? "download" : filename.replaceAll("[\\/\\:*?\"<>|]", "_");
        final String type = (mimeType == null || mimeType.trim().isEmpty()) ? "application/octet-stream" : mimeType;
        new Thread(() -> {
            Uri savedUri = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
                    values.put(MediaStore.Downloads.MIME_TYPE, type);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SundaySchoolAttendance");
                    values.put(MediaStore.Downloads.IS_PENDING, 1);
                    Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    savedUri = getContentResolver().insert(collection, values);
                    if (savedUri == null) throw new IOException("Could not create Downloads file");
                    try (OutputStream out = getContentResolver().openOutputStream(savedUri, "w")) {
                        if (out == null) throw new IOException("Could not open Downloads file");
                        out.write(bytes);
                        out.flush();
                    }
                    ContentValues complete = new ContentValues();
                    complete.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(savedUri, complete, null, null);
                    Log.d(TAG, "Saved to Downloads: " + savedUri);
                    showSaveResult(callbackId, true, "Saved to Downloads/SundaySchoolAttendance");
                } else {
                    // Older Android versions use the system document picker because scoped Downloads access is unavailable.
                    pendingBytes = bytes;
                    pendingContent = null;
                    pendingDownloadCallbackId = callbackId;
                    runOnUiThread(() -> {
                        try {
                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                            intent.setType(type);
                            intent.putExtra(Intent.EXTRA_TITLE, safeName);
                            startActivityForResult(intent, SAVE_FILE_REQUEST);
                        } catch (Exception e) {
                            Log.e(TAG, "Legacy save picker error", e);
                            pendingBytes = null;
                            pendingDownloadCallbackId = null;
                            showSaveResult(callbackId, false, "Unable to open save location");
                        }
                    });
                }
            } catch (Exception e) {
                if (savedUri != null) {
                    try { getContentResolver().delete(savedUri, null, null); } catch (Exception ignored) { }
                }
                Log.e(TAG, "Downloads write failed", e);
                showSaveResult(callbackId, false, e.getMessage() == null ? "File write failed" : e.getMessage());
            }
        }).start();
    }

    private void ensureDownloadNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, DOWNLOAD_NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    private void postDownloadNotification(String filename, Uri savedUri) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    DOWNLOAD_CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Notifications when Sunday School Attendance files are saved");
                manager.createNotificationChannel(channel);
            }
            Intent openFile = new Intent(Intent.ACTION_VIEW);
            openFile.setDataAndType(savedUri, getContentResolver().getType(savedUri));
            openFile.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            PendingIntent pending = PendingIntent.getActivity(
                this, (int) (System.currentTimeMillis() & 0x0fffffff), openFile,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, DOWNLOAD_CHANNEL_ID)
                : new Notification.Builder(this);
            builder.setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download complete")
                .setContentText(filename + " saved in Downloads/SundaySchoolAttendance")
                .setStyle(new Notification.BigTextStyle().bigText(filename + " was saved in Downloads/SundaySchoolAttendance."))
                .setContentIntent(pending).setAutoCancel(true)
                .setCategory(Notification.CATEGORY_PROGRESS).setPriority(Notification.PRIORITY_DEFAULT);
            if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), builder.build());
            } else {
                Log.w(TAG, "Notification permission denied; file is saved but notification is unavailable.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not show download notification", e);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == DOWNLOAD_NOTIFICATION_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Download notification permission: " + (granted ? "granted" : "denied"));
            if (!granted) Toast.makeText(this,
                "Download notifications are disabled. Files will still be saved in Downloads.", Toast.LENGTH_LONG).show();
        }
    }

    private void showSaveResult(String callbackId, boolean success, String message) {
        runOnUiThread(() -> {
            if (success) Toast.makeText(this, "Saved in Downloads/SundaySchoolAttendance", Toast.LENGTH_LONG).show();
            else Toast.makeText(this, "Download failed: " + message, Toast.LENGTH_LONG).show();
            if (callbackId != null && webView != null) {
                String id = JSONObject.quote(callbackId);
                String msg = JSONObject.quote(message == null ? "" : message);
                webView.evaluateJavascript("window.onAndroidDownloadResult && window.onAndroidDownloadResult(" + id + "," + success + "," + msg + ");", null);
            }
        });
    }

    private void checkForUpdate() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(GITHUB_RELEASES_API).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                int responseCode = connection.getResponseCode();
                Log.d(TAG, "GitHub update response: " + responseCode);
                if (responseCode != 200) return;
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] chunk = new byte[4096]; int count;
                    while ((count = in.read(chunk)) != -1) buffer.write(chunk, 0, count);
                }
                JSONObject release = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
                String tag = release.optString("tag_name", "");
                String page = release.optString("html_url", "");
                if (!tag.isEmpty() && !tag.equalsIgnoreCase("v" + CURRENT_VERSION) && !tag.equals(CURRENT_VERSION)) {
                    runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Update available")
                        .setMessage("A newer Sunday School Attendance version (" + tag + ") is available. Open GitHub Releases to download it?")
                        .setNegativeButton("Later", (dialog, which) -> dialog.dismiss())
                        .setPositiveButton("View update", (dialog, which) -> {
                            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(page))); }
                            catch (Exception e) { Log.e(TAG, "Update link error", e); }
                        }).show());
                }
            } catch (Exception e) { Log.e(TAG, "GitHub update check failed", e); }
            finally { if (connection != null) connection.disconnect(); }
        }).start();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
        if (requestCode == SAVE_FILE_REQUEST) {
            String callbackId = pendingDownloadCallbackId;
            boolean success = false;
            String message = "Save cancelled";
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new IOException("Cannot open selected file");
                    if (pendingBytes != null) out.write(pendingBytes);
                    else out.write((pendingContent == null ? "" : pendingContent).getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    success = true;
                    message = "File saved";
                } catch (Exception e) {
                    Log.e(TAG, "File writing error", e);
                    message = e.getMessage() == null ? "Unable to write file" : e.getMessage();
                }
            }
            pendingContent = null;
            pendingBytes = null;
            pendingDownloadCallbackId = null;
            showSaveResult(callbackId, success, message);
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (webView != null) {
            webView.removeJavascriptInterface("Android");
            webView.loadUrl("about:blank");
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
