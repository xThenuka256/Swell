package com.swell.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * Hosts the Swell web app in a WebView. The app files ship inside the APK
 * (assets/), so the player, library and lyrics all work offline.
 * Assets are served from https://appassets.swell/ so the page gets a secure origin
 * (IndexedDB, fetch and media all behave like a normal website).
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.swell";
    private static final int REQ_FILES = 7;
    private static final String BG = "#0A1519";

    private WebView web;
    private ValueCallback<Uri[]> chooser;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Window w = getWindow();
        w.setStatusBarColor(Color.parseColor(BG));
        w.setNavigationBarColor(Color.parseColor(BG));

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor(BG));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (!HOST.equals(u.getHost())) return null;
                String p = u.getPath();
                if (p == null || p.equals("/")) p = "/index.html";
                try {
                    InputStream in = getAssets().open(p.substring(1));
                    return new WebResourceResponse(mime(p), "utf-8", in);
                } catch (IOException e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not found",
                            new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                String[] acc = params.getAcceptTypes();
                boolean audio = acc != null && acc.length > 0 && acc[0] != null && acc[0].startsWith("audio/");
                i.setType(audio ? "audio/*" : "*/*");
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(Intent.createChooser(i, "Choose files"), REQ_FILES);
                } catch (Exception e) {
                    chooser = null;
                    return false;
                }
                return true;
            }
        });

        if (state == null) {
            web.loadUrl("https://" + HOST + "/index.html");
        } else {
            web.restoreState(state);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILES) {
            if (chooser != null) {
                chooser.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
                chooser = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    // Deliberately no webView.onPause(): music keeps playing when you leave the app or lock the screen.

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    private static String mime(String p) {
        String x = p.toLowerCase();
        if (x.endsWith(".html")) return "text/html";
        if (x.endsWith(".js")) return "application/javascript";
        if (x.endsWith(".css")) return "text/css";
        if (x.endsWith(".json")) return "application/json";
        if (x.endsWith(".png")) return "image/png";
        if (x.endsWith(".svg")) return "image/svg+xml";
        if (x.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}
