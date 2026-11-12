package ro.alynsampmobile.game.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import org.json.JSONObject;

public class CefManager {
    private static final String SHIM =
        "window.cef={_h:{},emit:function(e,d){CefBridge.emit(e,typeof d==='string'?d:JSON.stringify(d));}," +
        "on:function(e,f){(this._h[e]=this._h[e]||[]).push(f);}," +
        "off:function(e){delete this._h[e];}," +
        "_dispatch:function(e,d){(this._h[e]||[]).forEach(function(f){f(d);});}};";

    private final Activity activity;
    private final UI ui;
    private final SparseArray<WebView> browsers = new SparseArray<>();

    public CefManager(Activity activity, UI ui) {
        this.activity = activity;
        this.ui = ui;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void create(final int id, final String url) {
        activity.runOnUiThread(() -> {
            destroyNow(id);

            WebView wv = new WebView(activity);
            wv.setBackgroundColor(Color.TRANSPARENT);
            wv.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            WebSettings s = wv.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

            wv.addJavascriptInterface(new Bridge(id), "CefBridge");
            wv.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView v, String u, android.graphics.Bitmap f) {
                    v.evaluateJavascript(SHIM, null);
                }
                @Override
                public void onPageFinished(WebView v, String u) {
                    v.evaluateJavascript(SHIM, null);
                }
            });

            ((ViewGroup) activity.findViewById(android.R.id.content)).addView(
                    wv, new FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            browsers.put(id, wv);
            wv.loadUrl(url);
        });
    }

    public void destroy(final int id) {
        activity.runOnUiThread(() -> destroyNow(id));
    }

    private void destroyNow(int id) {
        WebView wv = browsers.get(id);
        if (wv == null) return;
        browsers.remove(id);
        ((ViewGroup) wv.getParent()).removeView(wv);
        wv.destroy();
    }

    public void emit(final int id, final String event, final String data) {
        activity.runOnUiThread(() -> {
            WebView wv = browsers.get(id);
            if (wv == null) return;
            wv.evaluateJavascript("window.cef&&window.cef._dispatch("
                    + JSONObject.quote(event) + "," + JSONObject.quote(data) + ")", null);
        });
    }

    private class Bridge {
        private final int id;
        Bridge(int id) { this.id = id; }

        @JavascriptInterface
        public void emit(String event, String data) {
            ui.sendCefEvent(id, event, data);   // JS -> UI -> C++ -> server
        }
    }
}