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
    private boolean adminUnlocked = false;
    private long adminUnlockedAt = 0L;
    private static final long ADMIN_SESSION_MS = 10L * 60L * 1000L;
    private static final int ADMIN_MAX_FAILED_ATTEMPTS = 5;
    private static final long ADMIN_LOCKOUT_MS = 5L * 60L * 1000L;

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

        sipManager = new SipRegistrationManager(
                this,
                (regState, detail, success) ->
                        runOnUiThread(() ->
                                updateSipRegistrationUi(regState, detail, success)));

        sipManager.setCallEventListener(
                (callState, detail, active) ->
                        runOnUiThread(() ->
                                updateCallUi(callState, detail, active)));

        sipManager.setAudioQualityListener(
                (profile, score, loss, jitter, rtt, down, up) ->
                        runOnUiThread(() ->
                                updateSmartAudioUi(
                                        profile, score, loss, jitter, rtt, down, up)));

        sipManager.setRouteListener(
                (networkType, route, reachable) ->
                        runOnUiThread(() -> {
                            if (routeStatus != null) {
                                routeStatus.setText(
                                        reachable
                                                ? "مسیر • " + networkType + " • " + route
                                                : "مسیر • Offline");
                                routeStatus.setTextColor(
                                        reachable
                                                ? Color.rgb(16, 185, 129)
                                                : Color.rgb(220, 38, 38));
                            }
                        }));

        connectivityManager = new MolidoConnectivityManager(
                this,
                (reachable, type, metered) ->
                        runOnUiThread(() ->
                                sipManager.onNetworkStateChanged(
                                        reachable, type, metered)));

        darkMode = prefs.getBoolean("dark_mode", false);

        int bg = darkMode
                ? Color.rgb(15, 23, 42)
                : Color.rgb(248, 250, 252);

        int card = darkMode
                ? Color.rgb(30, 41, 59)
                : Color.WHITE;

        int primaryText = darkMode
                ? Color.WHITE
                : Color.rgb(15, 23, 42);

        int secondaryText = darkMode
                ? Color.rgb(148, 163, 184)
                : Color.rgb(100, 116, 139);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(12), dp(10));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);

        TextView brand = new TextView(this);
        brand.setText("MOLIDO");
        brand.setTextSize(22f);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setTextColor(primaryText);

        String displayName = prefs.getString("display_name", "").trim();
        String ext = prefs.getString("extension", "").trim();

        extensionLabel = new TextView(this);
        extensionLabel.setText(
                (displayName.isEmpty() ? "کاربر" : displayName)
                        + (ext.isEmpty() ? "" : "  •  " + ext));
        extensionLabel.setTextSize(12f);
        extensionLabel.setTextColor(secondaryText);
        extensionLabel.setPadding(0, dp(2), 0, 0);

        identity.addView(brand);
        identity.addView(extensionLabel);

        header.addView(
                identity,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f));

        Button qr = new Button(this);
        qr.setText("QR");
        qr.setTextSize(12f);
        qr.setAllCaps(false);
        styleSecondaryButton(qr);
        qr.setOnClickListener(v -> startQrScan());

        Button more = new Button(this);
        more.setText("⋮");
        more.setTextSize(22f);
        more.setAllCaps(false);
        styleSecondaryButton(more);
        more.setOnClickListener(v -> showMoreCenter());

        header.addView(qr);
        header.addView(more);
        root.addView(header);

        LinearLayout connectionRow = new LinearLayout(this);
        connectionRow.setOrientation(LinearLayout.HORIZONTAL);
        connectionRow.setGravity(Gravity.CENTER_VERTICAL);
        connectionRow.setPadding(dp(16), dp(8), dp(16), dp(8));
        connectionRow.setBackground(
                roundedBg(
                        darkMode ? "#1E293B" : "#FFFFFF",
                        18,
                        darkMode ? "#334155" : "#E2E8F0"));

        connectionStatus = new TextView(this);
        connectionStatus.setTextSize(13f);
        connectionStatus.setTypeface(Typeface.DEFAULT_BOLD);

        String registrar = prefs.getString("sip_registrar", "").trim();
        String username = prefs.getString("sip_username", "").trim();
        boolean profileReady =
                !registrar.isEmpty()
                        && !username.isEmpty()
                        && hasStoredSipPassword();

        connectionStatus.setText(
                profileReady
                        ? "● آماده اتصال"
                        : "● حساب تلفن تنظیم نشده");

        connectionStatus.setTextColor(
                profileReady
                        ? Color.rgb(22, 163, 74)
                        : Color.rgb(217, 119, 6));

        connectionRow.addView(
                connectionStatus,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f));

        registerButton = new Button(this);
        registerButton.setText(profileReady ? "اتصال" : "تنظیم");
        registerButton.setTextSize(12f);
        registerButton.setAllCaps(false);
        stylePrimaryButton(registerButton);
        registerButton.setOnClickListener(v -> {
            if (!isSipProfileReady()) {
                showSettingsHub();
                return;
            }
            startSipRegistration();
        });

        connectionRow.addView(registerButton);

        LinearLayout.LayoutParams connectionLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        connectionLp.setMargins(dp(12), 0, dp(12), dp(8));
        root.addView(connectionRow, connectionLp);

        routeStatus = new TextView(this);
        routeStatus.setVisibility(
                adminUnlocked
                        ? android.view.View.VISIBLE
                        : android.view.View.GONE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(16));
        scroll.addView(content);

        TextView title = new TextView(this);
        title.setText("تماس");
        title.setTextSize(28f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(primaryText);
        content.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("شماره یا داخلی را وارد کنید");
        subtitle.setTextSize(12f);
        subtitle.setTextColor(secondaryText);
        subtitle.setPadding(0, dp(2), 0, dp(12));
        content.addView(subtitle);

        LinearLayout dialCard = new LinearLayout(this);
        dialCard.setOrientation(LinearLayout.VERTICAL);
        dialCard.setPadding(dp(14), dp(14), dp(14), dp(14));
        dialCard.setBackground(
                roundedBg(
                        darkMode ? "#1E293B" : "#FFFFFF",
                        24,
                        darkMode ? "#334155" : "#E2E8F0"));

        dialInput = new EditText(this);
        dialInput.setHint("شماره یا داخلی");
        dialInput.setInputType(InputType.TYPE_CLASS_PHONE);
        dialInput.setGravity(Gravity.CENTER);
        dialInput.setSingleLine(true);
        dialInput.setTextSize(25f);
        dialInput.setTextColor(primaryText);
        dialInput.setHintTextColor(secondaryText);
        dialInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        dialInput.setBackground(
                roundedBg(
                        darkMode ? "#0F172A" : "#F8FAFC",
                        18,
                        darkMode ? "#334155" : "#E2E8F0"));

        dialCard.addView(dialInput);

        LinearLayout dialActions = new LinearLayout(this);
        dialActions.setOrientation(LinearLayout.HORIZONTAL);
        dialActions.setGravity(Gravity.CENTER);
        dialActions.setPadding(0, dp(10), 0, 0);

        Button keypadButton = new Button(this);
        keypadButton.setText("⌨  صفحه کلید");
        keypadButton.setAllCaps(false);
        styleSecondaryButton(keypadButton);
        keypadButton.setOnClickListener(v -> showCleanDialpad());

        callButton = new Button(this);
        callButton.setText("☎  تماس");
        callButton.setAllCaps(false);
        stylePrimaryButton(callButton);
        callButton.setOnClickListener(v -> beginOutgoingCall());

        LinearLayout.LayoutParams d1 =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f);
        d1.setMargins(0, 0, dp(5), 0);

        LinearLayout.LayoutParams d2 =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f);
        d2.setMargins(dp(5), 0, 0, 0);

        dialActions.addView(keypadButton, d1);
        dialActions.addView(callButton, d2);

        dialCard.addView(dialActions);
        content.addView(dialCard);

        TextView recentTitle = new TextView(this);
        recentTitle.setText("اخیر");
        recentTitle.setTextSize(16f);
        recentTitle.setTypeface(Typeface.DEFAULT_BOLD);
        recentTitle.setTextColor(primaryText);
        recentTitle.setPadding(0, dp(18), 0, dp(8));
        content.addView(recentTitle);

        LinearLayout recentCard = new LinearLayout(this);
        recentCard.setOrientation(LinearLayout.HORIZONTAL);
        recentCard.setGravity(Gravity.CENTER_VERTICAL);
        recentCard.setPadding(dp(14), dp(13), dp(14), dp(13));
        recentCard.setBackground(
                roundedBg(
                        darkMode ? "#1E293B" : "#FFFFFF",
                        20,
                        darkMode ? "#334155" : "#E2E8F0"));

        String lastCallNumber =
                prefs.getString("last_call_number", "").trim();
        String lastCallResult =
                prefs.getString("last_call_result", "").trim();

        LinearLayout recentText = new LinearLayout(this);
        recentText.setOrientation(LinearLayout.VERTICAL);

        TextView recentNumber = new TextView(this);
        recentNumber.setText(
                lastCallNumber.isEmpty()
                        ? "هنوز تماسی ثبت نشده"
                        : lastCallNumber);
        recentNumber.setTextSize(15f);
        recentNumber.setTypeface(Typeface.DEFAULT_BOLD);
        recentNumber.setTextColor(primaryText);

        TextView recentMeta = new TextView(this);
        recentMeta.setText(
                lastCallNumber.isEmpty()
                        ? "تماس‌های شما اینجا نمایش داده می‌شوند"
                        : (lastCallResult.isEmpty()
                                ? "آخرین تماس"
                                : lastCallResult));
        recentMeta.setTextSize(11f);
        recentMeta.setTextColor(secondaryText);
        recentMeta.setPadding(0, dp(3), 0, 0);

        recentText.addView(recentNumber);
        recentText.addView(recentMeta);

        recentCard.addView(
                recentText,
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f));

        Button recentCall = new Button(this);
        recentCall.setText("☎");
        recentCall.setTextSize(18f);
        recentCall.setAllCaps(false);
        stylePrimaryButton(recentCall);
        recentCall.setEnabled(!lastCallNumber.isEmpty());
        recentCall.setOnClickListener(v -> {
            if (!lastCallNumber.isEmpty()) {
                dialInput.setText(lastCallNumber);
                beginOutgoingCall();
            }
        });

        recentCard.addView(recentCall);
        recentCard.setOnClickListener(v -> showCallHistory());
        content.addView(recentCard);

        LinearLayout callCard = new LinearLayout(this);
        callCard.setOrientation(LinearLayout.VERTICAL);
        callCard.setPadding(dp(14), dp(13), dp(14), dp(13));
        callCard.setBackground(
                roundedBg(
                        darkMode ? "#1E293B" : "#FFFFFF",
                        20,
                        darkMode ? "#334155" : "#E2E8F0"));

        callStatus = new TextView(this);
        callStatus.setText("آماده تماس");
        callStatus.setTextSize(14f);
        callStatus.setTypeface(Typeface.DEFAULT_BOLD);
        callStatus.setTextColor(primaryText);
        callCard.addView(callStatus);

        audioQuality = new TextView(this);
        audioQuality.setText("کیفیت تماس • آماده");
        audioQuality.setTextSize(11f);
        audioQuality.setTextColor(secondaryText);
        audioQuality.setPadding(0, dp(3), 0, dp(8));
        callCard.addView(audioQuality);

        LinearLayout incoming = new LinearLayout(this);
        incoming.setOrientation(LinearLayout.HORIZONTAL);

        answerButton = new Button(this);
        answerButton.setText("پاسخ");
        answerButton.setAllCaps(false);
        answerButton.setTextColor(Color.WHITE);
        answerButton.setBackground(
                roundedBg("#16A34A", 18, null));
        answerButton.setVisibility(android.view.View.GONE);
        answerButton.setEnabled(false);
        answerButton.setOnClickListener(v -> answerIncomingCall());

        rejectButton = new Button(this);
        rejectButton.setText("رد");
        rejectButton.setAllCaps(false);
        rejectButton.setTextColor(Color.WHITE);
        rejectButton.setBackground(
                roundedBg("#DC2626", 18, null));
        rejectButton.setVisibility(android.view.View.GONE);
        rejectButton.setEnabled(false);
        rejectButton.setOnClickListener(v ->
                sipManager.rejectIncomingCall());

        LinearLayout.LayoutParams i1 =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f);
        i1.setMargins(0, 0, dp(4), 0);

        LinearLayout.LayoutParams i2 =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f);
        i2.setMargins(dp(4), 0, 0, 0);

        incoming.addView(answerButton, i1);
        incoming.addView(rejectButton, i2);
        callCard.addView(incoming);

        LinearLayout activeControls = new LinearLayout(this);
        activeControls.setOrientation(LinearLayout.HORIZONTAL);
        activeControls.setPadding(0, dp(8), 0, 0);

        muteButton = new Button(this);
        muteButton.setText("بی‌صدا");
        muteButton.setAllCaps(false);
        styleSecondaryButton(muteButton);
        muteButton.setEnabled(false);
        muteButton.setOnClickListener(v -> {
            boolean muted = sipManager.toggleMute();
            muteButton.setText(muted ? "وصل صدا" : "بی‌صدا");
        });

        speakerButton = new Button(this);
        speakerButton.setText("بلندگو");
        speakerButton.setAllCaps(false);
        styleSecondaryButton(speakerButton);
        speakerButton.setEnabled(false);
        speakerButton.setOnClickListener(v -> toggleSpeaker());

        hangupButton = new Button(this);
        hangupButton.setText("قطع");
        hangupButton.setAllCaps(false);
        hangupButton.setTextColor(Color.WHITE);
        hangupButton.setBackground(
                roundedBg("#DC2626", 18, null));
        hangupButton.setEnabled(false);
        hangupButton.setOnClickListener(v ->
                sipManager.terminateCall());

        LinearLayout.LayoutParams c1 =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f);
        c1.setMargins(0, 0, dp(4), 0);

        LinearLayout.LayoutParams c2 =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f);
        c2.setMargins(dp(4), 0, dp(4), 0);

        LinearLayout.LayoutParams c3 =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f);
        c3.setMargins(dp(4), 0, 0, 0);

        activeControls.addView(muteButton, c1);
        activeControls.addView(speakerButton, c2);
        activeControls.addView(hangupButton, c3);

        callCard.addView(activeControls);

        LinearLayout.LayoutParams callCardLp =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        callCardLp.setMargins(0, dp(14), 0, 0);
        content.addView(callCard, callCardLp);

        updateStatus = new TextView(this);
        updateStatus.setVisibility(android.view.View.GONE);

        updateButton = new Button(this);
        updateButton.setVisibility(android.view.View.GONE);

        web = new WebView(this);
        web.setVisibility(android.view.View.GONE);

        root.addView(
                scroll,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        nav.setBackgroundColor(card);
        nav.setElevation(dp(10));

        Button navCalls = new Button(this);
        navCalls.setText("☎\nتماس‌ها");
        navCalls.setTextSize(11f);
        navCalls.setAllCaps(false);
        navCalls.setTextColor(Color.rgb(37, 99, 235));
        navCalls.setBackgroundColor(Color.TRANSPARENT);
        navCalls.setOnClickListener(v -> showCallsCenter());

        Button navContacts = new Button(this);
        navContacts.setText("👤\nمخاطبین");
        navContacts.setTextSize(11f);
        navContacts.setAllCaps(false);
        navContacts.setTextColor(secondaryText);
        navContacts.setBackgroundColor(Color.TRANSPARENT);
        navContacts.setOnClickListener(v -> showContactsCenter());

        Button navMessages = new Button(this);
        navMessages.setText("✉\nپیام‌ها");
        navMessages.setTextSize(11f);
        navMessages.setAllCaps(false);
        navMessages.setTextColor(secondaryText);
        navMessages.setBackgroundColor(Color.TRANSPARENT);
        navMessages.setOnClickListener(v -> showMessagesCenter());

        Button navMore = new Button(this);
        navMore.setText("☰\nبیشتر");
        navMore.setTextSize(11f);
        navMore.setAllCaps(false);
        navMore.setTextColor(secondaryText);
        navMore.setBackgroundColor(Color.TRANSPARENT);
        navMore.setOnClickListener(v -> showMoreCenter());

        LinearLayout.LayoutParams np =
                new LinearLayout.LayoutParams(
                        0,
                        dp(64),
                        1f);

        nav.addView(navCalls, np);
        nav.addView(navContacts, np);
        nav.addView(navMessages, np);
        nav.addView(navMore, np);

        root.addView(nav);

        setContentView(root);

        connectivityManager.start();
        configureWebView();
        checkUpdates(false);
    }

    private void showCallsCenter() {
        String[] items = {
                "همه تماس‌ها",
                "تماس‌های ازدست‌رفته",
                "مخاطبین",
                "شماره‌گیر"
        };

        new AlertDialog.Builder(this)
                .setTitle("تماس‌ها")
                .setItems(items, (dialog, which) -> {
                    if (which == 0 || which == 1) {
                        showCallHistory();
                    } else if (which == 2) {
                        openContacts();
                    } else if (which == 3) {
                        showCleanDialpad();
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showContactsCenter() {
        String[] items = {
                "همه مخاطبین",
                "جستجو و انتخاب برای تماس",
                "شماره‌گیر"
        };

        new AlertDialog.Builder(this)
                .setTitle("مخاطبین")
                .setItems(items, (dialog, which) -> {
                    if (which == 0 || which == 1) {
                        openContacts();
                    } else if (which == 2) {
                        showCleanDialpad();
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showMessagesCenter() {
        String[] items = {
                "صندوق پیام",
                "پیام‌های ارسال‌شده",
                "گفتگوها"
        };

        new AlertDialog.Builder(this)
                .setTitle("پیام‌ها")
                .setItems(items, (dialog, which) -> {
                    showSettingsInfo(
                            "پیام‌ها",
                            "رابط پیام آماده است. ارسال و دریافت واقعی SMS "
                                    + "بعد از اتصال Provider/API فعال می‌شود.");
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showMoreCenter() {
        String[] items = {
                "تنظیمات",
                "اسکن QR",
                "بررسی بروزرسانی",
                "خدمات MOLIDO",
                "درباره"
        };

        new AlertDialog.Builder(this)
                .setTitle("بیشتر")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showSettingsHub();
                    } else if (which == 1) {
                        startQrScan();
                    } else if (which == 2) {
                        checkUpdates(true);
                    } else if (which == 3) {
                        try {
                            startActivity(
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(HOME)));
                        } catch (Exception e) {
                            showToast("باز کردن خدمات MOLIDO ممکن نشد.");
                        }
                    } else if (which == 4) {
                        showSettingsInfo(
                                "MOLIDO VoIP",
                                "Version " + BuildConfig.VERSION_NAME);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showCleanDialpad() {
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(dp(14), dp(8), dp(14), dp(8));

        String[][] keys = {
                {"1", "2", "3"},
                {"4", "5", "6"},
                {"7", "8", "9"},
                {"*", "0", "#"}
        };

        for (String[] rowKeys : keys) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            for (String key : rowKeys) {
                Button b = new Button(this);
                b.setText(key);
                b.setTextSize(22f);
                b.setAllCaps(false);
                b.setOnClickListener(v ->
                        dialInput.append(
                                ((Button) v).getText()));

                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(
                                0,
                                dp(62),
                                1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(b, lp);
            }

            pad.addView(row);
        }

        new AlertDialog.Builder(this)
                .setTitle("شماره‌گیر")
                .setView(pad)
                .setNegativeButton("بستن", null)
                .setPositiveButton("تماس", (d, w) ->
                        beginOutgoingCall())
                .show();
    }

    private void showCompactMoreMenu() {
        String[] items = {
                "تنظیمات",
                "اسکن QR",
                "خدمات MOLIDO",
                "بررسی بروزرسانی",
                "درباره"
        };

        new AlertDialog.Builder(this)
                .setTitle("بیشتر")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showSettingsHub();
                    } else if (which == 1) {
                        startQrScan();
                    } else if (which == 2) {
                        try {
                            startActivity(
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(HOME)));
                        } catch (Exception e) {
                            showToast("باز کردن خدمات MOLIDO ممکن نشد.");
                        }
                    } else if (which == 3) {
                        checkUpdates(true);
                    } else if (which == 4) {
                        showSettingsInfo(
                                "MOLIDO VoIP",
                                "Version "
                                        + BuildConfig.VERSION_NAME);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
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
        if (adminUnlocked && !isAdminSessionValid()) {
            adminUnlocked = false;
            adminUnlockedAt = 0L;
            showToast("نشست مدیریت منقضی شد.");
        }

        ScrollView scroll = new ScrollView(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(18));
        root.setBackgroundColor(darkMode
                ? Color.rgb(17, 24, 39)
                : Color.rgb(246, 248, 252));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText(adminUnlocked ? "تنظیمات مدیریت" : "تنظیمات");
        title.setTextSize(24f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(darkMode ? Color.WHITE : Color.rgb(17, 24, 39));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(adminUnlocked
                ? "MOLIDO VoIP • حالت مدیریت فعال"
                : "MOLIDO VoIP • ساده و کاربردی");
        subtitle.setTextSize(12f);
        subtitle.setTextColor(darkMode
                ? Color.rgb(156, 163, 175)
                : Color.rgb(107, 114, 128));
        subtitle.setPadding(0, dp(3), 0, dp(14));
        root.addView(subtitle);

        addSettingsSection(
                root,
                "👤",
                "حساب من",
                accountSummary(),
                () -> {
                    if (adminUnlocked) {
                        showSettings();
                    } else {
                        showSettingsInfo(
                                "حساب من",
                                "نام و داخلی شما توسط مدیر سیستم تنظیم می‌شود.");
                    }
                });

        addSettingsSection(
                root,
                "☎",
                "تماس و صدا",
                "میکروفن • بلندگو • کیفیت تماس",
                () -> showSettingsInfo(
                        "تماس و صدا",
                        "MOLIDO به‌صورت خودکار کیفیت تماس را برای شبکه فعلی تنظیم می‌کند."));

        addSettingsSection(
                root,
                "🔔",
                "اعلان‌ها",
                "تماس ورودی • تماس ازدست‌رفته",
                () -> showSettingsInfo(
                        "اعلان‌ها",
                        "تنظیمات ساده اعلان تماس در این بخش قرار می‌گیرد."));

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

        if (adminUnlocked) {
            addSettingsSection(
                    root,
                    "PBX",
                    "مدیریت PBX",
                    pbxSummary(),
                    () -> showPbxCenter());

            addSettingsSection(
                    root,
                    "👥",
                    "کاربران و دسترسی",
                    usersSummary(),
                    () -> showUsersManager());

            addSettingsSection(
                    root,
                    "🛡",
                    "گزارش امنیت",
                    auditSummary(),
                    () -> showAuditLog());

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
                    "⚙",
                    "تنظیمات پیشرفته",
                    "SIP • STUN • TURN • Route • Failover",
                    () -> showSettings());

            addSettingsSection(
                    root,
                    "🔓",
                    "خروج از حالت مدیریت",
                    "بازگشت به رابط ساده کاربر",
                    () -> {
                        adminUnlocked = false;
                        adminUnlockedAt = 0L;
                        appendAudit("ADMIN_SESSION_END", "Admin mode closed");
                        recreate();
                    });
        } else {
            addSettingsSection(
                    root,
                    "🔐",
                    "مدیریت",
                    "فقط برای مدیر سیستم",
                    () -> showAdminAccess());
        }

        addSettingsSection(
                root,
                "ⓘ",
                "درباره MOLIDO",
                "v" + BuildConfig.VERSION_NAME,
                () -> showSettingsInfo(
                        "درباره MOLIDO",
                        "MOLIDO VoIP\nVersion: " + BuildConfig.VERSION_NAME));

        new AlertDialog.Builder(this)
                .setView(scroll)
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showAdminAccess() {
        String storedPin = null;
        try {
            storedPin = secureStore.getSecret("admin_pin");
        } catch (Exception ignored) {}

        if (storedPin == null || storedPin.isEmpty()) {
            showAdminPinSetup();
        } else {
            showAdminPinLogin();
        }
    }

    private void showAdminPinSetup() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));

        EditText pin = field("PIN مدیریت - حداقل 6 رقم", "");
        pin.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        EditText confirm = field("تکرار PIN", "");
        confirm.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        form.addView(pin);
        form.addView(confirm);

        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle("فعال‌سازی مدیریت")
                .setMessage("PIN فقط داخل SecureStore دستگاه ذخیره می‌شود.")
                .setView(form)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("فعال‌سازی", null)
                .create();

        ad.setOnShowListener(dialog -> {
            ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String a = pin.getText().toString().trim();
                String b = confirm.getText().toString().trim();

                if (a.length() < 6) {
                    pin.setError("حداقل 6 رقم");
                    return;
                }
                if (!a.equals(b)) {
                    confirm.setError("PIN یکسان نیست.");
                    return;
                }

                try {
                    secureStore.putSecret("admin_pin", a);
                    adminUnlocked = true;
                    adminUnlockedAt = System.currentTimeMillis();
                    prefs.edit()
                            .putInt("admin_failed_attempts", 0)
                            .putLong("admin_lockout_until", 0L)
                            .apply();
                    appendAudit("ADMIN_PIN_SETUP", "Admin PIN initialized");
                    ad.dismiss();
                    recreate();
                } catch (Exception e) {
                    showToast("ذخیره امن PIN ناموفق بود.");
                }
            });
        });

        ad.show();
    }

    private void showAdminPinLogin() {
        long now = System.currentTimeMillis();
        long lockoutUntil = prefs.getLong("admin_lockout_until", 0L);

        if (lockoutUntil > now) {
            long seconds = Math.max(1L, (lockoutUntil - now) / 1000L);
            showSettingsInfo(
                    "ورود مدیریت موقتاً قفل است",
                    "به دلیل تلاش‌های ناموفق، ورود حدود "
                            + seconds
                            + " ثانیه دیگر باز می‌شود.");
            return;
        }

        EditText pin = field("PIN مدیریت", "");
        pin.setInputType(
                InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle("ورود مدیر")
                .setView(pin)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ورود", null)
                .create();

        ad.setOnShowListener(dialog -> {
            ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String stored = null;
                try {
                    stored = secureStore.getSecret("admin_pin");
                } catch (Exception ignored) {}

                String entered = pin.getText().toString().trim();

                if (stored == null || !stored.equals(entered)) {
                    int failed = prefs.getInt("admin_failed_attempts", 0) + 1;

                    SharedPreferences.Editor editor = prefs.edit()
                            .putInt("admin_failed_attempts", failed);

                    if (failed >= ADMIN_MAX_FAILED_ATTEMPTS) {
                        editor.putLong(
                                "admin_lockout_until",
                                System.currentTimeMillis() + ADMIN_LOCKOUT_MS);
                    }

                    editor.apply();
                    appendAudit("ADMIN_LOGIN_FAILED", "Failed admin PIN attempt");

                    if (failed >= ADMIN_MAX_FAILED_ATTEMPTS) {
                        ad.dismiss();
                        showSettingsInfo(
                                "ورود مدیریت قفل شد",
                                "پس از چند تلاش ناموفق، ورود مدیریت برای 5 دقیقه قفل شد.");
                    } else {
                        pin.setError(
                                "PIN نادرست است. تلاش باقی‌مانده: "
                                        + (ADMIN_MAX_FAILED_ATTEMPTS - failed));
                    }
                    return;
                }

                prefs.edit()
                        .putInt("admin_failed_attempts", 0)
                        .putLong("admin_lockout_until", 0L)
                        .apply();

                adminUnlocked = true;
                adminUnlockedAt = System.currentTimeMillis();
                appendAudit("ADMIN_LOGIN_SUCCESS", "Admin mode unlocked");
                ad.dismiss();
                recreate();
            });
        });

        ad.show();
    }

    private boolean isAdminSessionValid() {
        return adminUnlocked
                && adminUnlockedAt > 0L
                && (System.currentTimeMillis() - adminUnlockedAt) < ADMIN_SESSION_MS;
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

    private String usersSummary() {
        int active = 0;
        int total = 0;

        try {
            JSONArray users = new JSONArray(
                    prefs.getString("rbac_users_json", "[]"));

            total = users.length();

            for (int i = 0; i < users.length(); i++) {
                JSONObject user = users.optJSONObject(i);
                if (user != null && user.optBoolean("active", true)) {
                    active++;
                }
            }
        } catch (Exception ignored) {}

        return total + " کاربر • " + active + " فعال";
    }

    private void showUsersManager() {
        if (!requireAdminSession()) return;

        JSONArray users = readRbacUsers();

        String[] rows = new String[users.length() + 1];
        rows[0] = "＋ افزودن کاربر";

        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.optJSONObject(i);
            if (user == null) {
                rows[i + 1] = "User";
                continue;
            }

            String name = user.optString("name", "User");
            String extension = user.optString("extension", "—");
            String role = user.optString("role", "USER");
            int securityLevel = user.optInt("securityLevel", 1);
            boolean active = user.optBoolean("active", true);

            rows[i + 1] =
                    (active ? "● " : "○ ")
                    + name
                    + " • "
                    + role
                    + " • L"
                    + securityLevel
                    + " • Ext "
                    + (extension.isEmpty() ? "—" : extension);
        }

        new AlertDialog.Builder(this)
                .setTitle("کاربران و دسترسی")
                .setItems(rows, (dialog, which) -> {
                    if (which == 0) {
                        showUserEditor(-1);
                    } else {
                        showUserActions(which - 1);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private JSONArray readRbacUsers() {
        try {
            return new JSONArray(prefs.getString("rbac_users_json", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void showUserActions(int index) {
        if (!requireAdminSession()) return;

        JSONArray users = readRbacUsers();
        JSONObject user = users.optJSONObject(index);
        if (user == null) return;

        String name = user.optString("name", "User");
        boolean active = user.optBoolean("active", true);

        String[] actions = {
                "ویرایش",
                "نمایش دسترسی‌ها",
                active ? "غیرفعال کردن" : "فعال کردن",
                "حذف"
        };

        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showUserEditor(index);
                    } else if (which == 1) {
                        showUserPermissions(index);
                    } else if (which == 2) {
                        setUserActive(index, !active);
                    } else if (which == 3) {
                        deleteRbacUser(index);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showUserEditor(int index) {
        if (!requireAdminSession()) return;

        JSONArray users = readRbacUsers();
        JSONObject existing = index >= 0 ? users.optJSONObject(index) : null;

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        scroll.addView(form);

        EditText name = field(
                "نام کاربر",
                existing == null ? "" : existing.optString("name", ""));

        EditText extension = field(
                "Extension",
                existing == null ? "" : existing.optString("extension", ""));
        extension.setInputType(InputType.TYPE_CLASS_PHONE);

        EditText role = field(
                "Role: USER / AGENT / OPERATOR / SUPERVISOR / ADMIN / OWNER",
                existing == null ? "USER" : existing.optString("role", "USER"));

        EditText securityLevel = field(
                "Security Level: 1 / 2 / 3 / 4",
                existing == null
                        ? "1"
                        : String.valueOf(existing.optInt("securityLevel", 1)));
        securityLevel.setInputType(InputType.TYPE_CLASS_NUMBER);

        EditText department = field(
                "Department / Queue",
                existing == null ? "" : existing.optString("department", ""));

        EditText did = field(
                "Assigned DID",
                existing == null ? "" : existing.optString("did", ""));

        form.addView(name);
        form.addView(extension);
        form.addView(role);
        form.addView(securityLevel);
        form.addView(department);
        form.addView(did);

        TextView note = new TextView(this);
        note.setText(
                "Role یک قالب دسترسی است. Security Level حساسیت امنیتی را مشخص می‌کند. "
                        + "همه دسترسی‌ها Deny-by-default هستند.");
        note.setTextSize(11f);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(10), 0, 0);
        form.addView(note);

        final JSONArray userList = users;

        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle(index < 0 ? "افزودن کاربر" : "ویرایش کاربر")
                .setView(scroll)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", null)
                .create();

        ad.setOnShowListener(dialog -> {
            ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String nameValue = name.getText().toString().trim();
                String roleValue = role.getText().toString()
                        .trim().toUpperCase(java.util.Locale.ROOT);

                if (nameValue.isEmpty()) {
                    name.setError("نام کاربر لازم است.");
                    return;
                }

                if (!isValidRole(roleValue)) {
                    role.setError("Role معتبر نیست.");
                    return;
                }

                int level;
                try {
                    level = Integer.parseInt(
                            securityLevel.getText().toString().trim());
                } catch (Exception e) {
                    securityLevel.setError("سطح 1 تا 4");
                    return;
                }

                if (level < 1 || level > 4) {
                    securityLevel.setError("سطح امنیت فقط 1 تا 4");
                    return;
                }

                if (!roleLevelAllowed(roleValue, level)) {
                    securityLevel.setError(
                            "این Role با Security Level انتخاب‌شده سازگار نیست.");
                    return;
                }

                try {
                    JSONObject user = existing == null
                            ? new JSONObject()
                            : new JSONObject(existing.toString());

                    String id = user.optString("id", "");
                    if (id.isEmpty()) {
                        id = "user_" + System.currentTimeMillis();
                    }

                    user.put("id", id);
                    user.put("name", nameValue);
                    user.put("extension",
                            extension.getText().toString().trim());
                    user.put("role", roleValue);
                    user.put("securityLevel", level);
                    user.put("department",
                            department.getText().toString().trim());
                    user.put("did",
                            did.getText().toString().trim());

                    if (!user.has("active")) user.put("active", true);

                    user.put("permissions",
                            rolePermissions(roleValue));

                    JSONArray next = new JSONArray();

                    for (int i = 0; i < userList.length(); i++) {
                        if (i == index) {
                            next.put(user);
                        } else {
                            JSONObject old = userList.optJSONObject(i);
                            if (old != null) next.put(old);
                        }
                    }

                    if (index < 0) next.put(user);

                    prefs.edit()
                            .putString("rbac_users_json", next.toString())
                            .apply();

                    appendAudit(
                            index < 0 ? "USER_CREATED" : "USER_UPDATED",
                            nameValue + " / " + roleValue + " / L" + level);

                    ad.dismiss();
                    showUsersManager();

                } catch (Exception e) {
                    showToast("ذخیره کاربر ناموفق بود.");
                }
            });
        });

        ad.show();
    }

    private boolean isValidRole(String role) {
        return "USER".equals(role)
                || "AGENT".equals(role)
                || "OPERATOR".equals(role)
                || "SUPERVISOR".equals(role)
                || "ADMIN".equals(role)
                || "OWNER".equals(role);
    }

    private boolean roleLevelAllowed(String role, int level) {
        if ("OWNER".equals(role)) return level == 4;
        if ("ADMIN".equals(role)) return level >= 3;
        if ("SUPERVISOR".equals(role)) return level >= 2 && level <= 3;
        if ("OPERATOR".equals(role)) return level >= 1 && level <= 2;
        if ("AGENT".equals(role)) return level >= 1 && level <= 2;
        return level == 1;
    }

    private JSONArray rolePermissions(String role) {
        JSONArray p = new JSONArray();

        addPermission(p, "calls.view_own");
        addPermission(p, "contacts.use");

        if ("AGENT".equals(role)
                || "OPERATOR".equals(role)
                || "SUPERVISOR".equals(role)
                || "ADMIN".equals(role)
                || "OWNER".equals(role)) {
            addPermission(p, "queue.use");
        }

        if ("OPERATOR".equals(role)
                || "SUPERVISOR".equals(role)
                || "ADMIN".equals(role)
                || "OWNER".equals(role)) {
            addPermission(p, "voicemail.manage");
        }

        if ("SUPERVISOR".equals(role)
                || "ADMIN".equals(role)
                || "OWNER".equals(role)) {
            addPermission(p, "calls.view_all");
            addPermission(p, "reports.view");
            addPermission(p, "users.view");
        }

        if ("ADMIN".equals(role)
                || "OWNER".equals(role)) {
            addPermission(p, "users.create");
            addPermission(p, "users.edit");
            addPermission(p, "users.disable");
            addPermission(p, "pbx.view");
            addPermission(p, "pbx.manage");
            addPermission(p, "did.manage");
            addPermission(p, "ivr.manage");
            addPermission(p, "routing.manage");
        }

        if ("OWNER".equals(role)) {
            addPermission(p, "trunk.manage");
            addPermission(p, "security.manage");
            addPermission(p, "roles.manage");
            addPermission(p, "audit.view");
        }

        return p;
    }

    private void addPermission(JSONArray target, String value) {
        target.put(value);
    }

    private boolean hasPermission(JSONObject user, String permission) {
        if (user == null || permission == null || permission.isEmpty()) {
            return false;
        }

        if (!user.optBoolean("active", true)) {
            return false;
        }

        JSONArray permissions = user.optJSONArray("permissions");
        if (permissions == null) return false;

        for (int i = 0; i < permissions.length(); i++) {
            if (permission.equals(permissions.optString(i, ""))) {
                return true;
            }
        }

        return false;
    }

    private void showUserPermissions(int index) {
        JSONArray users = readRbacUsers();
        JSONObject user = users.optJSONObject(index);
        if (user == null) return;

        JSONArray permissions = user.optJSONArray("permissions");
        StringBuilder text = new StringBuilder();

        text.append("Role: ")
                .append(user.optString("role", "USER"))
                .append("\nSecurity Level: ")
                .append(user.optInt("securityLevel", 1))
                .append("\n\n");

        if (permissions == null || permissions.length() == 0) {
            text.append("هیچ دسترسی اضافی ندارد.");
        } else {
            for (int i = 0; i < permissions.length(); i++) {
                text.append("✓ ")
                        .append(permissions.optString(i, ""))
                        .append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(user.optString("name", "User"))
                .setMessage(text.toString())
                .setPositiveButton("باشه", null)
                .show();
    }

    private void setUserActive(int index, boolean active) {
        if (!requireAdminSession()) return;

        JSONArray users = readRbacUsers();
        JSONArray next = new JSONArray();
        String changedName = "User";

        try {
            for (int i = 0; i < users.length(); i++) {
                JSONObject old = users.optJSONObject(i);
                if (old == null) continue;

                JSONObject copy = new JSONObject(old.toString());

                if (i == index) {
                    copy.put("active", active);
                    changedName = copy.optString("name", "User");
                }

                next.put(copy);
            }

            prefs.edit()
                    .putString("rbac_users_json", next.toString())
                    .apply();

            appendAudit(
                    active ? "USER_ENABLED" : "USER_DISABLED",
                    changedName);

            showUsersManager();

        } catch (Exception e) {
            showToast("تغییر وضعیت کاربر ناموفق بود.");
        }
    }

    private void deleteRbacUser(int index) {
        if (!requireAdminSession()) return;

        JSONArray users = readRbacUsers();
        JSONArray next = new JSONArray();
        String deletedName = "User";

        try {
            for (int i = 0; i < users.length(); i++) {
                JSONObject item = users.optJSONObject(i);
                if (item == null) continue;

                if (i == index) {
                    deletedName = item.optString("name", "User");
                    continue;
                }

                next.put(item);
            }

            prefs.edit()
                    .putString("rbac_users_json", next.toString())
                    .apply();

            appendAudit("USER_DELETED", deletedName);
            showUsersManager();

        } catch (Exception e) {
            showToast("حذف کاربر ناموفق بود.");
        }
    }

    private boolean requireAdminSession() {
        if (!isAdminSessionValid()) {
            adminUnlocked = false;
            adminUnlockedAt = 0L;
            showToast("برای ادامه دوباره وارد حالت مدیریت شوید.");
            showAdminAccess();
            return false;
        }

        adminUnlockedAt = System.currentTimeMillis();
        return true;
    }

    private void appendAudit(String action, String detail) {
        try {
            JSONArray current = new JSONArray(
                    prefs.getString("security_audit_json", "[]"));

            JSONObject event = new JSONObject();
            event.put("time", System.currentTimeMillis());
            event.put("action", action == null ? "" : action);
            event.put("detail", detail == null ? "" : detail);

            JSONArray next = new JSONArray();
            next.put(event);

            int limit = Math.min(current.length(), 99);
            for (int i = 0; i < limit; i++) {
                JSONObject old = current.optJSONObject(i);
                if (old != null) next.put(old);
            }

            prefs.edit()
                    .putString("security_audit_json", next.toString())
                    .apply();

        } catch (Exception ignored) {}
    }

    private String auditSummary() {
        try {
            JSONArray events = new JSONArray(
                    prefs.getString("security_audit_json", "[]"));

            return events.length() + " رویداد امنیتی محلی";
        } catch (Exception e) {
            return "بدون رویداد";
        }
    }

    private void showAuditLog() {
        if (!requireAdminSession()) return;

        try {
            JSONArray events = new JSONArray(
                    prefs.getString("security_audit_json", "[]"));

            if (events.length() == 0) {
                showToast("هنوز رویداد امنیتی ثبت نشده است.");
                return;
            }

            int count = Math.min(events.length(), 50);
            String[] rows = new String[count];

            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat(
                            "MM/dd HH:mm",
                            java.util.Locale.getDefault());

            for (int i = 0; i < count; i++) {
                JSONObject event = events.optJSONObject(i);
                if (event == null) {
                    rows[i] = "Audit";
                    continue;
                }

                long time = event.optLong("time", 0L);
                String when = time > 0L
                        ? sdf.format(new java.util.Date(time))
                        : "";

                rows[i] =
                        when
                        + " • "
                        + event.optString("action", "")
                        + " • "
                        + event.optString("detail", "");
            }

            new AlertDialog.Builder(this)
                    .setTitle("گزارش امنیت")
                    .setItems(rows, null)
                    .setNegativeButton("بستن", null)
                    .show();

        } catch (Exception e) {
            showToast("خواندن گزارش امنیت ناموفق بود.");
        }
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

        int ivrCount = 0;
        try {
            JSONArray ivrs = new JSONArray(prefs.getString("pbx_ivrs_json", "[]"));
            ivrCount = ivrs.length();
        } catch (Exception ignored) {}

        int trunkCount = 0;
        try {
            JSONArray trunks = new JSONArray(prefs.getString("pbx_trunks_json", "[]"));
            trunkCount = trunks.length();
        } catch (Exception ignored) {}

        String sip = prefs.getString("sip_username", "").trim();
        String registrar = prefs.getString("sip_registrar", "").trim();

        String sipState = (!sip.isEmpty() && !registrar.isEmpty())
                ? "SIP آماده"
                : "SIP تنظیم نشده";

        return sipState
                + " • " + didCount + " DID"
                + " • " + ivrCount + " IVR"
                + " • " + trunkCount + " Trunk";
    }

    private void showPbxCenter() {
        String[] items = {
                "SIP Account",
                "DID Numbers",
                "IVR / تلفن گویا",
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
                        showIvrManager();
                    } else if (which == 3) {
                        showTrunkManager();
                    } else if (which == 4) {
                        showSettingsInfo(
                                "SMS Center",
                                "مرکز پیامک در فاز بعدی اضافه می‌شود. "
                                        + "برای SMS واقعی به Provider/API یا PBX backend نیاز است.");
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showTrunkManager() {
        JSONArray trunks;
        try {
            trunks = new JSONArray(prefs.getString("pbx_trunks_json", "[]"));
        } catch (Exception e) {
            trunks = new JSONArray();
        }

        int count = trunks.length();
        String[] rows = new String[count + 1];
        rows[0] = "＋ افزودن SIP Trunk";

        for (int i = 0; i < count; i++) {
            JSONObject item = trunks.optJSONObject(i);
            if (item == null) {
                rows[i + 1] = "SIP Trunk";
                continue;
            }

            String provider = item.optString("provider", "Trunk");
            String host = item.optString("host", "—");
            String transport = item.optString("transport", "TLS");
            boolean enabled = item.optBoolean("enabled", true);
            boolean isDefault = item.optBoolean("default", false);

            rows[i + 1] =
                    (enabled ? "● " : "○ ")
                    + provider
                    + " • "
                    + host
                    + " • "
                    + transport
                    + (isDefault ? " • DEFAULT" : "");
        }

        new AlertDialog.Builder(this)
                .setTitle("SIP Trunks")
                .setItems(rows, (dialog, which) -> {
                    if (which == 0) {
                        showTrunkEditor(-1);
                    } else {
                        showTrunkActions(which - 1);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showTrunkActions(int index) {
        JSONArray trunks;
        try {
            trunks = new JSONArray(prefs.getString("pbx_trunks_json", "[]"));
        } catch (Exception e) {
            showToast("خواندن Trunk ناموفق بود.");
            return;
        }

        JSONObject item = trunks.optJSONObject(index);
        if (item == null) return;

        String provider = item.optString("provider", "SIP Trunk");
        boolean enabled = item.optBoolean("enabled", true);
        boolean isDefault = item.optBoolean("default", false);

        String[] actions = {
                "ویرایش",
                enabled ? "غیرفعال کردن" : "فعال کردن",
                isDefault ? "Trunk پیش‌فرض است" : "تنظیم به‌عنوان Default",
                "جزئیات / Health",
                "حذف"
        };

        new AlertDialog.Builder(this)
                .setTitle(provider)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showTrunkEditor(index);
                    } else if (which == 1) {
                        setTrunkEnabled(index, !enabled);
                    } else if (which == 2) {
                        if (!isDefault) setDefaultTrunk(index);
                    } else if (which == 3) {
                        showTrunkDetails(index);
                    } else if (which == 4) {
                        deleteTrunk(index);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showTrunkEditor(int index) {
        JSONArray trunks;
        try {
            trunks = new JSONArray(prefs.getString("pbx_trunks_json", "[]"));
        } catch (Exception e) {
            trunks = new JSONArray();
        }

        JSONObject existing = index >= 0 ? trunks.optJSONObject(index) : null;

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        scroll.addView(form);

        EditText provider = field(
                "Provider / نام Trunk",
                existing == null ? "" : existing.optString("provider", ""));

        EditText host = field(
                "Host / Domain",
                existing == null ? "" : existing.optString("host", ""));

        EditText port = field(
                "Port",
                existing == null ? "5061" : String.valueOf(existing.optInt("port", 5061)));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);

        EditText username = field(
                "Username",
                existing == null ? "" : existing.optString("username", ""));

        EditText authId = field(
                "Auth ID",
                existing == null ? "" : existing.optString("authId", ""));

        EditText password = field("Password", "");
        password.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        String existingId = existing == null ? "" : existing.optString("id", "");
        boolean hasPassword = false;
        if (!existingId.isEmpty()) {
            try {
                String stored = secureStore.getSecret("trunk_password_" + existingId);
                hasPassword = stored != null && !stored.isEmpty();
            } catch (Exception ignored) {}
        }

        password.setHint(
                hasPassword
                        ? "رمز ذخیره شده • برای تغییر، رمز جدید را وارد کنید"
                        : "Password");

        EditText transport = field(
                "Transport: TLS / TCP / UDP",
                existing == null ? "TLS" : existing.optString("transport", "TLS"));

        EditText mode = field(
                "Mode: REGISTRATION / IP",
                existing == null ? "REGISTRATION" : existing.optString("mode", "REGISTRATION"));

        EditText incomingRoute = field(
                "Incoming Route",
                existing == null ? "" : existing.optString("incomingRoute", ""));

        EditText outboundRoute = field(
                "Outbound Route / Prefix",
                existing == null ? "" : existing.optString("outboundRoute", ""));

        EditText backupTarget = field(
                "Backup Trunk ID / Name (اختیاری)",
                existing == null ? "" : existing.optString("backupTarget", ""));

        EditText codecs = field(
                "Codec Priority (مثال: OPUS,G722,PCMU,PCMA)",
                existing == null ? "OPUS,G722,PCMU,PCMA"
                        : existing.optString("codecs", "OPUS,G722,PCMU,PCMA"));

        form.addView(provider);
        form.addView(host);
        form.addView(port);
        form.addView(username);
        form.addView(authId);
        form.addView(password);
        form.addView(transport);
        form.addView(mode);
        form.addView(incomingRoute);
        form.addView(outboundRoute);
        form.addView(backupTarget);
        form.addView(codecs);

        TextView note = new TextView(this);
        note.setText(
                "رمز Trunk در SecureStore رمزگذاری می‌شود. "
                        + "این نسخه Trunk را مدیریت می‌کند؛ Register/Health واقعی "
                        + "به PBX یا Provider backend نیاز دارد.");
        note.setTextSize(11f);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(10), 0, 0);
        form.addView(note);

        final JSONArray trunkList = trunks;

        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle(index < 0 ? "افزودن SIP Trunk" : "ویرایش SIP Trunk")
                .setView(scroll)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", null)
                .create();

        ad.setOnShowListener(dialog -> {
            ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String providerValue = provider.getText().toString().trim();
                String hostValue = host.getText().toString().trim();

                if (providerValue.isEmpty()) {
                    provider.setError("نام Provider لازم است.");
                    return;
                }

                if (hostValue.isEmpty()) {
                    host.setError("Host / Domain لازم است.");
                    return;
                }

                int portValue;
                try {
                    portValue = Integer.parseInt(port.getText().toString().trim());
                } catch (Exception e) {
                    port.setError("Port معتبر وارد کنید.");
                    return;
                }

                if (portValue < 1 || portValue > 65535) {
                    port.setError("Port باید بین 1 تا 65535 باشد.");
                    return;
                }

                String transportValue = transport.getText().toString()
                        .trim().toUpperCase(java.util.Locale.ROOT);

                if (!transportValue.equals("TLS")
                        && !transportValue.equals("TCP")
                        && !transportValue.equals("UDP")) {
                    transport.setError("فقط TLS، TCP یا UDP");
                    return;
                }

                String modeValue = mode.getText().toString()
                        .trim().toUpperCase(java.util.Locale.ROOT);

                if (!modeValue.equals("REGISTRATION")
                        && !modeValue.equals("IP")) {
                    mode.setError("فقط REGISTRATION یا IP");
                    return;
                }

                if (modeValue.equals("REGISTRATION")
                        && username.getText().toString().trim().isEmpty()) {
                    username.setError("برای Registration، Username لازم است.");
                    return;
                }

                try {
                    JSONObject item = existing == null
                            ? new JSONObject()
                            : new JSONObject(existing.toString());

                    String trunkId = item.optString("id", "");
                    if (trunkId.isEmpty()) {
                        trunkId = "trunk_" + System.currentTimeMillis();
                        item.put("id", trunkId);
                    }

                    String newPassword = password.getText().toString();
                    if (!newPassword.isEmpty()) {
                        secureStore.putSecret(
                                "trunk_password_" + trunkId,
                                newPassword);
                    }

                    item.put("provider", providerValue);
                    item.put("host", hostValue);
                    item.put("port", portValue);
                    item.put("username", username.getText().toString().trim());
                    item.put("authId", authId.getText().toString().trim());
                    item.put("transport", transportValue);
                    item.put("mode", modeValue);
                    item.put("incomingRoute",
                            incomingRoute.getText().toString().trim());
                    item.put("outboundRoute",
                            outboundRoute.getText().toString().trim());
                    item.put("backupTarget",
                            backupTarget.getText().toString().trim());
                    item.put("codecs",
                            codecs.getText().toString().trim());

                    if (!item.has("enabled")) item.put("enabled", true);
                    if (!item.has("default")) item.put("default", trunkList.length() == 0);

                    JSONArray next = new JSONArray();

                    for (int i = 0; i < trunkList.length(); i++) {
                        if (i == index) {
                            next.put(item);
                        } else {
                            JSONObject old = trunkList.optJSONObject(i);
                            if (old != null) next.put(old);
                        }
                    }

                    if (index < 0) next.put(item);

                    prefs.edit()
                            .putString("pbx_trunks_json", next.toString())
                            .apply();

                    ad.dismiss();
                    showTrunkManager();

                } catch (Exception e) {
                    showToast("ذخیره امن Trunk ناموفق بود.");
                }
            });
        });

        ad.show();
    }

    private void setTrunkEnabled(int index, boolean enabled) {
        try {
            JSONArray trunks = new JSONArray(
                    prefs.getString("pbx_trunks_json", "[]"));

            JSONArray next = new JSONArray();

            for (int i = 0; i < trunks.length(); i++) {
                JSONObject item = trunks.optJSONObject(i);
                if (item == null) continue;

                JSONObject copy = new JSONObject(item.toString());
                if (i == index) copy.put("enabled", enabled);
                next.put(copy);
            }

            prefs.edit()
                    .putString("pbx_trunks_json", next.toString())
                    .apply();

            showTrunkManager();

        } catch (Exception e) {
            showToast("تغییر وضعیت Trunk ناموفق بود.");
        }
    }

    private void setDefaultTrunk(int index) {
        try {
            JSONArray trunks = new JSONArray(
                    prefs.getString("pbx_trunks_json", "[]"));

            JSONArray next = new JSONArray();

            for (int i = 0; i < trunks.length(); i++) {
                JSONObject item = trunks.optJSONObject(i);
                if (item == null) continue;

                JSONObject copy = new JSONObject(item.toString());
                copy.put("default", i == index);
                next.put(copy);
            }

            prefs.edit()
                    .putString("pbx_trunks_json", next.toString())
                    .apply();

            showTrunkManager();

        } catch (Exception e) {
            showToast("تنظیم Default Trunk ناموفق بود.");
        }
    }

    private void showTrunkDetails(int index) {
        try {
            JSONArray trunks = new JSONArray(
                    prefs.getString("pbx_trunks_json", "[]"));

            JSONObject item = trunks.optJSONObject(index);
            if (item == null) return;

            String id = item.optString("id", "");
            boolean passwordStored = false;
            if (!id.isEmpty()) {
                try {
                    String stored = secureStore.getSecret("trunk_password_" + id);
                    passwordStored = stored != null && !stored.isEmpty();
                } catch (Exception ignored) {}
            }

            String message =
                    "Provider: " + item.optString("provider", "—") + "\n"
                    + "Host: " + item.optString("host", "—")
                    + ":" + item.optInt("port", 5061) + "\n"
                    + "Mode: " + item.optString("mode", "REGISTRATION") + "\n"
                    + "Transport: " + item.optString("transport", "TLS") + "\n"
                    + "Username: " + item.optString("username", "—") + "\n"
                    + "Auth ID: " + item.optString("authId", "—") + "\n"
                    + "Password: " + (passwordStored ? "SecureStore ✓" : "Not stored") + "\n"
                    + "Incoming: " + item.optString("incomingRoute", "—") + "\n"
                    + "Outbound: " + item.optString("outboundRoute", "—") + "\n"
                    + "Backup: " + item.optString("backupTarget", "—") + "\n"
                    + "Codecs: " + item.optString("codecs", "—") + "\n"
                    + "Health: CONFIGURED / backend check required";

            new AlertDialog.Builder(this)
                    .setTitle("Trunk Details")
                    .setMessage(message)
                    .setPositiveButton("باشه", null)
                    .show();

        } catch (Exception e) {
            showToast("نمایش Trunk ناموفق بود.");
        }
    }

    private void deleteTrunk(int index) {
        try {
            JSONArray trunks = new JSONArray(
                    prefs.getString("pbx_trunks_json", "[]"));

            JSONObject deleting = trunks.optJSONObject(index);
            if (deleting != null) {
                String id = deleting.optString("id", "");
                if (!id.isEmpty()) {
                    secureStore.removeSecret("trunk_password_" + id);
                }
            }

            JSONArray next = new JSONArray();

            for (int i = 0; i < trunks.length(); i++) {
                if (i == index) continue;
                JSONObject item = trunks.optJSONObject(i);
                if (item != null) next.put(item);
            }

            if (next.length() > 0) {
                boolean hasDefault = false;
                for (int i = 0; i < next.length(); i++) {
                    JSONObject item = next.optJSONObject(i);
                    if (item != null && item.optBoolean("default", false)) {
                        hasDefault = true;
                        break;
                    }
                }

                if (!hasDefault) {
                    JSONObject first = next.optJSONObject(0);
                    if (first != null) first.put("default", true);
                }
            }

            prefs.edit()
                    .putString("pbx_trunks_json", next.toString())
                    .apply();

            showTrunkManager();

        } catch (Exception e) {
            showToast("حذف Trunk ناموفق بود.");
        }
    }

    private void showIvrManager() {
        JSONArray ivrs;
        try {
            ivrs = new JSONArray(prefs.getString("pbx_ivrs_json", "[]"));
        } catch (Exception e) {
            ivrs = new JSONArray();
        }

        int count = ivrs.length();
        String[] rows = new String[count + 1];
        rows[0] = "＋ افزودن تلفن گویا";

        for (int i = 0; i < count; i++) {
            JSONObject item = ivrs.optJSONObject(i);
            if (item == null) {
                rows[i + 1] = "IVR";
                continue;
            }

            String name = item.optString("name", "IVR");
            String lang = item.optString("language", "fa");
            boolean enabled = item.optBoolean("enabled", true);

            rows[i + 1] =
                    (enabled ? "● " : "○ ")
                    + name
                    + " • "
                    + lang.toUpperCase(java.util.Locale.ROOT);
        }

        final JSONArray current = ivrs;

        new AlertDialog.Builder(this)
                .setTitle("IVR / تلفن گویا")
                .setItems(rows, (dialog, which) -> {
                    if (which == 0) {
                        showIvrEditor(-1);
                    } else {
                        showIvrActions(which - 1);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showIvrActions(int index) {
        JSONArray ivrs;
        try {
            ivrs = new JSONArray(prefs.getString("pbx_ivrs_json", "[]"));
        } catch (Exception e) {
            showToast("خواندن IVR ناموفق بود.");
            return;
        }

        JSONObject item = ivrs.optJSONObject(index);
        if (item == null) return;

        String name = item.optString("name", "IVR");
        boolean enabled = item.optBoolean("enabled", true);

        String[] actions = {
                "ویرایش",
                enabled ? "غیرفعال کردن" : "فعال کردن",
                "پیش‌نمایش تنظیمات",
                "حذف"
        };

        new AlertDialog.Builder(this)
                .setTitle(name)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showIvrEditor(index);
                    } else if (which == 1) {
                        setIvrEnabled(index, !enabled);
                    } else if (which == 2) {
                        showIvrPreview(index);
                    } else if (which == 3) {
                        deleteIvr(index);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showIvrEditor(int index) {
        JSONArray ivrs;
        try {
            ivrs = new JSONArray(prefs.getString("pbx_ivrs_json", "[]"));
        } catch (Exception e) {
            ivrs = new JSONArray();
        }

        JSONObject existing = index >= 0 ? ivrs.optJSONObject(index) : null;

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        scroll.addView(form);

        EditText name = field(
                "نام IVR",
                existing == null ? "" : existing.optString("name", ""));

        EditText greeting = field(
                "متن پیام خوش‌آمدگویی",
                existing == null ? "" : existing.optString("greeting", ""));

        EditText audioSource = field(
                "Audio URL / PBX Media ID (اختیاری)",
                existing == null ? "" : existing.optString("audioSource", ""));

        EditText language = field(
                "زبان: fa / en / bal",
                existing == null ? "fa" : existing.optString("language", "fa"));

        EditText timeout = field(
                "Timeout ثانیه",
                existing == null ? "7" : String.valueOf(existing.optInt("timeout", 7)));
        timeout.setInputType(InputType.TYPE_CLASS_NUMBER);

        EditText repeats = field(
                "تعداد تکرار پیام",
                existing == null ? "2" : String.valueOf(existing.optInt("repeats", 2)));
        repeats.setInputType(InputType.TYPE_CLASS_NUMBER);

        EditText defaultDest = field(
                "مقصد پیش‌فرض / Timeout Destination",
                existing == null ? "" : existing.optString("defaultDestination", ""));

        EditText businessHours = field(
                "Business Hours (اختیاری، مثال 08:00-18:00)",
                existing == null ? "" : existing.optString("businessHours", ""));

        form.addView(name);
        form.addView(greeting);
        form.addView(audioSource);
        form.addView(language);
        form.addView(timeout);
        form.addView(repeats);
        form.addView(defaultDest);
        form.addView(businessHours);

        TextView mapTitle = new TextView(this);
        mapTitle.setText("DTMF Key Mapping");
        mapTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mapTitle.setTextSize(14f);
        mapTitle.setPadding(0, dp(12), 0, dp(6));
        form.addView(mapTitle);

        EditText[] keys = new EditText[10];

        for (int i = 0; i <= 9; i++) {
            String value = "";
            if (existing != null) {
                JSONObject mappings = existing.optJSONObject("mappings");
                if (mappings != null) {
                    value = mappings.optString(String.valueOf(i), "");
                }
            }

            keys[i] = field(
                    "کلید " + i + " → مقصد (Extension / Queue / Ring Group / Voicemail)",
                    value);
            form.addView(keys[i]);
        }

        TextView note = new TextView(this);
        note.setText(
                "این نسخه تنظیمات IVR را داخل اپ مدیریت می‌کند. "
                        + "اجرای واقعی Greeting، دریافت DTMF و Route تماس "
                        + "باید روی PBX/Provider backend انجام شود.");
        note.setTextSize(11f);
        note.setTextColor(Color.GRAY);
        note.setPadding(0, dp(10), 0, 0);
        form.addView(note);

        final JSONArray ivrList = ivrs;

        AlertDialog ad = new AlertDialog.Builder(this)
                .setTitle(index < 0 ? "افزودن تلفن گویا" : "ویرایش تلفن گویا")
                .setView(scroll)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", null)
                .create();

        ad.setOnShowListener(dialog -> {
            ad.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String nameValue = name.getText().toString().trim();
                if (nameValue.isEmpty()) {
                    name.setError("نام IVR لازم است.");
                    return;
                }

                int timeoutValue;
                int repeatsValue;

                try {
                    timeoutValue = Integer.parseInt(timeout.getText().toString().trim());
                    repeatsValue = Integer.parseInt(repeats.getText().toString().trim());
                } catch (Exception e) {
                    timeout.setError("عدد معتبر وارد کنید.");
                    return;
                }

                if (timeoutValue < 2 || timeoutValue > 60) {
                    timeout.setError("Timeout بین 2 تا 60 ثانیه باشد.");
                    return;
                }

                if (repeatsValue < 0 || repeatsValue > 10) {
                    repeats.setError("تعداد تکرار بین 0 تا 10 باشد.");
                    return;
                }

                String lang = language.getText().toString()
                        .trim()
                        .toLowerCase(java.util.Locale.ROOT);

                if (!lang.equals("fa") && !lang.equals("en") && !lang.equals("bal")) {
                    language.setError("فقط fa، en یا bal");
                    return;
                }

                try {
                    JSONObject item = existing == null
                            ? new JSONObject()
                            : new JSONObject(existing.toString());

                    item.put("name", nameValue);
                    item.put("greeting", greeting.getText().toString().trim());
                    item.put("audioSource", audioSource.getText().toString().trim());
                    item.put("language", lang);
                    item.put("timeout", timeoutValue);
                    item.put("repeats", repeatsValue);
                    item.put("defaultDestination",
                            defaultDest.getText().toString().trim());
                    item.put("businessHours",
                            businessHours.getText().toString().trim());

                    if (!item.has("enabled")) {
                        item.put("enabled", true);
                    }

                    JSONObject mappings = new JSONObject();
                    for (int i = 0; i <= 9; i++) {
                        String dest = keys[i].getText().toString().trim();
                        if (!dest.isEmpty()) {
                            mappings.put(String.valueOf(i), dest);
                        }
                    }
                    item.put("mappings", mappings);

                    JSONArray next = new JSONArray();

                    for (int i = 0; i < ivrList.length(); i++) {
                        if (i == index) {
                            next.put(item);
                        } else {
                            JSONObject old = ivrList.optJSONObject(i);
                            if (old != null) next.put(old);
                        }
                    }

                    if (index < 0) {
                        next.put(item);
                    }

                    prefs.edit()
                            .putString("pbx_ivrs_json", next.toString())
                            .apply();

                    ad.dismiss();
                    showIvrManager();

                } catch (Exception e) {
                    showToast("ذخیره IVR ناموفق بود.");
                }
            });
        });

        ad.show();
    }

    private void showIvrPreview(int index) {
        try {
            JSONArray ivrs = new JSONArray(
                    prefs.getString("pbx_ivrs_json", "[]"));

            JSONObject item = ivrs.optJSONObject(index);
            if (item == null) return;

            StringBuilder message = new StringBuilder();

            message.append("Greeting:\n")
                    .append(item.optString("greeting", "—"))
                    .append("\n\n");

            JSONObject mappings = item.optJSONObject("mappings");
            if (mappings != null) {
                for (int i = 0; i <= 9; i++) {
                    String dest = mappings.optString(String.valueOf(i), "");
                    if (!dest.isEmpty()) {
                        message.append(i)
                                .append(" → ")
                                .append(dest)
                                .append("\n");
                    }
                }
            }

            message.append("\nTimeout → ")
                    .append(item.optString("defaultDestination", "—"));

            new AlertDialog.Builder(this)
                    .setTitle(item.optString("name", "IVR"))
                    .setMessage(message.toString())
                    .setPositiveButton("باشه", null)
                    .show();

        } catch (Exception e) {
            showToast("پیش‌نمایش IVR ناموفق بود.");
        }
    }

    private void setIvrEnabled(int index, boolean enabled) {
        try {
            JSONArray ivrs = new JSONArray(
                    prefs.getString("pbx_ivrs_json", "[]"));

            JSONArray next = new JSONArray();

            for (int i = 0; i < ivrs.length(); i++) {
                JSONObject item = ivrs.optJSONObject(i);
                if (item == null) continue;

                JSONObject copy = new JSONObject(item.toString());
                if (i == index) {
                    copy.put("enabled", enabled);
                }
                next.put(copy);
            }

            prefs.edit()
                    .putString("pbx_ivrs_json", next.toString())
                    .apply();

            showIvrManager();

        } catch (Exception e) {
            showToast("تغییر وضعیت IVR ناموفق بود.");
        }
    }

    private void deleteIvr(int index) {
        try {
            JSONArray ivrs = new JSONArray(
                    prefs.getString("pbx_ivrs_json", "[]"));

            JSONArray next = new JSONArray();

            for (int i = 0; i < ivrs.length(); i++) {
                if (i == index) continue;

                JSONObject item = ivrs.optJSONObject(i);
                if (item != null) next.put(item);
            }

            prefs.edit()
                    .putString("pbx_ivrs_json", next.toString())
                    .apply();

            showIvrManager();

        } catch (Exception e) {
            showToast("حذف IVR ناموفق بود.");
        }
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
        if (adminUnlocked) {
            audioQuality.setText(
                    "Smart Audio • " + label
                    + " • " + score + "/100"
                    + " • Loss " + String.format(java.util.Locale.US, "%.1f%%", loss)
                    + " • Jitter " + Math.round(jitter) + "ms"
                    + " • RTT " + Math.round(rtt) + "ms"
                    + " • ↓" + Math.round(down) + " ↑" + Math.round(up) + " kbps");
        } else {
            audioQuality.setText("کیفیت تماس • " + label);
        }
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
            registerButton.setText("در حال اتصال…");
            connectionStatus.setText("●  در حال اتصال…");
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
            registerButton.setText("اتصال");
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
            registerButton.setText("متصل");
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
            registerButton.setText("اتصال");
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
