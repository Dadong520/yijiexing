package cn.hbzjhs.passenger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun MenuRow(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .clickable(onClick = onClick)
      .heightIn(min = 56.dp)
      .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(icon, contentDescription = null, tint = Teal, modifier = Modifier.size(24.dp))
    Column(Modifier.weight(1f)) {
      Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
      subtitle?.let { Note(it) }
    }
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Muted)
  }
}

@Composable
fun ExpandablePanel(
  title: String,
  subtitle: String? = null,
  initialExpanded: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  var expanded by remember(initialExpanded) { mutableStateOf(initialExpanded) }
  Panel {
    Row(
      Modifier.fillMaxWidth().clickable { expanded = !expanded }.heightIn(min = 48.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        subtitle?.let { Note(it) }
      }
      Icon(
        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
        contentDescription = if (expanded) "收起$title" else "展开$title",
        tint = Muted,
      )
    }
    if (expanded) content()
  }
}

@Composable
fun AddressRow(label: String, address: String, emphasize: Boolean = false, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth()
      .clickable(onClick = onClick)
      .heightIn(min = 64.dp)
      .padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      Icons.Default.LocationOn,
      contentDescription = null,
      tint = if (emphasize) Teal else Muted,
      modifier = Modifier.size(22.dp),
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(label, color = Muted, fontSize = 13.sp)
      Text(
        address,
        fontWeight = FontWeight.SemiBold,
        fontSize = if (emphasize) 20.sp else 16.sp,
        lineHeight = if (emphasize) 28.sp else 24.sp,
      )
    }
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Muted)
  }
}

/** Bounded content scrolls above a fixed action row, including while the IME is open. */
@Composable
fun PassengerModal(
  title: String,
  onDismiss: () -> Unit,
  confirmLabel: String,
  confirmEnabled: Boolean = true,
  onConfirm: () -> Unit,
  dismissLabel: String = "取消",
  content: @Composable ColumnScope.() -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Box(
      Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          Modifier.fillMaxWidth().heightIn(max = 680.dp).padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp)
          Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
          )
          HorizontalDivider()
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
              onClick = onDismiss,
              modifier = Modifier.weight(0.4f).heightIn(min = 48.dp),
            ) {
              Text(dismissLabel)
            }
            Button(
              onClick = onConfirm,
              enabled = confirmEnabled,
              modifier = Modifier.weight(0.6f).heightIn(min = 48.dp),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text(confirmLabel)
            }
          }
        }
      }
    }
  }
}
