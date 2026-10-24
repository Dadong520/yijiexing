package cn.hbzjhs.passenger

import org.json.JSONObject

data class OrderFarePresentation(
  val label: String,
  val detailLabel: String = "车费",
  val notice: String? = null,
)

fun orderFarePresentation(order: JSONObject): OrderFarePresentation =
  when (order.text("kind")) {
    "BID" ->
      // Existing responses retain DEMO_ESTIMATE in fareSource after driver selection. The
      // assigned driver identifies whether fare is still the estimate or the selected quote.
      if (order.text("driverId").isBlank())
        OrderFarePresentation(
          label = "体验估价（参考）",
          detailLabel = "参考车费（体验估价）",
          notice = "尚未选定司机。各司机报价见报价列表；当前金额仅为体验估价，选定后才按该司机报价模拟预付。",
        )
      else OrderFarePresentation(label = "选定司机报价")
    "NEGOTIATED" -> OrderFarePresentation(label = "司机报价")
    else -> OrderFarePresentation(label = "体验估价")
  }
