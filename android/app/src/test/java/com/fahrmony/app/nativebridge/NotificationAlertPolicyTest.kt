package com.fahrmony.app.nativebridge

import org.junit.Assert.*
import org.junit.Test

class NotificationAlertPolicyTest {
    @Test fun repeatedUpdateIsQuietButNewMessageOrTimestampCanAlert() {
        val first = listOf("100", "sender", "hello", "", "0")
        assertEquals(NotificationAlertPolicy.fingerprintParts(first), NotificationAlertPolicy.fingerprintParts(first.toList()))
        assertNotEquals(NotificationAlertPolicy.fingerprintParts(first), NotificationAlertPolicy.fingerprintParts(listOf("101", "sender", "hello", "", "0")))
        assertNotEquals(NotificationAlertPolicy.fingerprintParts(first), NotificationAlertPolicy.fingerprintParts(listOf("100", "sender", "next", "", "0")))
    }
    @Test fun fieldBoundariesCannotAliasAndDigestDoesNotContainPayload() {
        assertNotEquals(NotificationAlertPolicy.fingerprintParts(listOf("ab", "c")), NotificationAlertPolicy.fingerprintParts(listOf("a", "bc")))
        val digest = NotificationAlertPolicy.fingerprintParts(listOf("private contact", "secret message"))
        assertEquals(64, digest.length)
        assertFalse(digest.contains("secret"))
    }
}
