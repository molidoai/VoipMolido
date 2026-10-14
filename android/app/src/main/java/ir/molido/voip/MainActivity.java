package ir.molido.voip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;

public final class MainActivity extends Activity {
    private static final String HOME = "https://molidoai.github.io/VoipMolido/";
    private static final String RELEASES_API = "https://api.github.com/repos/molidoai/VoipMolido/releases/latest";
    private WebView web;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        setContentView(web);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme()) && "molidoai.github.io".equals(uri.getHost()) && uri.getPath() != null && uri.getPath().startsWith("/VoipMolido/")) return false;
                if ("https".equals(uri.getScheme())) {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
                return true;
            }
        });
        web.loadUrl(HOME);
        checkUpdates();
    }
    private void checkUpdates() {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(RELEASES_API).openConnection();
                conn.setConnectTimeout(6000); conn.setReadTimeout(6000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "MOLIDO-VOIP-Android");
                if (conn.getResponseCode() != 200) return;
                java.io.InputStream in = conn.getInputStream();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int total = 0, n;
                while ((n = in.read(buffer)) != -1 && total < 65536) {
                    int take = Math.min(n, 65536 - total);
                    out.write(buffer, 0, take);
                    total += take;
                }
                byte[] data = out.toByteArray();
                JSONObject release = new JSONObject(new String(data, java.nio.charset.StandardCharsets.UTF_8));
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) return;
                String tag = release.optString("tag_name", "");
                if (!isNewer(tag, BuildConfig.VERSION_NAME)) return;
                JSONArray assets = release.optJSONArray("assets");
                boolean apkPresent = false;
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset != null && asset.optString("name").endsWith(".apk")) apkPresent = true;
                }
                if (!apkPresent) return;
                String page = release.optString("html_url", "");
                Uri uri = Uri.parse(page);
                if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) || !uri.getPath().startsWith("/molidoai/VoipMolido/releases/")) return;
                runOnUiThread(() -> new AlertDialog.Builder(this).setTitle("به‌روزرسانی MOLIDO VOIP")
                    .setMessage("نسخه جدید " + tag + " منتشر شده است. صفحه رسمی دریافت باز شود؟")
                    .setNegativeButton("بعداً", null)
                    .setPositiveButton("مشاهده نسخه", (d, w) -> startActivity(new Intent(Intent.ACTION_VIEW, uri)))
                    .show());
            } catch (Exception ignored) { /* Offline or API unavailable: never interrupt app. */ }
            finally { if (conn != null) conn.disconnect(); }
        }).start();
    }
    static boolean isNewer(String candidate, String current) {
        if (candidate == null || !candidate.matches("v?[0-9]+\\.[0-9]+\\.[0-9]+")) return false;
        String[] a = candidate.replaceFirst("^v", "").split("\\.");
        String[] b = current.split("\\.");
        if (b.length != 3) return false;
        for (int i=0; i<3; i++) {
            long x = Long.parseLong(a[i]), y = Long.parseLong(b[i]);
            if (x != y) return x > y;
        }
        return false;
    }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
