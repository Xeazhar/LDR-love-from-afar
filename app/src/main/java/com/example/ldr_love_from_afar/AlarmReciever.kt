package com.example.ldr_love_from_afar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Force the widget to refresh
        CameraWidget.triggerWidgetRefresh(context)
    }
}
