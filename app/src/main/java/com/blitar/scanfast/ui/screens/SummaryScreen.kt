package com.blitar.scanfast.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.blitar.scanfast.model.BatchResult
import com.blitar.scanfast.model.BatchViewModel
import com.blitar.scanfast.ui.components.ScreenScaffold
import com.blitar.scanfast.ui.components.rememberHaptics

@Composable
fun SummaryScreen(
    batchViewModel: BatchViewModel,
    onNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val result = batchViewModel.result
    BackHandler(enabled = true) { onNewSession() }

    ScreenScaffold(title = "Selesai", modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (result != null) {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Berhasil", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${result.successCount}",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (result.deletedCount > 0) {
                            Text(
                                "Dihapus manual: ${result.deletedCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Folder", style = MaterialTheme.typography.labelLarge)
                        Text(result.relativePath, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (result.failedCount > 0) {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Gagal: ${result.failedCount}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Nomor ${result.failedNumbers.joinToString(", ")}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        haptics.tick()
                        openFolder(context, result)
                    },
                    enabled = result.successCount > 0,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("Buka folder", style = MaterialTheme.typography.titleMedium) }
            }
            FilledTonalButton(
                onClick = {
                    haptics.tick()
                    onNewSession()
                },
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Mulai sesi baru", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

private fun openFolder(context: android.content.Context, result: BatchResult) {
    val folderUri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:" + result.relativePath.trimEnd('/')
    )
    val folderIntent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(folderUri, DocumentsContract.Document.MIME_TYPE_DIR)
    try {
        context.startActivity(folderIntent)
        return
    } catch (_: ActivityNotFoundException) {
    }
    // Fallback: buka PDF pertama lewat MediaStore Uri.
    val file = result.firstFileUri
    if (file != null) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(file, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    Toast.makeText(context, "Tidak ada aplikasi untuk membuka folder", Toast.LENGTH_LONG).show()
}
