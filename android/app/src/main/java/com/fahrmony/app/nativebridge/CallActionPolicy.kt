package com.fahrmony.app.nativebridge

/** Ordinary messages mentioning a call must never create call controls. */
object CallActionPolicy {
    fun allowedForCall(command: String, video: Boolean): Boolean =
        !video || command == "decline" || command == "hangup"
    fun isCall(category: String, style: Boolean, ongoing: Boolean, text: String): Boolean =
        category == "call" || category == "missed_call" || style ||
            (ongoing && text.trim().lowercase() in setOf(
                "邀请你进行语音通话", "邀请你进行视频通话", "语音通话中", "视频通话中",
                "incoming voice call", "incoming video call", "voice call in progress"))
    fun command(label: String, semantic: Int): String? = when (label.trim().lowercase()) {
        "接听", "接听语音", "接听语音通话", "answer", "accept" -> "answer"
        "拒绝", "拒接", "decline", "reject" -> "decline"
        "挂断", "结束通话", "hang up", "end call" -> "hangup"
        "回拨", "回电", "call back", "callback" -> "callback"
        else -> if (semantic == 10) "callback" else null
    }
    fun voiceCommand(text: String): String? = when (text.trim().trimEnd('。', '.', '！', '!').lowercase()) {
        "关闭", "关闭提醒", "dismiss", "close" -> "dismiss"
        "接听", "answer" -> "answer"
        "拒接", "拒绝", "decline" -> "decline"
        "挂断", "hang up" -> "hangup"
        "回拨", "回电", "call back" -> "callback"
        else -> null
    }
    fun label(command: String): String = when (command) {
        "answer" -> "接听"; "decline" -> "拒接"; "hangup" -> "挂断"; "callback" -> "回拨"
        else -> "关闭"
    }
}
