package cn.hbzjhs.passenger

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OrderFarePresentationTest {
  @Test
  fun unselectedBidKeepsEstimateDistinctFromActualDriverQuotes() {
    val order =
      JSONObject(
        """{"kind":"BID","stage":"AVAILABLE","fare":2926,"fareSource":"DEMO_ESTIMATE","driverId":null,"bids":[{"driverId":"driver003","amount":3600}]}"""
      )
    val presentation = orderFarePresentation(order)
    assertEquals("体验估价（参考）", presentation.label)
    assertEquals("参考车费（体验估价）", presentation.detailLabel)
    assertTrue(presentation.notice.orEmpty().contains("尚未选定司机"))
    assertTrue(presentation.notice.orEmpty().contains("各司机报价"))
    assertEquals(2926, order.getInt("fare"))
    assertEquals(3600, order.getJSONArray("bids").getJSONObject(0).getInt("amount"))
    order.put("stage", "CANCELLED")
    assertEquals("体验估价（参考）", orderFarePresentation(order).label)
  }

  @Test
  fun assignedBidUsesSelectedQuoteEvenWhenLegacyFareSourceRemainsEstimate() {
    val order =
      JSONObject(
        """{"kind":"BID","stage":"PREPAY","fare":3600,"fareSource":"DEMO_ESTIMATE","driverId":"driver003"}"""
      )
    listOf("PREPAY", "PICKUP", "WAIT_CONTINUE", "CANCELLED").forEach { stage ->
      order.put("stage", stage)
      val presentation = orderFarePresentation(order)
      assertEquals("选定司机报价", presentation.label)
      assertEquals("车费", presentation.detailLabel)
      assertNull(presentation.notice)
    }
    // Continuing without a selected driver restores the original estimate meaning.
    order.put("stage", "AVAILABLE").put("driverId", JSONObject.NULL).put("fare", 2926)
    assertEquals("体验估价（参考）", orderFarePresentation(order).label)
    assertEquals("司机报价", orderFarePresentation(JSONObject().put("kind", "NEGOTIATED")).label)
    assertEquals("体验估价", orderFarePresentation(JSONObject().put("kind", "STANDARD")).label)
  }
}
