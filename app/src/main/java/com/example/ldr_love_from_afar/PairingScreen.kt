package com.example.ldr_love_from_afar

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

@Composable
fun PairingScreen(
    onPaired: () -> Unit
) {
    val context = LocalContext.current

    // 1) Get Firebase references
    val db = FirebaseDatabase.getInstance().reference
    val user = FirebaseAuth.getInstance().currentUser
    val uid = user?.uid
    if (uid == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("User not authenticated", style = MaterialTheme.typography.h6)
        }
        return
    }

    // 2) UI state
    var username by remember { mutableStateOf("") }
    var partnerCode by remember { mutableStateOf("") }

    // 3) Pairing state
    var pairedUsername by remember { mutableStateOf<String?>(null) }
    var hasCheckedPairing by remember { mutableStateOf(false) }
    var showPairedMessage by remember { mutableStateOf(false) }

    // 4) On first load, check if already paired
    LaunchedEffect(Unit) {
        val snapshot = db.child("users/$uid").get().await()
        val existingPartnerUid = snapshot.child("partnerUid").getValue(String::class.java)

        if (!existingPartnerUid.isNullOrEmpty()) {
            // Fetch partner’s username
            val partnerSnapshot = db.child("users/$existingPartnerUid").get().await()
            val name =
                partnerSnapshot.child("username").getValue(String::class.java) ?: "your partner"
            pairedUsername = name
            showPairedMessage = true

            // Wait 2s, then call onPaired()
            delay(2000)
            onPaired()
        } else {
            // Not paired yet
            hasCheckedPairing = true
        }
    }

    // 5) If user taps “Pair” and pairing succeeds, set pairedUsername → show message → navigate
    //    We will watch pairedUsername below in a separate LaunchedEffect.
    //    (So that no composables get called inside the onClick callback.)

    // 6) If pairedUsername changed, show message for 2s, then navigate
    LaunchedEffect(pairedUsername) {
        if (pairedUsername != null) {
            delay(2000)
            onPaired()
        }
    }

    // 7) Render UI based on state
    when {
        // 7a) Still checking Firebase for existing pairing
        !hasCheckedPairing && pairedUsername == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        // 7b) If we already have a pairedUsername (show it briefly)
        showPairedMessage && pairedUsername != null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("You’re paired with $pairedUsername!", style = MaterialTheme.typography.h5)
            }
        }

        // 7c) Otherwise, show the pairing form
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Enter your username", style = MaterialTheme.typography.h6)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = { Text("Your name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Enter partner’s code")
                OutlinedTextField(
                    value = partnerCode,
                    onValueChange = { partnerCode = it },
                    placeholder = { Text("Partner’s code (UID)") },
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(onClick = {
                    if (username.isBlank()) {
                        Toast.makeText(context, "Username can’t be empty", Toast.LENGTH_SHORT)
                            .show()
                        return@Button
                    }

                    // Save username at /users/$uid/username
                    db.child("users/$uid/username").setValue(username)

                    if (partnerCode.isNotBlank()) {
                        // Look up partner’s node
                        db.child("users/$partnerCode").get()
                            .addOnSuccessListener { snapshot ->
                                if (!snapshot.exists()) {
                                    Toast.makeText(
                                        context,
                                        "Partner code not found",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@addOnSuccessListener
                                }

                                // Write both partnerUids
                                db.child("users/$uid/partnerUid").setValue(partnerCode)
                                db.child("users/$partnerCode/partnerUid").setValue(uid)

                                val name =
                                    snapshot.child("username").getValue(String::class.java)
                                        ?: "your partner"
                                pairedUsername = name
                                showPairedMessage = true
                                // The 2s delay + navigation is handled by LaunchedEffect(pairedUsername)
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    context,
                                    "Failed to look up partner code",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    } else {
                        // Solo mode
                        Toast.makeText(
                            context,
                            "Paired without partner (solo)",
                            Toast.LENGTH_SHORT
                        ).show()
                        onPaired()
                    }
                }) {
                    Text("Pair")
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    "Your pairing code:",
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    uid,
                    style = MaterialTheme.typography.h6
                )
            }
        }
    }
}
