package com.fahrmony.app.nativebridge
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class FahrmonyMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent != null) FahrmonyNotificationActions.receive(context, intent)
    }
}
