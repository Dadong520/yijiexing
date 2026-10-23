package cn.hbzjhs.driver.preview

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NegotiationContractTest {
    private val now = Instant.parse("2026-10-23T10:00:00Z")

    private fun offerData(): JSONObject {
        val code = "A".repeat(32) // Synthetic test fixture, never a live invitation.
        return JSONObject()
            .put("serverTime", now.toString())
            .put(
                "result",
                JSONObject()
                    .put("orderId", "TEST-NEG")
                    .put("pairCode", code)
                    .put("pairUri", "yijiexing://negotiation?code=$code")
                    .put("expiresAt", now.plusSeconds(300).toString())
                    .put("validitySeconds", 300)
                    .put("paymentMode", "SIMULATED"),
            )
    }

    private fun order() =
        DriverOrder(
            "TEST-NEG",
            OrderKind.NEGOTIATED,
            "上车点",
            "终点",
            9000L,
            "",
            "",
            "",
            stage = Stage.PREPAY,
            shared = true,
            quoteVersion = 3,
            negotiationStatus = "OFFERED",
        )

    @Test
    fun validInvitationUsesServerClockAndHidesCredentialInToString() {
        val offer =
            NegotiationContract.parsePairOffer(offerData(), "TEST-NEG", now.plusSeconds(3600))
        assertEquals(300L, offer.remainingAtReceipt)
        assertEquals(now.plusSeconds(300), offer.expiresAt)
        assertFalse(offer.toString().contains(offer.pairCode))
        assertFalse(offer.toString().contains(offer.pairUri))
    }

    @Test
    fun invalidOrExpiredInvitationsNeverBecomeQrPayload() {
        val mutations: List<(JSONObject) -> Unit> =
            listOf(
                { it.put("orderId", "OTHER") },
                { it.put("pairCode", "short") },
                { it.put("pairUri", "https://other.example/invite") },
                {
                    it.put(
                        "pairUri",
                        "yijiexing://negotiation?code=${"A".repeat(32)}&session=unwanted",
                    )
                },
                { it.put("pairUri", "yijiexing://negotiation?code=${"B".repeat(32)}") },
                { it.put("pairUri", "yijiexing://negotiation?code=${"A".repeat(32)}#fragment") },
                { it.put("expiresAt", now.minusSeconds(1).toString()) },
                { it.put("validitySeconds", 3600) },
                { it.put("paymentMode", "REAL") },
            )
        mutations.forEach { change ->
            val data = offerData()
            change(data.getJSONObject("result"))
            assertTrue(
                runCatching { NegotiationContract.parsePairOffer(data, "TEST-NEG", now) }.isFailure
            )
        }
    }

    @Test
    fun qrRoundTripContainsOnlyTheValidatedPairingUri() {
        val offer = NegotiationContract.parsePairOffer(offerData(), "TEST-NEG", now)
        val matrix = NegotiationContract.qrMatrix(offer)
        val pixels =
            IntArray(matrix.width * matrix.height) { i ->
                if (matrix[i % matrix.width, i / matrix.width]) 0xff000000.toInt()
                else 0xffffffff.toInt()
            }
        val decoded =
            MultiFormatReader()
                .decode(
                    BinaryBitmap(
                        HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels))
                    )
                )
        assertEquals(offer.pairUri, decoded.text)
        assertFalse(decoded.text.contains("session"))
        assertFalse(decoded.text.contains("access"))
    }

    @Test
    fun requoteUsesTheExactShownCurrentVersionAndIntegerCents() {
        val order = order()
        val payload =
            NegotiationContract.requotePayload(order, NegotiationContract.snapshot(order), "95.50")
        assertEquals(order.id, payload.getString("id"))
        assertEquals(3, payload.getInt("quoteVersion"))
        assertEquals(9550L, payload.getLong("amount"))
        assertEquals(3, payload.length())
        assertEquals(9000L, order.fare)
    }

    @Test
    fun staleSnapshotCannotSilentlySubmitAgainstTheNewVersionOrAmount() {
        val order = order()
        val shown = NegotiationContract.snapshot(order)
        listOf(
                order.copy(quoteVersion = 4),
                order.copy(fare = 9500),
                order.copy(id = "OTHER"),
                order.copy(paid = true),
                order.copy(stage = Stage.TRIP),
                order.copy(shared = false),
                order.copy(driverParticipationStage = "CANCELLED"),
            )
            .forEach {
                assertTrue(
                    runCatching { NegotiationContract.requotePayload(it, shown, "100") }.isFailure
                )
            }
    }

    @Test
    fun invalidAmountsAndMissingVersionsCannotBeSubmitted() {
        val order = order()
        val shown = NegotiationContract.snapshot(order)
        listOf("", "0", "-1", "95.501", "NaN", "5000.01", "1e3").forEach {
            assertTrue(
                runCatching { NegotiationContract.requotePayload(order, shown, it) }.isFailure
            )
        }
        assertTrue(
            runCatching { NegotiationContract.snapshot(order.copy(quoteVersion = 0)) }.isFailure
        )
    }

    @Test
    fun legacyPrivateOrdersAreOptInAndCannotRequoteSharedPrices() {
        val privateOrder = order().copy(shared = false, quoteVersion = 0, negotiationStatus = "")
        assertTrue(privateOrder.canPairWithPassenger)
        assertFalse(privateOrder.canRequote)
        assertFalse(privateOrder.pairingRequired)
        assertFalse(privateOrder.copy(paid = true).canPairWithPassenger)
        assertFalse(privateOrder.copy(stage = Stage.CANCELLED).canPairWithPassenger)
    }

    @Test
    fun stateRetainsPairingMetadataWithoutAnyInvitationCredential() {
        val row =
            JSONObject()
                .put("id", "TEST-NEG")
                .put("kind", "NEGOTIATED")
                .put("origin", "上车点")
                .put("destination", "终点")
                .put("fare", 9000)
                .put("stage", "PREPAY")
                .put("negotiationPairingRequired", true)
                .put("pairingExpiresAt", now.plusSeconds(300).toString())
        val parsed =
            ConnectedStore.parseDriverState(JSONObject().put("orders", JSONArray().put(row)))
                .orders
                .single()
        assertTrue(parsed.pairingRequired)
        assertEquals(now.plusSeconds(300).toString(), parsed.pairingExpiresAt)
        assertTrue(parsed.canPairWithPassenger)
        assertFalse(parsed.shared)
    }

    @Test
    fun sharedDisputesAndCanonicalContinuationRemainDistinctFromDriverStage() {
        val row =
            JSONObject()
                .put("id", "TEST-SHARED")
                .put("kind", "NEGOTIATED")
                .put("origin", "上车点")
                .put("destination", "终点")
                .put("fare", 9000)
                .put("stage", "CANCELLED")
                .put("shared", true)
                .put("canonicalStage", "WAIT_CONTINUE")
                .put("driverParticipationStage", "CANCELLED")
                .put("negotiationStatus", "DISPUTED")
                .put("quoteVersion", 2)
                .put("paymentStatus", "UNPAID")
                .put(
                    "priceDisputes",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("quoteVersion", 2)
                                .put("content", "希望改为九十元")
                                .put("createdAt", now.toString())
                        ),
                )
        val parsed =
            ConnectedStore.parseDriverState(JSONObject().put("orders", JSONArray().put(row)))
                .orders
                .single()
        assertEquals(Stage.CANCELLED, parsed.stage)
        assertEquals("WAIT_CONTINUE", parsed.canonicalStage)
        assertTrue(parsed.participationCancelled)
        assertFalse(parsed.isActive)
        assertFalse(parsed.canCancel)
        assertEquals("希望改为九十元", parsed.priceDisputes.single().content)
        assertEquals(2, parsed.priceDisputes.single().quoteVersion)
        assertEquals("DISPUTED", parsed.negotiationStatus)
    }
}
