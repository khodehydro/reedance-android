package com.hydro.reedance.install

import android.util.Base64
import com.hydro.reedance.BuildConfig

/**
 * Reads the app's shared distribution key provisioned at build time. No key is
 * requested from or stored as a user password. The public debug key is only for
 * interoperability tests; production builds remain disabled until a real key is
 * provisioned outside the repository.
 */
object PackDistributionKey {
    fun load(keyId: String): ByteArray {
        require(keyId == "enc-main") { "App update required: unknown encryption key '$keyId'" }
        check(!BuildConfig.PACK_ENC_TEST_KEY || BuildConfig.DEBUG) {
            "Test distribution key cannot be used in a production build"
        }
        val encoded = BuildConfig.PACK_ENC_MAIN_KEY
        require(encoded.isNotBlank()) { "Distribution key enc-main is not provisioned in this app build" }
        val key = decode(encoded)
        require(key.size == 32) { "Distribution key enc-main must be exactly 32 bytes" }
        return key
    }

    private fun decode(value: String): ByteArray {
        val normalized = value.trim()
        if(normalized.matches(Regex("[a-fA-F0-9]{64}"))) {
            return ByteArray(32) { index -> normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }
        val decoded = try { Base64.decode(normalized, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING) }
        catch(_: IllegalArgumentException) { Base64.decode(normalized, Base64.DEFAULT) }
        return decoded
    }
}
