package com.example.pantryparty.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pantryparty.receipt.recognizeReceiptLines
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch

/**
 * The viewfinder step: frame the receipt, tap once, hand the recognized text back.
 *
 * OCR happens here rather than in the ViewModel so the ViewModel never has to know
 * about ML Kit or CameraX — it just receives [onLines].
 *
 * [busy] means a capture is being read. The caller must keep this composable mounted for
 * the whole of it: leaving the composition unbinds the camera, which aborts the in-flight
 * capture and cancels the coroutine running OCR.
 */
@Composable
fun ReceiptCameraStep(
    busy: Boolean,
    onCaptureStarted: () -> Unit,
    onLines: (List<String>) -> Unit,
    onFailure: (String) -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result -> granted = result }

    // Ask once on entry; the user only sees the system prompt if they haven't decided.
    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        CameraViewfinder(
            busy = busy,
            onCaptureStarted = onCaptureStarted,
            onLines = onLines,
            onFailure = onFailure
        )
    } else {
        CameraPermissionRationale(
            onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) }
        )
    }
}

/** Never a dead end: explain why the camera is needed and offer the prompt again. */
@Composable
private fun CameraPermissionRationale(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.PhotoCamera,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Camera access is needed to read your receipt.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The photo is processed on your phone and never leaves it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text("Grant permission") }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraViewfinder(
    busy: Boolean,
    onCaptureStarted: () -> Unit,
    onLines: (List<String>) -> Unit,
    onFailure: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        }
    }

    // Unbind when the dialog closes so the camera is released promptly rather than
    // waiting on the controller being garbage collected.
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        ReceiptFramingGuide(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Align the receipt in the frame",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Spacer(Modifier.height(12.dp))
            CaptureButton(
                enabled = !busy,
                onClick = {
                    onCaptureStarted()
                    controller.capture(context, scope, onLines, onFailure)
                }
            )
        }

        // Last child, so it covers the preview and the framing guide.
        if (busy) {
            ReadingOverlay()
        }
    }
}

/** Progress shown over the still-live viewfinder while the capture is being read. */
@Composable
private fun ReadingOverlay() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Color.White)
        Spacer(Modifier.height(16.dp))
        Text(
            "Reading your receipt…",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}

/** A dashed-free receipt-shaped cutout hint — tall and narrow, like a till roll. */
@Composable
private fun ReceiptFramingGuide(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.68f)
                .fillMaxHeight(0.62f)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
        )
    }
}

@Composable
private fun CaptureButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = "Capture receipt",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Takes one frame and runs it through OCR.
 *
 * The [ImageProxy] is closed in a `finally`: a leaked proxy holds a buffer from the
 * capture pipeline and the *next* capture then silently stalls.
 */
@OptIn(ExperimentalGetImage::class)
private fun LifecycleCameraController.capture(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onLines: (List<String>) -> Unit,
    onFailure: (String) -> Unit
) {
    takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val frame = image.image
                if (frame == null) {
                    image.close()
                    onFailure("Couldn't read that photo. Try again.")
                    return
                }
                val input = InputImage.fromMediaImage(frame, image.imageInfo.rotationDegrees)
                scope.launch {
                    try {
                        recognizeReceiptLines(input)
                            .onSuccess(onLines)
                            .onFailure { onFailure("Couldn't read the receipt text. Try again.") }
                    } finally {
                        image.close()
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onFailure("Camera error: ${exception.message ?: "capture failed"}")
            }
        }
    )
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
