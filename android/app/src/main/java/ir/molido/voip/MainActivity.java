package ir.molido.voip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;

public final class MainActivity extends Activity {
    private static final String HOME = "https://molidoai.github.io/VoipMolido/";
    private static final String RELEASES_API =
            "https://api.github.com/repos/molidoai/VoipMolido/releases/latest";

    private WebView web;
    private Button updateButton;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(12), dp(4), dp(8), dp(4));

        TextView version = new TextView(this);
        version.setText("MOLIDO VOIP  •  v" + BuildConfig.VERSION_NAME);
        version.setTextSize(12f);
        version.setTextColor(Color.DKGRAY);

        updateButton = new Button(this);
        updateButton.setText("بررسی بروزرسانی");
        updateButton.setTextSize(12f);
        updateButton.setAllCaps(false);
        updateButton.setOnClickListener(v -> checkUpdates(true));

        footer.addView(version, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        footer.addView(updateButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("https".equals(uri.getScheme())
                        && "molidoai.github.io".equals(uri.getHost())
                        && uri.getPath() != null
                        && uri.getPath().startsWith("/VoipMolido/")) {
                    return false;
                }

                if ("https".equals(uri.getScheme())) {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
                return true;
            }
        });

        web.loadUrl(HOME);

        // Automatic update check at app start.
        checkUpdates(false);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setUpdateButtonBusy(boolean busy) {
        runOnUiThread(() -> {
            updateButton.setEnabled(!busy);
            updateButton.setText(busy ? "در حال بررسی..." : "بررسی بروزرسانی");
        });
    }

    private void checkUpdates(boolean manual) {
        if (manual) setUpdateButtonBusy(true);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(RELEASES_API).openConnection();
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(7000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "MOLIDO-VOIP-Android");

                if (conn.getResponseCode() != 200) {
                    if (manual) showToast("بررسی بروزرسانی انجام نشد.");
                    return;
                }

                java.io.InputStream in = conn.getInputStream();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int total = 0, n;

                while ((n = in.read(buffer)) != -1 && total < 65536) {
                    int take = Math.min(n, 65536 - total);
                    out.write(buffer, 0, take);
                    total += take;
                }

                JSONObject release = new JSONObject(
                        new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));

                if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                    if (manual) showToast("نسخه رسمی جدیدی پیدا نشد.");
                    return;
                }

                String tag = release.optString("tag_name", "");
                JSONArray assets = release.optJSONArray("assets");
                boolean apkPresent = false;

                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.optJSONObject(i);
                        if (asset != null
                                && asset.optString("name", "").toLowerCase().endsWith(".apk")) {
                            apkPresent = true;
                            break;
                        }
                    }
                }

                if (!isNewer(tag, BuildConfig.VERSION_NAME) || !apkPresent) {
                    if (manual) {
                        showToast("نسخه " + BuildConfig.VERSION_NAME + " آخرین نسخه است.");
                    }
                    return;
                }

                String page = release.optString("html_url", "");
                Uri uri = Uri.parse(page);

                if (!"https".equals(uri.getScheme())
                        || !"github.com".equals(uri.getHost())
                        || uri.getPath() == null
                        || !uri.getPath().startsWith("/molidoai/VoipMolido/releases/")) {
                    if (manual) showToast("آدرس بروزرسانی معتبر نیست.");
                    return;
                }

                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("بروزرسانی MOLIDO VOIP")
                        .setMessage("نسخه جدید " + tag
                                + " منتشر شده است.\nنسخه فعلی: v"
                                + BuildConfig.VERSION_NAME)
                        .setNegativeButton("بعداً", null)
                        .setPositiveButton("دریافت بروزرسانی",
                                (d, w) -> startActivity(
                                        new Intent(Intent.ACTION_VIEW, uri)))
                        .show());

            } catch (Exception e) {
                if (manual) showToast("اتصال برای بررسی بروزرسانی برقرار نشد.");
            } finally {
                if (conn != null) conn.disconnect();
                if (manual) setUpdateButtonBusy(false);
            }
        }).start();
    }

    private void showToast(String text) {
        runOnUiThread(() ->
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    static boolean isNewer(String candidate, String current) {
        if (candidate == null
                || !candidate.matches("v?[0-9]+\\.[0-9]+\\.[0-9]+")) {
            return false;
        }

        String[] a = candidate.replaceFirst("^v", "").split("\\.");
        String[] b = current.split("\\.");

        if (b.length != 3) return false;

        for (int i = 0; i < 3; i++) {
            long x = Long.parseLong(a[i]);
            long y = Long.parseLong(b[i]);
            if (x != y) return x > y;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
