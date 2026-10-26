package org.sundayschool.attendance;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.ValueCallback;
import android.webkit.JavascriptInterface;
import android.widget.Toast;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;
import android.graphics.Bitmap;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    private static final int FILE_CHOOSER_REQUEST = 2401;
    private static final int SAVE_FILE_REQUEST = 2402;

    private String pendingContent;
    private String pendingFilename;
    private String pendingMimeType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(0xFF174EA6);
        getWindow().setNavigationBarColor(0xFF174EA6);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFFF4F7FB);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, WebResourceRequest request) {
                return !"file".equalsIgnoreCase(
                        request.getUrl().getScheme());
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params) {

                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }

                fileCallback = callback;

                try {
                    startActivityForResult(
                            params.createIntent(),
                            FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(
                            MainActivity.this,
                            "Unable to open file picker",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class AndroidBridge {

        @JavascriptInterface
        public void saveTextFile(
                String filename, String content, String mimeType) {

            runOnUiThread(() -> {
                pendingFilename = filename;
                pendingContent = content;
                pendingMimeType = mimeType;

                try {
                    Intent intent = new Intent(
                            Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(
                            mimeType == null
                                    ? "text/plain"
                                    : mimeType);
                    intent.putExtra(
                            Intent.EXTRA_TITLE, filename);

                    startActivityForResult(
                            intent, SAVE_FILE_REQUEST);

                } catch (Exception e) {
                    clearPendingFile();
                    Toast.makeText(
                            MainActivity.this,
                            "Unable to save file",
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager manager =
                            (PrintManager) getSystemService(
                                    Context.PRINT_SERVICE);

                    PrintDocumentAdapter adapter =
                            webView.createPrintDocumentAdapter(
                                    "Sunday_School_Attendance");

                    manager.print(
                            "Sunday School Attendance",
                            adapter, null);

                } catch (Exception e) {
                    Toast.makeText(
                            MainActivity.this,
                            "Unable to print this page",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivity
