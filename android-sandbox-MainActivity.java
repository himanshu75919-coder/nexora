package com.nexora.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;

/**
 * NEXORA native shell v2 (1.2.0)
 * REMOTE-FIRST architecture:
 *   - Loads the LIVE hosted app UI (https) directly -> same-origin config fetches,
 *     service-worker offline cache, zero CORS issues.
 *   - If the main frame cannot be reached (no internet / server down), falls back to a
 *     bundled offline shell (assets/app.html) with RETRY that relaunches the remote UI.
 *   - Secure WebView: no file/content access for remote pages, safe browsing, mixed
 *     content blocked, unknown schemes fail-closed. No credentials collected.
 */
public class MainActivity extends Activity {

    private static final String BASE = "https://nexora-admin-iota.vercel.app";
    private static final String REMOTE = BASE + "/app";
    private static final String LOCAL_FALLBACK = "file:///android_asset/app.html";

    private WebView wv;
    private boolean onFallback = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        wv = new WebView(this);
        wv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(wv);

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSafeBrowsingEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        wv.setBackgroundColor(Color.parseColor("#0b0714"));

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme() == null ? "" : request.getUrl().getScheme();
                // http(s): render in-app (internal browser). mail/tel/sms/intent: hand to OS.
                // everything else: fail closed.
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                if ("mailto".equals(scheme) || "tel".equals(scheme) || "sms".equals(scheme)) {
                    try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, request.getUrl())); } catch (Exception ignored) {}
                    return true;
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req != null && req.isForMainFrame() && !onFallback && "file".equals(req.getUrl().getScheme()) == false) {
                    // Remote UI unreachable -> bundled offline shell (keeps retry UX native)
                    onFallback = true;
                    view.post(new Runnable() { public void run() { wv.loadUrl(LOCAL_FALLBACK); } });
                }
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
                if ("nexora_setup".equals(message)) {
                    final EditText input = new EditText(MainActivity.this);
                    input.setSingleLine(true);
                    input.setHint("https://your-nexora-api-url");
                    input.setText(defaultValue == null ? BASE : defaultValue);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("NEXORA API URL")
                            .setMessage("Apna NEXORA backend URL daalo:")
                            .setView(input)
                            .setPositiveButton("Save", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int wch) {
                                String v = input.getText().toString().trim().replaceAll("/+$", "");
                                if (!v.startsWith("http")) v = "https://" + v;
                                onFallback = false;
                                wv.loadUrl(v + "/app");
                                result.confirm();
                            } })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int wch) { result.cancel(); } })
                            .show();
                    return true;
                }
                result.cancel();
                return true;
            }
        });

        wv.addJavascriptInterface(new Bridge(), "NexoraNative");
        wv.loadUrl(REMOTE);
    }

    public class Bridge {
        @JavascriptInterface
        public String platform() { return "android"; }
        @JavascriptInterface
        public String version() { return "1.2.0"; }
        /** Called by the offline shell's RETRY button to relaunch the remote UI. */
        @JavascriptInterface
        public void reloadOnline() {
            runOnUiThread(new Runnable() { public void run() { onFallback = false; wv.loadUrl(REMOTE); } });
        }
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) wv.goBack();
        else super.onBackPressed();
    }
}
