package cn.hbzjhs.driver.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private fun translateDistanceReason(reason: String?): String {
    return when (reason) {
        "DRIVER_LOCATION_MISSING" -> "司机定位缺失"
        "DRIVER_LOCATION_STALE" -> "司机定位已过期 (>120s)"
        "DRIVER_LOCATION_POOR_ACCURACY" -> "司机定位精度不足 (>100m)"
        "DRIVER_LOCATION_INVALID" -> "司机定位数据无效"
        "PASSENGER_LOCATION_MISSING" -> "乘客位置缺失"
        "PASSENGER_LOCATION_STALE" -> "乘客位置已过期 (>120s)"
        "PASSENGER_LOCATION_POOR_ACCURACY" -> "乘客定位精度不足 (>100m)"
        "PASSENGER_LOCATION_INVALID" -> "乘客定位数据无效"
        "DESTINATION_INVALID_OR_SYNTHETIC" -> "目的地坐标无效或为合成坐标"
        "OUTSIDE_DISTANCE" -> "超出免确认直线距离阈值"
        null, "" -> "距离或定位校验未通过"
        else -> reason
    }
}

private fun findOrder(payload: JSONObject, orderId: String): JSONObject? {
    val orders = payload.optJSONObject("state")?.optJSONArray("orders") ?: return null
    for (i in 0 until orders.length()) {
        val item = orders.optJSONObject(i)
        if (item != null && item.optString("id") == orderId) {
            return item
        }
    }
    return null
}

@Composable
fun driverHasPendingDistanceConsent(orderId: String, stage: String, store: ConnectedStore): Boolean {
    val rawPayload by store.payload.collectAsState()
    val order = findOrder(rawPayload, orderId) ?: return false
    val consent = order.optJSONObject("distanceConsent") ?: return false

    val status = consent.optString("status")
    val expectedStage = consent.optString("expectedStage")
    if (status != "PENDING" || expectedStage != stage) {
        return false
    }

    val expiresEpoch = parseIsoToEpochMilli(consent.optString("expiresAt"))
    if (expiresEpoch != null && System.currentTimeMillis() >= expiresEpoch) {
        return false
    }
    return true
}

@Composable
fun DriverTripDistanceCard(
    orderId: String,
    stage: String,
    store: ConnectedStore
) {
    val rawPayload by store.payload.collectAsState()
    val order = findOrder(rawPayload, orderId) ?: return

    val isShared = order.optBoolean("shared", true)
    val enforcement = order.optString("distanceEnforcement")
    val isSyntheticDemo = enforcement == "SYNTHETIC_DEMO_EXEMPT" || !isShared

    val primaryGreen = Color(0xFF00796B)
    val alertAmber = Color(0xFFE65100)
    val textDark = Color(0xFF212121)
    val textGrey = Color(0xFF757575)
    val bgWarning = Color(0xFFFFF3E0)
    val bgInfo = Color(0xFFE0F2F1)

    if (isSyntheticDemo) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = bgWarning)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "演示订单模式说明",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = alertAmber
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "本单为司机端本地演示订单，无真实乘客端交互，系统不创建伪造乘客同意。真实共享订单将严格执行乘客确认流程。",
                    fontSize = 14.sp,
                    color = textDark
                )
            }
        }
        return
    }

    val consent = order.optJSONObject("distanceConsent") ?: return
    val status = consent.optString("status")
    val expectedStage = consent.optString("expectedStage")
    val type = consent.optString("type")
    val typeLabel = if (type == "DROPOFF") "送达" else "接客(上车)"

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

    val distanceMeters = if (consent.has("distanceMeters") && !consent.isNull("distanceMeters")) {
        "${consent.optInt("distanceMeters")} 米"
    } else {
        "未知 / 无法计算"
    }

    val thresholdMeters = if (consent.has("thresholdMeters") && !consent.isNull("thresholdMeters")) {
        "${consent.optInt("thresholdMeters")} 米"
    } else {
        "规则待同步"
    }

    val reasonText = translateDistanceReason(consent.optString("reason"))
    val isExpired = remainingSeconds <= 0 && expiresEpoch != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (status == "PENDING") bgInfo else Color(0xFFF5F5F5))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when {
                status == "PENDING" && expectedStage == stage -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "等待乘客同意$typeLabel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = primaryGreen
                        )
                        Text(
                            text = if (isExpired) "已超时失效" else "有效期: ${remainingSeconds}秒",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isExpired) Color.Red else alertAmber
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "直线距离：$distanceMeters (免确认阈值: $thresholdMeters)",
                        fontSize = 14.sp,
                        color = textDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "未免确认原因：$reasonText",
                        fontSize = 14.sp,
                        color = textGrey
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "提示：原订单阶段尚未推进。请等待乘客在端内确认；客户端禁止绕过授权自行推进。",
                        fontSize = 14.sp,
                        color = textGrey
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { store.refresh() },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("刷新乘客确认状态", fontSize = 14.sp, color = Color.White)
                    }
                }
                status == "REJECTED" -> {
                    Text("乘客已拒绝本次$typeLabel确认", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = alertAmber)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("订单维持当前阶段。司机可调整定位或与乘客沟通后，重新移动到位再次申请。", fontSize = 14.sp, color = textDark)
                }
                status == "EXPIRED" || (status == "PENDING" && isExpired) -> {
                    Text("本次确认请求已超时失效", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Red)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("订单维持当前阶段。请核实双方定位后重新发起。", fontSize = 14.sp, color = textDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { store.refresh() },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("刷新订单状态", fontSize = 14.sp, color = primaryGreen)
                    }
                }
                status == "APPROVED" -> {
                    Text("乘客已同意确认 (历史已生效)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = primaryGreen)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("本次距离已核验完成，请依照最新订单状态继续服务。", fontSize = 14.sp, color = textDark)
                }
                else -> {
                    Text("确认请求失效 (状态: $status)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = textGrey)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("前置请求已被新操作覆盖或取消。", fontSize = 14.sp, color = textGrey)
                }
            }
        }
    }
}
