package com.example.ldr_love_from_afar

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ldr_love_from_afar.CameraWidget
import java.util.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

@Composable
fun SettingsScreen(onBack: () -> Unit = {}, onRePair: () -> Unit = {}) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    // 1) Initialize local state from SharedPreferences
    var tzMine by remember { mutableStateOf(settingsManager.timezoneMine) }
    var tzOther by remember { mutableStateOf(settingsManager.timezoneOther) }
    var isKikay by remember { mutableStateOf(settingsManager.isKikay) }

    // 2) Build a list of all timezones (ID → "GMT±HH:MM – DisplayName")
    val allTimezones = remember {
        TimeZone.getAvailableIDs().map { id ->
            val tz = TimeZone.getTimeZone(id)
            val hours = tz.rawOffset / 3_600_000
            val minutes = (tz.rawOffset % 3_600_000) / 60_000
            val sign = if (hours >= 0) "+" else "-"
            val offset = String.format("GMT%s%02d:%02d", sign, kotlin.math.abs(hours), kotlin.math.abs(minutes))
            val displayName = tz.getDisplayName(false, TimeZone.LONG)
            id to "$offset – $displayName"
        }.sortedBy { it.second }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TopAppBar(
            title = { Text("Settings", color = Color.White) },
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
            modifier = Modifier.padding(top = 50.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ─── “Your Timezone” ───────────────────────────────────────
            Text("Your Timezone", color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            DropdownSelector(
                options = allTimezones,
                selectedId = tzMine,
                onSelect = { tzMine = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── “His/Her Timezone” ────────────────────────────────────
            Text(if (isKikay) "His Timezone" else "Her Timezone", color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            DropdownSelector(
                options = allTimezones,
                selectedId = tzOther,
                onSelect = { tzOther = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── “Enable Kikay Mode” checkbox ─────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isKikay,
                    onCheckedChange = { isKikay = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color.White,
                        uncheckedColor = Color.White
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Enable Kikay Mode", color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── “Save” button: persist to SharedPreferences + refresh widget ───
            Button(
                onClick = {
                    // 1) Write the new values
                    settingsManager.timezoneMine = tzMine
                    settingsManager.timezoneOther = tzOther
                    settingsManager.isKikay = isKikay

                    // 2) Give user feedback
                    Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()

                    // 3) Trigger the widget to re-read these prefs immediately
                    CameraWidget.triggerWidgetRefresh(context)
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save", color = Color.Black)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider(color = Color.Gray)
            Spacer(modifier = Modifier.height(24.dp))

            // ─── “Unpair / Re-pair” section ─────────────────────────────
            Text("Paired Account", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    val db = FirebaseDatabase.getInstance().reference

                    if (uid != null) {
                        db.child("users/$uid/partnerUid").get().addOnSuccessListener { snapshot ->
                            val partnerUid = snapshot.getValue(String::class.java)
                            if (!partnerUid.isNullOrEmpty()) {
                                db.child("users/$uid/partnerUid").removeValue()
                                db.child("users/$partnerUid/partnerUid").removeValue()
                                Toast.makeText(context, "Unpaired successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "You are not paired", Toast.LENGTH_SHORT).show()
                            }
                            onRePair()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Re-pair with partner", color = Color.Black)
            }
        }
    }
}

@Composable
fun DropdownSelector(
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    // Look up the “display name” (second) for the selectedId (first). If not found, show placeholder.
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second
        ?: "Select timezone…"

    Box {
        // 1) The button itself (white border + white text on black background)
        OutlinedButton(
            onClick = { expanded = true },
            border = BorderStroke(1.dp, Color.White),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White,
                backgroundColor = Color.DarkGray // a bit of contrast so white text is visible
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedLabel, color = Color.White)
        }

        // 2) The dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .background(Color.White) // force a white background so black text is readable
        ) {
            options.forEach { (id, label) ->
                DropdownMenuItem(
                    onClick = {
                        onSelect(id)
                        expanded = false
                    }
                ) {
                    Text(label, color = Color.Black)
                }
            }
        }
    }
}
