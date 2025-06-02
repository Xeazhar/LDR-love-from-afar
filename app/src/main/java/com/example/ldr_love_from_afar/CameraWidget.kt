package com.example.ldr_love_from_afar

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CameraWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val settings = SettingsManager(context)
        val rawMine  = settings.timezoneMine
        val rawOther = settings.timezoneOther
        val isKikay  = settings.isKikay

        Log.d("CameraWidget", "Loaded prefs → timezoneMine=\"$rawMine\", timezoneOther=\"$rawOther\", isKikay=$isKikay")
        val zoneMine  = ZoneId.of(rawMine)
        val zoneOther = ZoneId.of(rawOther)

        // ← Instead of withZoneSameInstant, call now(zone) ↑
        val myTime    = ZonedDateTime.now(zoneMine).format(DateTimeFormatter.ofPattern("h:mm a"))
        val otherTime = ZonedDateTime.now(zoneOther).format(DateTimeFormatter.ofPattern("h:mm a"))

        // When isKikay == true, “Me = myTime” and “Him = otherTime”
        // When isKikay == false, “Me = myTime” and “Her = otherTime”
        val (meLabel, meTime, otherLabel, otherTimeStr) = if (isKikay) {
            arrayOf("Me", myTime, "Him", otherTime)
        } else {
            arrayOf("Me", myTime, "Her", otherTime)
        }

        val currentUid = FirebaseAuth.getInstance().currentUser?.uid

        ids.forEach { widgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_camera)

            views.setTextViewText(R.id.time_me_time, meTime)
            views.setTextViewText(R.id.time_me_label, meLabel)
            views.setTextViewText(R.id.time_him_time, otherTimeStr)
            views.setTextViewText(R.id.time_him_label, otherLabel)

            if (currentUid == null) {
                views.setTextViewText(R.id.widget_message, "Not signed in")
                views.setImageViewResource(R.id.widget_photo, R.drawable.ic_placeholder)
                manager.updateAppWidget(widgetId, views)
            } else {
                FirebaseDatabase.getInstance()
                    .getReference("widget_data")
                    .child(currentUid)
                    .get()
                    .addOnSuccessListener { dataSnap ->
                        val imageUrl = dataSnap.child("imageUrl").getValue(String::class.java)
                        val messageText = dataSnap.child("message").getValue(String::class.java) ?: ""

                        views.setTextViewText(R.id.widget_message, messageText)

                        if (!imageUrl.isNullOrEmpty()) {
                            val cacheFile = File(context.cacheDir, "widget_${currentUid}_$widgetId.jpg")
                            FirebaseStorage.getInstance()
                                .getReferenceFromUrl(imageUrl)
                                .getFile(cacheFile)
                                .addOnSuccessListener {
                                    val originalBitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                                    val rotatedBitmap = rotateBitmapIfRequired(cacheFile.absolutePath, originalBitmap)
                                    views.setImageViewBitmap(R.id.widget_photo, rotatedBitmap)
                                    manager.updateAppWidget(widgetId, views)
                                }
                                .addOnFailureListener {
                                    views.setImageViewResource(R.id.widget_photo, R.drawable.ic_placeholder)
                                    manager.updateAppWidget(widgetId, views)
                                }
                        } else {
                            views.setImageViewResource(R.id.widget_photo, R.drawable.ic_placeholder)
                            manager.updateAppWidget(widgetId, views)
                        }
                    }
                    .addOnFailureListener {
                        views.setTextViewText(R.id.widget_message, "Error loading")
                        views.setImageViewResource(R.id.widget_photo, R.drawable.ic_placeholder)
                        manager.updateAppWidget(widgetId, views)
                    }
            }
        }

        scheduleMinuteUpdates(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleMinuteUpdates(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelMinuteUpdates(context)
    }

    private fun scheduleMinuteUpdates(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildUpdateIntent(context)

        val now = System.currentTimeMillis()
        val millisUntilNextMinute = 60_000L - (now % 60_000L)
        val firstTrigger = now + millisUntilNextMinute

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    firstTrigger,
                    pendingIntent
                )
            } catch (se: SecurityException) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    firstTrigger,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                firstTrigger,
                pendingIntent
            )
        }
    }

    private fun buildUpdateIntent(context: Context): PendingIntent {
        val appWidgetManager = context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
        val componentName = ComponentName(context, CameraWidget::class.java)
        val appWidgetIds: IntArray = appWidgetManager.getAppWidgetIds(componentName)

        val intent = Intent(context, CameraWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelMinuteUpdates(context: Context) {
        val intent = Intent(context, CameraWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        fun triggerWidgetRefresh(context: Context) {
            val appWidgetManager = context.getSystemService(Context.APPWIDGET_SERVICE) as AppWidgetManager
            val componentName = ComponentName(context, CameraWidget::class.java)
            val appWidgetIds: IntArray = appWidgetManager.getAppWidgetIds(componentName)

            val intent = Intent(context, CameraWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val triggerTimeMillis = System.currentTimeMillis() + 1000L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            } else {
                try {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMillis,
                        pendingIntent
                    )
                } catch (se: SecurityException) {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerTimeMillis,
                        pendingIntent
                    )
                }
            }
        }
    }

    private fun rotateBitmapIfRequired(imagePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = androidx.exifinterface.media.ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )
            when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
