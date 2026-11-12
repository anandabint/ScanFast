package com.blitar.scanfast.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.blitar.scanfast.model.BatchViewModel
import com.blitar.scanfast.model.CaptureViewModel
import com.blitar.scanfast.model.CapturedPhoto
import com.blitar.scanfast.ui.components.ScreenScaffold
import com.blitar.scanfast.ui.components.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReviewScreen(
    captureViewModel: CaptureViewModel,
    batchViewModel: BatchViewModel,
    onOpenEditor: (number: Int) -> Unit,
    onAddPhoto: (number: Int) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = captureViewModel.currentSession()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val applying = batchViewModel.reordering

    // --- State drag-reorder ---
    var snapshot by remember { mutableStateOf<List<Long>?>(null) } // urutan awal sebelum diubah
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var fingerPos by remember { mutableStateOf(Offset.Zero) } // posisi jari di koordinat grid
    var grabOffset by remember { mutableStateOf(Offset.Zero) } // jari relatif ke pojok item yang digeser

    if (session == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val pending = snapshot != null && snapshot != session.photos.map { it.id }

    fun applyOrderThen(after: () -> Unit) {
        scope.launch {
            batchViewModel.applyOrder(session)
            snapshot = null
            haptics.confirm()
            after()
        }
    }

    // Back = Selesai (Processing sudah dibuang dari back stack). Urutan tertunda diterapkan dulu.
    fun finish() {
        if (applying) return
        if (pending) applyOrderThen(onDone) else onDone()
    }
    BackHandler(enabled = true) { finish() }

    /** Tukar posisi item yang digeser dengan item di bawah titik tengahnya. */
    fun trySwap() {
        val id = draggedId ?: return
        val infos = gridState.layoutInfo.visibleItemsInfo
        val dragged = infos.firstOrNull { it.key == id } ?: return
        val center = fingerPos - grabOffset + Offset(dragged.size.width / 2f, dragged.size.height / 2f)
        val target = infos.firstOrNull {
            it.key != id &&
                center.x >= it.offset.x && center.x < it.offset.x + it.size.width &&
                center.y >= it.offset.y && center.y < it.offset.y + it.size.height
        } ?: return
        val from = session.photos.indexOfFirst { it.id == id }
        val to = session.photos.indexOfFirst { it.id == target.key }
        if (from >= 0 && to >= 0) {
            session.movePhoto(from, to)
            haptics.tick()
        }
    }

    // Auto-scroll saat item diseret dekat tepi atas/bawah grid.
    LaunchedEffect(draggedId) {
        while (draggedId != null) {
            val h = gridState.layoutInfo.viewportSize.height
            val dy = when {
                fingerPos.y < EDGE_PX -> -SCROLL_STEP
                fingerPos.y > h - EDGE_PX -> SCROLL_STEP
                else -> 0f
            }
            if (dy != 0f) {
                gridState.scrollBy(dy)
                trySwap()
            }
            delay(16)
        }
    }

    val failed = batchViewModel.result?.failedNumbers.orEmpty()

    // --- Tambah foto: pilih nomor (chip kosong / manual), sisip kalau bentrok ---
    var showAdd by remember { mutableStateOf(false) }
    var insertConfirm by remember { mutableStateOf<Int?>(null) }
    var inserting by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "Review",
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (!pending && !applying && !inserting) {
                // FAB bulat berikon "+"; warna primary sama dengan tombol Selesai di bawahnya.
                FloatingActionButton(
                    onClick = {
                        haptics.tick()
                        showAdd = true
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // Di atas tombol Selesai (56dp + padding 24dp); end 8dp + margin FAB 16dp = sejajar
                    // dengan tepi kanan Selesai (padding 24dp).
                    modifier = Modifier
                        .padding(bottom = 88.dp, end = 8.dp)
                        .semantics { contentDescription = "Tambah foto" },
                ) { Icon(PlusIcon, contentDescription = null) }
            }
        },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                "${session.photos.size} foto. Ketuk untuk mengatur crop, tahan lalu geser untuk mengubah urutan.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(session) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                if (batchViewModel.reordering) return@detectDragGesturesAfterLongPress
                                val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    offset.x >= it.offset.x && offset.x < it.offset.x + it.size.width &&
                                        offset.y >= it.offset.y && offset.y < it.offset.y + it.size.height
                                }
                                if (hit != null) {
                                    if (snapshot == null) snapshot = session.photos.map { it.id }
                                    draggedId = hit.key as Long
                                    grabOffset = offset - Offset(hit.offset.x.toFloat(), hit.offset.y.toFloat())
                                    fingerPos = offset
                                    haptics.gestureStart()
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (draggedId != null) {
                                    change.consume()
                                    fingerPos += dragAmount
                                    trySwap()
                                }
                            },
                            onDragEnd = {
                                if (draggedId != null) haptics.gestureEnd()
                                draggedId = null
                                // Dikembalikan ke posisi semula → tidak ada perubahan tertunda.
                                if (snapshot == session.photos.map { it.id }) snapshot = null
                            },
                            onDragCancel = {
                                draggedId = null
                                if (snapshot == session.photos.map { it.id }) snapshot = null
                            },
                        )
                    },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(session.photos, key = { it.id }) { photo ->
                    val isDragging = draggedId == photo.id
                    val index = session.photos.indexOf(photo)
                    ThumbnailItem(
                        photo = photo,
                        // Saat urutan tertunda, label menampilkan nomor BARU (posisi + nomor awal).
                        label = if (pending) session.start + index else photo.number,
                        failed = photo.number in failed,
                        modifier = if (isDragging) {
                            Modifier
                                .zIndex(1f)
                                .graphicsLayer {
                                    val info = gridState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { it.key == photo.id }
                                    if (info != null) {
                                        translationX = fingerPos.x - grabOffset.x - info.offset.x
                                        translationY = fingerPos.y - grabOffset.y - info.offset.y
                                    }
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                    alpha = 0.92f
                                }
                        } else {
                            Modifier.animateItem()
                        },
                        onClick = {
                            if (pending) {
                                Toast.makeText(
                                    context, "Terapkan atau urungkan urutan dulu", Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                haptics.tick()
                                onOpenEditor(photo.number)
                            }
                        },
                    )
                }
            }
            if (pending) {
                Card(
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Urutan diubah", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Semua foto akan dinomori ulang ${session.start}–" +
                                "${session.start + session.photos.size - 1} sesuai posisi, dan PDF ditulis ulang.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    haptics.tick()
                                    snapshot?.let { session.restoreOrder(it) }
                                    snapshot = null
                                },
                                enabled = !applying,
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier.weight(1f),
                            ) { Text("Urungkan") }
                            Button(
                                onClick = {
                                    haptics.tick()
                                    applyOrderThen {}
                                },
                                enabled = !applying,
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier.weight(1f),
                            ) { Text("Terapkan") }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    haptics.confirm()
                    finish()
                },
                enabled = !applying,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().padding(24.dp).height(56.dp),
            ) { Text("Selesai", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (showAdd) {
        AddPhotoDialog(
            session = session,
            onDismiss = { showAdd = false },
            onPick = { n ->
                showAdd = false
                if (session.photoWithNumber(n) == null) onAddPhoto(n) else insertConfirm = n
            },
        )
    }
    insertConfirm?.let { n ->
        AlertDialog(
            onDismissRequest = { insertConfirm = null },
            title = { Text("Nomor $n sudah dipakai") },
            text = { Text("Nomor $n sudah dipakai. Sisipkan di sini? Semua nomor dari $n ke atas akan digeser naik 1.") },
            dismissButton = { TextButton(onClick = { insertConfirm = null }) { Text("Batal") } },
            confirmButton = {
                TextButton(onClick = {
                    insertConfirm = null
                    inserting = true
                    scope.launch {
                        try {
                            batchViewModel.insertSlot(session, n)
                            haptics.confirm()
                            onAddPhoto(n)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: java.io.IOException) {
                            haptics.reject()
                            Toast.makeText(context, "Gagal menggeser nomor: ${e.message}", Toast.LENGTH_LONG).show()
                        } catch (e: RuntimeException) {
                            haptics.reject()
                            Toast.makeText(context, "Gagal menggeser nomor: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            inserting = false
                        }
                    }
                }) { Text("Sisipkan") }
            },
        )
    }
    if (inserting) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Menggeser nomor…") },
            text = { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) },
        )
    }

    if (applying) {
        AlertDialog(
            onDismissRequest = { /* tidak bisa ditutup selama menulis ulang */ },
            confirmButton = {},
            title = { Text("Menyusun ulang…") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = {
                            if (batchViewModel.reorderTotal == 0) 0f
                            else batchViewModel.reorderDone.toFloat() / batchViewModel.reorderTotal
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${batchViewModel.reorderDone} / ${batchViewModel.reorderTotal}")
                }
            },
        )
    }
}

