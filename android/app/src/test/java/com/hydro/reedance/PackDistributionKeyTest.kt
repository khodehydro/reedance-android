package com.hydro.reedance

import com.google.gson.JsonObject
import com.hydro.reedance.contract.PackVerifier
import com.hydro.reedance.install.PackDistributionKey
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.security.MessageDigest

class PackDistributionKeyTest {
    @Test fun desktopEncMainPackOpensWithTheAndroidBuildKeyWithoutAUserSecret() {
        assumeTrue("run the golden fixture with the debug-only public key", BuildConfig.PACK_ENC_TEST_KEY)
        val pack = javaClass.getResourceAsStream("/desktop-enc-main-test.rdpack")!!.use { it.readBytes() }
        val packageHash = MessageDigest.getInstance("SHA-256").digest(pack)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val payload = JsonObject().apply {
            add("package", JsonObject().apply {
                addProperty("sizeBytes", pack.size)
                addProperty("sha256", packageHash)
                addProperty("encryptionKeyId", "enc-main")
            })
        }

        val zip = PackVerifier.decryptFixture(pack, payload, mapOf("enc-main" to PackDistributionKey.load("enc-main")))
        assertEquals("PK", zip.copyOfRange(0, 2).toString(Charsets.US_ASCII))
        assertEquals(32, PackDistributionKey.load("enc-main").size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownDistributionKeyIdsAreRejected() {
        PackDistributionKey.load("enc-not-provisioned")
    }
}
