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
import android.webkit.DownloadListener;
import android.widget.Toast;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;
import android.view.ViewGroup;
import android.graphics.Bitmap;
import android.os.Message;

public class MainActivity extends Activity {

    private WebView webView;
    private WebView printWebView;
    private ValueCallback<Uri[]> fileCallback;

    private static final int FILE_CHOOSER_REQUEST = 2401;

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
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                Uri uri = request.getUrl();

                // Keep app files local.
                return !"file".equalsIgnoreCase(uri.getScheme());
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
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
                    Intent intent = params.createIntent();
                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );
                    return true;

                } catch (Exception e) {
                    fileCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "Unable to open file picker",
                            Toast.LENGTH_SHORT
                    ).show();

                    return false;
                }
            }

            @Override
            public boolean onCreateWindow(
                    WebView view,
                    boolean isDialog,
                    boolean isUserGesture,
                    Message resultMsg) {

                WebView popup = new WebView(MainActivity.this);

                WebSettings popupSettings = popup.getSettings();
                popupSettings.setJavaScriptEnabled(true);
                popupSettings.setDomStorageEnabled(true);
                popupSettings.setAllowFileAccess(true);

                popup.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request) {

                        String url = request.getUrl().toString();

                        if (url.startsWith("file:///")) {
                            view.loadUrl(url);
                        }

                        return true;
                    }
                });

                WebView.WebViewTransport transport =
                        (WebView.WebViewTransport) resultMsg.obj;

                transport.setWebView(popup);
                resultMsg.sendToTarget();

                return true;
            }
        });

        // Handle file downloads, including CSV links.
        webView.setDownloadListener(
                new DownloadListener() {
                    @Override
                    public void onDownloadStart(
                            String url,
                            String userAgent,
                            String contentDisposition,
                            String mimeType,
                            long contentLength) {

                        try {
                            if (url.startsWith("data:")) {
                                Toast.makeText(
                                        MainActivity.this,
                                        "Preparing file...",
                                        Toast.LENGTH_SHORT
                                ).show();
                            } else {
                                Intent intent =
                                        new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse(url));
                                startActivity(intent);
                            }

                        } catch (Exception e) {
                            Toast.makeText(
                                    MainActivity.this,
                                    "Unable to download this file.",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
                }
        );

        setContentView(webView);

        webView.loadUrl("file:///android_asset/index.html");
    }

    // Called from JavaScript to print the current page.
    @android.webkit.JavascriptInterface
    public void printPage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    PrintManager printManager =
                            (PrintManager) getSystemService(
                                    Context.PRINT_SERVICE
                            );

                    PrintDocumentAdapter adapter =
                            webView.createPrintDocumentAdapter(
                                    "Sunday_School_Attendance"
                            );

                    printManager.print(
                            "Sunday School Attendance",
                            adapter,
                            null
                    );

                } catch (Exception e) {
                    Toast.makeText(
                            MainActivity.this,
                            "Unable to print this page.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST &&
                fileCallback != null) {

            Uri[] results = null;

            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();

                if (uri != null) {
                    results = new Uri[]{uri};
                }
            }

            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
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
