package cn.hbzjhs.driver.preview

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

/**
 * Origin, every waypoint in original order and the destination, with completed / next markers.
 * Waypoint labels are display positions; the server identifies stops by [StopRow.waypointIndex].
 */
@Composable
internal fun OrderStopsList(order: DriverOrder, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NavigationSessionRules.stopRows(order).forEach { row ->
            val color = when {
                row.waypointIndex != null -> Muted
                row.label == "起" -> Green
                else -> Orange
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.padding(top = 3.dp, end = 8.dp).widthIn(min = 20.dp).height(20.dp).clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(row.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Text(
                    row.name,
                    modifier = Modifier.weight(1f),
                    fontSize = if (compact || row.waypointIndex != null) DriverType.Body else DriverType.Title,
                    lineHeight = 22.sp,
                    fontWeight = if (row.waypointIndex != null) FontWeight.Medium else FontWeight.SemiBold,
                    color = if (row.progress == StopProgress.DONE) Muted else Ink,
                )
                when (row.progress) {
                    StopProgress.DONE -> Text("已完成", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, top = 3.dp))
                    StopProgress.NEXT -> Pill("下一站")
                    StopProgress.PENDING -> Unit
                }
            }
        }
    }
}

@Composable
internal fun TripWaypoints(order: DriverOrder, store: ConnectedStore, onMessage: (String) -> Unit) {
    if (order.waypoints.isEmpty()) return
    val busy by store.loading.collectAsState()
    var confirmation by remember(order.id) { mutableStateOf<DriverWaypoint?>(null) }
    var pendingIndex by remember(order.id) { mutableStateOf<Int?>(null) }
    var failure by remember(order.id) { mutableStateOf<String?>(null) }

    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "途经点",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${order.waypoints.size - order.pendingWaypoints.size}/${order.waypoints.size} 已到达",
                color = Muted,
                fontSize = 12.sp,
            )
        }
        order.waypoints.forEachIndexed { position, point ->
            val arrived = point.index in order.arrivedWaypoints
            if (position > 0) HorizontalDivider(color = Line)
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${position + 1}",
                    color = Green,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(26.dp),
                )
                Text(
                    point.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                val next = order.stage == Stage.TRIP && order.pendingWaypoints.firstOrNull()?.index == point.index
                Pill(
                    if (arrived) "已到达" else if (pendingIndex == point.index) "确认中" else if (next) "下一站" else "未到达",
                    if (arrived) Green else Muted,
                )
            }
            if (order.stage == Stage.TRIP && !arrived) {
                OutlinedButton(
                    onClick = {
                        if (pendingIndex == null) {
                            failure = null
                            confirmation = point
                        }
                    },
                    enabled = !busy && pendingIndex == null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Text(
                        if (pendingIndex == point.index) "正在记录到达…" else "确认到达此途经点",
                        fontSize = 14.sp,
                    )
                }
            }
        }
        failure?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (order.stage == Stage.TRIP && order.pendingWaypoints.isNotEmpty()) {
            Text(
                "请到达后逐一确认，完成所有途经点后可确认送达。",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (order.stage in setOf(Stage.RESERVED, Stage.PICKUP, Stage.ARRIVED)) {
            Text(
                "开始行程后，可在这里确认到达途经点。",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    confirmation?.let { point ->
        DriverDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("确认到达途经点") },
            text = { Text("请确认车辆已到达「${point.name}」。到达记录保存后，页面会更新状态。", lineHeight = 25.sp) },
            confirmButton = {
                DriverTextButton(
                    enabled = !busy && pendingIndex == null,
                    onClick = {
                        if (pendingIndex == null) {
                            confirmation = null
                            val latest = store.state.value.orders.firstOrNull { it.id == order.id }
                            if (latest == null || latest.stage != Stage.TRIP) {
                                failure = "订单状态已变化，请刷新行程后重试"
                            } else if (point.index in latest.arrivedWaypoints) {
                                onMessage("该途经点已确认到达")
                            } else {
                                pendingIndex = point.index
                                failure = null
                                store.perform(
                                    "order.waypoint",
                                    JSONObject().put("id", order.id).put("index", point.index),
                                    onFailure = {
                                        pendingIndex = null
                                        failure = it
                                    },
                                ) {
                                    pendingIndex = null
                                    onMessage("已确认到达途经点")
                                }
                            }
                        }
                    },
                ) {
                    Text("确认到达")
                }
            },
            dismissButton = { DriverTextButton(onClick = { confirmation = null }) { Text("尚未到达") } },
        )
    }
}
