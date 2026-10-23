package cn.hbzjhs.driver.preview

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URI
import java.net.URLDecoder
import java.time.Duration
import java.time.Instant
import org.json.JSONObject

/** Short-lived bearer invitation. Never persist, log or include it in a public screenshot. */
internal class PairOffer(
    val orderId: String,
    val pairCode: String,
    val pairUri: String,
    val expiresAt: Instant,
    val remainingAtReceipt: Long,
) {
    override fun toString() = "PairOffer(orderId=$orderId, invitation=REDACTED)"
}

internal data class RequoteSnapshot(val orderId: String, val version: Int, val amount: Long)

internal object NegotiationContract {
    fun parsePairOffer(
        data: JSONObject,
        expectedOrderId: String,
        now: Instant = Instant.now(),
    ): PairOffer {
        val result = data.optJSONObject("result") ?: error("服务端未返回配对信息，请重试")
        require(result.optString("orderId") == expectedOrderId) { "配对信息与当前订单不一致，请刷新订单" }
        val code = result.optString("pairCode")
        require(Regex("[A-Za-z0-9_-]{32}").matches(code)) { "配对码格式无效，请重试" }
        val uriText = result.optString("pairUri")
        val uri = runCatching { URI(uriText) }.getOrNull()
        require(
            uri != null &&
                uri.scheme == "yijiexing" &&
                uri.host == "negotiation" &&
                uri.rawUserInfo == null &&
                uri.port == -1 &&
                uri.rawFragment == null &&
                uri.rawPath.isNullOrEmpty()
        ) {
            "配对链接格式无效，请重试"
        }
        val parts = uri.rawQuery?.split("&") ?: emptyList()
        require(
            parts.size == 1 &&
                parts.single().startsWith("code=") &&
                runCatching { URLDecoder.decode(parts.single().substringAfter("="), "UTF-8") }.getOrNull() == code
        ) {
            "配对链接内容不一致，请重试"
        }
        require(
            result.optString("paymentMode") == "SIMULATED" &&
                result.optInt("validitySeconds") == 300
        ) {
            "配对服务模式或有效期无效，请刷新订单"
        }
        val expires = runCatching { Instant.parse(result.optString("expiresAt")) }.getOrNull()
        val serverNow =
            runCatching { Instant.parse(data.optString("serverTime")) }.getOrNull() ?: now
        require(expires != null) { "配对有效期无效，请重试" }
        val remaining = Duration.between(serverNow, expires).seconds.coerceAtMost(300)
        require(remaining > 0) { "配对码已过期，请重新生成" }
        return PairOffer(expectedOrderId, code, uriText, expires, remaining)
    }

    fun snapshot(order: DriverOrder): RequoteSnapshot {
        require(order.canRequote && order.quoteVersion > 0) { "当前订单不能重新报价，请刷新订单" }
        return RequoteSnapshot(order.id, order.quoteVersion, order.fare)
    }

    fun requotePayload(current: DriverOrder, shown: RequoteSnapshot, amount: String): JSONObject {
        require(current.canRequote) { "当前订单已付款或不能重新报价，请刷新订单" }
        require(
            current.id == shown.orderId &&
                current.quoteVersion == shown.version &&
                current.fare == shown.amount
        ) {
            "报价已变化，请先核对最新版本；已输入金额会保留"
        }
        require(shown.version > 0) { "报价版本缺失，请刷新订单" }
        return JSONObject()
            .put("id", current.id)
            .put("quoteVersion", shown.version)
            .put("amount", DriverRules.cents(amount))
    }

    fun qrMatrix(offer: PairOffer): BitMatrix =
        QRCodeWriter()
            .encode(
                offer.pairUri,
                BarcodeFormat.QR_CODE,
                320,
                320,
                mapOf(
                    EncodeHintType.MARGIN to 4,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
}
