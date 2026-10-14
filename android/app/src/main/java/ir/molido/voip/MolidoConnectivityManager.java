package ir.molido.voip;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

public final class MolidoConnectivityManager {
    public interface Listener {
        void onNetworkChanged(boolean reachable, String type, boolean metered);
    }

    private final ConnectivityManager cm;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable emitRunnable = this::emitCurrent;
    private boolean started = false;

    private final ConnectivityManager.NetworkCallback callback =
            new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            scheduleEmit();
        }

        @Override
        public void onLost(Network network) {
            scheduleEmit();
        }

        @Override
        public void onCapabilitiesChanged(
                Network network,
                NetworkCapabilities capabilities) {
            scheduleEmit();
        }
    };

    public MolidoConnectivityManager(Context context, Listener listener) {
        cm = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        this.listener = listener;
    }

    public synchronized void start() {
        if (started || cm == null) return;
        started = true;
        cm.registerDefaultNetworkCallback(callback);
        scheduleEmit();
    }

    public synchronized void stop() {
        if (!started || cm == null) return;
        started = false;
        handler.removeCallbacks(emitRunnable);
        try {
            cm.unregisterNetworkCallback(callback);
        } catch (Exception ignored) {}
    }

    private void scheduleEmit() {
        handler.removeCallbacks(emitRunnable);
        handler.postDelayed(emitRunnable, 800L);
    }

    private void emitCurrent() {
        if (!started || cm == null || listener == null) return;

        try {
            Network network = cm.getActiveNetwork();
            if (network == null) {
                listener.onNetworkChanged(false, "Offline", false);
                return;
            }

            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            if (caps == null
                    || !caps.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                listener.onNetworkChanged(false, "Offline", false);
                return;
            }

            boolean reachable = caps.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED);

            String type = "Other";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                type = "Wi-Fi";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                type = "Mobile";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                type = "Ethernet";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                type = "VPN";
            }

            listener.onNetworkChanged(
                    reachable,
                    type,
                    cm.isActiveNetworkMetered());
        } catch (Exception e) {
            listener.onNetworkChanged(false, "Unknown", false);
        }
    }
}
