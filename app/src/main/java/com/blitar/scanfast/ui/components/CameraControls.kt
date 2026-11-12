package com.blitar.scanfast.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/*
 * Kontrol layar kamera (CaptureScreen, Foto ulang, Tambah foto) yang memakai palet MaterialTheme.colorScheme
 * dan bentuk tema yang sama dengan layar lain (shape extraLarge = 28dp), bukan warna hitam/putih custom.
 * Semua opaque supaya kontras tetap jelas di atas overlay gelap guide-frame.
 */

/** Lebar sama untuk tombol kiri dan kanan agar shutter tepat di tengah. */
private val SideButtonWidth = 96.dp
private val SideButtonHeight = 52.dp
private val ShutterSize = 72.dp

/** Kartu pil di atas kamera: judul (mis. "301 / 305") dan baris keterangan opsional. */
@Composable
fun CameraInfoPill(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                style = if (subtitle != null) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.titleMedium,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Tombol sekunder di sisi bawah kamera (Skip, Batal): tonal, bentuk 28dp. */
@Composable
fun CameraSideButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.width(SideButtonWidth).height(SideButtonHeight),
    ) { Text(text, maxLines = 1) }
}

/** Tombol utama di sisi bawah kamera (Run): terisi primary, ukuran sama dengan [CameraSideButton]. */
@Composable
fun CameraPrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.width(SideButtonWidth).height(SideButtonHeight),
    ) { Text(text, maxLines = 1) }
}

/** Shutter bulat 72dp: primary dengan cincin tipis onPrimary supaya terbaca sebagai tombol capture. */
@Composable
fun ShutterButton(onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(4.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 0.9f else 0.3f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(),
        modifier = modifier.size(ShutterSize).semantics { contentDescription = "Ambil foto" },
    ) {}
}
