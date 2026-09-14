package com.fahrmony.app.nativebridge

import org.junit.Assert.*
import org.junit.Test

class CallActionPolicyTest {
    @Test fun videoCallsCannotBeAnsweredOrCalledBackByVoiceBridge() {
        assertFalse(CallActionPolicy.allowedForCall("answer", true))
        assertFalse(CallActionPolicy.allowedForCall("callback", true))
        assertTrue(CallActionPolicy.allowedForCall("decline", true))
        assertTrue(CallActionPolicy.allowedForCall("answer", false))
    }
    @Test fun normalMessagesCannotSpoofCalls() {
        assertFalse(CallActionPolicy.isCall("msg", false, false, "邀请你进行语音通话"))
        assertFalse(CallActionPolicy.isCall("msg", false, false, "明天打个语音通话"))
        assertFalse(CallActionPolicy.isCall("msg", false, true, "请接听微信电话"))
    }
    @Test fun standardCallsAndLegacyOngoingCallsAreRecognized() {
        assertTrue(CallActionPolicy.isCall("call", false, false, ""))
        assertTrue(CallActionPolicy.isCall("", true, false, ""))
        assertTrue(CallActionPolicy.isCall("", false, true, "邀请你进行语音通话"))
        assertTrue(CallActionPolicy.isCall("missed_call", false, false, ""))
    }
    @Test fun onlyExplicitActionLabelsOrCallSemanticAreAccepted() {
        assertEquals("answer", CallActionPolicy.command("接听", 0))
        assertEquals("decline", CallActionPolicy.command("Decline", 0))
        assertEquals("callback", CallActionPolicy.command("", 10))
        assertNull(CallActionPolicy.command("回复", 1))
        assertNull(CallActionPolicy.command("打开微信", 0))
        assertNull(CallActionPolicy.command("不接听", 0))
    }
    @Test fun arbitraryChatTextIsNeverAnAction() {
        assertNull(CallActionPolicy.voiceCommand("请不要接听"))
        assertNull(CallActionPolicy.voiceCommand("我马上到"))
        assertNull(CallActionPolicy.voiceCommand("接听并回拨"))
        assertNull(CallActionPolicy.voiceCommand(""))
    }
    @Test fun voiceCommandsTolerateOnlyWhitespaceAndTrailingPunctuation() {
        assertEquals("answer", CallActionPolicy.voiceCommand(" 接听。 "))
        assertEquals("dismiss", CallActionPolicy.voiceCommand("关闭提醒"))
        assertEquals("callback", CallActionPolicy.voiceCommand("回拨"))
        assertEquals("hangup", CallActionPolicy.voiceCommand("HANG UP"))
    }
    @Test fun privacyLogsContainNeitherSenderNorMessageNorExtras() {
        FahrmonyLogBuffer.clear()
        FahrmonyLogBuffer.addLog("IM_NOTIFICATION", "微信", "PrivateSender", "Secret message", "secret extras")
        val entry = FahrmonyLogBuffer.getLogs().single()
        assertFalse(entry.title.contains("PrivateSender"))
        assertFalse(entry.content.contains("Secret"))
        assertNull(entry.rawExtras)
    }
    @Test fun logStorageIsBounded() {
        FahrmonyLogBuffer.clear()
        repeat(150) { FahrmonyLogBuffer.addLog("CALL_DETECTED", "微信", "PrivateSender", "Secret") }
        assertEquals(100, FahrmonyLogBuffer.getLogs().size)
    }
}
