package org.sundayschool.attendance;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    private static final int FILE_CHOOSER_REQUEST = 2401;
    private static final int SAVE_FILE_REQUEST = 2402;

    private String pendingContent;
    private byte[] pendingBytes;

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

        // Enable WebView debugging
        WebView.setWebContentsDebuggingEnabled(true);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFFF4F7FB);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        Log.d(TAG, "WebView settings configured");

        webView.addJavascriptInterface(
            new AndroidBridge(),
            "Android"
        );

        // WEBVIEW CLIENT
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageStarted(
                WebView view,
                String url,
                android.graphics.Bitmap favicon
            ) {
                super.onPageStarted(view, url, favicon);

                Log.d(TAG, "Page started: " + url);
            }

            @Override
            public void onPageFinished(
                WebView view,
                String url
            ) {
                super.onPageFinished(view, url);

                Log.d(TAG, "Page finished: " + url);
            }

            @Override
            public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
            ) {
                super.onReceivedError(view, request, error);

                if (request.isForMainFrame()) {
                    Log.e(
                        TAG,
                        "MAIN PAGE ERROR: "
                            + error.getDescription()
                            + " | Code: "
                            + error.getErrorCode()
                            + " | URL: "
                            + request.getUrl()
                    );
                } else {
                    Log.e(
                        TAG,
                        "RESOURCE ERROR: "
                            + error.getDescription()
                            + " | URL: "
                            + request.getUrl()
                    );
                }
            }

            @Override
            public void onReceivedHttpError(
                WebView view,
                WebResourceRequest request,
                WebResourceResponse response
            ) {
                super.onReceivedHttpError(view, request, response);

                Log.e(
                    TAG,
                    "HTTP ERROR: "
                        + response.getStatusCode()
                        + " | URL: "
                        + request.getUrl()
                );
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                WebView view,
                WebResourceRequest request
            ) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();

                if (
                    "file".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme)
                    || "http".equalsIgnoreCase(scheme)
                ) {
                    return false;
                }

                try {
                    startActivity(
                        new Intent(Intent.ACTION_VIEW, uri)
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Unable to open external link", e);

                    Toast.makeText(
                        MainActivity.this,
                        "Unable to open link",
                        Toast.LENGTH_SHORT
                    ).show();
                }

                return true;
            }
        });

        // WEB CHROME CLIENT
        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onConsoleMessage(
                ConsoleMessage message
            ) {
                Log.e(
                    JS_TAG,
                    message.message()
                        + " | Line: "
                        + message.lineNumber()
                        + " | Source: "
                        + message.sourceId()
                        + " | Level: "
                        + message.messageLevel()
                );

                return true;
            }

            @Override
            public boolean onShowFileChooser(
                WebView view,
                ValueCallback<Uri[]> callback,
                FileChooserParams params
            ) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = callback;

                try {
                    startActivityForResult(
                        params.createIntent(),
                        FILE_CHOOSER_REQUEST
                    );

                    return true;

                } catch (Exception e) {
                    Log.e(TAG, "File chooser error", e);

                    fileCallback = null;

                    Toast.makeText(
                        MainActivity.this,
                        "Unable to open file picker",
                        Toast.LENGTH_SHORT
                    ).show();

                    return false;
                }
            }
        });

        setContentView(webView);

        Log.d(TAG, "Loading local HTML");

        webView.loadUrl(
            "file:///android_asset/index.html"
        );

        checkForUpdate();
    }

    // ANDROID JAVASCRIPT BRIDGE
    private class AndroidBridge {

        @JavascriptInterface
        public void saveTextFile(
            String filename,
            String content,
            String mimeType
        ) {
            runOnUiThread(() -> {

                pendingContent = content;

                try {
                    Intent i = new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                    );

                    i.addCategory(
                        Intent.CATEGORY_OPENABLE
                    );

                    i.setType(
                        mimeType == null
                            ? "text/plain"
                            : mimeType
                    );

                    i.putExtra(
                        Intent.EXTRA_TITLE,
                        filename
                    );

                    startActivityForResult(
                        i,
                        SAVE_FILE_REQUEST
                    );

                } catch (Exception e) {
                    Log.e(TAG, "Save text file error", e);

                    pendingContent = null;

                    Toast.makeText(
                        MainActivity.this,
                        "Unable to save file",
                        Toast.LENGTH_LONG
                    ).show();
                }
            });
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {

                try {
                    PrintManager manager =
                        (PrintManager) getSystemService(
                            Context.PRINT_SERVICE
                        );

                    PrintDocumentAdapter adapter =
                        webView.createPrintDocumentAdapter(
                            "Sunday_School_Attendance"
                        );

                    manager.print(
                        "Sunday School Attendance",
                        adapter,
                        null
                    );

                } catch (Exception e) {
                    Log.e(TAG, "Print error", e);

                    Toast.makeText(
                        MainActivity.this,
                        "Unable to print this page",
                        Toast.LENGTH_LONG
                    ).show();
                }
            });
        }

        @JavascriptInterface
        public void saveBase64File(
            String filename,
            String base64,
            String mimeType
        ) {
            runOnUiThread(() -> {

                try {
                    pendingBytes = Base64.decode(
                        base64,
                        Base64.DEFAULT
                    );

                    pendingContent = null;

                    Intent i = new Intent(
                        Intent.ACTION_CREATE_DOCUMENT
                    );

                    i.addCategory(
                        Intent.CATEGORY_OPENABLE
                    );

                    i.setType(
                        mimeType == null
                            ? "application/octet-stream"
                            : mimeType
                    );

                    i.putExtra(
                        Intent.EXTRA_TITLE,
                        filename
                    );

                    startActivityForResult(
                        i,
                        SAVE_FILE_REQUEST
                    );

                } catch (Exception e) {
                    Log.e(TAG, "Save Base64 file error", e);

                    pendingBytes = null;

                    Toast.makeText(
                        MainActivity.this,
                        "Unable to prepare Excel file",
                        Toast.LENGTH_LONG
                    ).show();
                }
            });
        }
    }

    // GITHUB UPDATE CHECK
    private void checkForUpdate() {

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {
                connection = (HttpURLConnection)
                    new URL(GITHUB_RELEASES_API).openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);

                connection.setRequestProperty(
                    "Accept",
                    "application/vnd.github+json"
                );

                int responseCode =
                    connection.getResponseCode();

                Log.d(
                    TAG,
                    "GitHub update response: " + responseCode
                );

                if (responseCode != 200) {
                    return;
                }

                ByteArrayOutputStream buffer =
                    new ByteArrayOutputStream();

                try (InputStream in = connection.getInputStream()) {

                    byte[] chunk = new byte[4096];

                    int count;

                    while ((count = in.read(chunk)) != -1) {
                        buffer.write(chunk, 0, count);
                    }
                }

                JSONObject release = new JSONObject(
                    new String(
                        buffer.toByteArray(),
                        StandardCharsets.UTF_8
                    )
                );

                String tag = release.optString(
                    "tag_name",
                    ""
                );

                String page = release.optString(
                    "html_url",
                    ""
                );

                if (
                    !tag.isEmpty()
                    && !tag.equalsIgnoreCase("v" + CURRENT_VERSION)
                    && !tag.equals(CURRENT_VERSION)
                ) {

                    runOnUiThread(() ->
                        new AlertDialog.Builder(MainActivity.this)

                            .setTitle("Update available")

                            .setMessage(
                                "A newer Sunday School Attendance version ("
                                    + tag
                                    + ") is available. Open GitHub Releases to download it?"
                            )

                            .setNegativeButton(
                                "Later",
                                (dialog, which) -> dialog.dismiss()
                            )

                            .setPositiveButton(
                                "View update",
                                (dialog, which) -> {
                                    try {
                                        startActivity(
                                            new Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(page)
                                            )
                                        );
                                    } catch (Exception e) {
                                        Log.e(TAG, "Update link error", e);
                                    }
                                }
                            )

                            .show()
                    );
                }

            } catch (Exception e) {
                Log.e(TAG, "GitHub update check failed", e);

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }

    // FILE RESULT
    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        );

        if (
            requestCode == FILE_CHOOSER_REQUEST
            && fileCallback != null
        ) {
            Uri[] result = null;

            if (
                resultCode == RESULT_OK
                && data != null
                && data.getData() != null
            ) {
                result = new Uri[]{
                    data.getData()
                };
            }

            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }

        if (requestCode == SAVE_FILE_REQUEST) {

            if (
                resultCode == RESULT_OK
                && data != null
                && data.getData() != null
            ) {
                try (
                    OutputStream out =
                        getContentResolver().openOutputStream(
                            data.getData()
                        )
                ) {
                    if (out == null) {
                        throw new IOException(
                            "Cannot open file"
                        );
                    }

                    if (pendingBytes != null) {
                        out.write(pendingBytes);
                    } else {
                        out.write(
                            (
                                pendingContent == null
                                    ? ""
                                    : pendingContent
                            ).getBytes(StandardCharsets.UTF_8)
                        );
                    }

                    out.flush();

                    Toast.makeText(
                        this,
                        "File saved successfully",
                        Toast.LENGTH_LONG
                    ).show();

                } catch (Exception e) {
                    Log.e(TAG, "File writing error", e);

                    Toast.makeText(
                        this,
                        "Unable to write file",
                        Toast.LENGTH_LONG
                    ).show();
                }
            }

            pendingContent = null;
            pendingBytes = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (
            webView != null
            && webView.canGoBack()
        ) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }

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
