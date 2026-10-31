package cn.hbzjhs.passenger

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PassengerFullPage(
    title: String,
    onBack: () -> Unit,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    // Pages that must bring a part of themselves into view (for example the place picker's query) pass their own.
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit
) {
    PassengerModalSlot()
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
            color = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Plain back control: the page title, not the back button, carries the weight.
                    Row(
                        Modifier
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClick = onBack)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Ink, modifier = Modifier.size(20.dp))
                        Text(text = "返回", color = Ink, fontSize = TypeScale.Body, modifier = Modifier.padding(start = 4.dp))
                    }
                    Text(
                        text = title,
                        fontSize = TypeScale.Title,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp,
                        color = Ink,
                        maxLines = 2,
                        modifier = Modifier.weight(1f).padding(start = 4.dp, end = 12.dp)
                    )
                }
                HorizontalDivider(color = DividerTone)
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
                if (bottom != null) {
                    HorizontalDivider(color = DividerTone)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = bottom
                    )
                }
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
                    .heightIn(min = 48.dp)
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
