package org.salabrowser.app;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String EMPTY_RESPONSE = "";
    private static final long CONTINUE_WATCHING_MS = 5 * 60 * 1000L;
    private static final long FULLSCREEN_CURSOR_TIMEOUT_MS = 10_000L;

    private WebView webView;
    private FrameLayout webContainer;
    private View cursorView;
    private ProgressBar progressBar;
    private FrameLayout root;
    private FrameLayout fullscreenContainer;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private HistoryStore historyStore;
    private final RequestBlocker requestBlocker = new RequestBlocker();
    private String homeHost;
    private boolean cursorEnabled = true;
    private float cursorX;
    private float cursorY;
    private final Handler watchHandler = new Handler(Looper.getMainLooper());
    private String trackedUrl;
    private String trackedTitle;
    private long trackedSince;
    private boolean trackedSaved;
    private final Runnable watchCheckpoint = this::checkpointWatchTime;
    private final Runnable hideFullscreenCursor = () -> {
        if (fullscreenView != null) {
            cursorView.setVisibility(View.GONE);
        }
    };

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
        root.post(() -> setCursorEnabled(true));
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
        toolbar.addView(toolbarButton("Sitio", view -> {
            webView.loadUrl(getString(R.string.site_url));
            setCursorEnabled(true);
        }));
        toolbar.addView(toolbarButton("Atrás", view -> navigateBack()));
        toolbar.addView(toolbarButton("Recargar", view -> webView.reload()));
        toolbar.addView(toolbarButton("Cursor", view -> setCursorEnabled(!cursorEnabled)));
        toolbar.addView(toolbarButton("Borrar", view -> {
            historyStore.clear();
            showLibrary();
            Toast.makeText(this, "Historial eliminado", Toast.LENGTH_SHORT).show();
        }));

        TextView title = new TextView(this);
        title.setText("Desarrollado MRIVAS · v" + getVersionName() + " · SALA");
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
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
        webView.setOnKeyListener((view, keyCode, event) -> handleRemoteKey(keyCode, event));

        webContainer = new FrameLayout(this);
        webContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        shell.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        shell.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);

        root.addView(shell);
        root.addView(fullscreenContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cursorView = new View(this);
        cursorView.setBackground(createCursorDrawable());
        cursorView.setElevation(dp(100));
        cursorView.setClickable(false);
        cursorView.setFocusable(false);
        FrameLayout.LayoutParams cursorParams = new FrameLayout.LayoutParams(dp(34), dp(34));
        root.addView(cursorView, cursorParams);
        setContentView(root);
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "?";
        }
    }

    private Button toolbarButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setFocusable(true);
        button.setOnKeyListener((view, keyCode, event) -> handleRemoteKey(keyCode, event));
        button.setOnClickListener(listener);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(14), 0, dp(14), 0);
        return button;
    }

    private android.graphics.drawable.Drawable createCursorDrawable() {
        android.graphics.drawable.GradientDrawable outer = new android.graphics.drawable.GradientDrawable();
        outer.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        outer.setColor(Color.argb(230, 229, 9, 20));
        outer.setStroke(dp(3), Color.WHITE);
        return outer;
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

    private void setCursorEnabled(boolean enabled) {
        cursorEnabled = enabled;
        if (cursorView == null) {
            return;
        }
        cursorView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (enabled) {
            root.post(() -> {
                if (cursorX == 0 && cursorY == 0) {
                    cursorX = root.getWidth() / 2f;
                    cursorY = root.getHeight() / 2f;
                }
                updateCursorPosition();
            });
            Toast.makeText(this, "Cursor: flechas para mover, OK para pulsar", Toast.LENGTH_SHORT).show();
        }
    }

    private void moveCursor(int keyCode, int repeatCount) {
        showCursorForInteraction();
        float step;
        if (repeatCount > 8) {
            step = dp(32);
        } else if (repeatCount > 3) {
            step = dp(22);
        } else {
            step = dp(14);
        }
        float edge = dp(18);
        float maxX = Math.max(edge, root.getWidth() - edge);
        float maxY = Math.max(edge, root.getHeight() - edge);
        float webTop = webContainer.getY();
        float webBottom = webTop + webContainer.getHeight();

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            cursorX -= step;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            cursorX += step;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (repeatCount > 0 && cursorY >= webTop && cursorY <= webTop + edge + step) {
                webView.scrollBy(0, -dp(180));
            } else {
                cursorY -= step;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (repeatCount > 0 && cursorY <= webBottom && cursorY >= webBottom - edge - step) {
                webView.scrollBy(0, dp(180));
            } else {
                cursorY += step;
            }
        }

        cursorX = Math.max(edge, Math.min(cursorX, maxX));
        cursorY = Math.max(edge, Math.min(cursorY, maxY));
        updateCursorPosition();
    }

    private void updateCursorPosition() {
        cursorView.setX(cursorX - cursorView.getWidth() / 2f);
        cursorView.setY(cursorY - cursorView.getHeight() / 2f);
        cursorView.bringToFront();
    }

    private void clickCursor() {
        showCursorForInteraction();
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, cursorX, cursorY, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, cursorX, cursorY, 0);
        cursorView.setVisibility(View.INVISIBLE);
        super.dispatchTouchEvent(down);
        super.dispatchTouchEvent(up);
        cursorView.setVisibility(View.VISIBLE);
        down.recycle();
        up.recycle();
        cursorView.animate().scaleX(0.72f).scaleY(0.72f).setDuration(70)
                .withEndAction(() -> cursorView.animate().scaleX(1f).scaleY(1f).setDuration(90))
                .start();
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
        stopTrackingPage();
        Uri uri = Uri.parse(url);
        String path = uri.getPath();
        if (isAllowedMainFrame(uri) && path != null && path.startsWith("/film/")) {
            trackedUrl = url;
            trackedTitle = title;
            trackedSince = SystemClock.elapsedRealtime();
            trackedSaved = false;
            watchHandler.postDelayed(watchCheckpoint, 30_000L);
        }
    }

    private void checkpointWatchTime() {
        if (trackedUrl == null || trackedSince == 0) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long total = historyStore.addWatchTime(trackedUrl, now - trackedSince);
        trackedSince = now;
        if (!trackedSaved && total >= CONTINUE_WATCHING_MS) {
            historyStore.save(trackedUrl, trackedTitle);
            trackedSaved = true;
            Toast.makeText(this, "Guardado en Seguir viendo", Toast.LENGTH_SHORT).show();
        }
        watchHandler.postDelayed(watchCheckpoint, 30_000L);
    }

    private void stopTrackingPage() {
        watchHandler.removeCallbacks(watchCheckpoint);
        if (trackedUrl != null && trackedSince != 0) {
            long now = SystemClock.elapsedRealtime();
            long total = historyStore.addWatchTime(trackedUrl, now - trackedSince);
            if (!trackedSaved && total >= CONTINUE_WATCHING_MS) {
                historyStore.save(trackedUrl, trackedTitle);
            }
        }
        trackedUrl = null;
        trackedTitle = null;
        trackedSince = 0;
        trackedSaved = false;
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
    protected void onPause() {
        stopTrackingPage();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String url = webView == null ? null : webView.getUrl();
        if (url != null && trackedUrl == null) {
            Uri uri = Uri.parse(url);
            String path = uri.getPath();
            if (isAllowedMainFrame(uri) && path != null && path.startsWith("/film/")) {
                trackedUrl = url;
                trackedTitle = webView.getTitle();
                trackedSince = SystemClock.elapsedRealtime();
                trackedSaved = false;
                watchHandler.postDelayed(watchCheckpoint, 30_000L);
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleRemoteKey(event.getKeyCode(), event)) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            navigateBack();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean handleRemoteKey(int keyCode, KeyEvent event) {
        if (isPhysicalMediaKey(keyCode)
                && (fullscreenView != null || trackedUrl != null)
                && handleMediaKey(keyCode, event)) {
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
            setCursorEnabled(!cursorEnabled);
            return true;
        }
        if (!cursorEnabled) {
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                moveCursor(keyCode, event.getRepeatCount());
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_A
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                clickCursor();
            }
            return true;
        }
        return false;
    }

    private boolean handleMediaKey(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) {
            return isMediaControlKey(keyCode);
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            String command = keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ? "play"
                    : keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ? "pause" : "toggle";
            sendPlayerCommand(command);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            sendPlayerCommand("forward");
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            sendPlayerCommand("rewind");
            return true;
        }
        return false;
    }

    private boolean isMediaControlKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    }

    private boolean isPhysicalMediaKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    }

    private void sendPlayerCommand(String command) {
        String script = "(function(){" +
                "var v=document.querySelector('video');" +
                "if(v){" +
                "if('" + command + "'==='toggle'){v.paused?v.play():v.pause();}" +
                "if('" + command + "'==='play'){v.play();}" +
                "if('" + command + "'==='pause'){v.pause();}" +
                "if('" + command + "'==='forward'){v.currentTime=Math.min(v.duration||1e9,v.currentTime+10);}" +
                "if('" + command + "'==='rewind'){v.currentTime=Math.max(0,v.currentTime-10);}" +
                "return true;}" +
                "var k='" + ("forward".equals(command) ? "ArrowRight" : "rewind".equals(command) ? "ArrowLeft" : " ") + "';" +
                "document.activeElement.dispatchEvent(new KeyboardEvent('keydown',{key:k,code:k,bubbles:true}));" +
                "document.activeElement.dispatchEvent(new KeyboardEvent('keyup',{key:k,code:k,bubbles:true}));" +
                "return false;})();";
        webView.evaluateJavascript(script, null);

        int forwardedKey = "forward".equals(command)
                ? KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                : "rewind".equals(command)
                ? KeyEvent.KEYCODE_MEDIA_REWIND
                : "play".equals(command)
                ? KeyEvent.KEYCODE_MEDIA_PLAY
                : "pause".equals(command)
                ? KeyEvent.KEYCODE_MEDIA_PAUSE
                : KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        if (fullscreenView != null) {
            fullscreenView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, forwardedKey));
            fullscreenView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, forwardedKey));
        }
        String message = "forward".equals(command) ? "+10 segundos"
                : "rewind".equals(command) ? "-10 segundos"
                : "play".equals(command) ? "Play"
                : "pause".equals(command) ? "Pausa" : "Play / Pausa";
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showCursorForInteraction() {
        if (!cursorEnabled) {
            return;
        }
        cursorView.setVisibility(View.VISIBLE);
        cursorView.bringToFront();
        watchHandler.removeCallbacks(hideFullscreenCursor);
        if (fullscreenView != null) {
            watchHandler.postDelayed(hideFullscreenCursor, FULLSCREEN_CURSOR_TIMEOUT_MS);
        }
    }

    @Override
    protected void onDestroy() {
        stopTrackingPage();
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
        watchHandler.removeCallbacks(hideFullscreenCursor);
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
        cursorView.setVisibility(cursorEnabled ? View.VISIBLE : View.GONE);
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
            Uri uri = Uri.parse(url);
            if (isAllowedMainFrame(uri) && uri.getPath() != null && !"/".equals(uri.getPath())) {
                setCursorEnabled(true);
            }
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
            showCursorForInteraction();
            watchHandler.postDelayed(() -> {
                if (fullscreenView != null) {
                    sendPlayerCommand("play");
                }
            }, 350L);
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
