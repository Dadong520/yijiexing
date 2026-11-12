package cn.hbzjhs.passenger

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray
import java.time.LocalTime
import java.time.Instant

class Feedback3PricingTest {
  private val day = LocalTime.NOON
  @Test fun distanceUnitsCeilAndReturnOnlyFromTenthKm() {
    val state = JSONObject()
    assertEquals(800L, BookingPricing.estimate(state,"420500",2000.0,day).tripFare)
    assertEquals(880L, BookingPricing.estimate(state,"420500",2000.1,day).tripFare)
    assertEquals(880L, BookingPricing.estimate(state,"420500",2500.0,day).tripFare)
    assertEquals(960L, BookingPricing.estimate(state,"420500",2500.1,day).tripFare)
    assertEquals(1920L, BookingPricing.estimate(state,"420500",9000.0,day).tripFare)
    assertEquals(2040L, BookingPricing.estimate(state,"420500",9000.1,day).tripFare)
    assertEquals(2160L, BookingPricing.estimate(state,"420500",10000.0,day).tripFare)
  }
  @Test fun nightEndMinuteInclusiveAndRatesAddRatherThanMultiply() {
    assertEquals(912L, BookingPricing.estimate(JSONObject(),"420500",2500.0,LocalTime.of(23,0)).tripFare)
    assertEquals(912L, BookingPricing.estimate(JSONObject(),"420500",2500.0,LocalTime.of(5,0,59)).tripFare)
    assertEquals(880L, BookingPricing.estimate(JSONObject(),"420500",2500.0,LocalTime.of(5,1)).tripFare)
    assertEquals(2672L, BookingPricing.estimate(JSONObject(),"420500",10000.0,LocalTime.MIDNIGHT).tripFare)
  }
  @Test fun selectedCityConfigAndServiceFeeNeverDiscounted() {
    val state = JSONObject("""{"operations":{"pricing":{"city":"420500","serviceFee":999},"cityPricing":{"420100":{"tariff":{"startPrice":1000,"startMeters":3000,"unitPrice":75,"unitMeters":500},"serviceFee":500}}}}""")
    val fare = BookingPricing.estimate(state,"420100",3001.0,day)
    assertEquals(1075L,fare.tripFare);assertEquals(500L,fare.serviceFee)
    assertEquals(500L,fare.payable(JSONObject().put("amount",5000)))
    assertEquals(1575L,fare.payable(null))
    assertEquals(0L,BookingPricing.estimate(state,"420600",1000.0,day).serviceFee)
  }
  @Test fun couponsUseTripThresholdAndValidStatusAndTimeWithStableBestFirst() {
    fun coupon(id:String,amount:Long,status:String="UNUSED",threshold:Long=1000)=JSONObject().put("id",id).put("amount",amount).put("status",status).put("threshold",threshold)
    val coupons=JSONArray().put(coupon("B",200)).put(coupon("A",500)).put(coupon("locked",999,"LOCKED")).put(coupon("used",999,"USED")).put(coupon("threshold",999,threshold=1100)).put(coupon("expired",999).put("expiresAt","2026-01-01T00:00:00Z")).put(coupon("future",999).put("validFrom","2027-01-01T00:00:00Z"))
    val state=JSONObject().put("coupons",coupons)
    assertEquals(listOf("A","B"),BookingPricing.usableCoupons(state,1000,Instant.parse("2026-11-11T00:00:00Z")).map{it.text("id")})
    assertTrue(BookingPricing.usableCoupons(state,999,Instant.parse("2026-11-11T00:00:00Z")).isEmpty())
  }
  @Test fun roundTripCountsEveryLegAndInvalidCoordinatesDoNotBecomeZero() {
    val a=30.6 to 111.3;val b=30.61 to 111.32
    val one=BookingPricing.routeMeters(listOf(a,b))!!
    assertEquals(one*2,BookingPricing.routeMeters(listOf(a,b,a))!!,0.00001)
    assertNull(BookingPricing.routeMeters(listOf(a,Double.NaN to 111.0)))
    assertNull(BookingPricing.routeMeters(listOf(a,0.0 to 0.0)))
  }
  @Test fun configuredUnitRatesRoundEachUnitHalfUp() {
    val state=JSONObject("""{"operations":{"cityPricing":{"420500":{"tariff":{"unitPrice":33}}}}}""")
    assertEquals(850L,BookingPricing.estimate(state,"420500",9000.1,day).tripFare - 14*33)
    assertEquals(846L,BookingPricing.estimate(state,"420500",2500.0,LocalTime.MIDNIGHT).tripFare)
  }
  @Test fun cancelledDebtRemainsDiscoverableOutsideActiveTripFilters() {
    val debt=JSONObject().put("id","O1").put("stage","CANCELLED").put("cancelFee",JSONObject().put("status","UNPAID").put("amount",800))
    assertSame(debt,outstandingCancelFee(listOf(debt)))
    debt.getJSONObject("cancelFee").put("status","PAID")
    assertNull(outstandingCancelFee(listOf(debt)))
  }
}
