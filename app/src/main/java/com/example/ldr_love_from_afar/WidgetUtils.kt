package com.example.ldr_love_from_afar

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

/**
 * Call this whenever your app writes a new image/message into Firebase.
 * It forces all instances of `CameraWidget` to re–run onUpdate() immediately.
 */
fun triggerCameraWidgetUpdate(context: Context) {
    val widgetManager = AppWidgetManager.getInstance(context)
    val widgetComponent = ComponentName(context, CameraWidget::class.java)
    val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)
    if (widgetIds.isNotEmpty()) {
        // Invalidate and ask for an update: this will call CameraWidget.onUpdate()
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_camera)
        widgetManager.updateAppWidget(widgetIds, remoteViews)
    }
}
