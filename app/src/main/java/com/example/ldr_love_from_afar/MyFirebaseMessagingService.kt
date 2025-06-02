package com.example.ldr_love_from_afar


import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMsg: RemoteMessage) {
        // We only care about our “refresh_widget” data messages
        val data = remoteMsg.data
        if (data["type"] == "refresh_widget") {
            // Immediately refresh all widget instances for CameraWidget
            CameraWidget.triggerWidgetRefresh(this)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Whenever the FCM registration token changes, save it under /users/<uid>/fcmToken
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseDatabase.getInstance()
            .getReference("users/$uid/fcmToken")
            .setValue(token)
    }
}
