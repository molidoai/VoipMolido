package ir.molido.voip;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

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
        root.setBackgroundColor(Color.rgb(246, 248, 252));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(14), dp(18), dp(12));
        header.setBackground(roundedBg("#111827", 0, null));
        header.setElevation(dp(5));

        TextView brand = new TextView(this);
        brand.setText("MOLIDO VOIP");
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(22f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("ارتباط هوشمند • امن • سریع");
        subtitle.setTextColor(Color.rgb(200, 210, 225));
        subtitle.setTextSize(12f);
        subtitle.setPadding(0, dp(2), 0, 0);

        header.addView(brand);
        header.addView(subtitle);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        webLp.setMargins(dp(10), dp(10), dp(10), dp(8));
        root.addView(web, webLp);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(12), dp(9), dp(12), dp(9));
        footer.setBackground(roundedBg("#FFFFFF", 22, null));
        footer.setElevation(dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView version = new TextView(this);
        version.setText("MOLIDO VOIP  •  v" + BuildConfig.VERSION_NAME);
        version.setTextSize(12f);
        version.setTextColor(Color.rgb(55, 65, 81));
        version.setTypeface(Typeface.DEFAULT_BOLD);

        Button qrButton = new Button(this);
        qrButton.setText("اسکن QR");
        qrButton.setTextSize(12f);
        qrButton.setAllCaps(false);
        styleSecondaryButton(qrButton);
        qrButton.setOnClickListener(v -> startQrScan());

        Button settingsButton = new Button(this);
        settingsButton.setText("تنظیمات");
        settingsButton.setTextSize(12f);
        settingsButton.setAllCaps(false);
        styleSecondaryButton(settingsButton);
        settingsButton.setOnClickListener(v -> showSettings());

        top.addView(version, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(qrButton);
        top.addView(settingsButton);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        updateStatus = new TextView(this);
        updateStatus.setText("وضعیت بروزرسانی: در انتظار بررسی");
        updateStatus.setTextSize(11f);
        updateStatus.setTextColor(Color.rgb(75, 85, 99));
        updateStatus.setPadding(dp(10), dp(7), dp(10), dp(7));
        updateStatus.setBackground(roundedBg("#F3F4F6", 14, null));

        updateButton = new Button(this);
        updateButton.setText("بررسی بروزرسانی");
        updateButton.setTextSize(12f);
        updateButton.setAllCaps(false);
        stylePrimaryButton(updateButton);
        updateButton.setOnClickListener(v -> checkUpdates(true));

        bottom.addView(updateStatus, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bottom.addView(updateButton);

        footer.addView(top);
        footer.addView(bottom);
        root.addView(footer);

        setContentView(root);

        configureWebView();
        web.loadUrl(HOME);
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

    private void startQrScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("QR تنظیمات MOLIDO VOIP را اسکن کنید");
        integrator.setBeepEnabled(false);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(
                requestCode, resultCode, data);

        if (result != null) {
            if (result.getContents() == null) {
                showToast("اسکن لغو شد.");
            } else {
                applyProvisioning(result.getContents());
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void applyProvisioning(String raw) {
        try {
            String server = "";
            String tenant = "";
            String extension = "";
            String displayName = "";
            String language = "fa";

            String value = raw == null ? "" : raw.trim();

            if (value.startsWith("{")) {
                JSONObject o = new JSONObject(value);
                server = o.optString("server", "").trim();
                tenant = o.optString("tenant", "").trim();
                extension = o.optString("extension", "").trim();
                displayName = o.optString("displayName",
                        o.optString("name", "")).trim();
                language = o.optString("language", "fa").trim();
            } else {
                Uri uri = Uri.parse(value);
                if (!"molido".equalsIgnoreCase(uri.getScheme())
                        || !"provision".equalsIgnoreCase(uri.getHost())) {
                    throw new IllegalArgumentException("Unsupported QR format");
                }

                server = safeQuery(uri, "server");
                tenant = safeQuery(uri, "tenant");
                extension = safeQuery(uri, "extension");
                displayName = safeQuery(uri, "name");
                language = safeQuery(uri, "lang");
                if (language.isEmpty()) language = "fa";
            }

            if (!server.isEmpty()) {
                Uri serverUri = Uri.parse(server);
                if (!"https".equalsIgnoreCase(serverUri.getScheme())
                        || serverUri.getHost() == null) {
                    throw new IllegalArgumentException("Server must use HTTPS");
                }
            }

            if (server.isEmpty() && tenant.isEmpty() && extension.isEmpty()) {
                throw new IllegalArgumentException("Provisioning payload is empty");
            }

            final String fServer = server;
            final String fTenant = tenant;
            final String fExtension = extension;
            final String fDisplayName = displayName;
            final String fLanguage = language;

            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("تأیید تنظیمات QR")
                    .setMessage(
                            "Server: " + valueOrDash(fServer) +
                            "\nTenant: " + valueOrDash(fTenant) +
                            "\nExtension: " + valueOrDash(fExtension) +
                            "\nName: " + valueOrDash(fDisplayName) +
                            "\nLanguage: " + valueOrDash(fLanguage))
                    .setNegativeButton("لغو", null)
                    .setPositiveButton("ذخیره", (d, w) -> {
                        prefs.edit()
                                .putString("server", fServer)
                                .putString("tenant", fTenant)
                                .putString("extension", fExtension)
                                .putString("display_name", fDisplayName)
                                .putString("language", fLanguage)
                                .apply();
                        showToast("تنظیمات QR ذخیره شد.");
                    })
                    .show());

        } catch (Exception e) {
            showToast("QR تنظیمات معتبر نیست.");
        }
    }

    private String safeQuery(Uri uri, String key) {
        String value = uri.getQueryParameter(key);
        return value == null ? "" : value.trim();
    }

    private String valueOrDash(String value) {
        return value == null || value.isEmpty() ? "—" : value;
    }

    private void showSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        scroll.addView(form);

        EditText server = field("آدرس سرور / PBX",
                prefs.getString("server", ""));
        server.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        EditText tenant = field("Tenant ID",
                prefs.getString("tenant", ""));

        EditText extension = field("شماره داخلی / Extension",
                prefs.getString("extension", ""));
        extension.setInputType(InputType.TYPE_CLASS_PHONE);

        EditText displayName = field("نام نمایشی",
                prefs.getString("display_name", ""));

        EditText language = field("زبان (fa / en / bal)",
                prefs.getString("language", "fa"));

        form.addView(server);
        form.addView(tenant);
        form.addView(extension);
        form.addView(displayName);
        form.addView(language);

        TextView note = new TextView(this);
        note.setText(
                "\nبرای ورود سریع می‌توانید از دکمه QR استفاده کنید. " +
                "رمز SIP در QR یا SharedPreferences ذخیره نمی‌شود.");
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
                    if (!"https".equalsIgnoreCase(uri.getScheme())
                            || uri.getHost() == null) {
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
        e.setTextSize(14f);
        e.setTextColor(Color.rgb(31, 41, 55));
        e.setHintTextColor(Color.rgb(156, 163, 175));
        e.setPadding(dp(14), dp(11), dp(14), dp(11));
        e.setBackground(roundedBg("#F9FAFB", 14, "#E5E7EB"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        e.setLayoutParams(lp);
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
                java.io.ByteArrayOutputStream out =
                        new java.io.ByteArrayOutputStream();

                byte[] buffer = new byte[4096];
                int total = 0, n;

                while ((n = in.read(buffer)) != -1 && total < 131072) {
                    int take = Math.min(n, 131072 - total);
                    out.write(buffer, 0, take);
                    total += take;
                }

                JSONObject release = new JSONObject(
                        new String(
                                out.toByteArray(),
                                java.nio.charset.StandardCharsets.UTF_8));

                if (release.optBoolean("draft")
                        || release.optBoolean("prerelease")) {
                    setStatus("نسخه رسمی جدیدی پیدا نشد.");
                    return;
                }

                UpdateInfo info = parseUpdateInfo(release);

                if (!info.updateAvailable) {
                    setStatus("آخرین نسخه نصب است • v"
                            + BuildConfig.VERSION_NAME);

                    if (manual) {
                        showToast("نسخه " + BuildConfig.VERSION_NAME
                                + " آخرین نسخه است.");
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
                    info.apkUrl =
                            asset.optString("browser_download_url", "");
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
                : info.publishedAt
                    .replace("T", " ")
                    .replace("Z", " UTC");

        new AlertDialog.Builder(this)
                .setTitle("بروزرسانی MOLIDO VOIP")
                .setMessage(
                        "نسخه فعلی: v" + BuildConfig.VERSION_NAME +
                        "\nنسخه جدید: " + info.tag +
                        "\nحجم APK: " + size +
                        "\nتاریخ انتشار: " + date)
                .setNegativeButton("بعداً", null)
                .setNeutralButton(
                        "صفحه نسخه",
                        (d, w) -> startActivity(
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(info.releasePage))))
                .setPositiveButton(
                        "دانلود APK",
                        (d, w) -> startActivity(
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(info.apkUrl))))
                .show();
    }

    private boolean isSafeReleasePage(String value) {
        try {
            Uri uri = Uri.parse(value);
            return "https".equals(uri.getScheme())
                    && "github.com".equals(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath()
                        .startsWith("/molidoai/VoipMolido/releases/");
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
                    && uri.getPath()
                        .startsWith("/molidoai/VoipMolido/releases/download/");
        } catch (Exception e) {
            return false;
        }
    }

    private String humanSize(long bytes) {
        if (bytes <= 0) return "نامشخص";

        double kb = bytes / 1024.0;

        if (kb < 1024.0) {
            return new DecimalFormat("0.0").format(kb) + " KB";
        }

        return new DecimalFormat("0.00").format(kb / 1024.0) + " MB";
    }

    private void setStatus(String text) {
        runOnUiThread(() -> updateStatus.setText(text));
    }

    private void setUpdateBusy(boolean busy) {
        runOnUiThread(() -> {
            updateButton.setEnabled(!busy);
            updateButton.setText(
                    busy ? "در حال بررسی..." : "بررسی بروزرسانی");
        });
    }

    private void showToast(String text) {
        runOnUiThread(() ->
                Toast.makeText(
                        this,
                        text,
                        Toast.LENGTH_SHORT).show());
    }

    private void stylePrimaryButton(Button button) {
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setBackground(roundedBg("#2563EB", 16, null));
        button.setElevation(dp(2));
    }

    private void styleSecondaryButton(Button button) {
        button.setTextColor(Color.rgb(37, 99, 235));
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(dp(12), dp(7), dp(12), dp(7));
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setBackground(roundedBg("#EFF6FF", 16, "#DBEAFE"));
    }

    private GradientDrawable roundedBg(String fill, int radiusDp, String stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(fill));
        d.setCornerRadius(dp(radiusDp));
        if (stroke != null) d.setStroke(dp(1), Color.parseColor(stroke));
        return d;
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density);
    }

    static boolean isNewer(String candidate, String current) {
        if (candidate == null
                || !candidate.matches(
                        "v?[0-9]+\\.[0-9]+\\.[0-9]+")) {
            return false;
        }

        String[] a =
                candidate.replaceFirst("^v", "").split("\\.");

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
