package com.hydro.reedance.install

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only a Keystore-wrapped derived pack key; never the password or a build secret. */
class PackKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("pack-key-v1", Context.MODE_PRIVATE)
    private fun master(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("reedance-pack-wrap-v1", null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("reedance-pack-wrap-v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    @Synchronized fun save(password: String) {
        val key = PackPassword.derive(password)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, master())
            cipher.updateAAD("reedance:enc-main:v1".toByteArray())
            val wrapped = cipher.iv + cipher.doFinal(key)
            check(prefs.edit().putString("wrapped", Base64.encodeToString(wrapped, Base64.NO_WRAP)).commit())
        } finally { key.fill(0) }
    }
    @Synchronized fun load(): ByteArray {
        val stored = prefs.getString("wrapped", null) ?: error("رمز فایل را در پروفایل وارد کنید / Enter file password in Profile")
        val wrapped = Base64.decode(stored, Base64.NO_WRAP); require(wrapped.size == 60)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, master(), GCMParameterSpec(128, wrapped.copyOfRange(0, 12)))
        cipher.updateAAD("reedance:enc-main:v1".toByteArray())
        return cipher.doFinal(wrapped, 12, wrapped.size - 12)
    }
    @Synchronized fun clear() { check(prefs.edit().clear().commit()) }
}
