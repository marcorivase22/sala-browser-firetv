package org.salabrowser.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String EMPTY_RESPONSE = "";

    private WebView webView;
    private ProgressBar progressBar;
    private FrameLayout root;
    private FrameLayout fullscreenContainer;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private HistoryStore historyStore;
    private final RequestBlocker requestBlocker = new RequestBlocker();
    private String homeHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        historyStore = new HistoryStore(this);
        homeHost = Uri.parse(getString(R.string.site_url)).getHost();
        buildInterface();
        configureWebView();

        if (savedInstanceState == null) {
            showLibrary();
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void buildInterface() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(8, 10, 15));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(8, 10, 15));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(8), dp(12), dp(8));
        toolbar.setBackgroundColor(Color.rgb(16, 19, 27));

        toolbar.addView(toolbarButton("Inicio", view -> showLibrary()));
        toolbar.addView(toolbarButton("Sitio", view -> webView.loadUrl(getString(R.string.site_url))));
        toolbar.addView(toolbarButton("Atrás", view -> navigateBack()));
        toolbar.addView(toolbarButton("Recargar", view -> webView.reload()));
        toolbar.addView(toolbarButton("Borrar", view -> {
            historyStore.clear();
            showLibrary();
            Toast.makeText(this, "Historial eliminado", Toast.LENGTH_SHORT).show();
        }));

        TextView title = new TextView(this);
        title.setText("SALA");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(8, 10, 15));
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);

        shell.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        shell.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);

        root.addView(shell);
        root.addView(fullscreenContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private Button toolbarButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setFocusable(true);
        button.setOnClickListener(listener);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(14), 0, dp(14), 0);
        return button;
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString(settings.getUserAgentString() + " SalaBrowser/0.1");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new GuardedWebViewClient());
        webView.setWebChromeClient(new VideoChromeClient());
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, length) ->
                Toast.makeText(this, "Descargas bloqueadas por seguridad", Toast.LENGTH_SHORT).show());
    }

    private void showLibrary() {
        List<WatchEntry> entries = historyStore.all();
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta name='viewport' content='width=device-width'>")
                .append("<style>")
                .append("body{margin:0;padding:32px;background:#080a0f;color:#fff;font-family:sans-serif}")
                .append("h1{font-size:32px;margin:0 0 6px}.muted{color:#9ba3b4;margin-bottom:28px}")
                .append(".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:16px}")
                .append("a{display:block;padding:22px;background:#151925;color:#fff;text-decoration:none;")
                .append("border-radius:12px;border:2px solid transparent;min-height:80px}")
                .append("a:focus{outline:none;border-color:#e50914;transform:scale(1.02)}")
                .append(".name{font-size:18px;font-weight:bold}.date{color:#9ba3b4;margin-top:12px;font-size:13px}")
                .append(".empty{padding:28px;background:#151925;border-radius:12px}")
                .append("</style></head><body><h1>Seguir viendo</h1>")
                .append("<div class='muted'>El historial permanece únicamente en este dispositivo.</div>");

        if (entries.isEmpty()) {
            html.append("<div class='empty'>Todavía no hay contenido. Abre “Sitio” y elige algo para comenzar.</div>");
        } else {
            html.append("<div class='grid'>");
            for (WatchEntry entry : entries) {
                html.append("<a href='").append(escape(entry.url)).append("'>")
                        .append("<div class='name'>").append(escape(entry.title)).append("</div>")
                        .append("<div class='date'>Continuar</div></a>");
            }
            html.append("</div>");
        }
        html.append("</body></html>");
        webView.loadDataWithBaseURL(getString(R.string.site_url), html.toString(), "text/html", "UTF-8", null);
    }

    private void navigateBack() {
        if (fullscreenView != null) {
            exitFullscreen();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            showLibrary();
        }
    }

    private boolean isAllowedMainFrame(Uri uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        return host != null && homeHost != null
                && (host.equalsIgnoreCase(homeHost)
                || host.toLowerCase(Locale.US).endsWith("." + homeHost.toLowerCase(Locale.US)));
    }

    private void rememberPage(String url, String title) {
        Uri uri = Uri.parse(url);
        String path = uri.getPath();
        if (isAllowedMainFrame(uri) && path != null && path.startsWith("/film/")) {
            historyStore.save(url, title);
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            navigateBack();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        webView.stopLoading();
        webView.destroy();
        super.onDestroy();
    }

    private void exitFullscreen() {
        if (fullscreenView == null) {
            return;
        }
        fullscreenContainer.removeView(fullscreenView);
        fullscreenContainer.setVisibility(View.GONE);
        fullscreenView = null;
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private final class GuardedWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (!request.isForMainFrame()) {
                return false;
            }
            Uri uri = request.getUrl();
            if (requestBlocker.shouldBlock(uri.toString()) || !isAllowedMainFrame(uri)) {
                Toast.makeText(MainActivity.this, "Redirección bloqueada", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (requestBlocker.shouldBlock(request.getUrl().toString())) {
                return new WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        new ByteArrayInputStream(EMPTY_RESPONSE.getBytes())
                );
            }
            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progressBar.setVisibility(View.GONE);
            rememberPage(url, view.getTitle());
            view.evaluateJavascript(
                    "(function(){window.open=function(){return null};" +
                    "document.querySelectorAll('a[target]').forEach(function(a){a.removeAttribute('target')});" +
                    "document.querySelectorAll('[onclick]').forEach(function(e){" +
                    "var v=(e.getAttribute('onclick')||'').toLowerCase();" +
                    "if(v.indexOf('window.open')>=0)e.removeAttribute('onclick')});})();",
                    null
            );
        }
    }

    private final class VideoChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int progress) {
            progressBar.setProgress(progress);
            progressBar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onCreateWindow(
                WebView view,
                boolean isDialog,
                boolean isUserGesture,
                android.os.Message resultMsg
        ) {
            Toast.makeText(MainActivity.this, "Popup bloqueado", Toast.LENGTH_SHORT).show();
            return false;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden();
                return;
            }
            fullscreenView = view;
            fullscreenCallback = callback;
            fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            fullscreenContainer.setVisibility(View.VISIBLE);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }
    }
}
