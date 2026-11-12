package cn.hbzjhs.passenger

import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalTime
import kotlin.math.*

internal data class BookingFare(val tripFare: Long, val serviceFee: Long, val meters: Double, val night: Boolean, val returning: Boolean) {
  fun discount(coupon: JSONObject?): Long = coupon?.optLong("amount", 0)?.coerceIn(0, tripFare) ?: 0
  fun payable(coupon: JSONObject?): Long = tripFare + serviceFee - discount(coupon)
}

/** Local preview follows the server's 1.3 × sum-of-legs estimate. Final metering remains server-owned. */
internal object BookingPricing {
  fun estimate(state: JSONObject, city: String, meters: Double, clock: LocalTime): BookingFare {
    require(meters.isFinite() && meters >= 0 && meters <= 390_000) { "预估里程无效" }
    val ops = state.optJSONObject("operations") ?: JSONObject()
    val pricing = ops.optJSONObject("cityPricing")?.optJSONObject(city)
      ?: ops.optJSONObject("pricing")?.takeIf { it.text("city", city) == city } ?: JSONObject()
    val tariff = pricing.optJSONObject("tariff") ?: JSONObject()
    fun number(k: String, fallback: Long) = tariff.optLong(k, fallback).takeIf { it >= 0 } ?: fallback
    val startMeters = number("startMeters", 2000)
    val unitMeters = number("unitMeters", 500).coerceAtLeast(1)
    val returnAfter = number("returnAfterMeters", 9000)
    val start = runCatching { LocalTime.parse(tariff.text("nightStart", "23:00")) }.getOrDefault(LocalTime.of(23, 0))
    val end = runCatching { LocalTime.parse(tariff.text("nightEnd", "05:00")) }.getOrDefault(LocalTime.of(5, 0))
    val minute = clock.hour * 60 + clock.minute
    val from = start.hour * 60 + start.minute; val to = end.hour * 60 + end.minute
    val night = if (from > to) minute >= from || minute <= to else minute in from..to
    val units = ceil((meters - startMeters).coerceAtLeast(0.0) / unitMeters).toLong()
    val normalUnits = ceil((returnAfter - startMeters).coerceAtLeast(0).toDouble() / unitMeters).toLong().coerceAtMost(units)
    val nightRate = if (night) number("nightRate", 40) else 0
    fun price(rate: Long) = BigDecimal.valueOf(number("unitPrice", 80)).multiply(BigDecimal.valueOf(rate)).divide(BigDecimal(100), 0, RoundingMode.HALF_UP).longValueExact()
    val trip = number("startPrice", 800) + normalUnits * price(100 + nightRate) + (units - normalUnits) * price(100 + nightRate + number("returnRate", 50))
    return BookingFare(trip, pricing.optLong("serviceFee", 0).coerceAtLeast(0), meters, night, units > normalUnits)
  }
  fun routeMeters(points: List<Pair<Double, Double>>): Double? {
    if (points.size < 2 || points.any { !it.first.isFinite() || !it.second.isFinite() || it.first !in -90.0..90.0 || it.second !in -180.0..180.0 || (it.first == 0.0 && it.second == 0.0) }) return null
    return points.zipWithNext().sumOf { (a,b) ->
      val lat1 = Math.toRadians(a.first); val lat2 = Math.toRadians(b.first)
      val h = sin(Math.toRadians(b.first-a.first)/2).pow(2) + cos(lat1)*cos(lat2)*sin(Math.toRadians(b.second-a.second)/2).pow(2)
      12742000 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    } * 1.3
  }
  fun usableCoupons(state: JSONObject, tripFare: Long, now: Instant = Instant.now()): List<JSONObject> = state.rows("coupons").filter {
    it.text("status") == "UNUSED" && it.text("id").isNotBlank() && tripFare >= it.optLong("threshold", 0) && it.optLong("amount") > 0 &&
      (it.text("validFrom").isBlank() || runCatching { !Instant.parse(it.text("validFrom")).isAfter(now) }.getOrDefault(false)) &&
      (it.text("expiresAt").isBlank() || runCatching { Instant.parse(it.text("expiresAt")).isAfter(now) }.getOrDefault(false))
  }.sortedWith(compareByDescending<JSONObject> { it.optLong("amount").coerceAtMost(tripFare) }.thenBy { it.text("id") })
}

internal fun outstandingCancelFee(orders: List<JSONObject>): JSONObject? = orders.firstOrNull { it.optJSONObject("cancelFee")?.text("status") == "UNPAID" }
internal fun referralRuleText(rule: JSONObject?): String {
  if (rule == null) return "拉新规则待同步 · 单账号最多奖励100人"
  val trigger = if (rule.text("trigger") == "REGISTER") "注册达标" else "首单完成达标"
  val tiers = rule.rows("tiers").joinToString("；") { "${it.optInt("from", it.optInt("count", it.optInt("min")))}人：${it.optInt("points")}积分" }
  val bonuses = rule.rows("bonuses").joinToString("；") { "累计${it.optInt("count", it.optInt("from"))}人加${it.optInt("points")}积分" }
  return "${if (rule.optBoolean("enabled", true)) "" else "活动已暂停 · "}$trigger · ${if (rule.optBoolean("tiered")) "阶梯奖励 $tiers" else "固定${rule.optInt("points")}积分"} · ${if (rule.optBoolean("cumulative")) "累计奖励" else "非累计奖励"} · 单账号上限100人${if (bonuses.isBlank()) "" else " · $bonuses"}"
}
