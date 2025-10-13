package com.k2fsa.sherpa.onnx.simulate.streaming.asr.widgets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    menuItems: List<String>,
    isRecognizing: Boolean,
    modifier: Modifier = Modifier,
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = isDropdownExpanded,
        onExpandedChange = {
            if (!isRecognizing && menuItems.isNotEmpty()) {
                isDropdownExpanded = it
            }
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(), // This is important
            label = label,
            trailingIcon = {
                if (isRecognizing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = isDropdownExpanded
                    )
                }
            }
        )

        if (menuItems.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = isDropdownExpanded,
                onDismissRequest = { isDropdownExpanded = false },
            ) {
                menuItems.forEach {
                    DropdownMenuItem(
                        text = { Text(it) },
                        onClick = {
                            onValueChange(it)
                            isDropdownExpanded = false
                        })
                }
            }
        }
    }
}
