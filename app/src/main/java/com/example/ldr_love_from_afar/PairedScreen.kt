package com.example.ldr_love_from_afar

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun PairedScreen(onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    var partnerUsername by remember { mutableStateOf<String?>(null) }

    // 1) Fetch partnerUsername once on composition
    LaunchedEffect(Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid ?: return@LaunchedEffect
        val snapshot = FirebaseDatabase.getInstance()
            .getReference("users/$uid")
            .get()
            .await()

        val partnerUid = snapshot.child("partnerUid").getValue(String::class.java)
        if (!partnerUid.isNullOrEmpty()) {
            val partnerSnap = FirebaseDatabase.getInstance()
                .getReference("users/$partnerUid")
                .get()
                .await()
            partnerUsername =
                partnerSnap.child("username").getValue(String::class.java) ?: "your partner"

            // 2) Wait 2 seconds, then navigate
            delay(2000)
            onFinish()
        } else {
            // If somehow not paired, jump back to pairing
            onFinish()
        }
    }

    // 3) UI: show the message
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (partnerUsername != null)
                "You’re paired with $partnerUsername!"
            else
                "Loading pairing…",
            style = MaterialTheme.typography.h5
        )
    }
}
