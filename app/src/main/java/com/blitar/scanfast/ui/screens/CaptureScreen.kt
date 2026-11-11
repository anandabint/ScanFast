package com.blitar.scanfast.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.blitar.scanfast.camera.CameraXController
import com.blitar.scanfast.camera.VolumeShutterHandler
import com.blitar.scanfast.model.CaptureViewModel
import com.blitar.scanfast.ui.components.CameraPreviewLayer
import com.blitar.scanfast.ui.components.TorchToggleButton
import com.blitar.scanfast.ui.components.rememberHaptics

@Composable
fun CaptureScreen(
    start: Int,
    end: Int,
    viewModel: CaptureViewModel,
    volumeShutter: VolumeShutterHandler,
    onRun: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = rememberHaptics()
    val session = remember(start, end) { viewModel.sessionFor(start, end) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val controller = remember { CameraXController(context) }
    DisposableEffect(controller) { onDispose { controller.release() } }

    var busy by remember { mutableStateOf(false) }
    val doCapture by rememberUpdatedState {
        if (hasPermission && !busy && !session.isFinished) {
            busy = true
            controller.capture(
                fileName = session.nextFileName(),
                onSuccess = { path ->
                    session.addPhoto(path)
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    busy = false
                },
                onError = {
                    busy = false
                    Toast.makeText(context, "Capture gagal: ${it.message}", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
    var showExitDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = session.photoCount > 0) { showExitDialog = true }
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Keluar dari sesi?") },
            text = { Text("Semua foto yang sudah diambil akan hilang.") },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("Batal") }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.clear()
                    onExit()
                }) { Text("Keluar") }
            },
        )
    }

    DisposableEffect(volumeShutter) {
        val listener: () -> Unit = { doCapture() }
        volumeShutter.register(listener)
        onDispose { volumeShutter.unregister(listener) }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraPreviewLayer(controller)
        } else {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Izin kamera diperlukan", color = Color.White)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Beri izin")
                }
            }
        }

        if (hasPermission) {
            TorchToggleButton(
                torchOn = controller.torchOn,
                onToggle = {
                    haptics.tick()
                    controller.toggleTorch()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(top = 24.dp, end = 16.dp),
            )
        }

        // Counter atas
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 24.dp),
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (session.isFinished) "Selesai / ${session.end}"
                    else "${session.current} / ${session.end}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Text(
                    text = "Foto: ${session.photoCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
        }

        // Kontrol bawah
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    haptics.tick()
                    session.skip()
                },
                enabled = !session.isFinished && !busy,
            ) { Text("Skip") }

            // Shutter sentuh sebagai cadangan tombol volume
            Button(
                onClick = { doCapture() },
                enabled = hasPermission && !session.isFinished,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {}

            Button(
                onClick = {
                    haptics.tick()
                    onRun()
                },
                enabled = session.photoCount >= 1,
            ) { Text("Run") }
        }
    }
}
