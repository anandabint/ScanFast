package com.blitar.scanfast.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.blitar.scanfast.ui.components.ScreenScaffold
import com.blitar.scanfast.ui.components.rememberHaptics

@Composable
fun RangeInputScreen(
    onStart: (start: Int, end: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val focusManager = LocalFocusManager.current
    var startText by rememberSaveable { mutableStateOf("1") }
    var endText by rememberSaveable { mutableStateOf("") }
    var startTouched by rememberSaveable { mutableStateOf(false) }
    var endTouched by rememberSaveable { mutableStateOf(false) }
    var startHadFocus by remember { mutableStateOf(false) }
    var endHadFocus by remember { mutableStateOf(false) }

    val start = startText.toIntOrNull()
    val end = endText.toIntOrNull()
    // Error inline: field kosong baru ditandai setelah disentuh; rentang/angka tidak valid langsung.
    val startError = when {
        startText.isEmpty() -> if (startTouched) "Nomor awal wajib diisi" else null
        start == null -> "Angka terlalu besar"
        else -> null
    }
    val endError = when {
        endText.isEmpty() -> if (endTouched) "Nomor akhir wajib diisi" else null
        end == null -> "Angka terlalu besar"
        start != null && end < start -> "Nomor akhir harus ≥ nomor awal"
        else -> null
    }
    val valid = start != null && end != null && end >= start

    ScreenScaffold(title = "ScanFast", modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Rentang nomor", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it.filter(Char::isDigit) },
                        label = { Text("Nomor awal") },
                        supportingText = { Text(startError ?: "Nomor halaman pertama yang difoto") },
                        isError = startError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number, imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                if (startHadFocus && !it.isFocused) startTouched = true
                                startHadFocus = it.isFocused
                            },
                    )
                    OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it.filter(Char::isDigit) },
                        label = { Text("Nomor akhir") },
                        supportingText = { Text(endError ?: "Nomor halaman terakhir yang difoto") },
                        isError = endError != null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged {
                                if (endHadFocus && !it.isFocused) endTouched = true
                                endHadFocus = it.isFocused
                            },
                    )
                    if (valid) {
                        Text(
                            "Total ${end!! - start!! + 1} halaman",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f, fill = false))
            Button(
                onClick = {
                    haptics.tick()
                    onStart(start!!, end!!)
                },
                enabled = valid,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Mulai Foto", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
