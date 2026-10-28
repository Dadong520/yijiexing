package cn.hbzjhs.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PassengerFullPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            color = Paper
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Text(text = "返回", color = Teal)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Ink,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                content()
            }
        }
    }
}

@Composable
fun PassengerCancelChoices(
    choices: List<String>,
    selected: String,
    other: String,
    onSelected: (String) -> Unit,
    onOther: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        choices.forEach { choice ->
            val isSelected = choice == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelected(choice) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Teal,
                        unselectedColor = Muted
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = choice,
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (selected == "其他") {
            OutlinedTextField(
                value = other,
                onValueChange = { if (it.length <= 200) onOther(it) },
                placeholder = { Text("请输入取消原因（2-200字）", color = Muted) },
                supportingText = {
                    Text(
                        text = "${other.length}/200",
                        color = if (other.length in 2..200) Muted else MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Teal,
                    unfocusedBorderColor = Muted,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink
                )
            )
        }
    }
}
