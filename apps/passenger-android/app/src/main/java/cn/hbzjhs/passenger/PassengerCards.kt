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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PassengerSectionHeader(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = TypeScale.Page,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!action.isNullOrEmpty()) {
            Surface(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                color = BrandSoft,
                border = BorderStroke(1.dp, Teal.copy(alpha = 0.25f)),
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Text(
                    text = action,
                    color = BrandStrong,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
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
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RouteBadgeIcon(RoutePinKind.ORIGIN)
                Text(
                    text = origin,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                    softWrap = true
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RouteBadgeIcon(RoutePinKind.DESTINATION)
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
    // Light row (neutral fill, no raised card) so it can sit inside a panel without nesting cards.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) BrandSoft else SurfaceMuted),
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
                    color = if (selected) BrandStrong else Ink,
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
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isDefault) Teal.copy(alpha = 0.45f) else DividerTone),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DefaultRadioDot(
                    selected = isDefault,
                    onClick = if (!busy) onDefault else null,
                    labelDescription = if (isDefault) "取消默认" else "设为默认",
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            title,
                            color = Ink,
                            fontSize = TypeScale.Title,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isDefault) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(BrandSoft)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("默认", color = BrandStrong, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Text(
                        subtitle,
                        color = Muted,
                        fontSize = TypeScale.Caption,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onEdit,
                    enabled = !busy,
                    shape = RoundedCornerShape(8.dp),
                    color = BrandSoft,
                    border = BorderStroke(1.dp, Teal.copy(alpha = 0.22f)),
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = if (!busy) BrandStrong else DisabledText, modifier = Modifier.size(14.dp))
                        Text("编辑", color = if (!busy) BrandStrong else DisabledText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Surface(
                    onClick = onRemove,
                    enabled = !busy,
                    shape = RoundedCornerShape(8.dp),
                    color = DangerSoft,
                    border = BorderStroke(1.dp, DangerLine),
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = if (!busy) Danger else DisabledText, modifier = Modifier.size(14.dp))
                        Text("删除", color = if (!busy) Danger else DisabledText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun PassengerDefaultCheck(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.RadioButton) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DefaultRadioDot(selected = checked, onClick = null)
        Text(label, color = Ink, fontSize = TypeScale.Body)
    }
}
