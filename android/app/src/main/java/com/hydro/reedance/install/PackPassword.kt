package com.hydro.reedance.install

import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Desktop keys.ts protocol at 0d1f612: preserve punctuation, normalize then trim. */
object PackPassword {
    fun derive(password: String): ByteArray {
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFKC)
             .trim { it in "\u0009\u000A\u000B\u000C\u000D\u0020\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF" }
        require(normalized.length in 12..128) { "رمز فایل باید ۱۲ تا ۱۲۸ نویسه باشد / File password must be 12–128 characters" }
        val chars = normalized.toCharArray()
        val spec = PBEKeySpec(chars, "reedance-pack-v1".toByteArray(Charsets.UTF_8), 200000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword(); chars.fill('\u0000') }
    }
}
