package cn.hbzjhs.passenger

import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OrderIdentityTest {
  @Test
  fun summaryShowsChineseKindAndPlacedTimeInsteadOfInternalIdentifier() {
    val order =
      JSONObject()
        .put("id", "PORDfe4ce3005b17")
        .put("kind", "BID")
        .put("createdAt", "2026-10-23T23:25:00+08:00")
        .put("origin", "BID-FINAL-113-2324")
    assertEquals("司机报价 · 10月23日 23:25 下单", orderSummary(order, ZoneId.of("Asia/Shanghai")))
    assertEquals("BID-FINAL-113-2324", order.getString("origin"))
    assertEquals("PORDfe4ce3005b17", order.getString("id"))
  }

  @Test
  fun invalidOrMissingTimeNeverLeaksRawMetadataIntoTitle() {
    val order = JSONObject().put("kind", "STANDARD").put("id", "PORDinternal")
    for (invalid in listOf("", "null", "bad date", "PORDinternal")) {
      order.put("createdAt", invalid)
      assertEquals("即时用车", orderSummary(order))
      assertNull(orderPlacedTime(invalid))
    }
    order.put("kind", "UNKNOWN_INTERNAL_KIND")
    assertEquals("出行订单", orderSummary(order))
  }
}
