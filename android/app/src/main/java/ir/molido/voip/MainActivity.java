package ir.molido.voip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;

public final class MainActivity extends Activity {
    private static final String HOME = "https://molidoai.github.io/VoipMolido/";
    private static final String RELEASES_API =
            "https://api.github.com/repos/molidoai/VoipMolido/releases/latest";
    private static final String PREFS = "molido_voip_settings";

    private WebView web;
    private TextView updateStatus;
    private Button updateButton;
    private SharedPreferences prefs;

    private static final class UpdateInfo {
        String tag = "";
        String releasePage = "";
        String apkUrl = "";
        String publishedAt = "";
        long apkSize = 0L;
        boolean updateAvailable = false;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(10), dp(4), dp(10), dp(5));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView version = new TextView(this);
        version.setText("MOLIDO VOIP  •  v" + BuildConfig.VERSION_NAME);
        version.setTextSize(12f);
        version.setTextColor(Color.DKGRAY);

        Button settingsButton = new Button(this);
        settingsButton.setText("تنظیمات");
        settingsButton.setTextSize(12f);
        settingsButton.setAllCaps(false);
        settingsButton.setOnClickListener(v -> showSettings());

        top.addView(version, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(settingsButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        updateStatus = new TextView(this);
        updateStatus.setText("وضعیت بروزرسانی: در انتظار بررسی");
        updateStatus.setTextSize(11f);
        updateStatus.setTextColor(Color.GRAY);

        updateButton = new Button(this);
        updateButton.setText("بررسی بروزرسانی");
        updateButton.setTextSize(12f);
        updateButton.setAllCaps(false);
        updateButton.setOnClickListener(v -> checkUpdates(true));

        bottom.addView(updateStatus, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bottom.addView(updateButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        footer.addView(top);
        footer.addView(bottom);
        root.addView(footer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        configureWebView();
        web.loadUrl(HOME);

        // Automatic update check on app launch.
        checkUpdates(false);
    }

    private void configureWebView() {
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
    }

    private void showSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        scroll.addView(form);

        EditText server = field("آدرس سرور / PBX", prefs.getString("server", ""));
        server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        EditText tenant = field("Tenant ID", prefs.getString("tenant", ""));
        EditText extension = field("شماره داخلی / Extension", prefs.getString("extension", ""));
        extension.setInputType(InputType.TYPE_CLASS_PHONE);

        EditText displayName = field("نام نمایشی", prefs.getString("display_name", ""));
        EditText language = field("زبان (fa / en / bal)", prefs.getString("language", "fa"));

        form.addView(server);
        form.addView(tenant);
        form.addView(extension);
        form.addView(displayName);
        form.addView(language);

        TextView note = new TextView(this);
        note.setText(
                "\nاین تنظیمات برای PHASE بعدی SIP/QR نگهداری می‌شوند. " +
                "رمز SIP هنوز در این بخش ذخیره نمی‌شود.");
        note.setTextSize(11f);
        note.setTextColor(Color.GRAY);
        form.addView(note);

        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle("تنظیمات MOLIDO VOIP")
                .setView(scroll)
                .setNegativeButton("انصراف", null)
                .setNeutralButton("پاک کردن Cache", (d, w) -> {
                    web.clearCache(true);
                    showToast("Cache پاک شد.");
                })
                .setPositiveButton("ذخیره", null)
                .create();

        ad.setOnShowListener(dialog -> {
            ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String serverValue = server.getText().toString().trim();
                if (!serverValue.isEmpty()) {
                    Uri uri = Uri.parse(serverValue);
                    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                        server.setError("آدرس سرور باید HTTPS معتبر باشد.");
                        return;
                    }
                }

                prefs.edit()
                        .putString("server", serverValue)
                        .putString("tenant", tenant.getText().toString().trim())
                        .putString("extension", extension.getText().toString().trim())
                        .putString("display_name", displayName.getText().toString().trim())
                        .putString("language", language.getText().toString().trim())
                        .apply();

                showToast("تنظیمات ذخیره شد.");
                ad.dismiss();
            });
        });

        ad.show();
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        return e;
    }

