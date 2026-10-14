package ir.molido.voip;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "molido_voip_secure_store_v1";
    private static final String PREFS = "molido_voip_secure_secrets";

    private final SharedPreferences prefs;

    public SecureStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);

        java.security.Key existing = ks.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }

        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

        kg.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());

        return kg.generateKey();
    }

    public void putSecret(String name, String value) throws Exception {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Secret name required");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());

        byte[] encrypted = cipher.doFinal(
                value.getBytes(StandardCharsets.UTF_8));

        String iv = Base64.encodeToString(
                cipher.getIV(), Base64.NO_WRAP);

        String payload = Base64.encodeToString(
                encrypted, Base64.NO_WRAP);

        prefs.edit()
                .putString(name + ".iv", iv)
                .putString(name + ".data", payload)
                .apply();
    }

    public String getSecret(String name) throws Exception {
        String ivB64 = prefs.getString(name + ".iv", null);
        String dataB64 = prefs.getString(name + ".data", null);

        if (ivB64 == null || dataB64 == null) return null;

        byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(dataB64, Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                new GCMParameterSpec(128, iv));

        byte[] clear = cipher.doFinal(encrypted);
        return new String(clear, StandardCharsets.UTF_8);
    }

    public void removeSecret(String name) {
        prefs.edit()
                .remove(name + ".iv")
                .remove(name + ".data")
                .apply();
    }

    public void clearSecrets() {
        prefs.edit().clear().apply();
    }
}
