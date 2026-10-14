package ir.molido.voip;

import android.content.Context;

import org.linphone.core.Account;
import org.linphone.core.AccountParams;
import org.linphone.core.Address;
import org.linphone.core.AuthInfo;
import org.linphone.core.Call;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.Factory;
import org.linphone.core.PayloadType;
import org.linphone.core.RegistrationState;
import org.linphone.core.TransportType;

import java.util.Arrays;

public final class SipRegistrationManager {
    public interface Listener {
        void onStateChanged(String state, String detail, boolean success);
    }

    public interface CallEventListener {
        void onCallStateChanged(String state, String detail, boolean active);
    }

    private final Context appContext;
    private final Listener listener;

    private CallEventListener callEventListener;
    private Core core;
    private Account account;
    private AuthInfo authInfo;
    private Call currentCall;
    private boolean registered;
    private String registeredDomain = "";

    private final CoreListenerStub coreListener = new CoreListenerStub() {
        @Override
        public void onAccountRegistrationStateChanged(
                Core core,
                Account changedAccount,
                RegistrationState state,
                String message) {

            if (account != null && changedAccount != account) return;

            if (state == RegistrationState.Progress) {
                registered = false;
                notifyState("Connecting", safe(message), false);
            } else if (state == RegistrationState.Refreshing) {
                notifyState("Refreshing", safe(message), registered);
            } else if (state == RegistrationState.Ok) {
                registered = true;
                notifyState("Registered", safe(message), true);
            } else if (state == RegistrationState.Failed) {
                registered = false;
                String detail = safe(message);
                try {
                    if (changedAccount.getErrorInfo() != null
                            && changedAccount.getErrorInfo().getPhrase() != null
                            && !changedAccount.getErrorInfo().getPhrase().isEmpty()) {
                        detail = changedAccount.getErrorInfo().getPhrase();
                    }
                } catch (Exception ignored) {}
                notifyState("Failed", detail, false);
            } else if (state == RegistrationState.Cleared) {
                registered = false;
                notifyState("Offline", "Unregistered", false);
            } else {
                notifyState("Offline", safe(message), false);
            }
        }

        @Override
        public void onCallStateChanged(
                Core core,
                Call call,
                Call.State state,
                String message) {

            if (currentCall == null) {
                if (state == Call.State.OutgoingInit
                        || state == Call.State.OutgoingProgress
                        || state == Call.State.OutgoingRinging
                        || state == Call.State.OutgoingEarlyMedia
                        || state == Call.State.Connected
                        || state == Call.State.StreamsRunning) {
                    currentCall = call;
                }
            }

            if (currentCall != null && call != currentCall) return;

            if (state == Call.State.OutgoingInit
                    || state == Call.State.OutgoingProgress) {
                notifyCall("Calling", safe(message), true);
            } else if (state == Call.State.OutgoingRinging
                    || state == Call.State.OutgoingEarlyMedia) {
                notifyCall("Ringing", safe(message), true);
            } else if (state == Call.State.Connected
                    || state == Call.State.StreamsRunning) {
                notifyCall("Connected", safe(message), true);
            } else if (state == Call.State.Paused
                    || state == Call.State.PausedByRemote) {
                notifyCall("Paused", safe(message), true);
            } else if (state == Call.State.Error) {
                notifyCall("Failed", safe(message), false);
                currentCall = null;
            } else if (state == Call.State.End) {
                notifyCall("Ended", safe(message), false);
            } else if (state == Call.State.Released) {
                notifyCall("Ended", "Call released", false);
                currentCall = null;
            }
        }
    };

    public SipRegistrationManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    public void setCallEventListener(CallEventListener listener) {
        this.callEventListener = listener;
    }

