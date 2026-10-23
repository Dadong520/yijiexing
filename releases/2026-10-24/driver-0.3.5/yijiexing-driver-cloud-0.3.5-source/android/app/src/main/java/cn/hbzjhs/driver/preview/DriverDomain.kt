package cn.hbzjhs.driver.preview

import java.math.BigDecimal
import java.math.RoundingMode

enum class OrderKind(val title: String) {
    STANDARD("即时订单"),
    RESERVED("预约订单"),
    PHONE("电话叫车"),
    BID("竞价订单"),
    NEGOTIATED("线下议价"),
}

enum class Stage(val title: String) {
    AVAILABLE("待接单"),
    BID_SENT("等待乘客选择"),
    PREPAY("等待乘客支付"),
    RESERVED("待出发"),
    PICKUP("接驾中"),
    ARRIVED("已到上车点"),
    TRIP("行程中"),
    WAIT_PAY("待支付"),
    DONE("已完成"),
    CANCELLED("已取消"),
}

data class DriverWaypoint(
    // The local service identifies a stop by its original zero-based array index.
    val index: Int,
    val name: String,
    val id: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class PriceDispute(val quoteVersion: Int, val content: String, val createdAt: String)

data class DriverOrder(
    val id: String,
    val kind: OrderKind,
    val origin: String,
    val destination: String,
    val fare: Long,
    val distance: String,
    val mileage: String,
    val time: String,
    val stage: Stage = Stage.AVAILABLE,
    val remark: String = "",
    val phoneTail: String = "6758",
    val credited: Boolean = false,
    val paid: Boolean = false,
    // WGS84; NaN means unknown. Never substitute a sample city coordinate for missing data.
    val originLat: Double = Double.NaN,
    val originLng: Double = Double.NaN,
    val destinationLat: Double = Double.NaN,
    val destinationLng: Double = Double.NaN,
    val waypoints: List<DriverWaypoint> = emptyList(),
    val arrivedWaypoints: Set<Int> = emptySet(),
    val shared: Boolean = false,
    val canonicalStage: String = "",
    val driverParticipationStage: String = "",
    val destinationPending: Boolean = false,
    val negotiationStatus: String = "",
    val quoteVersion: Int = 0,
    val priceDisputes: List<PriceDispute> = emptyList(),
    val paymentStatus: String = "",
    val refundAmount: Long? = null,
    val pairingStatus: String = "",
    val pairingRequired: Boolean = false,
    val pairingExpiresAt: String = "",
) {
    val paymentStatusLabel: String
        get() = when (paymentStatus) {
            "REFUNDED" -> "已模拟退款"
            "SUCCESS" -> "已模拟支付"
            "FAILED" -> "模拟支付失败"
            "PROCESSING" -> "模拟支付处理中"
            "CANCELLED" -> "已取消模拟支付"
            "UNPAID" -> "未模拟支付"
            "" -> if (paid) "已模拟支付" else "未模拟支付"
            else -> "支付状态待确认"
        }

    val refundAmountText: String?
        get() = refundAmount
            ?.takeIf { paymentStatus == "REFUNDED" && it >= 0 }
            ?.let { "¥${money(it)}" }

    private val closedWithoutIncome: Boolean
        get() = !credited &&
            (paymentStatus == "REFUNDED" || stage == Stage.CANCELLED || participationCancelled)

    val incomeLabel: String
        get() = when {
            credited -> "本单已入账收入"
            closedWithoutIncome -> "本单未计入收入"
            else -> "预计收入（未入账）"
        }

    fun incomeForDisplay(estimatedIncome: Long): Long =
        if (closedWithoutIncome) 0 else estimatedIncome

    val participationCancelled: Boolean
        get() = driverParticipationStage == "CANCELLED"

    val canPairWithPassenger: Boolean
        get() = kind == OrderKind.NEGOTIATED && !shared && stage == Stage.PREPAY && !paid

    val canRequote: Boolean
        get() =
            kind == OrderKind.NEGOTIATED &&
                shared &&
                stage == Stage.PREPAY &&
                !paid &&
                !participationCancelled

    val pendingWaypoints: List<DriverWaypoint>
        get() = waypoints.filter { it.index !in arrivedWaypoints }

    val isActive: Boolean
        get() =
            !participationCancelled &&
                stage in
                    setOf(
                        Stage.BID_SENT,
                        Stage.PREPAY,
                        Stage.RESERVED,
                        Stage.PICKUP,
                        Stage.ARRIVED,
                        Stage.TRIP,
                    )

    val canCancel: Boolean
        get() =
            !participationCancelled &&
                stage in
                    setOf(Stage.BID_SENT, Stage.PREPAY, Stage.RESERVED, Stage.PICKUP, Stage.ARRIVED) &&
                // Shared pre-trip cancellation refunds prepayment on the server.
                // Private paid orders and unselected paid bids remain blocked.
                (!paid || (shared && stage != Stage.BID_SENT))
}

data class WalletEntry(val title: String, val amount: Long, val note: String)

data class DriverState(
    val loggedIn: Boolean = false,
    val online: Boolean = false,
    val voice: Boolean = true,
    val largeText: Boolean = false,
    val floating: Boolean = false,
    val range: Int = 5,
    val instant: Boolean = true,
    val reservation: Boolean = true,
    val bidding: Boolean = true,
    val orders: List<DriverOrder> = emptyList(),
    val balance: Long = 0,
    val todayIncome: Long = 0,
    val todayAccepted: Int = 0,
    val todayCompleted: Int = 0,
    val entries: List<WalletEntry> = emptyList(),
) {
    val activeOrder: DriverOrder?
        get() = orders.firstOrNull { it.isActive }
}

object DriverRules {
    fun cents(text: String): Long {
        require(Regex("\\d{1,5}(\\.\\d{1,2})?").matches(text.trim())) { "请输入有效金额，最多两位小数" }
        val result =
            BigDecimal(text.trim())
                .multiply(BigDecimal(100))
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact()
        require(result in 100L..500000L) { "金额需在 1 至 5000 元之间" }
        return result
    }
}

fun money(cents: Long) = BigDecimal.valueOf(cents, 2).setScale(2).toPlainString()
