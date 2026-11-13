package com.hydro.reedance.contract

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.hydro.reedance.data.*
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Pure JVM protocol primitives. Not an installer: release remains disabled pending full ZIP/schema audit. */
object PackVerifier {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    fun base64(value: String): ByteArray {
        require(value.matches(Regex("[A-Za-z0-9_-]+"))) { "Non-canonical Base64URL" }
        val bytes = Base64.getUrlDecoder().decode(value)
        require(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == value)
        return bytes
    }
    fun strictJson(bytes: ByteArray, limit: Int): JsonObject {
        require(bytes.size <= limit)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val reader = JsonReader(StringReader(text)).apply { strictness = Strictness.STRICT }
        fun read(depth: Int): JsonElement {
            require(depth <= 64) { "Excessive nesting" }
            return when(reader.peek()) {
                JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                    reader.beginObject()
                    while(reader.hasNext()) { val key = reader.nextName(); require(!has(key)) { "Duplicate key: $key" }; add(key, read(depth + 1)) }
                    reader.endObject()
                }
                JsonToken.BEGIN_ARRAY -> JsonArray().apply { reader.beginArray(); while(reader.hasNext()) add(read(depth + 1)); reader.endArray() }
                JsonToken.STRING -> JsonPrimitive(reader.nextString())
                JsonToken.NUMBER -> JsonPrimitive(reader.nextString().toBigDecimal())
                JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
                else -> error("Invalid JSON")
            }
        }
        val result = read(0).asJsonObject
        require(reader.peek() == JsonToken.END_DOCUMENT)
        reader.close()
        return result
    }
    private fun keys(obj: JsonObject, expected: Set<String>) { require(obj.keySet() == expected) { "Unknown or missing fields" } }
    fun manifest(envelope: ByteArray, publicKeys: Map<String, ByteArray>, songId: String, language: String, highestVersion: Long = 0, previousHash: String = ""): JsonObject {
        val e = strictJson(envelope, 32768)
        keys(e, setOf("signatureKeyId", "payload", "signature"))
        val keyId = e.s("signatureKeyId")
        require(keyId.matches(Regex("sig-[a-z0-9-]{1,28}")))
        val publicKey = publicKeys[keyId] ?: error("App update required: unknown signature key")
        require(publicKey.size == 32)
        val bytes = base64(e.s("payload")); require(bytes.size <= 16384)
        val signature = base64(e.s("signature")); require(signature.size == 64)
        val message = "REEDANCE-MANIFEST-V1\n$keyId\n".toByteArray() + bytes
        val verifier = Ed25519Signer().apply { init(false, Ed25519PublicKeyParameters(publicKey, 0)); update(message, 0, message.size) }
        require(verifier.verifySignature(signature)) { "Invalid publisher signature" }
        val p = strictJson(bytes, 16384)
        keys(p, setOf("manifestVersion", "songId", "targetLanguage", "contentVersionCode", "displayVersion", "containerVersion", "lessonSchemaVersion", "releasedAt", "package"))
        require(p.n("manifestVersion") == 1L && p.n("containerVersion") == 1L && p.n("lessonSchemaVersion") == 1L)
        require(p.s("songId") == songId && p.s("targetLanguage") == language)
        val version = p.get("contentVersionCode").asBigDecimal.longValueExact()
        require(version in 1..Int.MAX_VALUE.toLong() && version >= highestVersion) { "Rollback rejected" }
        val pack = p.obj("package")
        keys(pack, setOf("fileName", "sizeBytes", "sha256", "encryptionKeyId"))
        val hash = pack.s("sha256"); require(hash.matches(Regex("[a-f0-9]{64}")))
        require(version != highestVersion || previousHash == hash) { "Same-version mutation" }
        require(pack.s("fileName") == "v${version.toString().padStart(6, '0')}-$hash.rdpack")
        require(pack.get("sizeBytes").asBigDecimal.longValueExact() in 50..268435456)
        require(pack.s("encryptionKeyId").matches(Regex("enc-[a-z0-9-]{1,28}")))
        require(p.s("displayVersion").matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")))
        java.time.Instant.parse(p.s("releasedAt"))
        return p
    }
    /** Bounded in-memory fixture verifier. Full-size production packages require streaming to private staging. */
    fun decryptFixture(pack: ByteArray, payload: JsonObject, aesKeys: Map<String, ByteArray>): ByteArray {
        require(pack.size <= 8 * 1024 * 1024) { "Fixture verifier size limit" }
        val spec = payload.obj("package")
        require(pack.size.toLong() == spec.n("sizeBytes") && sha256(pack) == spec.s("sha256"))
        require(pack.size >= 50 && pack.copyOfRange(0, 8).contentEquals("RDPACK1\u0000".toByteArray()))
        val length = pack[8].toInt() and 255; require(length in 1..32 && pack.size >= 45 + length)
        val id = String(pack, 9, length, Charsets.US_ASCII)
        require(id == spec.s("encryptionKeyId") && id.matches(Regex("enc-[a-z0-9-]+")))
        val key = aesKeys[id] ?: error("App update required: unknown encryption key")
        require(key.size == 32)
        val headerLength = 29 + length
        val plainLength = ByteBuffer.wrap(pack, 21 + length, 8).order(ByteOrder.BIG_ENDIAN).long
        require(plainLength >= 0 && plainLength <= 8 * 1024 * 1024 && pack.size.toLong() == 45 + length + plainLength)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, pack.copyOfRange(9 + length, 21 + length)))
        cipher.updateAAD(pack, 0, headerLength)
        return cipher.doFinal(pack, headerLength, pack.size - headerLength).also { require(it.size.toLong() == plainLength) }
    }
}