private const val EDGE_PX = 120f
private const val SCROLL_STEP = 24f

@Composable
private fun ThumbnailItem(
    photo: CapturedPhoto,
    label: Int,
    failed: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val version = photo.thumbVersion // berubah saat thumbnail ditulis ulang
    val thumb by produceState<ImageBitmap?>(null, photo, version) {
        val path = photo.thumbPath
        value = if (path == null) null else withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(path)?.asImageBitmap()
        }
    }
    val quad = photo.quad
    val needsCheck = quad != null && !quad.autoDetected && !quad.manuallyEdited
    val colors = MaterialTheme.colorScheme
    val borderColor = when {
        failed -> colors.error
        needsCheck -> colors.tertiary
        else -> Color.Transparent
    }
    val shape = MaterialTheme.shapes.large

    Box(
        modifier
            .aspectRatio(0.75f)
            .clip(shape)
            .background(colors.surfaceContainerHighest)
            .border(2.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = thumb
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "Halaman $label",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                if (failed) "Gagal" else "…",
                color = if (failed) colors.error else colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "$label",
            color = colors.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(colors.surfaceContainerHighest.copy(alpha = 0.85f), MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (failed) {
            Badge("Gagal, ketuk untuk coba manual", colors.errorContainer, colors.onErrorContainer)
        } else if (needsCheck) {
            Badge("Cek manual disarankan", colors.tertiaryContainer, colors.onTertiaryContainer)
        }
    }
}

@Composable
private fun BoxScope.Badge(text: String, bg: Color, fg: Color) {
    Text(
        text,
        color = fg,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}


/** Pilih nomor halaman baru: chip nomor kosong (langsung pakai) atau input manual. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AddPhotoDialog(
    session: com.blitar.scanfast.model.CaptureSession,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val empty = session.emptyNumbers()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah foto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (empty.isNotEmpty()) {
                    Text("Nomor yang kosong:", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        empty.forEach { n ->
                            androidx.compose.material3.AssistChip(
                                onClick = { onPick(n) },
                                label = { Text("$n") },
                            )
                        }
                    }
                } else {
                    Text(
                        "Tidak ada nomor kosong di ${session.start}–${session.end}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        text = v.filter { it.isDigit() }.take(5)
                        error = null
                    },
                    label = { Text("atau masukkan nomor lain") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        confirmButton = {
            TextButton(onClick = {
                val n = text.toIntOrNull()
                when {
                    n == null -> error = "Masukkan nomor"
                    n < session.start -> error = "Nomor minimal ${session.start}"
                    else -> onPick(n)
                }
            }) { Text("Lanjut") }
        },
    )
}


// Ikon "+" Material (add), digambar manual karena material-icons tidak ada di dependency.
private val PlusIcon: ImageVector = ImageVector.Builder(
    name = "Add", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).path(fill = SolidColor(androidx.compose.ui.graphics.Color.Black)) {
    moveTo(19f, 13f); horizontalLineToRelative(-6f); verticalLineToRelative(6f)
    horizontalLineToRelative(-2f); verticalLineToRelative(-6f); horizontalLineTo(5f)
    verticalLineToRelative(-2f); horizontalLineToRelative(6f); verticalLineTo(5f)
    horizontalLineToRelative(2f); verticalLineToRelative(6f); horizontalLineToRelative(6f); close()
}.build()
