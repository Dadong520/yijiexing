package cn.hbzjhs.passenger

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PassengerSectionHeader(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!action.isNullOrEmpty()) {
            PassengerTextButton(
                onClick = onAction,
                modifier = Modifier.defaultMinSize(minHeight = 40.dp),
            ) {
                Text(text = action, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** One slim line per waiting reservation so it never pushes the booking form down. */
@Composable
fun PassengerReservationNotice(
    time: String,
    onView: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WarmSoft)
            .clickable(role = Role.Button, onClick = onView)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, tint = Warm, modifier = Modifier.size(16.dp))
        Text(
            "待出发预约 · $time",
            fontSize = TypeScale.Caption,
            fontWeight = FontWeight.Medium,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("查看", fontSize = TypeScale.Caption, fontWeight = FontWeight.SemiBold, color = Warm)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Warm, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun PassengerRouteSummary(
    origin: String,
    destination: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Paper)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp, end = 10.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Teal)
                )
                Text(
                    text = origin,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                    softWrap = true
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp, end = 10.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Amber)
                )
                Text(
                    text = destination,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                    softWrap = true
                )
            }
        }
    }
}

@Composable
fun PassengerCompactRecord(
    title: String,
    subtitle: String,
    selected: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        border = if (selected) BorderStroke(1.5.dp, Teal) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Teal else Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                trailing()
            }
        }
    }
}

@Composable
fun PassengerManagedRecord(
    title: String,
    subtitle: String,
    isDefault: Boolean,
    busy: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onDefault: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (isDefault) Surface(color = Teal.copy(alpha = .10f), shape = RoundedCornerShape(4.dp)) {
                    Text("默认", color = Teal, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PassengerTextButton(enabled = !busy, onClick = onDefault) { Text(if (isDefault) "取消默认" else "设为默认") }
                Row {
                    PassengerTextButton(enabled = !busy, onClick = onEdit) { Text("编辑") }
                    PassengerTextButton(enabled = !busy, onClick = onRemove) { Text("删除", color = Muted) }
                }
            }
        }
    }
}

@Composable
fun PassengerDefaultCheck(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable(role = Role.Checkbox) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null)
        Text(label, color = Ink, style = MaterialTheme.typography.bodyMedium)
    }
}
