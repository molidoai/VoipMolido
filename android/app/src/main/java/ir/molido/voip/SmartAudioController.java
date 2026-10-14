package ir.molido.voip;

import org.linphone.core.CallStats;
import org.linphone.core.Core;

public final class SmartAudioController {
    public interface Listener {
        void onAudioQuality(String profile, int score, float lossPercent,
                            float jitterMs, float rttMs,
                            float downloadKbps, float uploadKbps);
    }

    private final Core core;
    private final Listener listener;

    private String activeProfile = "HD";
    private String pendingProfile = "";
    private int pendingSamples = 0;
    private long lastProfileChangeMs = 0L;

    SmartAudioController(Core core, Listener listener) {
        this.core = core;
        this.listener = listener;

        core.setAdaptiveRateControlEnabled(true);
        core.setAdaptiveRateAlgorithm("advanced");
        core.setAudioAdaptiveJittcompEnabled(true);
        core.setAudioJittcomp(80);
    }

    void onStats(CallStats stats) {
        if (stats == null) return;

        float loss = maxFinite(
                stats.getLocalLossRate(),
                stats.getReceiverLossRate(),
                stats.getSenderLossRate());

        float jitterMs = 1000f * maxFinite(
                stats.getReceiverInterarrivalJitter(),
                stats.getSenderInterarrivalJitter());

        float rttMs = finiteNonNegative(stats.getRoundTripDelay()) * 1000f;
        float down = finiteNonNegative(stats.getDownloadBandwidth());
        float up = finiteNonNegative(stats.getUploadBandwidth());

        int score = calculateScore(loss, jitterMs, rttMs);
        String wanted = chooseProfile(loss, jitterMs, rttMs);

        applyWithHysteresis(wanted);

        if (listener != null) {
            listener.onAudioQuality(
                    activeProfile, score, loss, jitterMs, rttMs, down, up);
        }
    }

    private void applyWithHysteresis(String wanted) {
        if (wanted.equals(activeProfile)) {
            pendingProfile = "";
            pendingSamples = 0;
            return;
        }

        if (!wanted.equals(pendingProfile)) {
            pendingProfile = wanted;
            pendingSamples = 1;
            return;
        }

        pendingSamples++;
        long now = System.currentTimeMillis();

        if (pendingSamples < 3) return;
        if (lastProfileChangeMs != 0L
                && now - lastProfileChangeMs < 5000L) return;

        applyProfile(wanted);
        activeProfile = wanted;
        lastProfileChangeMs = now;
        pendingProfile = "";
        pendingSamples = 0;
    }

    private void applyProfile(String profile) {
        if ("EXCELLENT".equals(profile)) {
            core.setAudioJittcomp(60);
        } else if ("GOOD".equals(profile)) {
            core.setAudioJittcomp(90);
        } else if ("FAIR".equals(profile)) {
            core.setAudioJittcomp(140);
        } else {
            core.setAudioJittcomp(220);
        }

        core.setAudioAdaptiveJittcompEnabled(true);
        core.setAdaptiveRateControlEnabled(true);
    }

    private String chooseProfile(float loss, float jitterMs, float rttMs) {
        if (loss < 1f && jitterMs < 25f && rttMs < 180f) return "EXCELLENT";
        if (loss < 3f && jitterMs < 50f && rttMs < 300f) return "GOOD";
        if (loss < 7f && jitterMs < 100f && rttMs < 500f) return "FAIR";
        return "VOICE SURVIVAL";
    }

    private int calculateScore(float loss, float jitterMs, float rttMs) {
        float penalty = 0f;
        penalty += Math.min(45f, loss * 6f);
        penalty += Math.min(30f, jitterMs / 4f);

        if (rttMs > 120f) {
            penalty += Math.min(25f, (rttMs - 120f) / 16f);
        }

        int score = Math.round(100f - penalty);
        return Math.max(0, Math.min(100, score));
    }

    private static float finiteNonNegative(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f) {
            return 0f;
        }
        return value;
    }

    private static float maxFinite(float... values) {
        float result = 0f;
        for (float value : values) {
            float safe = finiteNonNegative(value);
            if (safe > result) result = safe;
        }
        return result;
    }
}
