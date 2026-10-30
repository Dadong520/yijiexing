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
      .heightIn(min = 48.dp)
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val (accent, accentSoft) = iconAccent(icon)
    Box(
      modifier = Modifier
        .size(34.dp)
        .background(
          androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, accentSoft)),
          RoundedCornerShape(10.dp),
        ),
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
        fontSize = TypeScale.Body,
        fontWeight = FontWeight.Medium,
        color = Ink,
        lineHeight = 20.sp,
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
          Text(title, fontWeight = FontWeight.SemiBold, fontSize = TypeScale.Title, color = Ink)
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
      Text(label, color = Muted, fontSize = TypeScale.Caption)
      Text(
        address,
        fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = TypeScale.Title,
        lineHeight = 22.sp,
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

/**
 * The one popup style for the whole app: centered card, 18sp title, 15sp body and a single row of
 * equal-height buttons (optional extra action on the left, cancel, then the gradient confirm).
 */
@Composable
fun PassengerModal(
  title: String,
  onDismiss: () -> Unit,
  confirmLabel: String,
  confirmEnabled: Boolean = true,
  onConfirm: () -> Unit,
  dismissLabel: String = "取消",
  confirmColor: Color = Teal,
  extraLabel: String? = null,
  onExtra: () -> Unit = {},
  // Callers that already hold a higher-ranked slot (app-wide prompts) must not add a page slot,
  // or they would outrank themselves and hide.
  holdPageSlot: Boolean = true,
  dismissEnabled: Boolean = true,
  extraEnabled: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (holdPageSlot) PassengerModalSlot()
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Box(
      Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .imePadding()
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 24.sp,
            color = Ink,
          )
          ProvideTextStyle(TextStyle(fontSize = TypeScale.Body, lineHeight = 22.sp, color = Ink)) {
            Column(
              Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(10.dp),
              content = content,
            )
          }
          // Labels wrap instead of clipping on narrow screens / large fonts; the row takes its height
          // from the tallest label and every button fills it, so the three stay one equal-height row.
          Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            val buttonShape = RoundedCornerShape(12.dp)
            val buttonPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
            if (extraLabel != null)
              OutlinedButton(
                onClick = onExtra,
                enabled = extraEnabled,
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 46.dp),
                shape = buttonShape,
                border = BorderStroke(1.dp, DangerLine),
                contentPadding = buttonPadding,
              ) {
                Text(extraLabel, color = Danger, fontSize = TypeScale.Body, textAlign = TextAlign.Center)
              }
            Button(
              onClick = onDismiss,
              enabled = dismissEnabled,
              modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 46.dp),
              shape = buttonShape,
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F3F6), contentColor = Muted),
              elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
              contentPadding = buttonPadding,
            ) {
              Text(dismissLabel, fontSize = TypeScale.Body, textAlign = TextAlign.Center)
            }
            Button(
              onClick = onConfirm,
              enabled = confirmEnabled,
              modifier = Modifier.weight(if (extraLabel != null) 1.4f else 1.2f).fillMaxHeight().heightIn(min = 46.dp),
              shape = buttonShape,
              colors = ButtonDefaults.buttonColors(
                containerColor = confirmColor,
                contentColor = Color.White,
                disabledContainerColor = Line,
                disabledContentColor = Muted,
              ),
              elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
              contentPadding = buttonPadding,
            ) {
              Text(confirmLabel, fontSize = TypeScale.Body, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
          }
        }
      }
    }
  }
}

/** Plain message popup in the unified style. */
@Composable
fun PassengerAlert(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  dismissLabel: String = "取消",
  confirmEnabled: Boolean = true,
  confirmColor: Color = Teal,
  extraLabel: String? = null,
  onExtra: () -> Unit = {},
) {
  PassengerModal(title, onDismiss, confirmLabel, confirmEnabled, onConfirm, dismissLabel, confirmColor, extraLabel, onExtra) {
    Text(message, color = Muted)
  }
}

private enum class DialogButtonRole { None, Confirm, Dismiss }

private val LocalDialogButtonRole = staticCompositionLocalOf { DialogButtonRole.None }

/**
 * Text-level action shown as a real button: a soft tinted pill instead of bare coloured text. Inside
 * [PassengerDialog] the same call renders as the dialog's full-width confirm or cancel button.
 */
@Composable
fun PassengerTextButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
  content: @Composable RowScope.() -> Unit,
) {
  val role = LocalDialogButtonRole.current
  val shape = RoundedCornerShape(if (role == DialogButtonRole.None) 10.dp else 12.dp)
  Button(
    onClick = onClick,
    // Dialog buttons ignore caller sizing so every popup gets the same equal-height button row; they
    // grow with wrapped labels instead of clipping them.
    modifier = if (role == DialogButtonRole.None) modifier.heightIn(min = 36.dp) else Modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 46.dp),
    enabled = enabled,
    shape = shape,
    colors = ButtonDefaults.buttonColors(
      containerColor = if (role == DialogButtonRole.Dismiss) Color(0xFFF1F3F6) else BrandSoft,
      contentColor = if (role == DialogButtonRole.Dismiss) Muted else BrandStrong,
      disabledContainerColor = Color(0xFFF3F4F6),
      disabledContentColor = Color(0xFF9CA3AF),
    ),
    border = if (role == DialogButtonRole.Confirm) BorderStroke(1.dp, Teal.copy(alpha = 0.45f)) else null,
    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
    contentPadding = if (role == DialogButtonRole.None) contentPadding else PaddingValues(horizontal = 6.dp, vertical = 6.dp),
  ) {
    val style = TextStyle(
      fontSize = if (role == DialogButtonRole.None) 14.sp else TypeScale.Body,
      fontWeight = if (role == DialogButtonRole.Confirm) FontWeight.SemiBold else FontWeight.Medium,
    )
    ProvideTextStyle(if (role == DialogButtonRole.None) style else style.copy(textAlign = TextAlign.Center)) { content() }
  }
}

/** Drop-in for Material's AlertDialog that renders in the unified [PassengerModal] look. */
@Composable
fun PassengerDialog(
  onDismissRequest: () -> Unit,
  confirmButton: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  dismissButton: (@Composable () -> Unit)? = null,
  title: (@Composable () -> Unit)? = null,
  text: (@Composable () -> Unit)? = null,
) {
  Dialog(
    onDismissRequest = onDismissRequest,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    Box(
      Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(shape = RoundedCornerShape(20.dp), color = Color.White, shadowElevation = 12.dp, modifier = modifier.fillMaxWidth()) {
        Column(
          Modifier.fillMaxWidth().heightIn(max = 640.dp).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          title?.let {
            ProvideTextStyle(TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Ink)) { it() }
          }
          text?.let {
            // Long bodies bring their own scroll; nesting another vertical scroll here would crash.
            Box(Modifier.weight(1f, fill = false)) {
              ProvideTextStyle(TextStyle(fontSize = TypeScale.Body, lineHeight = 22.sp, color = Ink)) { it() }
            }
          }
          Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            dismissButton?.let {
              Box(Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalDialogButtonRole provides DialogButtonRole.Dismiss) { it() }
              }
            }
            Box(Modifier.weight(1.2f).fillMaxHeight()) {
              CompositionLocalProvider(LocalDialogButtonRole provides DialogButtonRole.Confirm) { confirmButton() }
            }
          }
        }
      }
    }
  }
}
