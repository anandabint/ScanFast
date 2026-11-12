package com.blitar.scanfast.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Tombol toggle senter. State dipegang pemanggil; tombol hanya menampilkan dan melapor klik. */
@Composable
fun TorchToggleButton(
    torchOn: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tonal (secondaryContainer) seperti tombol kamera lain; status ditunjukkan oleh ikon on/off.
    FilledTonalIconButton(
        onClick = onToggle,
        modifier = modifier
            .size(52.dp)
            .semantics { contentDescription = if (torchOn) "Torch menyala" else "Torch mati" },
    ) {
        Icon(
            imageVector = if (torchOn) FlashOnIcon else FlashOffIcon,
            contentDescription = null,
        )
    }
}

// Path ikon Material (flash_on / flash_off), digambar manual agar tidak perlu material-icons-extended.
private val FlashOnIcon: ImageVector = ImageVector.Builder(
    name = "FlashOn", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).path(fill = SolidColor(Color.Black)) {
    moveTo(7f, 2f); verticalLineTo(13f); horizontalLineToRelative(3f)
    verticalLineToRelative(9f); lineToRelative(7f, -12f); horizontalLineToRelative(-4f)
    lineToRelative(4f, -8f); close()
}.build()

private val FlashOffIcon: ImageVector = ImageVector.Builder(
    name = "FlashOff", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).path(fill = SolidColor(Color.Black)) {
    moveTo(3.27f, 3f); lineTo(2f, 4.27f); lineToRelative(5f, 5f); verticalLineTo(13f)
    horizontalLineToRelative(3f); verticalLineToRelative(9f); lineToRelative(3.58f, -6.14f)
    lineTo(17.73f, 20f); lineTo(19f, 18.73f); lineTo(3.27f, 3f); close()
    moveTo(17f, 10f); horizontalLineToRelative(-4f); lineToRelative(4f, -7f)
    horizontalLineTo(7f); verticalLineToRelative(2.18f); lineToRelative(8.46f, 8.46f)
    lineTo(17f, 10f); close()
}.build()
