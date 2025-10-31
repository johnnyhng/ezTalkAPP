package tw.com.johnnyhng.eztalk.asr.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun EditableDropdown(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    menuItems: List<String>,
    isRecognizing: Boolean,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = false
) {
    var isDropdownExpanded by remember { mutableStateOf(startExpanded) }
    var rowWidth by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { rowWidth = it.width },
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                label = label,
                readOnly = isRecognizing
            )

            IconButton(
                onClick = { isDropdownExpanded = true },
                enabled = !isRecognizing && menuItems.isNotEmpty()
            ) {
                if (isRecognizing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Open Menu"
                    )
                }
            }
        }

        DropdownMenu(
            expanded = isDropdownExpanded,
            onDismissRequest = { isDropdownExpanded = false },
            modifier = Modifier.width(with(density) { rowWidth.toDp() })
        ) {
            menuItems.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text("${index + 1}: $item") },
                    onClick = {
                        onValueChange(item)
                        isDropdownExpanded = false
                    })
            }
        }
    }
}
