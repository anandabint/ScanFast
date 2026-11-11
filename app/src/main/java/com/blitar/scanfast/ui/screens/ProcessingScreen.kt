package com.blitar.scanfast.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blitar.scanfast.model.BatchViewModel
import com.blitar.scanfast.model.CaptureViewModel
import com.blitar.scanfast.ui.components.ScreenScaffold
import com.blitar.scanfast.ui.components.rememberHaptics

@Composable
fun ProcessingScreen(
    captureViewModel: CaptureViewModel,
    batchViewModel: BatchViewModel,
    onDone: () -> Unit,
    onNoSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = remember { captureViewModel.currentSession() }
    val haptics = rememberHaptics()
    BackHandler(enabled = true) { /* back diblokir selama batch berjalan */ }

    LaunchedEffect(Unit) {
        if (session != null) batchViewModel.start(session)
        else if (batchViewModel.result == null) onNoSession()
    }
    val result = batchViewModel.result
    LaunchedEffect(result) {
        if (result != null) {
            haptics.confirm() // batch selesai
            onDone()
        }
    }

    val total = batchViewModel.total
    val done = batchViewModel.done
    ScreenScaffold(title = "Memproses", modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Memproses $done / $total", style = MaterialTheme.typography.titleLarge)
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Meluruskan, menajamkan, dan menyimpan PDF…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
