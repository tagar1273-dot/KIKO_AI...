package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.HudGlassCard
import com.example.ui.components.StatusIndicatorPill
import com.example.ui.theme.KikoAmber
import com.example.ui.theme.KikoCrimson
import com.example.ui.theme.KikoCyan
import com.example.ui.theme.KikoEmerald
import com.example.ui.theme.KikoOutline
import com.example.ui.theme.KikoSurfaceElevated
import com.example.ui.theme.KikoTextMuted
import com.example.ui.theme.KikoTextPrimary
import com.example.ui.theme.KikoTextSecondary
import com.example.viewmodel.KikoMainViewModel
import com.example.viewmodel.UiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisionScreen(
    viewModel: KikoMainViewModel,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val visionState by viewModel.visionState.collectAsStateWithLifecycle()
    val torchEnabled by viewModel.torchEnabled.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var visionQuestion by rememberSaveable {
        mutableStateOf("Identify objects, read any text or code, and give a tactical summary.")
    }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            decodeSampledBitmapFromUri(context, uri)?.let { bmp ->
                previewBitmap = bmp
                viewModel.analyzeBitmap(bmp, visionQuestion)
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = resolveDisplayName(context, uri)
            val mime = context.contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("image/")) {
                decodeSampledBitmapFromUri(context, uri)?.let { bmp ->
                    previewBitmap = bmp
                    viewModel.analyzeBitmap(bmp, "Analyze document image $fileName: $visionQuestion")
                }
            } else {
                val text = readDocumentTextSafely(context, uri)
                if (text.isNotBlank()) {
                    viewModel.analyzeDocument(fileName, text)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "KIKO VISION HUD",
                        style = MaterialTheme.typography.titleLarge,
                        color = KikoCyan,
                    )
                    Text(
                        text = "Real-Time CameraX • Object & Document Intelligence",
                        style = MaterialTheme.typography.labelSmall,
                        color = KikoTextSecondary,
                    )
                }
                IconButton(onClick = { viewModel.toggleTorch() }) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                        contentDescription = "Toggle camera torch",
                        tint = if (torchEnabled) KikoAmber else KikoCyan,
                    )
                }
            }
        }

        // CameraX Live Preview Card
        item {
            HudGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 800.dp),
                borderColor = KikoCyan.copy(alpha = 0.5f),
            ) {
                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(MaterialTheme.shapes.small)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                    )
                                }
                                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                                providerFuture.addListener({
                                    try {
                                        val cameraProvider = providerFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageCapture,
                                        )
                                        cameraError = null
                                    } catch (e: Exception) {
                                        Log.w("VisionScreen", "CameraX bind failed", e)
                                        cameraError = "Back camera unavailable on this host. Use Photo Picker or Document Scan below."
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize(),
                        )

                        // Holographic HUD targeting reticle overlay
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val pad = 20.dp.toPx()
                            val corner = 24.dp.toPx()
                            val stroke = 2.5.dp.toPx()
                            // Top-left
                            drawLine(KikoCyan, Offset(pad, pad), Offset(pad + corner, pad), stroke)
                            drawLine(KikoCyan, Offset(pad, pad), Offset(pad, pad + corner), stroke)
                            // Top-right
                            drawLine(KikoCyan, Offset(size.width - pad, pad), Offset(size.width - pad - corner, pad), stroke)
                            drawLine(KikoCyan, Offset(size.width - pad, pad), Offset(size.width - pad, pad + corner), stroke)
                            // Bottom-left
                            drawLine(KikoCyan, Offset(pad, size.height - pad), Offset(pad + corner, size.height - pad), stroke)
                            drawLine(KikoCyan, Offset(pad, size.height - pad), Offset(pad, size.height - pad - corner), stroke)
                            // Bottom-right
                            drawLine(KikoCyan, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - corner, size.height - pad), stroke)
                            drawLine(KikoCyan, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - corner), stroke)
                        }
                    }

                    if (cameraError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = cameraError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = KikoAmber,
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            imageCapture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bmp = imageProxyToBitmap(image)
                                        image.close()
                                        if (bmp != null) {
                                            previewBitmap = bmp
                                            viewModel.analyzeBitmap(bmp, visionQuestion)
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        cameraError = exception.localizedMessage ?: "Capture failed."
                                    }
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KikoCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("vision_capture_button"),
                    ) {
                        Icon(Icons.Filled.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Capture & Analyze Live Frame")
                    }
                } else {
                    Text(
                        text = "Camera access enables live object and document scanning. Grant camera permission or use zero-permission Photo Picker / Document Scan below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KikoTextSecondary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { cameraPermLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = KikoCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("grant_camera_button"),
                    ) {
                        Icon(Icons.Filled.CameraAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enable Camera Vision")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        border = BorderStroke(1.dp, KikoCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("vision_photo_picker_button"),
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null, tint = KikoCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Photo Picker", color = KikoCyan)
                    }

                    OutlinedButton(
                        onClick = {
                            documentPickerLauncher.launch(
                                arrayOf("text/*", "application/json", "image/*")
                            )
                        },
                        border = BorderStroke(1.dp, KikoCyan.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("vision_doc_picker_button"),
                    ) {
                        Icon(Icons.Filled.Description, null, tint = KikoCyan, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Document Scan", color = KikoCyan)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = visionQuestion,
                    onValueChange = { visionQuestion = it },
                    label = { Text("Vision Analysis Directive", color = KikoCyan) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KikoCyan,
                        unfocusedBorderColor = KikoOutline,
                        focusedContainerColor = KikoSurfaceElevated,
                        focusedTextColor = KikoTextPrimary,
                        unfocusedTextColor = KikoTextPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (previewBitmap != null) {
            item {
                HudGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp)) {
                    Text("CAPTURED INPUT PREVIEW", style = MaterialTheme.typography.labelMedium, color = KikoTextMuted)
                    Spacer(Modifier.height(8.dp))
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Captured visual input",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
            }
        }

        // Vision Analysis Results
        when (val state = visionState) {
            null -> {
                item {
                    HudGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp)) {
                        Text(
                            text = "No visual scan executed yet. Capture a live frame or pick an image/document to begin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KikoTextSecondary,
                        )
                    }
                }
            }
            is UiState.Loading -> {
                item {
                    HudGlassCard(modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = KikoCyan, strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("KIKO Vision Team analyzing visual payload…", color = KikoCyan)
                        }
                    }
                }
            }
            is UiState.Error -> {
                item {
                    HudGlassCard(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp),
                        borderColor = KikoCrimson,
                    ) {
                        Text("Vision Analysis Error", style = MaterialTheme.typography.titleMedium, color = KikoCrimson)
                        Spacer(Modifier.height(4.dp))
                        Text(state.message, color = KikoTextPrimary)
                    }
                }
            }
            is UiState.Success -> {
                val res = state.data
                item {
                    HudGlassCard(
                        modifier = Modifier.fillMaxWidth().widthIn(max = 800.dp),
                        borderColor = KikoEmerald.copy(alpha = 0.6f),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("VISION INTELLIGENCE REPORT", style = MaterialTheme.typography.labelLarge, color = KikoEmerald)
                            Row {
                                IconButton(onClick = { viewModel.voiceEngine.speak(res.summary) }) {
                                    Icon(Icons.AutoMirrored.Filled.VolumeUp, "Speak summary", tint = KikoCyan)
                                }
                                IconButton(onClick = {
                                    viewModel.addMemory(
                                        title = "Vision Finding",
                                        content = res.summary.take(500),
                                        category = "FACT",
                                        source = "AI_APPROVED",
                                    )
                                }) {
                                    Icon(Icons.Filled.BookmarkAdd, "Save to memory", tint = KikoAmber)
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(res.summary, style = MaterialTheme.typography.bodyLarge, color = KikoTextPrimary)

                        if (res.detectedObjects.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text("DETECTED ENTITIES", style = MaterialTheme.typography.labelSmall, color = KikoCyan)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                res.detectedObjects.forEach { obj ->
                                    StatusIndicatorPill(label = obj, color = KikoCyan)
                                }
                            }
                        }

                        if (res.extractedText.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text("EXTRACTED TEXT / OCR", style = MaterialTheme.typography.labelSmall, color = KikoCyan)
                            Spacer(Modifier.height(4.dp))
                            Text(res.extractedText, style = MaterialTheme.typography.labelMedium, color = KikoTextSecondary)
                        }

                        if (res.recommendedAction.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text("RECOMMENDED ACTION", style = MaterialTheme.typography.labelSmall, color = KikoAmber)
                            Spacer(Modifier.height(4.dp))
                            Text(res.recommendedAction, style = MaterialTheme.typography.bodyMedium, color = KikoTextPrimary)
                        }
                    }
                }
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    return try {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rotation = image.imageInfo.rotationDegrees
        if (rotation == 0) raw
        else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }
    } catch (_: Exception) {
        null
    }
}

private fun decodeSampledBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeStream(stream, null, options)
        }
    } catch (_: Exception) {
        null
    }
}

private fun readDocumentTextSafely(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            reader.readText().take(12000)
        }.orEmpty()
    } catch (_: Exception) {
        ""
    }
}

private fun resolveDisplayName(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) cursor.getString(idx) else "Document"
        } ?: "Document"
    } catch (_: Exception) {
        "Document"
    }
}
