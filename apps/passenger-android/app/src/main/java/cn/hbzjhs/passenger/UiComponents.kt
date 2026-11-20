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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
      .heightIn(min = 52.dp)
      .padding(vertical = 6.dp),
    // Long titles/subtitles wrap downward from the top instead of re-centring against the icon.
    verticalAlignment = if (subtitle == null) Alignment.CenterVertically else Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Each module keeps its own hue (see iconAccent) in a soft rounded tile.
    val (tint, soft) = iconAccent(icon)
    Box(Modifier.size(36.dp).background(soft, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
      Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
    Column(Modifier.weight(1f).padding(top = if (subtitle == null) 0.dp else 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
      modifier = Modifier.size(20.dp).then(if (subtitle == null) Modifier else Modifier.padding(top = 8.dp)),
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
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.dp, DividerTone),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
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

/**
 * One stop of a route (or a place field): a small marker, a caption and the place. [placeholder]
 * renders the text as a muted prompt instead of a chosen place. [dot] overrides the marker colour
 * (pickup teal, destination amber by default). When [pinKind] is provided, renders the unified route badge.
 */
@Composable
fun AddressRow(
  label: String,
  address: String,
  emphasize: Boolean = false,
  placeholder: Boolean = false,
  dot: Color? = null,
  pinKind: RoutePinKind? = null,
  onClick: () -> Unit,
) {
  Row(
    Modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .heightIn(min = 52.dp)
      .padding(vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (pinKind != null) {
      RouteBadgeIcon(pinKind)
    } else {
      Box(
        modifier = Modifier
          .size(10.dp)
          .background(
            dot ?: if (emphasize) Amber else Teal,
            CircleShape,
          ),
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(label, color = Muted, fontSize = TypeScale.Caption, lineHeight = 16.sp)
      Text(
        address,
        fontWeight = if (placeholder) FontWeight.Medium else if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
        fontSize = if (emphasize) 16.sp else TypeScale.Title,
        lineHeight = 22.sp,
        color = if (placeholder) Muted else Ink,
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
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 6.dp,
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
            val buttonShape = RoundedCornerShape(ButtonSize.Corner)
            val buttonPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
            if (extraLabel != null)
              OutlinedButton(
                onClick = onExtra,
                enabled = extraEnabled,
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = ButtonSize.Min),
                shape = buttonShape,
                border = BorderStroke(1.dp, DangerLine),
                contentPadding = buttonPadding,
              ) {
                Text(extraLabel, color = Danger, fontSize = TypeScale.Body, textAlign = TextAlign.Center)
              }
            Button(
              onClick = onDismiss,
              enabled = dismissEnabled,
              modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = ButtonSize.Min),
              shape = buttonShape,
              colors = ButtonDefaults.buttonColors(
                containerColor = SurfaceMuted,
                contentColor = Ink,
                disabledContainerColor = SurfaceMuted,
                disabledContentColor = DisabledText,
              ),
              elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
              contentPadding = buttonPadding,
            ) {
              Text(dismissLabel, fontSize = TypeScale.Body, textAlign = TextAlign.Center)
            }
            Button(
              onClick = onConfirm,
              enabled = confirmEnabled,
              modifier = Modifier.weight(if (extraLabel != null) 1.4f else 1.2f).fillMaxHeight().heightIn(min = ButtonSize.Min),
              shape = buttonShape,
              colors = ButtonDefaults.buttonColors(
                containerColor = confirmColor,
                contentColor = Color.White,
                disabledContainerColor = SurfaceMuted,
                disabledContentColor = DisabledText,
              ),
              elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
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
 * Text-level action shown as a light button: a neutral grey pill with brand text, so it never
 * competes with the page's one primary action. Inside [PassengerDialog] the same call renders as the
 * dialog's full-width confirm or cancel button.
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
  val shape = RoundedCornerShape(if (role == DialogButtonRole.None) 12.dp else ButtonSize.Corner)
  Button(
    onClick = onClick,
    // Dialog buttons ignore caller sizing so every popup gets the same equal-height button row; they
    // grow with wrapped labels instead of clipping them. Every variant keeps a 48dp touch height.
    modifier = if (role == DialogButtonRole.None) modifier.heightIn(min = ButtonSize.Min) else Modifier.fillMaxWidth().fillMaxHeight().heightIn(min = ButtonSize.Min),
    enabled = enabled,
    shape = shape,
    colors = ButtonDefaults.buttonColors(
      containerColor = if (role == DialogButtonRole.Dismiss) SurfaceMuted else BrandSoft,
      contentColor = if (role == DialogButtonRole.Dismiss) Ink else BrandStrong,
      disabledContainerColor = SurfaceMuted,
      disabledContentColor = DisabledText,
    ),
    border = when (role) {
      DialogButtonRole.Confirm -> BorderStroke(1.dp, Teal.copy(alpha = 0.45f))
      DialogButtonRole.None -> if (enabled) BorderStroke(1.dp, Teal.copy(alpha = 0.18f)) else null
      DialogButtonRole.Dismiss -> null
    },
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
      Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
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

/** 全乘客端统一样式的分段切换栏：浅灰底槽 + 白色高亮浮块。 */
@Composable
fun PassengerSegmentTabs(
  items: List<String>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Row(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(SurfaceMuted)
      .padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    items.forEachIndexed { idx, label ->
      val on = idx == selectedIndex
      Box(
        Modifier
          .weight(1f)
          .heightIn(min = 38.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(if (on) Color.White else Color.Transparent)
          .then(if (on) Modifier.border(1.dp, Teal.copy(alpha = 0.25f), RoundedCornerShape(8.dp)) else Modifier)
          .selectable(selected = on, enabled = enabled, role = Role.Tab, onClick = { onSelect(idx) })
          .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          label,
          fontSize = 13.sp,
          lineHeight = 18.sp,
          fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
          color = if (!enabled) DisabledText else if (on) BrandStrong else Muted,
          maxLines = 1,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

/** 全乘客端统一样式单选胶囊，替代风格不统一的默认 FilterChip。 */
@Composable
fun PassengerChoiceChip(
  label: String,
  selected: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(10.dp),
    color = if (!enabled) SurfaceMuted else if (selected) BrandSoft else Color.White,
    border = BorderStroke(1.dp, if (!enabled) DividerTone else if (selected) Teal else Line),
    modifier = modifier.heightIn(min = 36.dp),
  ) {
    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
      Text(
        label,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (!enabled) DisabledText else if (selected) BrandStrong else Ink,
        maxLines = 1,
      )
    }
  }
}

/** 默认项圆圈指示器：高亮品牌绿外晕+对勾圆圈，非默认呈空心细环。 */
@Composable
fun DefaultRadioDot(
  selected: Boolean,
  onClick: (() -> Unit)? = null,
  labelDescription: String = if (selected) "已设为默认" else "设为默认",
  modifier: Modifier = Modifier,
) {
  val ringModifier = modifier
    .size(if (onClick != null) 34.dp else 22.dp)
    .clip(CircleShape)
    .then(
      if (onClick != null) Modifier.clickable(role = Role.RadioButton, onClickLabel = labelDescription, onClick = onClick)
      else Modifier,
    )
  Box(ringModifier, contentAlignment = Alignment.Center) {
    Box(
      Modifier
        .size(22.dp)
        .clip(CircleShape)
        .background(if (selected) BrandSoft else Color.Transparent),
      contentAlignment = Alignment.Center,
    ) {
      if (selected) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(Teal), contentAlignment = Alignment.Center) {
          Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
        }
      } else {
        Box(Modifier.size(16.dp).border(1.5.dp, ControlOutline, CircleShape))
      }
    }
  }
}

/** 投诉状态分色标签。 */
@Composable
fun ComplaintStatusBadge(status: String, modifier: Modifier = Modifier) {
  val (tint, bg) = when (PassengerUiRules.complaintStatusTone(status)) {
    PassengerUiRules.ComplaintTone.PENDING -> Warm to WarmSoft
    PassengerUiRules.ComplaintTone.POSITIVE -> Success to SuccessSoft
    PassengerUiRules.ComplaintTone.NEGATIVE -> Danger to DangerSoft
    PassengerUiRules.ComplaintTone.NEUTRAL -> Muted to SurfaceMuted
  }
  Box(
    modifier
      .clip(RoundedCornerShape(6.dp))
      .background(bg)
      .padding(horizontal = 8.dp, vertical = 3.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(supportStatus(status), color = tint, fontSize = TypeScale.Caption, fontWeight = FontWeight.SemiBold)
  }
}