    private void checkUpdates(boolean manual) {
        setUpdateBusy(true);
        setStatus("در حال بررسی نسخه جدید...");

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(RELEASES_API).openConnection();
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(7000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("User-Agent", "MOLIDO-VOIP-Android");

                if (conn.getResponseCode() != 200) {
                    setStatus("بررسی بروزرسانی ناموفق بود.");
                    if (manual) showToast("پاسخ GitHub دریافت نشد.");
                    return;
                }

                java.io.InputStream in = conn.getInputStream();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int total = 0, n;

                while ((n = in.read(buffer)) != -1 && total < 131072) {
                    int take = Math.min(n, 131072 - total);
                    out.write(buffer, 0, take);
                    total += take;
                }

                JSONObject release = new JSONObject(
                        new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));

                if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                    setStatus("نسخه رسمی جدیدی پیدا نشد.");
                    return;
                }

                UpdateInfo info = parseUpdateInfo(release);

                if (!info.updateAvailable) {
                    setStatus("آخرین نسخه نصب است • v" + BuildConfig.VERSION_NAME);
                    if (manual) {
                        showToast("نسخه " + BuildConfig.VERSION_NAME + " آخرین نسخه است.");
                    }
                    return;
                }

                setStatus("نسخه جدید " + info.tag + " موجود است");
                runOnUiThread(() -> showUpdateDialog(info));

            } catch (Exception e) {
                setStatus("اتصال برای بررسی بروزرسانی برقرار نشد.");
                if (manual) showToast("خطا در اتصال برای بروزرسانی.");
            } finally {
                if (conn != null) conn.disconnect();
                setUpdateBusy(false);
            }
        }).start();
    }

    private UpdateInfo parseUpdateInfo(JSONObject release) {
        UpdateInfo info = new UpdateInfo();
        info.tag = release.optString("tag_name", "");
        info.releasePage = release.optString("html_url", "");
        info.publishedAt = release.optString("published_at", "");

        JSONArray assets = release.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;

                String name = asset.optString("name", "").toLowerCase();
                if (name.endsWith(".apk")) {
                    info.apkUrl = asset.optString("browser_download_url", "");
                    info.apkSize = asset.optLong("size", 0L);
                    break;
                }
            }
        }

        info.updateAvailable =
                isNewer(info.tag, BuildConfig.VERSION_NAME)
                && isSafeReleasePage(info.releasePage)
                && isSafeApkUrl(info.apkUrl);

        return info;
    }

    private void showUpdateDialog(UpdateInfo info) {
        String size = humanSize(info.apkSize);
        String date = info.publishedAt.isEmpty()
                ? "نامشخص"
                : info.publishedAt.replace("T", " ").replace("Z", " UTC");

        new AlertDialog.Builder(this)
                .setTitle("بروزرسانی MOLIDO VOIP")
                .setMessage(
                        "نسخه فعلی: v" + BuildConfig.VERSION_NAME +
                        "\nنسخه جدید: " + info.tag +
                        "\nحجم APK: " + size +
                        "\nتاریخ انتشار: " + date)
                .setNegativeButton("بعداً", null)
                .setNeutralButton("صفحه نسخه", (d, w) ->
                        startActivity(new Intent(
                                Intent.ACTION_VIEW, Uri.parse(info.releasePage))))
                .setPositiveButton("دانلود APK", (d, w) ->
                        startActivity(new Intent(
                                Intent.ACTION_VIEW, Uri.parse(info.apkUrl))))
                .show();
    }

    private boolean isSafeReleasePage(String value) {
        try {
            Uri uri = Uri.parse(value);
            return "https".equals(uri.getScheme())
                    && "github.com".equals(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/molidoai/VoipMolido/releases/");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isSafeApkUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            return "https".equals(uri.getScheme())
                    && "github.com".equals(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/molidoai/VoipMolido/releases/download/");
        } catch (Exception e) {
            return false;
        }
    }

    private String humanSize(long bytes) {
        if (bytes <= 0) return "نامشخص";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return new DecimalFormat("0.0").format(kb) + " KB";
        return new DecimalFormat("0.00").format(kb / 1024.0) + " MB";
    }

    private void setStatus(String text) {
        runOnUiThread(() -> updateStatus.setText(text));
    }

    private void setUpdateBusy(boolean busy) {
        runOnUiThread(() -> {
            updateButton.setEnabled(!busy);
            updateButton.setText(busy ? "در حال بررسی..." : "بررسی بروزرسانی");
        });
    }

    private void showToast(String text) {
        runOnUiThread(() ->
                Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