    public synchronized void register(
            String registrar,
            String username,
            String password,
            String transportName) throws Exception {

        registrar = clean(registrar);
        username = clean(username);
        password = password == null ? "" : password;

        if (registrar.isEmpty()) throw new IllegalArgumentException("SIP Registrar is required");
        if (username.isEmpty()) throw new IllegalArgumentException("SIP Username is required");
        if (password.isEmpty()) throw new IllegalArgumentException("SIP Password is required");

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

        registeredDomain = serverAddress.getDomain();
        Address identity = factory.createAddress(
                "sip:" + username + "@" + registeredDomain);

        if (identity == null) throw new IllegalArgumentException("Invalid SIP identity");

        authInfo = factory.createAuthInfo(
                username, null, password, null, null, registeredDomain);
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

    public synchronized boolean isRegistered() {
        return registered;
    }

    public synchronized boolean hasActiveCall() {
        return currentCall != null;
    }

    public synchronized void startOutgoingCall(String destination) throws Exception {
        destination = clean(destination);

        if (!registered || core == null || account == null) {
            throw new IllegalStateException("SIP is not registered");
        }
        if (destination.isEmpty()) {
            throw new IllegalArgumentException("Destination is empty");
        }
        if (currentCall != null) {
            throw new IllegalStateException("Another call is already active");
        }

        String uri = destination;
        if (!uri.startsWith("sip:") && !uri.startsWith("sips:")) {
            if (uri.contains("@")) {
                uri = "sip:" + uri;
            } else {
                if (registeredDomain.isEmpty()) {
                    throw new IllegalStateException("SIP domain unavailable");
                }
                uri = "sip:" + uri + "@" + registeredDomain;
            }
        }

        Address address = Factory.instance().createAddress(uri);
        if (address == null) {
            throw new IllegalArgumentException("Invalid destination");
        }

        Call call = core.inviteAddress(address);
        if (call == null) {
            throw new IllegalStateException("Unable to create outgoing call");
        }

        currentCall = call;
        notifyCall("Calling", "INVITE sent", true);
    }

    public synchronized void terminateCall() {
        if (currentCall == null) {
            notifyCall("Ended", "No active call", false);
            return;
        }
        int result = currentCall.terminate();
        if (result != 0) {
            notifyCall("Failed", "Unable to terminate call", true);
        }
    }

    public synchronized boolean toggleMute() {
        if (currentCall == null) return false;
        boolean muted = !currentCall.getMicrophoneMuted();
        currentCall.setMicrophoneMuted(muted);
        return muted;
    }

    public synchronized void unregister() {
        if (core == null || account == null) {
            registered = false;
            notifyState("Offline", "No active SIP account", false);
            return;
        }

        try {
            AccountParams params = account.getParams().clone();
            params.setRegisterEnabled(false);
            account.setParams(params);
            registered = false;
            notifyState("Offline", "Unregister requested", false);
        } catch (Exception e) {
            notifyState("Failed", "Unregister failed", false);
        }
    }

    public synchronized void shutdown() {
        try {
            if (currentCall != null) currentCall.terminate();
        } catch (Exception ignored) {}

        try {
            if (account != null) {
                AccountParams params = account.getParams().clone();
                params.setRegisterEnabled(false);
                account.setParams(params);
            }
        } catch (Exception ignored) {}

        try {
            if (core != null) {
                core.removeListener(coreListener);
                core.stop();
            }
        } catch (Exception ignored) {}

        registered = false;
        currentCall = null;
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

        configureHdAudio(core);
        core.start();
    }

    private void removeCurrentAccount() {
        if (core == null) return;

        try {
            if (currentCall != null) currentCall.terminate();
        } catch (Exception ignored) {}

        currentCall = null;
        registered = false;

        try {
            if (account != null) {
                AccountParams params = account.getParams().clone();
                params.setRegisterEnabled(false);
                account.setParams(params);
                core.removeAccount(account);
            }
        } catch (Exception ignored) {}

        try {
            core.clearAllAuthInfo();
        } catch (Exception ignored) {}

        account = null;
        authInfo = null;
    }

    private void configureHdAudio(Core targetCore) {
        // Prefer quality while keeping adaptation enabled for unstable mobile networks.
        targetCore.setEchoCancellationEnabled(true);

        // Echo limiter can reduce full-duplex quality; keep it off unless EC cannot cope.
        targetCore.setEchoLimiterEnabled(false);

        // Linphone adaptive jitter compensation should stay enabled for VoIP.
        targetCore.setAudioAdaptiveJittcompEnabled(true);

        // RTCP-driven bitrate adaptation protects audio when network quality changes.
        targetCore.setAdaptiveRateControlEnabled(true);
        targetCore.setAdaptiveRateAlgorithm("advanced");

        // Linphone software AGC is documented as experimental; Android hardware/audio
        // processing is preferred.
        targetCore.setAgcEnabled(false);

        // Let the OS choose the media port to reduce avoidable local port conflicts.
        targetCore.setAudioPort(-1);

        preferHdAudioCodecs(targetCore);
    }

    private void preferHdAudioCodecs(Core targetCore) {
        try {
            PayloadType[] codecs = targetCore.getAudioPayloadTypes();

            for (PayloadType codec : codecs) {
                String mime = codec.getMimeType();
                if (mime == null) continue;

                if ("opus".equalsIgnoreCase(mime)
                        || "g722".equalsIgnoreCase(mime)
                        || "pcmu".equalsIgnoreCase(mime)
                        || "pcma".equalsIgnoreCase(mime)) {
                    codec.enable(true);
                }
            }

            Arrays.sort(codecs, (a, b) ->
                    Integer.compare(codecPriority(a), codecPriority(b)));

            targetCore.setAudioPayloadTypes(codecs);
        } catch (Exception ignored) {
            // Keep Linphone defaults if codec reordering is unavailable on a device.
        }
    }

    private int codecPriority(PayloadType codec) {
        if (codec == null || codec.getMimeType() == null) return 100;

        String mime = codec.getMimeType().toLowerCase(java.util.Locale.ROOT);

        if ("opus".equals(mime)) return 0;
        if ("g722".equals(mime)) return 1;
        if ("pcmu".equals(mime)) return 2;
        if ("pcma".equals(mime)) return 3;
        return 20;
    }

    private TransportType parseTransport(String value) {
        String v = clean(value).toUpperCase(java.util.Locale.ROOT);
        if ("UDP".equals(v)) return TransportType.Udp;
        if ("TCP".equals(v)) return TransportType.Tcp;
        return TransportType.Tls;
    }

    private void notifyState(String state, String detail, boolean success) {
        if (listener != null) listener.onStateChanged(state, detail, success);
    }

    private void notifyCall(String state, String detail, boolean active) {
        if (callEventListener != null) {
            callEventListener.onCallStateChanged(state, detail, active);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String v = value.replace('\n', ' ').replace('\r', ' ').trim();
        return v.length() > 180 ? v.substring(0, 180) : v;
    }
}
