package cn.hbzjhs.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime

private fun parseIsoToEpochMilli(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            Instant.parse(iso).toEpochMilli()
        } catch (e2: Exception) {
            null
        }
    }
}

private fun translatePassengerDistanceReason(reason: String?): String {
    return when (reason) {
        "DRIVER_LOCATION_MISSING" -> "司机定位未获取"
        "DRIVER_LOCATION_STALE" -> "司机定位过旧 (>120s)"
        "DRIVER_LOCATION_POOR_ACCURACY" -> "司机定位精度不足 (>100m)"
        "DRIVER_LOCATION_INVALID" -> "司机定位数据无效"
        "PASSENGER_LOCATION_MISSING" -> "您的定位未获取"
        "PASSENGER_LOCATION_STALE" -> "您的定位已过期 (>120s)"
        "PASSENGER_LOCATION_POOR_ACCURACY" -> "您的定位精度不足 (>100m)"
        "PASSENGER_LOCATION_INVALID" -> "您的定位数据无效"
        "DESTINATION_INVALID_OR_SYNTHETIC" -> "目的地为合成或无有效坐标"
        "OUTSIDE_DISTANCE" -> "超出免确认直线距离阈值"
        null, "" -> "距离或定位校验需确认"
        else -> reason
    }
}

@Composable
fun PassengerTripDistanceCard(
    order: JSONObject,
    store: PassengerStore,
    busy: Boolean
) {
    val consent = order.optJSONObject("distanceConsent") ?: return
    val status = consent.optString("status")
    val expectedStage = consent.optString("expectedStage")
    val currentStage = order.optString("stage")

    if (status != "PENDING" || expectedStage != currentStage) {
        if (status in listOf("APPROVED", "REJECTED", "EXPIRED")) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Text(
                    text = "历史距离确认状态：" + when (status) { "APPROVED" -> "乘客已同意"; "REJECTED" -> "乘客已拒绝"; "EXPIRED" -> "请求已过期"; "INVALIDATED" -> "请求已失效"; "SUPERSEDED" -> "已被新请求替代"; else -> "状态已更新，请刷新查看" },
                    fontSize = 14.sp,
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        return
    }

    val consentId = consent.optString("id")
    val type = consent.optString("type")
    val isDropoff = type == "DROPOFF"
    val actionTitle = if (isDropoff) "司机申请确认已送达" else "司机申请确认已接到乘客"
    val approveBtnText = if (isDropoff) "同意已送达" else "同意已上车"

    val expiresEpoch = parseIsoToEpochMilli(consent.optString("expiresAt"))
    var remainingSeconds by remember(consent.optString("expiresAt")) {
        mutableStateOf(
            expiresEpoch?.let { Math.max(0L, (it - System.currentTimeMillis()) / 1000L) } ?: 0L
        )
    }

    LaunchedEffect(expiresEpoch) {
        if (expiresEpoch == null) return@LaunchedEffect
        while (true) {
            val diff = (expiresEpoch - System.currentTimeMillis()) / 1000L
            remainingSeconds = Math.max(0L, diff)
            if (diff <= 0L) break
            delay(1000L)
        }
    }

    val isExpired = remainingSeconds <= 0 && expiresEpoch != null

    val distanceText = if (consent.has("distanceMeters") && !consent.isNull("distanceMeters")) {
        "${consent.optInt("distanceMeters")} 米"
    } else {
        "定位不足，无法计算"
    }

    val thresholdText = if (consent.has("thresholdMeters") && !consent.isNull("thresholdMeters")) {
        "${consent.optInt("thresholdMeters")} 米"
    } else {
        "规则待同步"
    }

    val reasonText = translatePassengerDistanceReason(consent.optString("reason"))

    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingApprovalChoice by remember { mutableStateOf(true) }
    var dialogSnapshotConsentId by remember { mutableStateOf("") }
    var dialogSnapshotStage by remember { mutableStateOf("") }

    // 校验 Dialog 存在时是否有环境变动
    LaunchedEffect(consentId, currentStage, isExpired) {
        if (showConfirmDialog) {
            if (dialogSnapshotConsentId != consentId || dialogSnapshotStage != currentStage || isExpired) {
                showConfirmDialog = false
            }
        }
    }

    val primaryTeal = Color(0xFF00796B)
    val warningAmber = Color(0xFFE65100)
    val textDark = Color(0xFF212121)
    val textGrey = Color(0xFF757575)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = warningAmber
                )
                Text(
                    text = if (isExpired) "已超时" else "剩余: ${remainingSeconds}秒",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExpired) Color.Red else primaryTeal
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("距离核验：$distanceText (免确认阈值: $thresholdText)", fontSize = 14.sp, color = textDark)
            Spacer(modifier = Modifier.height(2.dp))
            Text("原因说明：$reasonText", fontSize = 14.sp, color = textGrey)

            Spacer(modifier = Modifier.height(14.dp))

            if (isExpired) {
                Button(
                    onClick = {
                        store.action("order.detail", JSONObject().put("id", order.optString("id")))
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryTeal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("请求已超时，刷新查看最新状态", fontSize = 14.sp, color = Color.White)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            dialogSnapshotConsentId = consentId
                            dialogSnapshotStage = currentStage
                            pendingApprovalChoice = false
                            showConfirmDialog = true
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("拒绝", fontSize = 14.sp, color = textGrey)
                    }

                    Button(
                        onClick = {
                            dialogSnapshotConsentId = consentId
                            dialogSnapshotStage = currentStage
                            pendingApprovalChoice = true
                            showConfirmDialog = true
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1.5f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryTeal),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(approveBtnText, fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }
    }

    if (showConfirmDialog) {
        PassengerModal(
            title = if (pendingApprovalChoice) "请核实并确认" else "确认拒绝此申请",
            onDismiss = { showConfirmDialog = false },
            confirmLabel = if (pendingApprovalChoice) approveBtnText else "确认拒绝",
            confirmEnabled = !busy,
            confirmColor = if (pendingApprovalChoice) Teal else Danger,
            onConfirm = {
                showConfirmDialog = false
                val payload = JSONObject().apply {
                    put("id", order.optString("id"))
                    put("consentId", dialogSnapshotConsentId)
                    put("approve", pendingApprovalChoice)
                }
                store.action("order.distanceConsent", payload)
            },
        ) {
            Text(
                if (pendingApprovalChoice) "您确认已与司机实际碰面或已到达目的地吗？确认后订单将进入下一阶段。"
                else "拒绝后订单将维持原阶段不推进，司机可重新校准定位再次申请。",
                color = Muted,
            )
        }
    }
}

@Composable
fun PassengerTripLocationReporter(
    state: JSONObject,
    loggedIn: Boolean,
    store: PassengerStore
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val passengerId = state.optJSONObject("passenger")?.optString("id").orEmpty()

    val latestState by rememberUpdatedState(state)
    LaunchedEffect(loggedIn, passengerId, lifecycleOwner) {
        if (!loggedIn || passengerId.isBlank()) return@LaunchedEffect

        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastReportedTime = 0L
            var lastReportedOrderId = ""

            val activeStages = setOf("AVAILABLE", "PREPAY", "RESERVED", "PICKUP", "ARRIVED", "TRIP")

            while (true) {
                val orders = latestState.optJSONArray("orders")
                var activeOrderId: String? = null
                if (orders != null) {
                    for (i in 0 until orders.length()) {
                        val item = orders.optJSONObject(i) ?: continue
                        val st = item.optString("stage")
                        if (st in activeStages) {
                            activeOrderId = item.optString("id")
                            break
                        }
                    }
                }

                if (!activeOrderId.isNullOrBlank()) {
                    val pos = PassengerPositionBus.position.value
                    val now = System.currentTimeMillis()
                    if (pos != null &&
                        pos.accuracy > 0f &&
                        pos.accuracy <= 100f &&
                        Math.abs(now - pos.time) <= 120_000L &&
                        !pos.latitude.isNaN() &&
                        !pos.longitude.isNaN() &&
                        !pos.latitude.isInfinite() &&
                        !pos.longitude.isInfinite()
                    ) {
                        if (latestState.optJSONObject("passenger")?.optString("id") == passengerId) {
                            lastReportedTime = pos.time
                            lastReportedOrderId = activeOrderId
                            store.reportLocation(
                                orderId = activeOrderId,
                                latitude = pos.latitude,
                                longitude = pos.longitude,
                                accuracy = pos.accuracy,
                                time = pos.time
                            )
                        }
                    }
                }
                delay(10000L)
            }
        }
    }
}
