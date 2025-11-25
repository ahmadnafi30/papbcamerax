package com.example.camerax.ui.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }

    var isCameraReady by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }

    // State untuk Thumbnail foto terakhir
    var lastCapturedUri by remember { mutableStateOf<Uri?>(null) }
    var lastCapturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var cameraPermissionGranted by remember { mutableStateOf(false) }
    var mediaPermissionGranted by remember { mutableStateOf(false) }

    var currentCameraSelector by remember { mutableStateOf(CameraSelector.DEFAULT_BACK_CAMERA) }

    val launcherCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraPermissionGranted = granted }

    val launcherMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> mediaPermissionGranted = granted }

    LaunchedEffect(Unit) {
        launcherCamera.launch(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcherMedia.launch(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            launcherMedia.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Efek untuk memuat Bitmap saat URI berubah
    LaunchedEffect(lastCapturedUri) {
        lastCapturedUri?.let { uri ->
            // Load bitmap di background thread agar UI tidak lag
            withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    lastCapturedBitmap = bitmap
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (cameraPermissionGranted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewView = this
                    }
                }
            )

            LaunchedEffect(previewView, currentCameraSelector) {
                val pv = previewView ?: return@LaunchedEffect
                val cameraProvider = getCameraProvider(context)
                val rotation = pv.display?.rotation ?: Surface.ROTATION_0

                val preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.setSurfaceProvider(pv.surfaceProvider) }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(AspectRatio.RATIO_16_9)
                    .setFlashMode(ImageCapture.FLASH_MODE_AUTO)
                    .setTargetRotation(rotation)
                    .setJpegQuality(95)
                    .build()

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    currentCameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )

                isCameraReady = true
            }

            // --- UI Tombol ---

            // 1. Switch Camera (Kiri Atas)
            IconButton(
                onClick = {
                    currentCameraSelector = if (currentCameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else CameraSelector.DEFAULT_BACK_CAMERA
                },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Filled.Cameraswitch, contentDescription = "Switch Camera")
            }

            // 2. Torch/Flash (Kanan Atas)
            IconButton(
                onClick = {
                    scope.launch {
                        if (imageCapture != null) {
                            val cameraProvider = getCameraProvider(context)
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                currentCameraSelector,
                                imageCapture!!
                            )
                            if (camera.cameraInfo.hasFlashUnit()) {
                                camera.cameraControl.enableTorch(!isTorchOn)
                                isTorchOn = !isTorchOn
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(
                    imageVector = if (isTorchOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Torch Toggle"
                )
            }

            // 3. Take Photo (Tengah Bawah)
            IconButton(
                onClick = {
                    if (isCameraReady) {
                        imageCapture?.let { ic ->
                            takePhoto(context, ic) { uri ->
                                // Update state URI agar thumbnail muncul
                                lastCapturedUri = uri
                                Toast.makeText(context, "Saved: $uri", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
            ) {
                Icon(Icons.Filled.AddCircle, contentDescription = "Take Photo", modifier = Modifier.size(84.dp))
            }

            // 4. Video Record (Kanan Bawah)
            IconButton(
                onClick = {
                    if (isCameraReady && videoCapture != null) {
                        if (recording != null) {
                            recording?.stop()
                            recording = null
                        } else {
                            val outputFile = File(context.filesDir, "video_${System.currentTimeMillis()}.mp4")
                            val outputOptions = FileOutputOptions.Builder(outputFile).build()

                            // Cek permission audio sederhana
                            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)

                            val pendingRecording = videoCapture!!.output
                                .prepareRecording(context, outputOptions)

                            if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                pendingRecording.withAudioEnabled()
                            }

                            recording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
                                if (event is VideoRecordEvent.Finalize) {
                                    Toast.makeText(context, "Video saved: ${outputFile.path}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
            ) {
                Icon(
                    imageVector = if (recording != null) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    contentDescription = "Start/Stop Video",
                    tint = if (recording != null) Color.Red else Color.White
                )
            }

            // 5. THUMBNAIL PHOTO (Kiri Bawah) - Baru ditambahkan
            if (lastCapturedBitmap != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                        .size(60.dp) // Ukuran thumbnail
                        .border(2.dp, Color.White, CircleShape) // Border putih
                        .clip(CircleShape)
                ) {
                    Image(
                        bitmap = lastCapturedBitmap!!.asImageBitmap(),
                        contentDescription = "Last Captured Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

        } else {
            Text("Camera permission required", modifier = Modifier.align(Alignment.Center))
        }
    }
}

suspend fun getCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { cont.resume(future.get()) },
            ContextCompat.getMainExecutor(context)
        )
    }

fun takePhoto(ctx: Context, ic: ImageCapture, onSaved: (Uri) -> Unit) {
    val opts = outputOptions(ctx)
    ic.takePicture(
        opts,
        ContextCompat.getMainExecutor(ctx),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                // Gunakan result.savedUri jika ada, atau buat URI dari outputOptions jika null (kasus tertentu)
                val savedUri = result.savedUri ?: Uri.fromFile(File(opts.toString()))
                // Karena kita pakai MediaStore, result.savedUri biasanya tidak null.
                result.savedUri?.let(onSaved)
            }
            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(ctx, "Error: ${exc.message}", Toast.LENGTH_LONG).show()
            }
        }
    )
}

fun outputOptions(ctx: Context): ImageCapture.OutputFileOptions {
    val name = "IMG_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/KameraKu")
        }
    }
    return ImageCapture.OutputFileOptions.Builder(
        ctx.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ).build()
}