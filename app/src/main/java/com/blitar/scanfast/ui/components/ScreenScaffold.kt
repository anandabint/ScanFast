package com.blitar.scanfast.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Scaffold M3 + TopAppBar seragam untuk semua layar non-kamera. [onBack] null = tanpa tombol kembali. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(BackArrowIcon, contentDescription = "Kembali")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

// Ikon panah kembali Material, digambar manual (material-icons tidak ada di dependency).
private val BackArrowIcon: ImageVector = ImageVector.Builder(
    name = "ArrowBack", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f, autoMirror = true,
).path(fill = SolidColor(Color.Black)) {
    moveTo(20f, 11f); horizontalLineTo(7.83f); lineToRelative(5.59f, -5.59f)
    lineTo(12f, 4f); lineToRelative(-8f, 8f); lineToRelative(8f, 8f)
    lineToRelative(1.41f, -1.41f); lineTo(7.83f, 13f); horizontalLineTo(20f); close()
}.build()
