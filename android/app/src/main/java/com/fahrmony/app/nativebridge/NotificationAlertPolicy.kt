package com.fahrmony.app.nativebridge

import android.app.Notification
import java.security.MessageDigest

object NotificationAlertPolicy {
    fun fingerprint(notification: Notification): String = fingerprintParts(listOf(
        notification.`when`.toString(),
        notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
        notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
        notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty(),
        notification.extras.getInt("android.callType", 0).toString()
    ))

    // Only an in-memory digest is retained; neither it nor the payload is logged.
    fun fingerprintParts(parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
