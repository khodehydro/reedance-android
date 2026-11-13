package com.hydro.reedance

import com.google.gson.JsonParser
import com.hydro.reedance.contract.PackVerifier as V
import com.hydro.reedance.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
class PackVerifierTest {
    private fun bytes(name: String) = javaClass.getResourceAsStream("/$name")!!.readBytes()
    private fun keys() = JsonParser.parseString(String(bytes("keys.test.json"))).asJsonObject
    private fun manifest() = V.manifest(bytes("latest.json"), mapOf("sig-test-v1" to V.base64(keys().s("ed25519PublicKeyBase64url"))), "quiet-step", "en")
    private fun reject(block: () -> Unit) { try { block() } catch(_: Exception) { return }; fail("Expected rejection") }
    @Test fun goldenManifestAndContainerInteroperate() {
        val p = manifest()
        val zip = V.decryptFixture(bytes(p.obj("package").s("fileName")), p, mapOf("enc-test-v1" to V.base64(keys().s("aes256KeyBase64url"))))
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input -> while(true) { val e = input.nextEntry ?: break; entries[e.name] = input.readBytes() } }
        assertArrayEquals(bytes("lesson.json"), entries["lesson.json"])
        assertArrayEquals(bytes("audio.wav"), entries["audio.wav"])
    }
    @Test fun duplicateKeysTrailingJsonAndPaddedBase64AreRejected() {
        reject { V.strictJson("{\"x\":1,\"x\":2}".toByteArray(), 100) }
        reject { V.base64("YQ==") }
        reject { V.strictJson("{} {}".toByteArray(), 100) }
        reject { V.strictJson(byteArrayOf(0xff.toByte()), 100) }
    }
    @Test fun rollbackIdentityAndSameVersionMutationAreRejected() {
        val publicKeys = mapOf("sig-test-v1" to V.base64(keys().s("ed25519PublicKeyBase64url")))
        reject { V.manifest(bytes("latest.json"), publicKeys, "other-song", "en") }
        reject { V.manifest(bytes("latest.json"), publicKeys, "quiet-step", "ko") }
        reject { V.manifest(bytes("latest.json"), publicKeys, "quiet-step", "en", 2) }
        reject { V.manifest(bytes("latest.json"), publicKeys, "quiet-step", "en", 1, "bad-hash") }
    }
    @Test fun tamperedSignatureIsRejected() {
        val e = JsonParser.parseString(String(bytes("latest.json"))).asJsonObject
        val s = V.base64(e.s("signature")); s[0] = (s[0].toInt() xor 1).toByte()
        e.addProperty("signature", java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s))
        reject { V.manifest(e.toString().toByteArray(), mapOf("sig-test-v1" to V.base64(keys().s("ed25519PublicKeyBase64url"))), "quiet-step", "en") }
    }
    @Test fun gcmTagAndNonceTamperingRejectedEvenWithUpdatedOuterHash() {
        for(offsetFromEnd in listOf(1, 20)) {
            val p = manifest(); val pack = bytes(p.obj("package").s("fileName"))
            pack[pack.size-offsetFromEnd] = (pack[pack.size-offsetFromEnd].toInt() xor 1).toByte()
            p.obj("package").addProperty("sha256", V.sha256(pack))
            reject { V.decryptFixture(pack, p, mapOf("enc-test-v1" to V.base64(keys().s("aes256KeyBase64url")))) }
        }
    }
}
