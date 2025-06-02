package com.example.ldr_love_from_afar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.ldr_love_from_afar.R

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val isPreview = LocalInspectionMode.current

    // If not in preview, delay 1.5 seconds then call onFinished
    LaunchedEffect(Unit) {
        if (!isPreview) {
            delay(1500)
            onFinished()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF9F6)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.intro),
            contentDescription = "Intro Image",
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SplashScreenPreview() {
    SplashScreen(onFinished = {})
}
