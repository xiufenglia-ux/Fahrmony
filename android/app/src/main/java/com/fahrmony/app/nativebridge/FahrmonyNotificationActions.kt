package com.fahrmony.app.nativebridge

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.fahrmony.app.R
import java.util.UUID

/** Capabilities stay in :car memory. No PendingIntent supplied by a broadcast is trusted. */
object FahrmonyNotificationActions {
    const val ID = 20001
    const val CHANNEL = "fahrmony_wechat_calls"
    const val INPUT = "local_command"
    private val entries = linkedMapOf<String, Entry>()
    private val expirationTasks = mutableMapOf<String, Runnable>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    data class Entry(val sourceKey: String, val packageName: String,
        val token: String = UUID.randomUUID().toString(),
        val tag: String = "bridge:" + UUID.randomUUID().toString(),
        val expires: Long, val fingerprint: String = "", val alert: Boolean = true, val actions: Map<String, Notification.Action> = emptyMap())

    fun register(context: Context, source: StatusBarNotification,
                 actions: Map<String, Notification.Action> = emptyMap(), call: Boolean = false): Entry {
        val fingerprint = NotificationAlertPolicy.fingerprint(source.notification)
        val previous = entries[source.key]
        val entry = Entry(source.key, source.packageName,
            tag = entries[source.key]?.tag ?: ("bridge:" + UUID.randomUUID().toString()),
            fingerprint = fingerprint, alert = previous?.fingerprint != fingerprint,
            expires = SystemClock.elapsedRealtime() + if (call) 90_000L else 600_000L, actions = actions)
        expirationTasks.remove(source.key)?.let { handler.removeCallbacks(it) }
        entries[source.key] = entry
        while (entries.size > 64) remove(context, entries.keys.first())
        val expiration = Runnable {
            if (entries[source.key]?.token == entry.token) remove(context, source.key)
        }
        expirationTasks[source.key] = expiration
        handler.postDelayed(expiration, if (call) 90_000L else 600_000L)
        return entry
    }
    fun remove(context: Context, key: String) {
        expirationTasks.remove(key)?.let { handler.removeCallbacks(it) }
        entries.remove(key)?.let { NotificationManagerCompat.from(context).cancel(it.tag, ID) }
    }
    fun clear(context: Context) { entries.keys.toList().forEach { remove(context, it) } }
    fun intent(context: Context, entry: Entry, command: String, mutable: Boolean = false): PendingIntent {
        val intent = Intent(context, FahrmonyMessageReceiver::class.java).apply {
            action = "${context.packageName}.LOCAL_NOTIFICATION_ACTION"
            data = Uri.Builder().scheme("fahrmony").authority("action")
                .appendPath(entry.token).appendPath(command).build()
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable && Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE
            else if (mutable) 0 else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }
    fun dismissAction(context: Context, entry: Entry): NotificationCompat.Action =
        NotificationCompat.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,
            "标记车载提醒已读", intent(context, entry, "read"))
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false).build()
    fun voiceAction(context: Context, entry: Entry): NotificationCompat.Action {
        val choices = (entry.actions.keys.map { CallActionPolicy.label(it) } + "关闭").toTypedArray()
        // Auto requires RemoteInput. This is an explicitly LOCAL command, never a pretend chat reply.
        return NotificationCompat.Action.Builder(android.R.drawable.ic_btn_speak_now,
            "本地指令（不发送消息）", intent(context, entry, "voice", true))
            .addRemoteInput(RemoteInput.Builder(INPUT).setLabel("仅支持：${choices.joinToString("、")}")
                .setChoices(choices).build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(false).setShowsUserInterface(false).build()
    }
    private fun trusted(context: Context, source: StatusBarNotification, pi: PendingIntent?): Boolean {
        val uid = try { context.packageManager.getApplicationInfo(source.packageName, 0).uid }
            catch (_: android.content.pm.PackageManager.NameNotFoundException) { return false }
        return pi != null && pi.creatorPackage == source.packageName && pi.creatorUid == uid
    }
    private fun sourceActions(n: Notification): List<Notification.Action> =
        (n.actions.orEmpty().toList() + Notification.WearableExtender(n).actions).distinctBy {
            Triple(it.actionIntent, it.title?.toString(), it.remoteInputs?.map { input -> input.resultKey })
        }

    @Suppress("DEPRECATION")
    private fun carConversation(n: Notification): Notification.CarExtender.UnreadConversation? =
        try { Notification.CarExtender(n).unreadConversation }
        catch (_: android.os.BadParcelableException) { null }
        catch (_: ClassCastException) { null }

    fun replyCapability(context: Context, source: StatusBarNotification): Notification.Action? {
        val candidates = sourceActions(source.notification).filter {
            trusted(context, source, it.actionIntent) &&
                (Build.VERSION.SDK_INT < 31 || !it.actionIntent.isImmutable) &&
                it.remoteInputs.orEmpty().count { input -> input.allowFreeFormInput } == 1 &&
                (Build.VERSION.SDK_INT < 28 || it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY ||
                    it.semanticAction == Notification.Action.SEMANTIC_ACTION_NONE)
        }
        // Do not resolve ambiguous actions by choosing an arbitrary recipient/action.
        if (candidates.isNotEmpty()) return candidates.singleOrNull()
        // Nevolution's WeChat decorator revealed this previously missed public API path.
        // A reply capability may exist here even when Notification.actions is empty.
        val conversation = carConversation(source.notification) ?: return null
        val pending = conversation.replyPendingIntent ?: return null
        val input = conversation.remoteInput ?: return null
        if (!trusted(context, source, pending) || !input.allowFreeFormInput ||
            input.resultKey.isBlank() || (Build.VERSION.SDK_INT >= 31 && pending.isImmutable)) return null
        return Notification.Action.Builder(null, "回复", pending).addRemoteInput(input).apply {
            if (Build.VERSION.SDK_INT >= 28) setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
        }.build()
    }

    fun replyAction(context: Context, entry: Entry): NotificationCompat.Action? {
        if (entry.actions["reply"] == null) return null
        return NotificationCompat.Action.Builder(android.R.drawable.ic_btn_speak_now,
            "回复", intent(context, entry, "reply", true))
            .addRemoteInput(RemoteInput.Builder(INPUT).setLabel("回复消息").build())
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setAllowGeneratedReplies(false).setShowsUserInterface(false).build()
    }

    fun diagnostic(context: Context, source: StatusBarNotification, call: Boolean, supported: Int) {
        val n = source.notification
        val conversation = carConversation(n)
        val manager = context.getSystemService(NotificationManager::class.java)
        val importance = manager.getNotificationChannel(if (call) CHANNEL else FahrmonyNotificationListener.CHANNEL_ID)?.importance ?: -1
        FahrmonyLogBuffer.addLog("BRIDGE_DIAGNOSTIC", "通知能力", if (call) "通话诊断" else "消息诊断",
            "原始按钮=${n.actions.orEmpty().size}; 标准通话样式=${n.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().endsWith("CallStyle")}; " +
            "穿戴按钮=${Notification.WearableExtender(n).actions.size}; 同应用按钮=${sourceActions(n).count { trusted(context, source, it.actionIntent) }}; " +
            "旧式车载会话=${conversation != null}; 车载回复凭据=${conversation?.replyPendingIntent != null}; 车载输入=${conversation?.remoteInput != null}; " +
            "快捷输入=${sourceActions(n).sumOf { it.remoteInputs.orEmpty().size }}; 可用操作=$supported; " +
            "通知已授权=${NotificationManagerCompat.from(context).areNotificationsEnabled()}; 提醒级别=$importance")
    }
    fun forwardCall(context: Context, source: StatusBarNotification): Boolean {
        if (source.packageName != "com.tencent.mm") return false
        val n = source.notification
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val style = n.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().endsWith("CallStyle")
        if (!CallActionPolicy.isCall(n.category.orEmpty(), style,
                (n.flags and Notification.FLAG_ONGOING_EVENT) != 0, text)) return false
        val sourceUid = if (Build.VERSION.SDK_INT >= 29) source.uid else try {
            context.packageManager.getApplicationInfo(source.packageName, 0).uid
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { return false }
        val video = n.extras.getBoolean("android.callIsVideo", false) ||
            text.contains("视频") || text.contains("video", ignoreCase = true)
        val actions = sourceActions(n).mapNotNull { action ->
            val pi = action.actionIntent ?: return@mapNotNull null
            if (pi.creatorPackage != source.packageName || pi.creatorUid != sourceUid) return@mapNotNull null
            val semantic = if (Build.VERSION.SDK_INT >= 28) action.semanticAction else 0
            val command = CallActionPolicy.command(action.title?.toString().orEmpty(), semantic)
                ?: return@mapNotNull null
            if (!CallActionPolicy.allowedForCall(command, video)) return@mapNotNull null
            command to action
        }.groupBy({ it.first }, { it.second }).filterValues { it.size == 1 }.mapValues { it.value.single() }.toMutableMap()
        // Standard CallStyle stores capabilities in extras as well as rendered actions.
        // Only read the documented fields for a verified CallStyle notification.
        if (style && Build.VERSION.SDK_INT >= 31) {
            mapOf("answer" to Notification.EXTRA_ANSWER_INTENT,
                "decline" to Notification.EXTRA_DECLINE_INTENT,
                "hangup" to Notification.EXTRA_HANG_UP_INTENT).forEach { (command, key) ->
                val pi = if (Build.VERSION.SDK_INT >= 33) n.extras.getParcelable(key, PendingIntent::class.java)
                    else @Suppress("DEPRECATION") (n.extras.getParcelable(key) as? PendingIntent)
                if (trusted(context, source, pi) && CallActionPolicy.allowedForCall(command, video) && command !in actions) {
                    actions[command] = Notification.Action.Builder(null, CallActionPolicy.label(command), pi).build()
                }
            }
        }
        val entry = register(context, source, actions, call = true)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "微信通话提醒", NotificationManager.IMPORTANCE_HIGH))
        val name = if (FahrmonyConfig.isPreviewHidden(context)) "微信" else
            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(160)?.ifBlank { "微信" } ?: "微信"
        val instructions = if (actions.isEmpty()) "未识别到可转交的通话操作，当前仅提醒。请通过原微信通话界面处理。" else
            "可用本地指令：${actions.keys.joinToString("、") { CallActionPolicy.label(it) }}。在车机选择回复后说出指令；不会发送聊天消息。"
        val sender = Person.Builder().setName(name).build()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_car_notification_badge)
            .setStyle(NotificationCompat.MessagingStyle(Person.Builder().setName("我").build())
                .addMessage("微信通话提醒。$instructions", source.postTime, sender))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setTimeoutAfter(90_000)
            .setOnlyAlertOnce(!entry.alert).setNumber(1).setAutoCancel(false).extend(NotificationCompat.CarExtender())
            .addAction(dismissAction(context, entry))
            .setDeleteIntent(intent(context, entry, "dismiss"))
        if (actions.isNotEmpty()) builder.addAction(voiceAction(context, entry))
        diagnostic(context, source, true, actions.size)
        actions.keys.forEach { command ->
            builder.addAction(NotificationCompat.Action.Builder(android.R.drawable.sym_action_call,
                CallActionPolicy.label(command), intent(context, entry, command)).build())
        }
        try {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled())
                manager.notify(entry.tag, ID, builder.build())
        } catch (_: SecurityException) { remove(context, source.key) }
        FahrmonyLogBuffer.addLog("CALL_DETECTED", "WeChat", "通话通知", "操作数 ${actions.size}")
        return true
    }
    fun receive(context: Context, received: Intent) {
        if (received.action != "${context.packageName}.LOCAL_NOTIFICATION_ACTION") return
        val uri = received.data ?: return
        if (uri.scheme != "fahrmony" || uri.authority != "action" || uri.pathSegments.size != 2) return
        val entry = entries.values.firstOrNull { it.token == uri.pathSegments[0] } ?: return
        if (SystemClock.elapsedRealtime() >= entry.expires ||
            !FahrmonyConfig.isAppEnabled(context, entry.packageName)) { remove(context, entry.sourceKey); return }
        val command = if (uri.pathSegments[1] == "voice") {
            CallActionPolicy.voiceCommand(RemoteInput.getResultsFromIntent(received)
                ?.getCharSequence(INPUT)?.toString().orEmpty())
        } else uri.pathSegments[1]
        if (command == "read") {
            // Auto invokes this BEFORE reply. Keep source capabilities alive until withdrawal/expiry.
            NotificationManagerCompat.from(context).cancel(entry.tag, ID)
            FahrmonyLogBuffer.addLog("BRIDGE_DIAGNOSTIC", "通知操作", "车机已读", "保留后续回复和通话操作")
            return
        }
        if (command == "dismiss") { remove(context, entry.sourceKey); return }
        val action = entry.actions[command]
        if (action == null) {
            feedback(context, "未发送任何消息。此处仅支持本地指令：关闭，或提醒中列出的通话操作。")
            return
        }
        if (Build.VERSION.SDK_INT >= 31 && action.isAuthenticationRequired &&
            context.getSystemService(KeyguardManager::class.java).isDeviceLocked) {
            feedback(context, "微信要求先解锁手机，此次操作未执行。")
            return
        }
        try {
            if (command == "reply") {
                val text = RemoteInput.getResultsFromIntent(received)?.getCharSequence(INPUT)?.toString()
                if (text.isNullOrBlank() || text.length > 2000) {
                    feedback(context, "未发送：回复为空或超过 2000 字。")
                    return
                }
                val input = action.remoteInputs.orEmpty().singleOrNull { it.allowFreeFormInput } ?: return
                val fillIn = Intent()
                android.app.RemoteInput.addResultsToIntent(arrayOf(input), fillIn,
                    android.os.Bundle().apply { putCharSequence(input.resultKey, text) })
                if (Build.VERSION.SDK_INT >= 28) android.app.RemoteInput.setResultsSource(fillIn, android.app.RemoteInput.SOURCE_FREE_FORM_INPUT)
                action.actionIntent.send(context, 0, fillIn)
            } else action.actionIntent.send()
            FahrmonyLogBuffer.addLog("BRIDGE_DIAGNOSTIC", "通知操作", "已转交", "操作=$command；不代表微信已完成")
            remove(context, entry.sourceKey)
            feedback(context, if (command == "reply") "已转交原应用的快捷回复接口，无法确认消息是否送达。"
                else "已转交微信处理；是否接通及音频输出以微信和车机实际状态为准。")
        } catch (_: PendingIntent.CanceledException) {
            FahrmonyLogBuffer.addLog("BRIDGE_DIAGNOSTIC", "通知操作", "转交失败", "凭据已取消")
            remove(context, entry.sourceKey); feedback(context, "微信操作已失效，请等待新的通话提醒。")
        } catch (_: SecurityException) {
            FahrmonyLogBuffer.addLog("BRIDGE_DIAGNOSTIC", "通知操作", "转交失败", "系统拒绝")
            remove(context, entry.sourceKey); feedback(context, "系统阻止了此次微信操作。")
        }
    }
    private fun feedback(context: Context, message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        try {
            NotificationManagerCompat.from(context).notify("bridge-feedback", ID,
                NotificationCompat.Builder(context, FahrmonyNotificationListener.CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_car_notification_badge).setContentTitle("Fahrmony 本地操作")
                    .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setTimeoutAfter(15_000).build())
        } catch (_: SecurityException) { }
    }
}
