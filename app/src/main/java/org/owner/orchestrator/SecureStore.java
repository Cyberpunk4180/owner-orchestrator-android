package org.owner.orchestrator;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Local Android Keystore-backed token storage. Never sync or export the token. */
final class SecureStore {
    private static final String KEY_ALIAS = "owner_orchestrator_token_v1";
    private static final String PREFS = "owner_orchestrator_secure";
    private final SharedPreferences prefs;

    SecureStore(Context context) {
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String baseUrl() { return prefs.getString("base_url", ""); }
    void setBaseUrl(String value) { prefs.edit().putString("base_url", value).apply(); }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    void setToken(String token) throws Exception {
        if (token.isEmpty()) { prefs.edit().remove("token").apply(); return; }
        if (token.length() < 32) throw new IllegalArgumentException("Токен должен содержать не менее 32 символов");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        String packed = Base64.encodeToString(iv, Base64.NO_WRAP) + "." +
                Base64.encodeToString(encrypted, Base64.NO_WRAP);
        prefs.edit().putString("token", packed).apply();
    }

    String token() throws Exception {
        String packed = prefs.getString("token", "");
        if (packed.isEmpty()) return "";
        String[] parts = packed.split("\\.", -1);
        if (parts.length != 2) throw new IllegalStateException("Неверный формат сохранённого токена");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] cipherText = Base64.decode(parts[1], Base64.NO_WRAP);
        if (iv.length != 12) throw new IllegalStateException("Неверный IV токена");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }

    void clearToken() { prefs.edit().remove("token").apply(); }
}
