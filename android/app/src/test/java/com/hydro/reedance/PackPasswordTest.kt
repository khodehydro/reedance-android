package com.hydro.reedance

import com.hydro.reedance.install.PackPassword
import org.junit.Assert.*
import org.junit.Test

/** Expected values generated independently with Node crypto.pbkdf2Sync (desktop parameters). */
class PackPasswordTest {
    private fun hex(password: String) = PackPassword.derive(password).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Test fun matchesNodeDesktopAscii() {
        assertEquals("598b0228ba6c330c48a601584e9c91f53cc9556f2072dde32691338f798f6166", hex("abcde-fghjk-mnpqr-stuvw"))
    }
    @Test fun matchesNodeNfkcAndEcmaWhitespace() {
        assertEquals("b56e858c31a823ba47c51872f4bf7885618d8b55348c96f2bb72c50cc7eb0f01", hex("\u3000ＡＢＣＤＥ-fghjk-mnpqr-stuvw\uFEFF"))
    }
    @Test fun matchesNodeUtf8Password() {
        assertEquals("ee852b074bc81a0cd57103cd2ca128ce8ff2d656b0ebd2be750763cfc2da74bf", hex("  آزمون-رمز-فایل-ترکی  "))
    }
    @Test fun punctuationIsPartOfPassword() {
        assertNotEquals(hex("abcde-fghjk-mnpqr-stuvw"), hex("abcdefghjkmnpqrstuvw"))
    }
    @Test fun nodeEncryptedContainerDecryptsWithDerivedKey() {
        val data = com.google.gson.JsonParser.parseString(javaClass.getResourceAsStream("/password-v1.json")!!.bufferedReader().use { it.readText() }).asJsonObject
        fun unhex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val pack = unhex(data.get("packHex").asString)
        val payload = com.google.gson.JsonObject().apply { add("package", com.google.gson.JsonObject().apply {
            addProperty("sizeBytes", pack.size); addProperty("sha256", data.get("packSha256").asString); addProperty("encryptionKeyId", "enc-main")
        }) }
        assertArrayEquals(unhex(data.get("plaintextHex").asString), com.hydro.reedance.contract.PackVerifier.decryptFixture(pack, payload,
            mapOf("enc-main" to PackPassword.derive("abcde-fghjk-mnpqr-stuvw"))))
    }
    @Test(expected = IllegalArgumentException::class) fun shortPasswordRejected() { PackPassword.derive("short") }
    @Test(expected = IllegalArgumentException::class) fun longPasswordRejected() { PackPassword.derive("a".repeat(129)) }
}
