package com.macrotap.app

import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object License {
    private var cache: Boolean? = null
    private fun prefs(c: Context) = c.getSharedPreferences("license", Context.MODE_PRIVATE)

    fun deviceId(c: Context): String {
        val raw = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val h = MessageDigest.getInstance("SHA-256").digest(("drivex:" + raw).toByteArray())
        val hex = h.joinToString("") { "%02X".format(it) }
        return hex.substring(0, 4) + "-" + hex.substring(4, 8) + "-" + hex.substring(8, 12)
    }

    private fun verify(c: Context, key: String): Boolean = try {
        val pub = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(Base64.decode(PubKey.B64, Base64.DEFAULT)))
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initVerify(pub)
        sig.update(deviceId(c).toByteArray())
        sig.verify(Base64.decode(key, Base64.DEFAULT))
    } catch (e: Exception) {
        false
    }

    fun isPro(c: Context): Boolean {
        cache?.let { return it }
        val k = prefs(c).getString("key", "") ?: ""
        val ok = k.isNotEmpty() && verify(c, k)
        cache = ok
        return ok
    }

    fun activate(c: Context, key: String): Boolean {
        val k = key.replace("\\s".toRegex(), "")
        val ok = k.isNotEmpty() && verify(c, k)
        if (ok) prefs(c).edit().putString("key", k).apply()
        cache = null
        return ok
    }
}
