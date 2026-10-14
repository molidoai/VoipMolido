package ir.molido.voip;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.ContactsContract;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String HOME = "https://molidoai.github.io/VoipMolido/";
    private static final String RELEASES_API =
            "https://api.github.com/repos/molidoai/VoipMolido/releases/latest";
    private static final String PREFS = "molido_voip_settings";
    private static final int REQ_MIC = 4102;
    private static final int REQ_CONTACTS = 4103;

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
    private Button answerButton;
    private Button rejectButton;
    private Button muteButton;
    private Button speakerButton;
    private TextView callStatus;
    private TextView audioQuality;
    private TextView routeStatus;
    private MolidoConnectivityManager connectivityManager;
    private String pendingDialNumber = "";
    private boolean speakerEnabled = false;
    private boolean pendingIncomingAnswer = false;
    private String activeCallPeer = "";
    private String activeCallDirection = "";
    private boolean activeCallConnected = false;
    private long activeCallStartedAt = 0L;

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
        sipManager.setRouteListener(
                (networkType, route, reachable) -> runOnUiThread(() -> {
                    if (routeStatus != null) {
                        routeStatus.setText(reachable
                                ? "مسیر • " + networkType + " • " + route
                                : "مسیر • Offline");
                        routeStatus.setTextColor(reachable
                                ? Color.rgb(16, 185, 129)
                                : Color.rgb(220, 38, 38));
                    }
                }));
        connectivityManager = new MolidoConnectivityManager(this,
                (reachable, type, metered) -> runOnUiThread(() ->
                        sipManager.onNetworkStateChanged(reachable, type, metered)));
        darkMode = prefs.getBoolean("dark_mode", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setBackgroundColor(darkMode ? Color.rgb(17, 24, 39) : Color.rgb(246, 248, 252));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(14));
        header.setBackground(roundedBg("#0F172A", 0, null));
        header.setElevation(dp(5));

        TextView brand = new TextView(this);
        String homeDisplayName = prefs.getString("display_name", "").trim();
        brand.setText(homeDisplayName.isEmpty()
                ? "سلام، کاربر MOLIDO"
                : "سلام، " + homeDisplayName);
        brand.setTextColor(Color.WHITE);
        brand.setTextSize(21f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);

        TextView subtitle = new TextView(this);
        subtitle.setText("تماس امن، ساده و همیشه در دسترس");
        subtitle.setTextColor(Color.rgb(200, 210, 225));
        subtitle.setTextSize(12f);
        subtitle.setPadding(0, dp(3), 0, 0);

        LinearLayout headerTop = new LinearLayout(this);
        headerTop.setOrientation(LinearLayout.HORIZONTAL);
        headerTop.setGravity(Gravity.CENTER_VERTICAL);

        headerTop.addView(brand, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        Button headerUpdateButton = new Button(this);
        headerUpdateButton.setText("↻");
        headerUpdateButton.setTextSize(22f);
        headerUpdateButton.setAllCaps(false);
        headerUpdateButton.setContentDescription("بررسی بروزرسانی");
        headerUpdateButton.setTextColor(Color.WHITE);
        headerUpdateButton.setBackground(roundedBg("#1F2937", 18, "#374151"));
        headerUpdateButton.setPadding(dp(10), dp(3), dp(10), dp(3));
        headerUpdateButton.setOnClickListener(v -> checkUpdates(true));

        LinearLayout.LayoutParams updateIconLp = new LinearLayout.LayoutParams(
                dp(52),
                dp(44));
        headerTop.addView(headerUpdateButton, updateIconLp);

        header.addView(headerTop);
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
        statusCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        statusCard.setBackground(roundedBg(darkMode ? "#1F2937" : "#FFFFFF", 22, darkMode ? "#374151" : "#E5E7EB"));
        statusCard.setElevation(dp(3));

        TextView profileTitle = new TextView(this);
        profileTitle.setText("وضعیت شما");
        profileTitle.setTextSize(13f);
        profileTitle.setTypeface(Typeface.DEFAULT_BOLD);
        profileTitle.setTextColor(darkMode ? Color.WHITE : Color.rgb(31, 41, 55));

        extensionLabel = new TextView(this);
        String ext = prefs.getString("extension", "");
        String displayName = prefs.getString("display_name", "");
        extensionLabel.setText(
                (displayName.isEmpty() ? "کاربر MOLIDO" : displayName)
                        + "  •  داخلی "
                        + (ext.isEmpty() ? "—" : ext));
        extensionLabel.setTextSize(16f);
        extensionLabel.setTypeface(Typeface.DEFAULT_BOLD);
        extensionLabel.setTextColor(darkMode ? Color.rgb(229, 231, 235) : Color.rgb(17, 24, 39));
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

        routeStatus = new TextView(this);
        routeStatus.setText("مسیر • در حال بررسی");
        routeStatus.setTextSize(11f);
        routeStatus.setTextColor(Color.rgb(107, 114, 128));
        routeStatus.setPadding(0, dp(4), 0, 0);
        statusCard.addView(routeStatus);

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
        dialTitle.setText("با چه کسی تماس می‌گیری؟");
        dialTitle.setTextSize(15f);
        dialTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dialTitle.setTextColor(darkMode ? Color.WHITE : Color.rgb(17, 24, 39));
        dialTitle.setPadding(dp(2), dp(14), 0, dp(8));
        dashboard.addView(dialTitle);

        dialInput = new EditText(this);
        dialInput.setHint("شماره یا داخلی را وارد کنید");
        dialInput.setInputType(InputType.TYPE_CLASS_PHONE);
        dialInput.setGravity(Gravity.CENTER);
        dialInput.setTextSize(24f);
        dialInput.setSingleLine(true);
        dialInput.setTextColor(darkMode ? Color.WHITE : Color.rgb(17, 24, 39));
        dialInput.setHintTextColor(Color.rgb(156, 163, 175));
        dialInput.setPadding(dp(14), dp(15), dp(14), dp(15));
        dialInput.setBackground(roundedBg(
                darkMode ? "#111827" : "#FFFFFF",
                22,
                darkMode ? "#374151" : "#D1D5DB"));
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
                b.setTextSize(22f);
                b.setAllCaps(false);
                b.setTextColor(darkMode ? Color.WHITE : Color.rgb(17, 24, 39));
                b.setBackground(roundedBg(
                        darkMode ? "#1F2937" : "#FFFFFF",
                        26,
                        darkMode ? "#374151" : "#E5E7EB"));
                b.setOnClickListener(v -> dialInput.append(((Button) v).getText()));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, dp(62), 1f);
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
        callButton.setText("شروع تماس");
        callButton.setAllCaps(false);
        stylePrimaryButton(callButton);
        callButton.setOnClickListener(v -> beginOutgoingCall());

        Button clearButton = new Button(this);
        clearButton.setText("پاک کردن");
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

        TextView quickTitle = new TextView(this);
        quickTitle.setText("دسترسی سریع");
        quickTitle.setTextSize(14f);
        quickTitle.setTypeface(Typeface.DEFAULT_BOLD);
        quickTitle.setTextColor(darkMode ? Color.WHITE : Color.rgb(31, 41, 55));
        quickTitle.setPadding(dp(2), dp(16), 0, dp(8));
        dashboard.addView(quickTitle);

        LinearLayout smartQuick = new LinearLayout(this);
        smartQuick.setOrientation(LinearLayout.HORIZONTAL);
        smartQuick.setGravity(Gravity.CENTER);

        Button quickRegister = new Button(this);
        quickRegister.setText("SIP");
        quickRegister.setAllCaps(false);
        styleSecondaryButton(quickRegister);
        quickRegister.setOnClickListener(v -> {
            if (isSipProfileReady()) startSipRegistration();
            else showSettingsHub();
        });

        Button quickSettings = new Button(this);
        quickSettings.setText("تنظیمات");
        quickSettings.setAllCaps(false);
        styleSecondaryButton(quickSettings);
        quickSettings.setOnClickListener(v -> showSettingsHub());

        Button quickUpdate = new Button(this);
        quickUpdate.setText("آپدیت");
        quickUpdate.setAllCaps(false);
        styleSecondaryButton(quickUpdate);
        quickUpdate.setOnClickListener(v -> checkUpdates(true));

        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        qlp.setMargins(dp(3), 0, dp(3), 0);

        smartQuick.addView(quickRegister, qlp);
        smartQuick.addView(quickSettings, qlp);
        smartQuick.addView(quickUpdate, qlp);
        dashboard.addView(smartQuick);

        LinearLayout callCard = new LinearLayout(this);
        callCard.setOrientation(LinearLayout.VERTICAL);
        callCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        callCard.setBackground(roundedBg(
                darkMode ? "#1F2937" : "#FFFFFF",
                18,
                darkMode ? "#374151" : "#E5E7EB"));

        callStatus = new TextView(this);
        callStatus.setText("آماده تماس");
        callStatus.setTextSize(14f);
        callStatus.setTypeface(Typeface.DEFAULT_BOLD);
        callStatus.setTextColor(
                darkMode ? Color.WHITE : Color.rgb(31, 41, 55));
        callCard.addView(callStatus);

        audioQuality = new TextView(this);
        String configuredStun = prefs.getString("stun_server", "").trim();
        String configuredTurn = prefs.getString("turn_transport", "OFF").trim();
        if (!"OFF".equalsIgnoreCase(configuredTurn)) {
            audioQuality.setText("Smart Audio • ICE + TURN " +
                    configuredTurn.toUpperCase(java.util.Locale.ROOT));
        } else {
            audioQuality.setText(
                    configuredStun.isEmpty()
                            ? "Smart Audio • ICE آماده • STUN تنظیم نشده"
                            : "Smart Audio • ICE + STUN آماده");
        }
        audioQuality.setTextSize(11f);
        audioQuality.setTextColor(
                darkMode ? Color.rgb(156, 163, 175) : Color.rgb(107, 114, 128));
        audioQuality.setPadding(0, dp(4), 0, 0);
        callCard.addView(audioQuality);

        LinearLayout incomingControls = new LinearLayout(this);
        incomingControls.setOrientation(LinearLayout.HORIZONTAL);
        incomingControls.setGravity(Gravity.CENTER);
        incomingControls.setPadding(0, dp(8), 0, 0);

        answerButton = new Button(this);
        answerButton.setText("پاسخ");
        answerButton.setAllCaps(false);
        answerButton.setTextColor(Color.WHITE);
        answerButton.setTypeface(Typeface.DEFAULT_BOLD);
        answerButton.setBackground(roundedBg("#16A34A", 18, null));
        answerButton.setEnabled(false);
        answerButton.setVisibility(android.view.View.GONE);
        answerButton.setOnClickListener(v -> answerIncomingCall());

        rejectButton = new Button(this);
        rejectButton.setText("رد تماس");
        rejectButton.setAllCaps(false);
        rejectButton.setTextColor(Color.WHITE);
        rejectButton.setTypeface(Typeface.DEFAULT_BOLD);
        rejectButton.setBackground(roundedBg("#DC2626", 18, null));
        rejectButton.setEnabled(false);
        rejectButton.setVisibility(android.view.View.GONE);
        rejectButton.setOnClickListener(v -> sipManager.rejectIncomingCall());

        LinearLayout.LayoutParams answerLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        answerLp.setMargins(0, 0, dp(5), 0);
        incomingControls.addView(answerButton, answerLp);

        LinearLayout.LayoutParams rejectLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rejectLp.setMargins(dp(5), 0, 0, 0);
        incomingControls.addView(rejectButton, rejectLp);

        callCard.addView(incomingControls);

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
        recentButton.setOnClickListener(v -> showCallHistory());

        Button contactsButton = new Button(this);
        contactsButton.setText("مخاطبین");
        contactsButton.setAllCaps(false);
        styleSecondaryButton(contactsButton);
        contactsButton.setOnClickListener(v -> openContacts());

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

        String lastCallNumber = prefs.getString("last_call_number", "").trim();
        String lastCallResult = prefs.getString("last_call_result", "").trim();

        TextView h1 = new TextView(this);
        h1.setText(lastCallNumber.isEmpty()
                ? "هنوز تماسی ثبت نشده"
                : lastCallNumber);
        h1.setTextSize(14f);
        h1.setTypeface(Typeface.DEFAULT_BOLD);
        h1.setTextColor(darkMode ? Color.rgb(229, 231, 235) : Color.rgb(55, 65, 81));

        TextView h2 = new TextView(this);
        h2.setText(lastCallNumber.isEmpty()
                ? "اولین تماس شما بعد از برقراری SIP اینجا دیده می‌شود."
                : (lastCallResult.isEmpty() ? "آخرین تماس" : lastCallResult));
        h2.setTextSize(11f);
        h2.setTextColor(Color.rgb(156, 163, 175));
        h2.setPadding(0, dp(3), 0, 0);

        historyCard.addView(h1);
        historyCard.addView(h2);
        dashboard.addView(historyCard);

        TextView webTitle = new TextView(this);
        webTitle.setText("خدمات MOLIDO");
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
        version.setText("MOLIDO  •  v" + BuildConfig.VERSION_NAME);
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
        settingsButton.setOnClickListener(v -> showSettingsHub());

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
        connectivityManager.start();

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

    private void showSettingsHub() {
        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(18));
        root.setBackgroundColor(darkMode
                ? Color.rgb(17, 24, 39)
                : Color.rgb(246, 248, 252));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("تنظیمات");
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(darkMode ? Color.WHITE : Color.rgb(17, 24, 39));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("MOLIDO VoIP • تنظیمات ساده، دسته‌بندی‌شده و حرفه‌ای");
        subtitle.setTextSize(12f);
        subtitle.setTextColor(darkMode
                ? Color.rgb(156, 163, 175)
                : Color.rgb(107, 114, 128));
        subtitle.setPadding(0, dp(3), 0, dp(14));
        root.addView(subtitle);

        addSettingsSection(
                root,
                "👤",
                "حساب و پروفایل",
                accountSummary(),
                () -> showSettings());

        addSettingsSection(
                root,
                "☎",
                "تماس و صدا",
                "Smart Audio • Codec • Speaker • Microphone",
                () -> showSettingsInfo(
                        "تماس و صدا",
                        "تنظیمات صوتی فعال شامل Smart Audio، Echo Cancellation، "
                                + "Adaptive Jitter و اولویت Codecها است. "
                                + "کنترل‌های تخصصی‌تر در نسخه‌های بعدی به این بخش منتقل می‌شوند."));

        addSettingsSection(
                root,
                "🌐",
                "شبکه و اتصال",
                networkSummary(),
                () -> showSettings());

        addSettingsSection(
                root,
                "🔒",
                "امنیت",
                securitySummary(),
                () -> showSettings());

        addSettingsSection(
                root,
                "🔔",
                "اعلان‌ها و تماس ورودی",
                "اعلان تماس • تماس ازدست‌رفته • مزاحم نشوید",
                () -> showSettingsInfo(
                        "اعلان‌ها و تماس ورودی",
                        "این بخش برای مدیریت تماس ورودی، اعلان تماس ازدست‌رفته، "
                                + "صدای زنگ و حالت مزاحم نشوید آماده شده است."));

        addSettingsSection(
                root,
                "◐",
                "ظاهر و زبان",
                (darkMode ? "حالت تیره" : "حالت روشن")
                        + " • "
                        + prefs.getString("language", "fa"),
                () -> showAppearanceQuickSettings());

        addSettingsSection(
                root,
                "↻",
                "بروزرسانی",
                "نسخه فعلی v" + BuildConfig.VERSION_NAME,
                () -> checkUpdates(true));

        addSettingsSection(
                root,
                "PBX",
                "مرکز PBX",
                pbxSummary(),
                () -> showPbxCenter());

        addSettingsSection(
                root,
                "⚙",
                "پیشرفته",
                "SIP • STUN • TURN • Smart Route • Failover",
                () -> showSettings());

        addSettingsSection(
                root,
                "ⓘ",
                "درباره MOLIDO VoIP",
                "Build " + BuildConfig.VERSION_NAME + " • Android",
                () -> showSettingsInfo(
                        "درباره MOLIDO VoIP",
                        "MOLIDO VoIP\\n"
                                + "Version: " + BuildConfig.VERSION_NAME + "\\n\\n"
                                + "Secure SIP • ICE/STUN/TURN • Smart Route • "
                                + "Auto Reconnect • SIP Failover • SRTP"));

        new AlertDialog.Builder(this)
                .setView(scroll)
                .setNegativeButton("بستن", null)
                .show();
    }

    private void addSettingsSection(
            LinearLayout parent,
            String icon,
            String title,
            String subtitle,
            Runnable action) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(roundedBg(
                darkMode ? "#1F2937" : "#FFFFFF",
                18,
                darkMode ? "#374151" : "#E5E7EB"));
        card.setElevation(dp(2));

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(23f);
        iconView.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                dp(42), dp(42));
        card.addView(iconView, iconLp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(10), 0, dp(8), 0);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(15f);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(
                darkMode ? Color.WHITE : Color.rgb(31, 41, 55));

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(11f);
        subtitleView.setTextColor(
                darkMode
                        ? Color.rgb(156, 163, 175)
                        : Color.rgb(107, 114, 128));
        subtitleView.setPadding(0, dp(3), 0, 0);

        texts.addView(titleView);
        texts.addView(subtitleView);

        card.addView(texts, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView arrow = new TextView(this);
        arrow.setText("‹");
        arrow.setTextSize(26f);
        arrow.setTextColor(
                darkMode
                        ? Color.rgb(156, 163, 175)
                        : Color.rgb(107, 114, 128));
        card.addView(arrow);

        card.setOnClickListener(v -> {
            if (action != null) action.run();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        parent.addView(card, lp);
    }

    private String accountSummary() {
        String displayName = prefs.getString("display_name", "").trim();
        String extension = prefs.getString("extension", "").trim();

        if (displayName.isEmpty()) displayName = "کاربر MOLIDO";
        if (extension.isEmpty()) extension = "بدون داخلی";

        return displayName + " • " + extension;
    }

    private String networkSummary() {
        String route = prefs.getString("route_mode", "AUTO")
                .trim()
                .toUpperCase(java.util.Locale.ROOT);

        String transport = prefs.getString("sip_transport", "TLS")
                .trim()
                .toUpperCase(java.util.Locale.ROOT);

        String turn = prefs.getString("turn_transport", "OFF")
                .trim()
                .toUpperCase(java.util.Locale.ROOT);

        return transport + " • Smart Route " + route + " • TURN " + turn;
    }

    private String securitySummary() {
        String mode = prefs.getString("media_security", "REQUIRED")
                .trim()
                .toUpperCase(java.util.Locale.ROOT);

        return "SRTP " + mode + " • SecureStore فعال";
    }

    private void showAppearanceQuickSettings() {
        String currentLanguage = prefs.getString("language", "fa");

        String[] items = {
                darkMode ? "تغییر به حالت روشن" : "تغییر به حالت تیره",
                "زبان فارسی",
                "English",
                "بلوچی"
        };

        new AlertDialog.Builder(this)
                .setTitle("ظاهر و زبان")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        prefs.edit()
                                .putBoolean("dark_mode", !darkMode)
                                .apply();
                        recreate();
                        return;
                    }

                    String lang = currentLanguage;
                    if (which == 1) lang = "fa";
                    if (which == 2) lang = "en";
                    if (which == 3) lang = "bal";

                    prefs.edit()
                            .putString("language", lang)
                            .apply();

                    showToast("زبان ذخیره شد: " + lang);
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private String pbxSummary() {
        int didCount = 0;
        try {
            JSONArray dids = new JSONArray(prefs.getString("pbx_dids_json", "[]"));
            didCount = dids.length();
        } catch (Exception ignored) {}

        String sip = prefs.getString("sip_username", "").trim();
        String registrar = prefs.getString("sip_registrar", "").trim();

        String sipState = (!sip.isEmpty() && !registrar.isEmpty())
                ? "SIP آماده"
                : "SIP تنظیم نشده";

        return sipState + " • " + didCount + " DID";
    }

    private void showPbxCenter() {
        String[] items = {
                "SIP Account",
                "DID Numbers",
                "SIP Trunks",
                "SMS Center"
        };

        new AlertDialog.Builder(this)
                .setTitle("مرکز PBX")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showSipAccountManager();
                    } else if (which == 1) {
                        showDidManager();
                    } else if (which == 2) {
                        showSettingsInfo(
                                "SIP Trunks",
                                "مدیریت Trunk در فاز بعدی اضافه می‌شود. "
                                        + "این بخش برای Provider، Host، Auth، Route و Failover آماده خواهد شد.");
                    } else if (which == 3) {
                        showSettingsInfo(
                                "SMS Center",
                                "مرکز پیامک در فاز بعدی اضافه می‌شود. "
                                        + "برای SMS واقعی به Provider/API یا PBX backend نیاز است.");
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showSipAccountManager() {
        String registrar = prefs.getString("sip_registrar", "").trim();
        String backup = prefs.getString("sip_backup_registrar", "").trim();
        String username = prefs.getString("sip_username", "").trim();
        String extension = prefs.getString("extension", "").trim();
        String transport = prefs.getString("sip_transport", "TLS").trim();
        String media = prefs.getString("media_security", "REQUIRED").trim();

        String status = sipManager != null && sipManager.isRegistered()
                ? "Registered"
                : "Offline";

        String message =
                "Status: " + status + "\n\n"
                + "Extension: " + (extension.isEmpty() ? "—" : extension) + "\n"
                + "Username: " + (username.isEmpty() ? "—" : username) + "\n"
                + "Registrar: " + (registrar.isEmpty() ? "—" : registrar) + "\n"
                + "Backup: " + (backup.isEmpty() ? "—" : backup) + "\n"
                + "Transport: " + transport.toUpperCase(java.util.Locale.ROOT) + "\n"
                + "Secure Media: " + media.toUpperCase(java.util.Locale.ROOT) + "\n\n"
                + "رمز SIP فقط در SecureStore نگهداری می‌شود.";

        new AlertDialog.Builder(this)
                .setTitle("SIP Account")
                .setMessage(message)
                .setPositiveButton("ویرایش", (d, w) -> showSettings())
                .setNeutralButton(
                        sipManager != null && sipManager.isRegistered()
                                ? "Unregister"
                                : "Register",
                        (d, w) -> {
                            if (sipManager != null && sipManager.isRegistered()) {
                                sipManager.unregister();
                            } else if (isSipProfileReady()) {
                                startSipRegistration();
                            } else {
                                showSettings();
                            }
                        })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showDidManager() {
        JSONArray dids;
        try {
            dids = new JSONArray(prefs.getString("pbx_dids_json", "[]"));
        } catch (Exception e) {
            dids = new JSONArray();
        }

        final JSONArray current = dids;
        int count = current.length();

        String[] rows = new String[count + 1];
        rows[0] = "＋ افزودن DID جدید";

        for (int i = 0; i < count; i++) {
            JSONObject item = current.optJSONObject(i);
            if (item == null) {
                rows[i + 1] = "DID";
                continue;
            }

            String number = item.optString("number", "—");
            String label = item.optString("label", "");
            String route = item.optString("route", "");
            boolean enabled = item.optBoolean("enabled", true);

            rows[i + 1] =
                    (enabled ? "● " : "○ ")
                    + number
                    + (label.isEmpty() ? "" : " • " + label)
                    + (route.isEmpty() ? "" : " → " + route);
        }

        new AlertDialog.Builder(this)
                .setTitle("DID Numbers")
                .setItems(rows, (dialog, which) -> {
                    if (which == 0) {
                        showDidEditor(-1);
                    } else {
                        showDidActions(which - 1);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showDidActions(int index) {
        JSONArray dids;
        try {
            dids = new JSONArray(prefs.getString("pbx_dids_json", "[]"));
        } catch (Exception e) {
            showToast("خواندن DID ناموفق بود.");
            return;
        }

        JSONObject item = dids.optJSONObject(index);
        if (item == null) return;

        String number = item.optString("number", "DID");
        boolean enabled = item.optBoolean("enabled", true);

        String[] actions = {
                "ویرایش",
                enabled ? "غیرفعال کردن" : "فعال کردن",
                "حذف"
        };

        new AlertDialog.Builder(this)
                .setTitle(number)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showDidEditor(index);
                    } else if (which == 1) {
                        setDidEnabled(index, !enabled);
                    } else if (which == 2) {
                        deleteDid(index);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showDidEditor(int index) {
        JSONArray dids;
        try {
            dids = new JSONArray(prefs.getString("pbx_dids_json", "[]"));
        } catch (Exception e) {
            dids = new JSONArray();
        }

        JSONObject existing = index >= 0 ? dids.optJSONObject(index) : null;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));

        EditText number = field(
                "DID Number / E.164",
                existing == null ? "" : existing.optString("number", ""));
        number.setInputType(InputType.TYPE_CLASS_PHONE);

        EditText label = field(
                "نام / Label",
                existing == null ? "" : existing.optString("label", ""));

        EditText route = field(
                "Route / Extension",
                existing == null ? "" : existing.optString("route", ""));
        route.setInputType(InputType.TYPE_CLASS_TEXT);

        form.addView(number);
        form.addView(label);
        form.addView(route);

        TextView note = new TextView(this);
        note.setText(
                "این نسخه DID را به‌صورت محلی مدیریت می‌کند. "
                        + "Provisioning واقعی شماره و Route نیازمند PBX/Provider backend است.");
        note.setTextSize(11f);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(8), 0, 0);
        form.addView(note);

        final JSONArray didList = dids;

        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle(index < 0 ? "افزودن DID" : "ویرایش DID")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", null)
                .create();

        ad.setOnShowListener(dialog -> {
            ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String numberValue = number.getText().toString().trim();
                if (numberValue.isEmpty()) {
                    number.setError("شماره DID لازم است.");
                    return;
                }

                try {
                    JSONObject item = existing == null
                            ? new JSONObject()
                            : new JSONObject(existing.toString());

                    item.put("number", numberValue);
                    item.put("label", label.getText().toString().trim());
                    item.put("route", route.getText().toString().trim());

                    if (!item.has("enabled")) {
                        item.put("enabled", true);
                    }

                    JSONArray next = new JSONArray();

                    for (int i = 0; i < didList.length(); i++) {
                        if (i == index) {
                            next.put(item);
                        } else {
                            JSONObject old = didList.optJSONObject(i);
                            if (old != null) next.put(old);
                        }
                    }

                    if (index < 0) {
                        next.put(item);
                    }

                    prefs.edit()
                            .putString("pbx_dids_json", next.toString())
                            .apply();

                    ad.dismiss();
                    showDidManager();

                } catch (Exception e) {
                    showToast("ذخیره DID ناموفق بود.");
                }
            });
        });

        ad.show();
    }

    private void setDidEnabled(int index, boolean enabled) {
        try {
            JSONArray dids = new JSONArray(
                    prefs.getString("pbx_dids_json", "[]"));

            JSONArray next = new JSONArray();

            for (int i = 0; i < dids.length(); i++) {
                JSONObject item = dids.optJSONObject(i);
                if (item == null) continue;

                JSONObject copy = new JSONObject(item.toString());
                if (i == index) {
                    copy.put("enabled", enabled);
                }
                next.put(copy);
            }

            prefs.edit()
                    .putString("pbx_dids_json", next.toString())
                    .apply();

            showDidManager();

        } catch (Exception e) {
            showToast("تغییر وضعیت DID ناموفق بود.");
        }
    }

    private void deleteDid(int index) {
        try {
            JSONArray dids = new JSONArray(
                    prefs.getString("pbx_dids_json", "[]"));

            JSONArray next = new JSONArray();

            for (int i = 0; i < dids.length(); i++) {
                if (i == index) continue;
                JSONObject item = dids.optJSONObject(i);
                if (item != null) next.put(item);
            }

            prefs.edit()
                    .putString("pbx_dids_json", next.toString())
                    .apply();

            showDidManager();

        } catch (Exception e) {
            showToast("حذف DID ناموفق بود.");
        }
    }

    private void showSettingsInfo(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("باشه", null)
                .show();
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

        EditText sipBackupRegistrar = field(
                "Backup SIP Registrar (اختیاری)",
                prefs.getString("sip_backup_registrar", ""));

        EditText stunServer = field(
                "STUN Server (اختیاری، مثال: stun.example.com:3478)",
                prefs.getString("stun_server", ""));

        EditText turnServer = field(
                "TURN Server (اختیاری، مثال: turn.example.com:3478)",
                prefs.getString("turn_server", ""));

        EditText turnUsername = field(
                "TURN Username",
                prefs.getString("turn_username", ""));

        EditText turnPassword = field("TURN Password", "");
        turnPassword.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        turnPassword.setHint(hasStoredTurnPassword()
                ? "رمز TURN ذخیره شده • برای تغییر، رمز جدید را وارد کنید"
                : "TURN Password");

        EditText turnTransport = field(
                "TURN Mode: OFF / UDP / TCP / TLS",
                prefs.getString("turn_transport", "OFF"));

        EditText routeMode = field(
                "Smart Route: AUTO / DIRECT / RELAY",
                prefs.getString("route_mode", "AUTO"));

        EditText mediaSecurity = field(
                "Secure Media: REQUIRED / PREFERRED / OFF",
                prefs.getString("media_security", "REQUIRED"));

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
        form.addView(sipBackupRegistrar);
        form.addView(stunServer);
        form.addView(turnServer);
        form.addView(turnUsername);
        form.addView(turnPassword);
        form.addView(turnTransport);
        form.addView(routeMode);
        form.addView(mediaSecurity);
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

                String turnTransportValue = turnTransport.getText().toString()
                        .trim().toUpperCase(java.util.Locale.ROOT);
                if (!turnTransportValue.equals("OFF")
                        && !turnTransportValue.equals("UDP")
                        && !turnTransportValue.equals("TCP")
                        && !turnTransportValue.equals("TLS")) {
                    turnTransport.setError("TURN فقط OFF، UDP، TCP یا TLS");
                    return;
                }

                String routeModeValue = routeMode.getText().toString()
                        .trim().toUpperCase(java.util.Locale.ROOT);
                if (!routeModeValue.equals("AUTO")
                        && !routeModeValue.equals("DIRECT")
                        && !routeModeValue.equals("RELAY")) {
                    routeMode.setError("Smart Route فقط AUTO، DIRECT یا RELAY");
                    return;
                }

                String mediaSecurityValue = mediaSecurity.getText().toString()
                        .trim().toUpperCase(java.util.Locale.ROOT);
                if (!mediaSecurityValue.equals("REQUIRED")
                        && !mediaSecurityValue.equals("PREFERRED")
                        && !mediaSecurityValue.equals("OFF")) {
                    mediaSecurity.setError("Secure Media فقط REQUIRED، PREFERRED یا OFF");
                    return;
                }

                if (!turnTransportValue.equals("OFF")
                        && (turnServer.getText().toString().trim().isEmpty()
                        || turnUsername.getText().toString().trim().isEmpty()
                        || (!hasStoredTurnPassword()
                        && turnPassword.getText().toString().isEmpty()))) {
                    turnServer.setError("برای TURN، Server / Username / Password لازم است.");
                    return;
                }

                String newPassword = sipPassword.getText().toString();
                String newTurnPassword = turnPassword.getText().toString();

                try {
                    if (!newPassword.isEmpty()) {
                        secureStore.putSecret("sip_password", newPassword);
                    }
                    if (!newTurnPassword.isEmpty()) {
                        secureStore.putSecret("turn_password", newTurnPassword);
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
                        .putString("sip_backup_registrar", sipBackupRegistrar.getText().toString().trim())
                        .putString("stun_server", stunServer.getText().toString().trim())
                        .putString("turn_server", turnServer.getText().toString().trim())
                        .putString("turn_username", turnUsername.getText().toString().trim())
                        .putString("turn_transport", turnTransportValue)
                        .putString("route_mode", routeModeValue)
                        .putString("media_security", mediaSecurityValue)
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

    private void openContacts() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.READ_CONTACTS},
                    REQ_CONTACTS);
            return;
        }

        showContactsDialog();
    }

    private void showContactsDialog() {
        LinkedHashMap<String, String> contacts = new LinkedHashMap<>();

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                            + " COLLATE NOCASE ASC");

            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER);

                while (cursor.moveToNext() && contacts.size() < 300) {
                    String name = nameIndex >= 0 ? cursor.getString(nameIndex) : "";
                    String number = numberIndex >= 0 ? cursor.getString(numberIndex) : "";

                    if (number == null) continue;

                    number = normalizeContactNumber(number);
                    if (number.isEmpty()) continue;

                    if (name == null || name.trim().isEmpty()) {
                        name = number;
                    }

                    String key = name.trim() + "\n" + number;
                    contacts.putIfAbsent(key, number);
                }
            }
        } catch (Exception e) {
            showToast("خواندن مخاطبین ناموفق بود.");
            return;
        } finally {
            if (cursor != null) cursor.close();
        }

        if (contacts.isEmpty()) {
            showToast("مخاطبی با شماره تلفن پیدا نشد.");
            return;
        }

        ArrayList<String> rows = new ArrayList<>();
        ArrayList<String> numbers = new ArrayList<>();

        for (Map.Entry<String, String> entry : contacts.entrySet()) {
            String[] parts = entry.getKey().split("\n", 2);
            String name = parts.length > 0 ? parts[0] : entry.getValue();
            String number = entry.getValue();

            rows.add(name + "   •   " + number);
            numbers.add(number);
        }

        String[] rowArray = rows.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("مخاطبین")
                .setItems(rowArray, (dialog, which) -> {
                    String number = numbers.get(which);
                    dialInput.setText(number);
                    dialInput.setSelection(number.length());
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private String normalizeContactNumber(String raw) {
        if (raw == null) return "";

        String value = raw.trim()
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "");

        if (value.startsWith("00")) {
            value = "+" + value.substring(2);
        }

        return value;
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
                if (pendingIncomingAnswer) {
                    pendingIncomingAnswer = false;
                    sipManager.acceptIncomingCall();
                } else {
                    String number = pendingDialNumber;
                    pendingDialNumber = "";
                    if (!number.isEmpty()) placeOutgoingCall(number);
                }
            } else {
                pendingIncomingAnswer = false;
                pendingDialNumber = "";
                showToast("برای تماس صوتی، اجازه میکروفن لازم است.");
            }
        }

        if (requestCode == REQ_CONTACTS) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showContactsDialog();
            } else {
                showToast("برای نمایش مخاطبین، اجازه Contacts لازم است.");
            }
        }
    }

    private void answerIncomingCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            pendingIncomingAnswer = true;
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_MIC);
            return;
        }

        pendingIncomingAnswer = false;
        sipManager.acceptIncomingCall();
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

        if ("Incoming".equals(state)) {
            activeCallDirection = "IN";
            activeCallPeer = safeDetail.isEmpty() ? "تماس ورودی" : safeDetail;
            activeCallConnected = false;
            activeCallStartedAt = System.currentTimeMillis();
        } else if ("Calling".equals(state)) {
            activeCallDirection = "OUT";
            activeCallPeer = number;
            activeCallConnected = false;
            activeCallStartedAt = System.currentTimeMillis();
        } else if ("Connected".equals(state)) {
            activeCallConnected = true;
            if (activeCallStartedAt == 0L) {
                activeCallStartedAt = System.currentTimeMillis();
            }
        }

        if ("Incoming".equals(state)) {
            text = "تماس ورودی" + (safeDetail.isEmpty() ? "" : " • " + safeDetail);
        }

        callStatus.setText(text);

        boolean incoming = "Incoming".equals(state);
        if (answerButton != null) {
            answerButton.setVisibility(incoming ? android.view.View.VISIBLE : android.view.View.GONE);
            answerButton.setEnabled(incoming);
        }
        if (rejectButton != null) {
            rejectButton.setVisibility(incoming ? android.view.View.VISIBLE : android.view.View.GONE);
            rejectButton.setEnabled(incoming);
        }

        boolean controls = active
                && !incoming
                && !"Calling".equals(state)
                && !"Ringing".equals(state);

        hangupButton.setEnabled(active && !"Incoming".equals(state));
        muteButton.setEnabled(controls);
        speakerButton.setEnabled(controls);
        callButton.setEnabled(!active);

        if ("Ended".equals(state) || "Failed".equals(state)) {
            muteButton.setText("Mute");
            setSpeakerEnabled(false);

            String peer = activeCallPeer;
            if (peer == null || peer.trim().isEmpty()) {
                peer = number;
            }

            String result;
            if ("IN".equals(activeCallDirection) && !activeCallConnected) {
                result = "MISSED";
            } else if ("Failed".equals(state)) {
                result = "FAILED";
            } else {
                result = "COMPLETED";
            }

            saveCallHistoryEntry(
                    activeCallDirection.isEmpty() ? "OUT" : activeCallDirection,
                    peer,
                    result,
                    activeCallStartedAt == 0L
                            ? System.currentTimeMillis()
                            : activeCallStartedAt,
                    System.currentTimeMillis());

            activeCallPeer = "";
            activeCallDirection = "";
            activeCallConnected = false;
            activeCallStartedAt = 0L;
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

    private void saveCallHistoryEntry(
            String direction,
            String peer,
            String result,
            long startedAt,
            long endedAt) {

        if (peer == null || peer.trim().isEmpty()) return;

        try {
            JSONArray history = new JSONArray(
                    prefs.getString("call_history_json", "[]"));

            JSONObject item = new JSONObject();
            item.put("direction", direction == null ? "OUT" : direction);
            item.put("peer", peer.trim());
            item.put("result", result == null ? "" : result);
            item.put("startedAt", startedAt);
            item.put("endedAt", endedAt);

            JSONArray next = new JSONArray();
            next.put(item);

            int limit = Math.min(history.length(), 19);
            for (int i = 0; i < limit; i++) {
                JSONObject old = history.optJSONObject(i);
                if (old != null) next.put(old);
            }

            prefs.edit()
                    .putString("call_history_json", next.toString())
                    .putString("last_call_number", peer.trim())
                    .putString("last_call_result", result == null ? "" : result)
                    .putLong("last_call_time", endedAt)
                    .apply();

        } catch (Exception ignored) {
        }
    }

    private void showCallHistory() {
        try {
            JSONArray history = new JSONArray(
                    prefs.getString("call_history_json", "[]"));

            if (history.length() == 0) {
                showToast("هنوز تماسی ثبت نشده است.");
                return;
            }

            int count = Math.min(history.length(), 20);
            String[] rows = new String[count];
            String[] peers = new String[count];

            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat(
                            "MM/dd  HH:mm",
                            java.util.Locale.getDefault());

            for (int i = 0; i < count; i++) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) continue;

                String direction = item.optString("direction", "OUT");
                String peer = item.optString("peer", "—");
                String result = item.optString("result", "");
                long endedAt = item.optLong("endedAt", 0L);

                String icon = "IN".equals(direction) ? "↙" : "↗";
                String state;
                if ("MISSED".equals(result)) state = "از دست رفته";
                else if ("FAILED".equals(result)) state = "ناموفق";
                else state = "پایان یافته";

                String when = endedAt > 0
                        ? sdf.format(new java.util.Date(endedAt))
                        : "";

                rows[i] = icon + "  " + peer + "   •   " + state
                        + (when.isEmpty() ? "" : "   •   " + when);
                peers[i] = peer;
            }

            new AlertDialog.Builder(this)
                    .setTitle("تماس‌های اخیر")
                    .setItems(rows, (dialog, which) -> {
                        String peer = peers[which];
                        if (peer != null && !peer.isEmpty()
                                && !"تماس ورودی".equals(peer)) {
                            dialInput.setText(peer);
                            dialInput.setSelection(peer.length());
                        }
                    })
                    .setNeutralButton("پاک کردن تاریخچه", (dialog, which) -> {
                        prefs.edit()
                                .remove("call_history_json")
                                .remove("last_call_number")
                                .remove("last_call_result")
                                .remove("last_call_time")
                                .apply();
                        showToast("تاریخچه تماس پاک شد.");
                    })
                    .setNegativeButton("بستن", null)
                    .show();

        } catch (Exception e) {
            showToast("خواندن تاریخچه تماس ناموفق بود.");
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
            String mediaMode = prefs.getString("media_security", "REQUIRED")
                    .trim().toUpperCase(java.util.Locale.ROOT);
            if (audioQuality != null) {
                audioQuality.setText("Secure Media • " + mediaMode + " • SRTP");
            }

            sipManager.setBackupRegistrar(
                    prefs.getString("sip_backup_registrar", "").trim());

            sipManager.setStunServer(
                    prefs.getString("stun_server", "").trim());

            sipManager.setTurnConfiguration(
                    prefs.getString("turn_server", "").trim(),
                    prefs.getString("turn_username", "").trim(),
                    getStoredTurnPassword(),
                    prefs.getString("turn_transport", "OFF").trim());

            sipManager.setRouteMode(
                    prefs.getString("route_mode", "AUTO").trim());

            sipManager.setMediaSecurityMode(
                    prefs.getString("media_security", "REQUIRED").trim());

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

    private boolean hasStoredTurnPassword() {
        try {
            String value = secureStore.getSecret("turn_password");
            return value != null && !value.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String getStoredTurnPassword() {
        try {
            String value = secureStore.getSecret("turn_password");
            return value == null ? "" : value;
        } catch (Exception e) {
            return "";
        }
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
            if (connectivityManager != null) connectivityManager.stop();
        } catch (Exception ignored) {
        }
        try {
            if (sipManager != null) sipManager.shutdown();
        } catch (Exception ignored) {
        }
        web.destroy();
        super.onDestroy();
    }
}
