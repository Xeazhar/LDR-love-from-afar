package com.example.ldr_love_from_afar

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

@Composable
fun CameraScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isInPreview = LocalInspectionMode.current

    // Holds the ImageCapture use‐case instance
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    // Single‐thread executor for camera tasks
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    // Check for camera permission
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Which lens to bind (front/back)
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }

    // Show a “Processing…” overlay while the picture is being captured
    var isCapturing by remember { mutableStateOf(false) }

    // Permission launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // As soon as this Composable enters composition, request permission if not granted
    LaunchedEffect(Unit) {
        if (!hasPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // Clean up executor when Composable leaves
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top Row: Title + Settings Icon ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Take a Photo!", color = Color.White)

                Image(
                    painter = painterResource(id = R.drawable.settings),
                    contentDescription = "Settings",
                    modifier = Modifier
                        .padding(16.dp)
                        .size(24.dp)
                        .clickable {
                            navController.navigate("settings")
                        }
                )
            }

            // ── Camera Preview Box ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !hasPermission -> {
                        Text("Grant camera permission", color = Color.White)
                    }
                    isInPreview -> {
                        // In @Preview mode, just placeholder text
                        Text("Camera Preview", color = Color.White)
                    }
                    else -> {
                        // key(lensFacing) ensures that when lensFacing changes, the old Preview is torn down and new one is bound immediately
                        key(lensFacing) {
                            AndroidView(
                                modifier = Modifier.matchParentSize(),
                                factory = { ctx ->
                                    val previewView = PreviewView(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                    cameraProviderFuture.addListener({
                                        val cameraProvider = cameraProviderFuture.get()
                                        val previewUseCase = CameraPreview.Builder()
                                            .build()
                                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                                        // Build a new ImageCapture instance each time lensFacing changes
                                        imageCapture = ImageCapture.Builder()
                                            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                                            .setTargetRotation(previewView.display.rotation)
                                            .build()

                                        try {
                                            cameraProvider.unbindAll()
                                            cameraProvider.bindToLifecycle(
                                                lifecycleOwner,
                                                CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                                                previewUseCase,
                                                imageCapture
                                            )
                                        } catch (e: Exception) {
                                            Log.e("CameraScreen", "Camera bind failed", e)
                                        }
                                    }, ContextCompat.getMainExecutor(ctx))

                                    previewView
                                }
                            )
                        }
                    }
                }

                // Overlay while capturing
                if (isCapturing) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Processing...", color = Color.White)
                    }
                }
            }

            // ── Bottom Row: Shutter Button + Flip Button ────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Shutter Button ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clickable(enabled = imageCapture != null && !isCapturing) {
                            imageCapture?.let { captureUseCase ->
                                isCapturing = true

                                // Create a temporary file in cacheDir
                                val file = File(
                                    context.externalCacheDir ?: context.cacheDir,
                                    "IMG_${System.currentTimeMillis()}.jpg"
                                )
                                val outputOptions =
                                    ImageCapture.OutputFileOptions.Builder(file).build()

                                captureUseCase.takePicture(
                                    outputOptions,
                                    cameraExecutor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            // Wrap in a FileProvider URI
                                            val photoUri = FileProvider.getUriForFile(
                                                context,
                                                "com.example.ldr_love_from_afar.fileprovider",
                                                file
                                            )

                                            // Encode, then navigate on MAIN thread
                                            val encodedUri = URLEncoder.encode(
                                                photoUri.toString(),
                                                StandardCharsets.UTF_8.toString()
                                            )
                                            isCapturing = false

                                            // Post navigation to main thread
                                            Handler(Looper.getMainLooper()).post {
                                                navController.navigate("review/$encodedUri")
                                            }
                                        }

                                        override fun onError(exc: ImageCaptureException) {
                                            isCapturing = false
                                            Toast.makeText(
                                                context,
                                                "Capture failed: ${exc.message}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    })
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.camera),
                        contentDescription = "Take Photo",
                        modifier = Modifier.size(56.dp)
                    )
                }

                Spacer(modifier = Modifier.width(32.dp))

                // ── Flip Camera Button ────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                                CameraSelector.LENS_FACING_FRONT
                            else
                                CameraSelector.LENS_FACING_BACK
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.swap),
                        contentDescription = "Swap Camera",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
