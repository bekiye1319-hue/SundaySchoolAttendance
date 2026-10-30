package org.sundayschool.attendance;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.ProgressBar;
import android.view.View;
import android.view.MotionEvent;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;

import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private PullToRefreshWebView webView;
    private FrameLayout rootLayout;
    private ProgressBar refreshProgress;
    private ValueCallback<Uri[]> fileCallback;

    private static final int FILE_CHOOSER_REQUEST = 2401;
    private static final int SAVE_FILE_REQUEST = 2402;

    private String pendingContent;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTimeout = () -> finishRefresh();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(0xFF174EA6);
        getWindow().setNavigationBarColor(0xFF174EA6);

        rootLayout = new FrameLayout(this);

        webView = new PullToRefreshWebView(this);
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
                Uri uri = request.getUrl();

                // Keep local app files and HTTPS web content inside the WebView.
                // Open other external schemes with Android's matching app.
                String scheme = uri.getScheme();
                if ("file".equalsIgnoreCase(scheme) ||
                        "https".equalsIgnoreCase(scheme) ||
                        "http".equalsIgnoreCase(scheme)) {
                    return false;
                }

                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                    Toast.makeText(MainActivity.this,
                            "No app can open this link",
                            Toast.LENGTH_SHORT).show();
                }
                return true;
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
                            params.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this,
                            "Unable to open file picker",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        refreshProgress = new ProgressBar(this);
        refreshProgress.setIndeterminate(true);
        refreshProgress.setVisibility(View.GONE);

        FrameLayout.LayoutParams progressParams =
                new FrameLayout.LayoutParams(
                        dp(36), dp(36), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        progressParams.topMargin = dp(12);

        rootLayout.addView(webView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
        rootLayout.addView(refreshProgress, progressParams);

        setContentView(rootLayout);
        webView.setRefreshListener(this::startRefresh);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void startRefresh() {
        if (refreshProgress.getVisibility() == View.VISIBLE) return;

        refreshProgress.setVisibility(View.VISIBLE);

        // The HTML app should define window.refreshAppData() to reload Firestore data.
        // If it is not defined, reload the local app page as a fallback.
        webView.evaluateJavascript(
                "(function(){if(typeof window.refreshAppData==='function')" +
                "{window.refreshAppData();return 'refresh-called';}" +
                "window.location.reload();return 'page-reloaded';})()",
                null);

        handler.removeCallbacks(refreshTimeout);
        handler.postDelayed(refreshTimeout, 20000);
    }

    private void finishRefresh() {
        handler.removeCallbacks(refreshTimeout);
        if (refreshProgress != null) {
            refreshProgress.setVisibility(View.GONE);
        }
        if (webView != null) {
            webView.setRefreshing(false);
        }
    }

    private class AndroidBridge {

        @JavascriptInterface
        public void refreshFinished() {
            runOnUiThread(() -> finishRefresh());
        }

        @JavascriptInterface
        public void saveTextFile(
                String filename, String content, String mimeType) {

            runOnUiThread(() -> {
                pendingContent = content;

                try {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(
                            mimeType == null ? "text/plain" : mimeType);
                    intent.putExtra(Intent.EXTRA_TITLE, filename);
                    startActivityForResult(intent, SAVE_FILE_REQUEST);
                } catch (Exception e) {
                    pendingContent = null;
                    Toast.makeText(MainActivity.this,
                            "Unable to save file",
                            Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface
        public void printPage() {
            runOnUiThread(() -> {
                try {
                    PrintManager manager = (PrintManager)
                            getSystemService(Context.PRINT_SERVICE);
                    PrintDocumentAdapter adapter =
                            webView.createPrintDocumentAdapter(
                                    "Sunday_School_Attendance");
                    manager.print(
                            "Sunday School Attendance", adapter, null);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this,
                            "Unable to print this page",
                            Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private class PullToRefreshWebView extends WebView {
        private float startY;
        private boolean refreshing = false;
        private Runnable refreshListener;

        public PullToRefreshWebView(Context context) {
            super(context);
        }

        public void setRefreshListener(Runnable listener) {
            refreshListener = listener;
        }

        public void setRefreshing(boolean value) {
            refreshing = value;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startY = event.getY();
                    break;

                case MotionEvent.ACTION_UP:
                    float distance = event.getY() - startY;
                    if (!refreshing && getScrollY() <= 0 &&
                            distance > dp(110)) {
                        refreshing = true;
                        if (refreshListener != null) {
                            refreshListener.run();
                        }
                    }
                    break;
            }
            return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == FILE_CHOOSER_REQUEST && fileCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null &&
                    data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
        }

        if (requestCode == SAVE_FILE_REQUEST) {
            if (resultCode == RESULT_OK && data != null &&
                    data.getData() != null) {
                Uri uri = data.getData();

                try (OutputStream output =
                             getContentResolver().openOutputStream(uri)) {
                    if (output == null) {
                        throw new IOException("Cannot open file");
                    }
                    String content = pendingContent == null ? "" : pendingContent;
                    output.write(content.getBytes(StandardCharsets.UTF_8));
                    output.flush();

                    Toast.makeText(this, "File saved successfully",
                            Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Unable to write file",
                            Toast.LENGTH_LONG).show();
                }
            }
            pendingContent = null;
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
        handler.removeCallbacks(refreshTimeout);

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
