package cn.hbzjhs.driver.preview

import org.json.JSONObject

internal fun driverReferralRuleText(rule: JSONObject?): String {
    if (rule == null) return "拉新规则待同步 · 单账号上限100人 · 奖励为模拟积分"
    val tiers = rule.bList("tiers").joinToString("；") { "${it.optInt("from")}人：${it.optInt("points")}分" }
    val bonuses = rule.bList("bonuses").joinToString("；") { "累计${it.optInt("from", it.optInt("count"))}人加${it.optInt("points")}分" }
    return "${if(rule.optBoolean("enabled")) "" else "活动暂停 · "}${if(rule.optString("trigger") == "REGISTER") "注册达标" else "首单达标"} · ${if(rule.optBoolean("tiered")) "阶梯积分 $tiers" else "固定${rule.optInt("points")}积分"} · ${if(rule.optBoolean("cumulative")) "累计奖励" else "非累计奖励"} · $bonuses · 单账号上限100人，奖励为模拟积分"
}

internal data class DriverFeeIncome(val amount: Long, val label: String, val credited: Boolean)

internal fun driverFeeIncome(order: DriverOrder?, estimate: Long): DriverFeeIncome = DriverFeeIncome(
    amount = order?.incomeForDisplay(estimate) ?: estimate,
    label = order?.incomeLabel ?: "预计收入（未核对入账）",
    credited = order != null && order.paymentStatus != "REFUNDED" &&
        (order.settlementStatus == "CREDITED" || (order.settlementStatus.isBlank() && order.credited)),
)

internal data class DriverCancelFee(val amount: Long = 0, val status: String = "", val responsible: String = "") {
    val text: String? get() = if (amount <= 0) null else
        "空车费 ¥${money(amount)} · ${when (status) {
            "UNPAID" -> "待乘客模拟支付"
            "PAID" -> "已模拟补偿入账"
            else -> "补偿状态待同步"
        }} · 独立模拟补偿，不计入正常完单分账"
}

internal fun parseDriverCancelFee(value: JSONObject?): DriverCancelFee = DriverCancelFee(
    amount = value?.optLong("amount", 0)?.coerceAtLeast(0) ?: 0,
    status = value?.optString("status")?.takeUnless { it == "null" }.orEmpty(),
    responsible = value?.optString("responsible")?.takeUnless { it == "null" }.orEmpty(),
)

internal fun DriverOrder.cancelFeeDisplay(): DriverCancelFee = DriverCancelFee(cancelFeeAmount, cancelFeeStatus, cancelFeeResponsible)

internal fun serviceScoreRangeText(level: JSONObject): String {
    fun bound(key: String): Double? = if (level.isNull(key)) null else level.optDouble(key).takeIf { it.isFinite() }
    fun number(value: Double): String = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    val min = bound("min"); val max = bound("max")
    return when {
        min != null && max != null -> "${number(min)}–${number(max)}分"
        min != null -> "≥${number(min)}分"
        max != null -> "<${number(max)}分"
        else -> "区间待同步"
    }
}
