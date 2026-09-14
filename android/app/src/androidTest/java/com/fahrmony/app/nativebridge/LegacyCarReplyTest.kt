package com.fahrmony.app.nativebridge

import android.app.Notification
import android.app.PendingIntent
import androidx.core.app.RemoteInput
import androidx.core.app.NotificationCompat
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run on a device: exercises Android's actual legacy CarExtender parcel format. Never sends a message. */
@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class LegacyCarReplyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun notification(mutable: Boolean, includeInput: Boolean = true): Pair<StatusBarNotification, PendingIntent> {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE
            else if (mutable) 0 else PendingIntent.FLAG_IMMUTABLE
        val target = Intent(context, FahrmonyMessageReceiver::class.java).setAction("legacy-reply-test-$mutable-$includeInput")
        val pending = PendingIntent.getBroadcast(context, 9201, target, flags)
        val conversation = NotificationCompat.CarExtender.UnreadConversation.Builder("test contact")
            .addMessage("test message").setLatestTimestamp(1)
        if (includeInput) conversation.setReplyAction(pending,
            RemoteInput.Builder("original-result-key").setAllowFreeFormInput(true).build())
        val n = NotificationCompat.Builder(context, "test-only").setContentTitle("test contact")
            .extend(NotificationCompat.CarExtender().setUnreadConversation(conversation.build())).build()
        val source = StatusBarNotification(context.packageName, context.packageName, 9201, "test",
            Process.myUid(), 0, 0, n, Process.myUserHandle(), 1)
        return source to pending
    }
    @Test fun replyExistsWithNoOrdinaryNotificationButtons() {
        val (source, pending) = notification(true)
        assertTrue(source.notification.actions.isNullOrEmpty())
        val reply = FahrmonyNotificationActions.replyCapability(context, source)
        assertNotNull(reply)
        assertEquals(pending, reply!!.actionIntent)
        assertEquals("original-result-key", reply.remoteInputs.single().resultKey)
    }
    @Test fun absentLegacyReplyDoesNotCreateFakeReply() {
        assertNull(FahrmonyNotificationActions.replyCapability(context, notification(true, false).first))
    }
    @Test fun immutableLegacyCapabilityIsRejectedOnAndroid12AndAbove() {
        if (Build.VERSION.SDK_INT >= 31)
            assertNull(FahrmonyNotificationActions.replyCapability(context, notification(false).first))
    }
}
