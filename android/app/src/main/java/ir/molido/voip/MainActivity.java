package ir.molido.voip;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
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
    private static final int REQ_MIC = 4102;

    private WebView web;
    private TextView updateStatus;
    private Button updateButton;
    private TextView connectionStatus;
    private TextView extensionLabel;
    private EditText dialInput;
    private boolean darkMode;
    private SharedPreferences prefs;
    private SecureStore secureStore;
    private SipRegistrationManager sipManager;
    private Button registerButton;
    private Button callButton;
    private Button hangupButton;
    private Button muteButton;
    private Button speakerButton;
    private TextView callStatus;
    private TextView audioQuality;
    private String pendingDialNumber = "";
    private boolean speakerEnabled = false;

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
        secureStore = new SecureStore(this);
        sipManager = new SipRegistrationManager(this,
                (regState, detail, success) -> runOnUiThread(() ->
                        updateSipRegistrationUi(regState, detail, success)));
        sipManager.setCallEventListener(
                (callState, detail, active) -> runOnUiThread(() ->
                        updateCallUi(callState, detail, active)));
        sipManager.setAudioQualityListener(
                (profile, score, loss, jitter, rtt, down, up) ->
                        runOnUiThread(() -> updateSmartAudioUi(
                                profile, score, loss, jitter, rtt, down, up)));
        darkMode = prefs.getBoolean("dark_mode", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(darkMode ? Color.rgb(17, 24, 39) : Color.rgb(246, 248, 252));

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

        ScrollView dashboardScroll = new ScrollView(this);
        LinearLayout dashboard = new LinearLayout(this);
        dashboard.setOrientation(LinearLayout.VERTICAL);
        dashboard.setPadding(dp(12), dp(10), dp(12), dp(12));
        dashboardScroll.addView(dashboard);

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        statusCard.setBackground(roundedBg("#FFFFFF", 20, "#E5E7EB"));
        statusCard.setElevation(dp(3));

        TextView profileTitle = new TextView(this);
        profileTitle.setText("پروفایل اتصال");
        profileTitle.setTextSize(13f);
        profileTitle.setTypeface(Typeface.DEFAULT_BOLD);
        profileTitle.setTextColor(Color.rgb(31, 41, 55));

        extensionLabel = new TextView(this);
        String ext = prefs.getString("extension", "");
        String displayName = prefs.getString("display_name", "");
        extensionLabel.setText(
                (displayName.isEmpty() ? "کاربر MOLIDO" : displayName)
                        + "  •  داخلی "
                        + (ext.isEmpty() ? "—" : ext));
        extensionLabel.setTextSize(16f);
        extensionLabel.setTypeface(Typeface.DEFAULT_BOLD);
        extensionLabel.setTextColor(Color.rgb(17, 24, 39));
        extensionLabel.setPadding(0, dp(5), 0, dp(3));

        connectionStatus = new TextView(this);
        String sipRegistrarReady = prefs.getString("sip_registrar", "");
        String userReady = prefs.getString("sip_username", "");
        boolean passwordReady = hasStoredSipPassword();
        boolean profileReady =
                !sipRegistrarReady.isEmpty()
                && !userReady.isEmpty()
                && passwordReady;

        connectionStatus.setText(profileReady
                ? "●  Offline • آماده Register"
                : "●  تنظیمات Secure SIP ناقص");
        connectionStatus.setTextSize(12f);
        connectionStatus.setTextColor(profileReady
                ? Color.rgb(37, 99, 235)
                : Color.rgb(245, 158, 11));

        statusCard.addView(profileTitle);
        statusCard.addView(extensionLabel);
        statusCard.addView(connectionStatus);

        registerButton = new Button(this);
        registerButton.setText(profileReady ? "Register SIP" : "تنظیمات SIP");
        registerButton.setAllCaps(false);
        stylePrimaryButton(registerButton);
        registerButton.setOnClickListener(v -> {
            if (!isSipProfileReady()) {
                showSettings();
                return;
            }
            startSipRegistration();
        });

        LinearLayout.LayoutParams regLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        regLp.setMargins(0, dp(10), 0, 0);
        statusCard.addView(registerButton, regLp);

        Button unregisterButton = new Button(this);
        unregisterButton.setText("Unregister");
        unregisterButton.setAllCaps(false);
        styleSecondaryButton(unregisterButton);
        unregisterButton.setOnClickListener(v -> sipManager.unregister());

        LinearLayout.LayoutParams unregLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        unregLp.setMargins(0, dp(6), 0, 0);
        statusCard.addView(unregisterButton, unregLp);

        dashboard.addView(statusCard);

        TextView dialTitle = new TextView(this);
        dialTitle.setText("شماره‌گیر");
        dialTitle.setTextSize(15f);
        dialTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dialTitle.setTextColor(Color.rgb(17, 24, 39));
        dialTitle.setPadding(dp(2), dp(14), 0, dp(8));
        dashboard.addView(dialTitle);

        dialInput = new EditText(this);
        dialInput.setHint("شماره یا داخلی");
        dialInput.setInputType(InputType.TYPE_CLASS_PHONE);
        dialInput.setGravity(Gravity.CENTER);
        dialInput.setTextSize(22f);
        dialInput.setSingleLine(true);
        dialInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        dialInput.setBackground(roundedBg("#FFFFFF", 18, "#D1D5DB"));
        dashboard.addView(dialInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout keypad = new LinearLayout(this);
        keypad.setOrientation(LinearLayout.VERTICAL);
        keypad.setPadding(0, dp(10), 0, 0);

        String[][] keys = {
                {"1", "2", "3"},
                {"4", "5", "6"},
                {"7", "8", "9"},
                {"*", "0", "#"}
        };

        for (String[] rowKeys : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            for (String key : rowKeys) {
                Button b = new Button(this);
                b.setText(key);
                b.setTextSize(20f);
                b.setAllCaps(false);
                b.setTextColor(Color.rgb(17, 24, 39));
                b.setBackground(roundedBg("#F9FAFB", 20, "#E5E7EB"));
                b.setOnClickListener(v -> dialInput.append(((Button) v).getText()));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, dp(56), 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(b, lp);
            }

            keypad.addView(row);
        }

        dashboard.addView(keypad);

        LinearLayout callActions = new LinearLayout(this);
        callActions.setOrientation(LinearLayout.HORIZONTAL);
        callActions.setGravity(Gravity.CENTER);

        callButton = new Button(this);
        callButton.setText("تماس");
        callButton.setAllCaps(false);
        stylePrimaryButton(callButton);
        callButton.setOnClickListener(v -> beginOutgoingCall());

        Button clearButton = new Button(this);
        clearButton.setText("پاک");
        clearButton.setAllCaps(false);
        styleSecondaryButton(clearButton);
        clearButton.setOnClickListener(v -> dialInput.setText(""));

        LinearLayout.LayoutParams callLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        callLp.setMargins(0, dp(8), dp(5), 0);
        callActions.addView(callButton, callLp);

        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clearLp.setMargins(dp(5), dp(8), 0, 0);
        callActions.addView(clearButton, clearLp);

        dashboard.addView(callActions);

        LinearLayout callCard = new LinearLayout(this);
        callCard.setOrientation(LinearLayout.VERTICAL);
        callCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        callCard.setBackground(roundedBg(
                darkMode ? "#1F2937" : "#FFFFFF",
                18,
                darkMode ? "#374151" : "#E5E7EB"));

        callStatus = new TextView(this);
        callStatus.setText("تماس فعال نیست");
        callStatus.setTextSize(14f);
        callStatus.setTypeface(Typeface.DEFAULT_BOLD);
        callStatus.setTextColor(
                darkMode ? Color.WHITE : Color.rgb(31, 41, 55));
        callCard.addView(callStatus);

        audioQuality = new TextView(this);
        audioQuality.setText("Smart Audio • آماده پایش شبکه");
        audioQuality.setTextSize(11f);
        audioQuality.setTextColor(
                darkMode ? Color.rgb(156, 163, 175) : Color.rgb(107, 114, 128));
        audioQuality.setPadding(0, dp(4), 0, 0);
        callCard.addView(audioQuality);

        LinearLayout liveControls = new LinearLayout(this);
        liveControls.setOrientation(LinearLayout.HORIZONTAL);
        liveControls.setGravity(Gravity.CENTER);
        liveControls.setPadding(0, dp(8), 0, 0);

        muteButton = new Button(this);
        muteButton.setText("Mute");
        muteButton.setAllCaps(false);
        styleSecondaryButton(muteButton);
        muteButton.setEnabled(false);
        muteButton.setOnClickListener(v -> {
            boolean muted = sipManager.toggleMute();
            muteButton.setText(muted ? "Unmute" : "Mute");
        });

        speakerButton = new Button(this);
        speakerButton.setText("Speaker");
        speakerButton.setAllCaps(false);
        styleSecondaryButton(speakerButton);
        speakerButton.setEnabled(false);
        speakerButton.setOnClickListener(v -> toggleSpeaker());

        hangupButton = new Button(this);
        hangupButton.setText("قطع تماس");
        hangupButton.setAllCaps(false);
        hangupButton.setTextColor(Color.WHITE);
        hangupButton.setTypeface(Typeface.DEFAULT_BOLD);
        hangupButton.setBackground(roundedBg("#DC2626", 16, null));
        hangupButton.setEnabled(false);
        hangupButton.setOnClickListener(v -> sipManager.terminateCall());

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMargins(0, 0, dp(4), 0);
        liveControls.addView(muteButton, lp1);

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.setMargins(dp(4), 0, dp(4), 0);
        liveControls.addView(speakerButton, lp2);

        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp3.setMargins(dp(4), 0, 0, 0);
        liveControls.addView(hangupButton, lp3);

        callCard.addView(liveControls);

        LinearLayout.LayoutParams callCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        callCardLp.setMargins(0, dp(10), 0, 0);
        dashboard.addView(callCard, callCardLp);

        LinearLayout quickActions = new LinearLayout(this);
        quickActions.setOrientation(LinearLayout.HORIZONTAL);
        quickActions.setGravity(Gravity.CENTER);
        quickActions.setPadding(0, dp(12), 0, dp(6));

        Button recentButton = new Button(this);
        recentButton.setText("تماس‌های اخیر");
        recentButton.setAllCaps(false);
        styleSecondaryButton(recentButton);
        recentButton.setOnClickListener(v ->
                showToast("تاریخچه تماس در فاز بعدی فعال می‌شود."));

        Button contactsButton = new Button(this);
        contactsButton.setText("مخاطبین");
        contactsButton.setAllCaps(false);
        styleSecondaryButton(contactsButton);
        contactsButton.setOnClickListener(v ->
                showToast("مخاطبین در فاز بعدی فعال می‌شوند."));

        LinearLayout.LayoutParams q1 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        q1.setMargins(0, 0, dp(5), 0);
        quickActions.addView(recentButton, q1);

        LinearLayout.LayoutParams q2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        q2.setMargins(dp(5), 0, 0, 0);
        quickActions.addView(contactsButton, q2);

        dashboard.addView(quickActions);

        TextView historyTitle = new TextView(this);
        historyTitle.setText("تماس‌های اخیر");
        historyTitle.setTextSize(15f);
        historyTitle.setTypeface(Typeface.DEFAULT_BOLD);
        historyTitle.setTextColor(darkMode ? Color.WHITE : Color.rgb(17, 24, 39));
        historyTitle.setPadding(dp(2), dp(14), 0, dp(7));
        dashboard.addView(historyTitle);

        LinearLayout historyCard = new LinearLayout(this);
        historyCard.setOrientation(LinearLayout.VERTICAL);
        historyCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        historyCard.setBackground(roundedBg(
                darkMode ? "#1F2937" : "#FFFFFF",
                18,
                darkMode ? "#374151" : "#E5E7EB"));

        TextView h1 = new TextView(this);
        h1.setText("هنوز تماس واقعی ثبت نشده است");
        h1.setTextSize(13f);
        h1.setTextColor(darkMode ? Color.rgb(229, 231, 235) : Color.rgb(55, 65, 81));

        TextView h2 = new TextView(this);
        h2.setText("پس از فعال شدن SIP، سه تماس آخر اینجا نمایش داده می‌شود.");
        h2.setTextSize(11f);
        h2.setTextColor(Color.rgb(156, 163, 175));
        h2.setPadding(0, dp(3), 0, 0);

        historyCard.addView(h1);
        historyCard.addView(h2);
        dashboard.addView(historyCard);

        TextView webTitle = new TextView(this);
        webTitle.setText("پنل وب MOLIDO");
        webTitle.setTextSize(13f);
        webTitle.setTypeface(Typeface.DEFAULT_BOLD);
        webTitle.setTextColor(Color.rgb(75, 85, 99));
        webTitle.setPadding(dp(2), dp(12), 0, dp(6));
        dashboard.addView(webTitle);

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams webLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(250));
        dashboard.addView(web, webLp);

        root.addView(dashboardScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(dp(12), dp(9), dp(12), dp(9));
        footer.setBackground(roundedBg(darkMode ? "#111827" : "#FFFFFF", 22, null));
        footer.setElevation(dp(8));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView version = new TextView(this);
        version.setText("MOLIDO VOIP  •  v" + BuildConfig.VERSION_NAME);
        version.setTextSize(12f);
        version.setTextColor(darkMode ? Color.rgb(229, 231, 235) : Color.rgb(55, 65, 81));
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

        Button themeButton = new Button(this);
        themeButton.setText(darkMode ? "روشن" : "تیره");
        themeButton.setTextSize(12f);
        themeButton.setAllCaps(false);
        styleSecondaryButton(themeButton);
        themeButton.setOnClickListener(v -> {
            prefs.edit().putBoolean("dark_mode", !darkMode).apply();
            recreate();
        });

        top.addView(version, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(qrButton);
        top.addView(themeButton);
        top.addView(settingsButton);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        updateStatus = new TextView(this);
        updateStatus.setText("وضعیت بروزرسانی: در انتظار بررسی");
        updateStatus.setTextSize(11f);
        updateStatus.setTextColor(darkMode ? Color.rgb(209, 213, 219) : Color.rgb(75, 85, 99));
        updateStatus.setPadding(dp(10), dp(7), dp(10), dp(7));
        updateStatus.setBackground(roundedBg(darkMode ? "#1F2937" : "#F3F4F6", 14, null));

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
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        web.getSettings().setSupportMultipleWindows(false);
        web.getSettings().setGeolocationEnabled(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            web.getSettings().setSafeBrowsingEnabled(true);
        }
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

            if (value.length() > 4096) {
                throw new IllegalArgumentException("QR payload too large");
            }

            if (value.toLowerCase().contains("\"password\"")
                    || value.toLowerCase().contains("\"secret\"")
                    || value.toLowerCase().contains("\"token\"")
                    || value.toLowerCase().contains("sippassword=")
                    || value.toLowerCase().contains("password=")
                    || value.toLowerCase().contains("secret=")
                    || value.toLowerCase().contains("token=")) {
                throw new IllegalArgumentException(
                        "Sensitive credentials are not accepted in QR");
            }

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

        EditText sipRegistrar = field(
                "SIP Registrar / Domain (مثال: pbx.example.com:5061)",
                prefs.getString("sip_registrar", ""));

        EditText sipUsername = field("SIP Username",
                prefs.getString("sip_username", ""));

        EditText sipPassword = field("SIP Password",
                "");
        sipPassword.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        sipPassword.setHint(hasStoredSipPassword()
                ? "رمز SIP ذخیره شده • برای تغییر، رمز جدید را وارد کنید"
                : "SIP Password");

        EditText transport = field("Transport: TLS / TCP / UDP",
                prefs.getString("sip_transport", "TLS"));

        EditText displayName = field("نام نمایشی",
                prefs.getString("display_name", ""));

        EditText language = field("زبان (fa / en / bal)",
                prefs.getString("language", "fa"));

        form.addView(server);
        form.addView(tenant);
        form.addView(extension);
        form.addView(sipRegistrar);
        form.addView(sipUsername);
        form.addView(sipPassword);
        form.addView(transport);
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

                String transportValue =
                        transport.getText().toString().trim().toUpperCase(java.util.Locale.ROOT);

                if (!transportValue.equals("TLS")
                        && !transportValue.equals("TCP")
                        && !transportValue.equals("UDP")) {
                    transport.setError("فقط TLS، TCP یا UDP مجاز است.");
                    return;
                }

                String newPassword = sipPassword.getText().toString();

                try {
                    if (!newPassword.isEmpty()) {
                        secureStore.putSecret("sip_password", newPassword);
                    }
                } catch (Exception e) {
                    showToast("ذخیره امن رمز SIP ناموفق بود.");
                    return;
                }

                prefs.edit()
                        .putString("server", serverValue)
                        .putString("tenant", tenant.getText().toString().trim())
                        .putString("extension", extension.getText().toString().trim())
                        .putString("sip_registrar", sipRegistrar.getText().toString().trim())
                        .putString("sip_username", sipUsername.getText().toString().trim())
                        .putString("sip_transport", transportValue)
                        .putString("display_name", displayName.getText().toString().trim())
                        .putString("language", language.getText().toString().trim())
                        .apply();

                showToast("تنظیمات و Credential امن ذخیره شد.");
                ad.dismiss();
            });
        });

        ad.show();
    }

    private void beginOutgoingCall() {
        String number = dialInput.getText().toString().trim();

        if (number.isEmpty()) {
            showToast("شماره یا داخلی را وارد کنید.");
            return;
        }

        if (!sipManager.isRegistered()) {
            showToast("ابتدا SIP را Register کنید.");
            return;
        }

        if (sipManager.hasActiveCall()) {
            showToast("یک تماس دیگر فعال است.");
            return;
        }

        pendingDialNumber = number;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_MIC);
            return;
        }

        placeOutgoingCall(number);
    }

    private void placeOutgoingCall(String number) {
        try {
            callStatus.setText("Calling • " + number);
            callButton.setEnabled(false);
            sipManager.startOutgoingCall(number);
        } catch (Exception e) {
            callButton.setEnabled(true);
            callStatus.setText("Failed • " + safeUiMessage(e.getMessage()));
            showToast("شروع تماس ناموفق بود.");
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == REQ_MIC) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                String number = pendingDialNumber;
                pendingDialNumber = "";
                if (!number.isEmpty()) placeOutgoingCall(number);
            } else {
                pendingDialNumber = "";
                showToast("برای تماس صوتی، اجازه میکروفن لازم است.");
            }
        }
    }

    private void updateCallUi(
            String state,
            String detail,
            boolean active) {

        String number = dialInput == null
                ? ""
                : dialInput.getText().toString().trim();

        String text = state;
        if (!number.isEmpty()
                && ("Calling".equals(state)
                || "Ringing".equals(state)
                || "Connected".equals(state))) {
            text += " • " + number;
        }

        String safeDetail = safeUiMessage(detail);
        if (!safeDetail.isEmpty()
                && !"INVITE sent".equals(safeDetail)) {
            text += " • " + safeDetail;
        }

        callStatus.setText(text);

        boolean controls = active
                && !"Calling".equals(state)
                && !"Ringing".equals(state);

        hangupButton.setEnabled(active);
        muteButton.setEnabled(controls);
        speakerButton.setEnabled(controls);
        callButton.setEnabled(!active);

        if ("Ended".equals(state) || "Failed".equals(state)) {
            muteButton.setText("Mute");
            setSpeakerEnabled(false);
            saveRecentOutgoingCall(number, state);
        }
    }

    private void updateSmartAudioUi(
            String profile, int score, float loss, float jitter,
            float rtt, float down, float up) {
        if (audioQuality == null) return;
        String label;
        if ("EXCELLENT".equals(profile)) label = "عالی";
        else if ("GOOD".equals(profile)) label = "خوب";
        else if ("FAIR".equals(profile)) label = "متوسط";
        else label = "حالت حفظ صدا";
        audioQuality.setText(
                "Smart Audio • " + label
                + " • " + score + "/100"
                + " • Loss " + String.format(java.util.Locale.US, "%.1f%%", loss)
                + " • Jitter " + Math.round(jitter) + "ms"
                + " • RTT " + Math.round(rtt) + "ms"
                + " • ↓" + Math.round(down) + " ↑" + Math.round(up) + " kbps");
    }

    private void toggleSpeaker() {
        setSpeakerEnabled(!speakerEnabled);
    }

    @SuppressWarnings("deprecation")
    private void setSpeakerEnabled(boolean enabled) {
        speakerEnabled = enabled;
        try {
            AudioManager audio =
                    (AudioManager) getSystemService(AUDIO_SERVICE);
            if (audio != null) {
                audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
                audio.setSpeakerphoneOn(enabled);
            }
        } catch (Exception ignored) {}

        if (speakerButton != null) {
            speakerButton.setText(enabled ? "Earpiece" : "Speaker");
        }
    }

    private void saveRecentOutgoingCall(String number, String result) {
        if (number == null || number.trim().isEmpty()) return;

        prefs.edit()
                .putString("last_call_number", number.trim())
                .putString("last_call_result", result)
                .putLong("last_call_time", System.currentTimeMillis())
                .apply();
    }

    private boolean isSipProfileReady() {
        return !prefs.getString("sip_registrar", "").trim().isEmpty()
                && !prefs.getString("sip_username", "").trim().isEmpty()
                && hasStoredSipPassword();
    }

    private void startSipRegistration() {
        try {
            String registrar = prefs.getString("sip_registrar", "").trim();
            String username = prefs.getString("sip_username", "").trim();
            String transport = prefs.getString("sip_transport", "TLS").trim();
            String password = secureStore.getSecret("sip_password");

            if (password == null || password.isEmpty()) {
                showToast("رمز SIP ذخیره نشده است.");
                showSettings();
                return;
            }

            registerButton.setEnabled(false);
            registerButton.setText("Connecting…");
            connectionStatus.setText("●  Connecting…");
            connectionStatus.setTextColor(Color.rgb(37, 99, 235));

            sipManager.register(
                    registrar,
                    username,
                    password,
                    transport);

        } catch (Exception e) {
            registerButton.setEnabled(true);
            registerButton.setText("Register SIP");
            connectionStatus.setText("●  Failed • " + safeUiMessage(e.getMessage()));
            connectionStatus.setTextColor(Color.rgb(220, 38, 38));
        }
    }

    private void updateSipRegistrationUi(
            String state,
            String detail,
            boolean success) {

        String safeDetail = safeUiMessage(detail);

        if ("Registered".equals(state)) {
            connectionStatus.setText("●  Registered" +
                    (safeDetail.isEmpty() ? "" : " • " + safeDetail));
            connectionStatus.setTextColor(Color.rgb(16, 185, 129));
            registerButton.setEnabled(true);
            registerButton.setText("Registered");
        } else if ("Connecting".equals(state)
                || "Refreshing".equals(state)) {
            connectionStatus.setText("●  " + state);
            connectionStatus.setTextColor(Color.rgb(37, 99, 235));
            registerButton.setEnabled(false);
            registerButton.setText(state + "…");
        } else if ("Failed".equals(state)) {
            connectionStatus.setText("●  Failed" +
                    (safeDetail.isEmpty() ? "" : " • " + safeDetail));
            connectionStatus.setTextColor(Color.rgb(220, 38, 38));
            registerButton.setEnabled(true);
            registerButton.setText("Retry Register");
        } else {
            connectionStatus.setText("●  Offline" +
                    (safeDetail.isEmpty() ? "" : " • " + safeDetail));
            connectionStatus.setTextColor(Color.rgb(107, 114, 128));
            registerButton.setEnabled(true);
            registerButton.setText("Register SIP");
        }
    }

    private String safeUiMessage(String value) {
        if (value == null) return "";
        String v = value.replace('\n', ' ').replace('\r', ' ').trim();
        return v.length() > 120 ? v.substring(0, 120) : v;
    }

    private boolean hasStoredSipPassword() {
        try {
            String value = secureStore.getSecret("sip_password");
            return value != null && !value.isEmpty();
        } catch (Exception e) {
            return false;
        }
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
        try {
            if (sipManager != null) sipManager.shutdown();
        } catch (Exception ignored) {
        }
        web.destroy();
        super.onDestroy();
    }
}
