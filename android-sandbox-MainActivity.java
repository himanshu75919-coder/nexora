package com.nexora.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.webkit.CookieManager;
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
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;

/**
 * NEXORA native shell v3 (1.3.0)
 * - REMOTE-FIRST: loads the hosted app UI (same-origin config, SW offline cache)
 * - OFFLINE-GUARANTEED: main-frame failure -> bundled fallback catalog (always shows cards)
 * - IN-APP UPDATES (Play-Store-style): on launch checks /api/update; if a newer
 *   version_code exists, shows a dialog and downloads the APK via DownloadManager,
 *   then hands it to the system installer (user approval per Android policy).
 * - Secure WebView: no file/content access, safe browsing, mixed content blocked,
 *   unknown schemes fail-closed, no credentials collected.
 */
public class MainActivity extends Activity {

    private static final String DEFAULT_BASE = "https://nexora-admin-iota.vercel.app";
    private static final String REMOTE = DEFAULT_BASE + "/app";
    private static final String LOCAL_FALLBACK = "file:///android_asset/fallback.html";
    private static final long INSTALLED_CODE = 3;

    private WebView wv;
    private boolean onFallback = false;
    private long downloadId = -1;

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
        CookieManager.getInstance().setAcceptCookie(true);

        wv.setBackgroundColor(Color.parseColor("#0b0714"));

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme() == null ? "" : request.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) return false;
                if ("mailto".equals(scheme) || "tel".equals(scheme) || "sms".equals(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl())); } catch (Exception ignored) {}
                    return true;
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req != null && req.isForMainFrame() && !onFallback && !"file".equals(req.getUrl().getScheme())) {
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
                    input.setText(defaultValue == null ? DEFAULT_BASE : defaultValue);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("NEXORA API URL")
                            .setMessage("Apna NEXORA backend URL daalo:")
                            .setView(input)
                            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int wch) {
                                    String v = input.getText().toString().trim().replaceAll("/+$", "");
                                    if (!v.startsWith("http")) v = "https://" + v;
                                    onFallback = false;
                                    wv.loadUrl(v + "/app");
                                    result.confirm();
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int wch) { result.cancel(); }
                            })
                            .show();
                    return true;
                }
                result.cancel();
                return true;
            }
        });

        wv.addJavascriptInterface(new Bridge(), "NexoraNative");
        wv.loadUrl(REMOTE);

        // register download-complete receiver for in-app update install
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) promptInstall();
            }
        }, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));

        // non-blocking update check after 6s (never slows first paint)
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() { new Thread(new Runnable() { public void run() { checkUpdate(); } }).start(); }
        }, 6000);
    }

    /* ---------------- IN-APP UPDATE ---------------- */
    private void checkUpdate() {
        try {
            URL u = new URL(DEFAULT_BASE + "/api/update");
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(8000); c.setReadTimeout(8000);
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            JSONObject j = new JSONObject(sb.toString());
            final long code = j.optLong("version_code", INSTALLED_CODE);
            final String name = j.optString("version_name", "");
            final String notes = j.optString("notes", "");
            final String apkUrl = j.optString("apk_url", "/nexora.apk");
            if (code > INSTALLED_CODE) {
                runOnUiThread(new Runnable() { public void run() { showUpdateDialog(code, name, notes, apkUrl); } });
            }
        } catch (Exception ignored) { /* silent: update-check must never break the app */ }
    }

    private void showUpdateDialog(long code, String name, String notes, String apkUrl) {
        new AlertDialog.Builder(this)
                .setTitle("NEXORA " + name + " - UPDATE AVAILABLE")
                .setMessage(notes + "\n\nUpdate abhi install karein? (direct in-app, Play-Store-style)")
                .setPositiveButton("UPDATE NOW", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { startUpdateDownload(apkUrl); }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    private void startUpdateDownload(String apkPath) {
        try {
            String full = apkPath.startsWith("http") ? apkPath : DEFAULT_BASE + apkPath;
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(full));
            req.setTitle("NEXORA Update");
            req.setDescription("Downloading NEXORA update...");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NEXORA-update.apk");
            req.setMimeType("application/vnd.android.package-archive");
            downloadId = dm.enqueue(req);
            Toast.makeText(this, "Update download started...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void promptInstall() {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Cursor cur = dm.query(new DownloadManager.Query().setFilterById(downloadId));
            if (cur != null && cur.moveToFirst()) {
                int status = cur.getInt(cur.getColumnIndex(DownloadManager.COLUMN_STATUS));
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    Uri uri = dm.getUriForDownloadedFile(downloadId);
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(uri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(install);
                }
            }
            if (cur != null) cur.close();
        } catch (Exception e) {
            Toast.makeText(this, "Install prompt failed - open Downloads and tap NEXORA-update.apk", Toast.LENGTH_LONG).show();
        }
    }

    public class Bridge {
        @JavascriptInterface
        public String platform() { return "android"; }
        @JavascriptInterface
        public String version() { return "1.3.0"; }
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
