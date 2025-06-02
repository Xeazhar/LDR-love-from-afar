package com.example.ldr_love_from_afar

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun PhotoReviewScreen(
    photoUri: Uri,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isInPreview = LocalInspectionMode.current

    // Holds user's typed message
    var messageText by remember { mutableStateOf("") }
    val defaultMsgs = listOf(
        "I love you", "I miss you", "Thinking of you", "You’re my everything 💖"
    )

    // Button press effect
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f)

    // Whether we already sent this one successfully
    var sentSuccess by remember { mutableStateOf(false) }

    // Whether we are currently “uploading + DB write”
    var isSending by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            backgroundColor = Color.Black,
            contentColor = Color.White,
            elevation = 0.dp
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Square image preview ───────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (isInPreview) {
                        Text("Image Preview", color = Color.White)
                    } else {
                        Image(
                            painter = rememberAsyncImagePainter(model = photoUri),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                // ── Text input for message ──────────────────────────────────
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("Write a message...", color = Color.Gray) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        textColor = Color.White,
                        backgroundColor = Color.DarkGray,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = Color.White
                    )
                )

                // ── Send button (Image icon) ──────────────────────────────
                val iconRes = if (sentSuccess) R.drawable.checked else R.drawable.send
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = "Send",
                    modifier = Modifier
                        .size(56.dp)
                        .scale(scale)
                        .clickable(enabled = !sentSuccess && !isSending) {
                            pressed = true
                            isSending = true

                            coroutineScope.launch {
                                // 1) Determine final message
                                val finalMessage = messageText.takeIf { it.isNotBlank() }
                                    ?: defaultMsgs.random()

                                // 2) Get current user & partnerUid
                                val currentUser = FirebaseAuth.getInstance().currentUser
                                val currentUid = currentUser?.uid ?: return@launch

                                try {
                                    val db = FirebaseDatabase.getInstance()
                                    val userRef = db.getReference("users/$currentUid")
                                    val userSnapshot = userRef.get().await()
                                    val partnerUid =
                                        userSnapshot.child("partnerUid").getValue(String::class.java)
                                            ?: run {
                                                Toast.makeText(
                                                    context,
                                                    "No partner linked",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                pressed = false
                                                isSending = false
                                                return@launch
                                            }

                                    // 3) Upload photo file under /images/{partnerUid}/...
                                    val storageRef = FirebaseStorage.getInstance().reference
                                    val filename = "IMG_${System.currentTimeMillis()}.jpg"
                                    val imageRef = storageRef.child("images/$partnerUid/$filename")

                                    imageRef.putFile(photoUri).await()
                                    val downloadUri = imageRef.downloadUrl.await()

                                    // 4) Build payloads
                                    val timestamp = System.currentTimeMillis()

                                    val widgetPayload = mapOf(
                                        "imageUrl" to downloadUri.toString(),
                                        "message" to finalMessage,
                                        "timestamp" to timestamp
                                    )
                                    val fullMessagePayload = mapOf(
                                        "from" to currentUid,
                                        "to" to partnerUid,
                                        "imageUrl" to downloadUri.toString(),
                                        "message" to finalMessage,
                                        "timestamp" to timestamp
                                    )

                                    // 5) Write both:
                                    //    • widget_data/{partnerUid}  (their widget shows this)
                                    //    • messages/latest  (latest message for both sides)
                                    db.getReference("widget_data/$partnerUid")
                                        .setValue(widgetPayload)
                                        .await()
                                    db.getReference("messages/latest")
                                        .setValue(fullMessagePayload)
                                        .await()

                                    // Trigger widget refresh immediately:
                                    CameraWidget.triggerWidgetRefresh(context)

                                    // 6) Success: show checkmark, wait 1s, then pop back
                                    sentSuccess = true
                                    delay(1000)
                                    pressed = false
                                    isSending = false
                                    onBack()

                                } catch (e: Exception) {
                                    Toast.makeText(
                                        context,
                                        "Upload or DB write failed: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    pressed = false
                                    isSending = false
                                }
                            }
                        }
                )
            }

            // ── Overlay while sending ────────────────────────────────────
            if (isSending) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Processing...", color = Color.White)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PhotoReviewScreenPreview() {
    val dummyUri = Uri.parse("https://via.placeholder.com/150")
    PhotoReviewScreen(photoUri = dummyUri, onBack = {})
}
