package cn.hbzjhs.passenger

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .heightIn(min = 54.dp)
      .padding(vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    val (accent, accentSoft) = iconAccent(icon)
    Box(
      modifier = Modifier
        .size(36.dp)
        .background(accentSoft, RoundedCornerShape(10.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(20.dp),
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = Ink,
        lineHeight = 22.sp,
      )
      subtitle?.let { Note(it) }
    }
    Icon(
      Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = Chevron,
      modifier = Modifier.size(20.dp),
    )
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
  Card(
    colors = CardDefaults.cardColors(containerColor = Color.White),
    shape = RoundedCornerShape(18.dp),
    border = BorderStroke(1.dp, Line),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(
        Modifier
          .fillMaxWidth()
          .clickable { expanded = !expanded }
          .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Ink)
          subtitle?.let { Note(it) }
        }
        Icon(
          if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
          contentDescription = if (expanded) "收起$title" else "展开$title",
          tint = Muted,
          modifier = Modifier.size(22.dp),
        )
      }
      if (expanded) {
        HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
        content()
      }
    }
  }
}

@Composable
fun AddressRow(label: String, address: String, emphasize: Boolean = false, onClick: () -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .heightIn(min = 56.dp)
      .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier
        .size(10.dp)
        .background(
          if (emphasize) Amber else Teal,
          CircleShape,
        ),
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(label, color = Muted, fontSize = 12.sp)
      Text(
        address,
        fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = if (emphasize) 18.sp else 16.sp,
        lineHeight = if (emphasize) 24.sp else 22.sp,
        color = Ink,
      )
    }
    Icon(
      Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = Chevron,
      modifier = Modifier.size(20.dp),
    )
  }
}

@Composable
fun PassengerModal(
  title: String,
  onDismiss: () -> Unit,
  confirmLabel: String,
  confirmEnabled: Boolean = true,
  onConfirm: () -> Unit,
  dismissLabel: String = "取消",
  confirmColor: Color = Teal,
  content: @Composable ColumnScope.() -> Unit,
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .imePadding()
        .padding(16.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          Modifier
            .fillMaxWidth()
            .heightIn(max = 680.dp)
            .padding(22.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp,
            color = Ink,
          )
          Column(
            Modifier
              .weight(1f, fill = false)
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
          )
          HorizontalDivider(color = DividerTone, thickness = 0.8.dp)
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
              onClick = onDismiss,
              modifier = Modifier.weight(0.4f).heightIn(min = 48.dp),
            ) {
              Text(dismissLabel, color = Muted, fontSize = 15.sp)
            }
            Button(
              onClick = onConfirm,
              enabled = confirmEnabled,
              modifier = Modifier.weight(0.6f).heightIn(min = 48.dp),
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = confirmColor,
                contentColor = Color.White,
                disabledContainerColor = Line,
                disabledContentColor = Muted,
              ),
            ) {
              Text(confirmLabel, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
    }
  }
}

