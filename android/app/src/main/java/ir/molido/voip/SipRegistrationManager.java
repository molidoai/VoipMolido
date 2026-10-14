package ir.molido.voip;

import android.content.Context;

import org.linphone.core.Account;
import org.linphone.core.AccountParams;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.RegistrationState;
import org.linphone.core.TransportType;

public final class SipRegistrationManager {
    public interface Listener {
        void onStateChanged(String state, String detail, boolean success);
    }

    private final Context appContext;
    private final Listener listener;

    private Core core;
    private Account account;
    private AuthInfo authInfo;

    private final CoreListenerStub coreListener = new CoreListenerStub() {
        @Override
        public void onAccountRegistrationStateChanged(
                Core core,
                Account changedAccount,
                RegistrationState state,
                String message) {

            if (account != null && changedAccount != account) return;

            if (state == RegistrationState.Progress) {
                notifyState("Connecting", safe(message), false);
            } else if (state == RegistrationState.Refreshing) {
                notifyState("Refreshing", safe(message), true);
            } else if (state == RegistrationState.Ok) {
                notifyState("Registered", safe(message), true);
            } else if (state == RegistrationState.Failed) {
                String detail = safe(message);
                try {
                    if (changedAccount.getErrorInfo() != null
                            && changedAccount.getErrorInfo().getPhrase() != null
                            && !changedAccount.getErrorInfo().getPhrase().isEmpty()) {
                        detail = changedAccount.getErrorInfo().getPhrase();
                    }
                } catch (Exception ignored) {
                }
                notifyState("Failed", detail, false);
            } else if (state == RegistrationState.Cleared) {
                notifyState("Offline", "Unregistered", false);
            } else {
                notifyState("Offline", safe(message), false);
            }
        }
    };

    public SipRegistrationManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized void register(
            String registrar,
            String username,
            String password,
            String transportName) throws Exception {

        registrar = clean(registrar);
        username = clean(username);
        password = password == null ? "" : password;

        if (registrar.isEmpty()) {
            throw new IllegalArgumentException("SIP Registrar is required");
        }
        if (username.isEmpty()) {
            throw new IllegalArgumentException("SIP Username is required");
        }
        if (password.isEmpty()) {
            throw new IllegalArgumentException("SIP Password is required");
        }

        ensureCore();
        removeCurrentAccount();

        Factory factory = Factory.instance();

        String registrarUri = registrar;
        if (!registrarUri.startsWith("sip:")
                && !registrarUri.startsWith("sips:")) {
            registrarUri = "sip:" + registrarUri;
        }

        Address serverAddress = factory.createAddress(registrarUri);
        if (serverAddress == null || serverAddress.getDomain() == null) {
            throw new IllegalArgumentException("Invalid SIP Registrar");
        }

        String domain = serverAddress.getDomain();

        Address identity = factory.createAddress(
                "sip:" + username + "@" + domain);

        if (identity == null) {
            throw new IllegalArgumentException("Invalid SIP identity");
        }

        authInfo = factory.createAuthInfo(
                username,
                null,
                password,
                null,
                null,
                domain);

        core.addAuthInfo(authInfo);

        AccountParams params = core.createAccountParams();

        if (params.setIdentityAddress(identity) != 0) {
            throw new IllegalArgumentException("Invalid SIP identity");
        }

        if (params.setServerAddress(serverAddress) != 0) {
            throw new IllegalArgumentException("Invalid SIP server");
        }

        params.setTransport(parseTransport(transportName));
        params.setRegisterEnabled(true);
        params.setUnregisterAtStopEnabled(true);

        account = core.createAccount(params);

        if (core.addAccount(account) != 0) {
            throw new IllegalStateException("Unable to add SIP account");
        }

        core.setDefaultAccount(account);
        notifyState("Connecting", "Sending SIP REGISTER…", false);
    }

    public synchronized void unregister() {
        if (core == null || account == null) {
            notifyState("Offline", "No active SIP account", false);
            return;
        }

        try {
            AccountParams params = account.getParams().clone();
            params.setRegisterEnabled(false);
            account.setParams(params);
            notifyState("Offline", "Unregister requested", false);
        } catch (Exception e) {
            notifyState("Failed", "Unregister failed", false);
        }
    }

    public synchronized void shutdown() {
        try {
            if (account != null) {
                AccountParams params = account.getParams().clone();
                params.setRegisterEnabled(false);
                account.setParams(params);
            }
        } catch (Exception ignored) {
        }

        try {
            if (core != null) {
                core.removeListener(coreListener);
                core.stop();
            }
        } catch (Exception ignored) {
        }

        account = null;
        authInfo = null;
        core = null;
    }

    private void ensureCore() {
        if (core != null) return;

        Factory factory = Factory.instance();
        core = factory.createCore(null, null, appContext);
        core.setAutoIterateEnabled(true);
        core.addListener(coreListener);
        core.setUserAgent("MOLIDO-VOIP", BuildConfig.VERSION_NAME);
        core.start();
    }

    private void removeCurrentAccount() {
        if (core == null) return;

        try {
            if (account != null) {
                AccountParams params = account.getParams().clone();
                params.setRegisterEnabled(false);
                account.setParams(params);
                core.removeAccount(account);
            }
        } catch (Exception ignored) {
        }

        try {
            core.clearAllAuthInfo();
        } catch (Exception ignored) {
        }

        account = null;
        authInfo = null;
    }

    private TransportType parseTransport(String value) {
        String v = clean(value).toUpperCase(java.util.Locale.ROOT);

        if ("UDP".equals(v)) return TransportType.Udp;
        if ("TCP".equals(v)) return TransportType.Tcp;
        return TransportType.Tls;
    }

    private void notifyState(String state, String detail, boolean success) {
        if (listener != null) {
            listener.onStateChanged(state, detail, success);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "No details";
        String v = value.trim();
        return v.length() > 180 ? v.substring(0, 180) : v;
    }
}
