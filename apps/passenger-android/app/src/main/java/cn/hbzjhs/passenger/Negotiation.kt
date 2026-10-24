package cn.hbzjhs.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.net.URI
import java.net.URLDecoder
import org.json.JSONObject

/** The invitation is a short-lived credential. Never persist it or put it into logs. */
fun parseNegotiationCode(input: String): String {
  val value = input.trim()
  require(value.length <= 1024) { "配对码或链接过长，请重新复制司机提供的一次配对码" }
  val code =
    if (value.startsWith("yijiexing:")) {
      val uri =
        runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("配对链接格式无效，请重新复制") }
      require(
        uri.scheme == "yijiexing" &&
          uri.host == "negotiation" &&
          uri.port == -1 &&
          uri.rawUserInfo == null &&
          uri.rawFragment == null &&
          uri.path.orEmpty().isEmpty()
      ) {
        "这不是有效的宜捷行线下议价配对链接"
      }
      val query = uri.rawQuery.orEmpty().split('&')
      require(query.size == 1 && query[0].startsWith("code=")) { "配对链接参数无效，请重新复制" }
      URLDecoder.decode(query[0].substringAfter('='), "UTF-8")
    } else value
  require(Regex("[A-Za-z0-9_-]{32}").matches(code)) { "请输入司机提供的 32 位一次配对码，或完整宜捷行配对链接" }
  return code
}

data class QuoteSnapshot(val version: Int, val amount: Long) {
  companion object {
    fun from(order: JSONObject) = QuoteSnapshot(order.optInt("quoteVersion"), order.optLong("fare"))
  }
}

fun quotationMatches(shown: QuoteSnapshot?, latest: JSONObject): Boolean =
  shown != null &&
    shown.version > 0 &&
    shown.amount > 0 &&
    shown == QuoteSnapshot.from(latest) &&
    latest.text("negotiationStatus") == "OFFERED" &&
    latest.text("stage") == "PREPAY"

fun negotiatedPaymentPayload(
  id: String,
  shown: QuoteSnapshot,
  latest: JSONObject,
  outcome: String,
): JSONObject {
  require(quotationMatches(shown, latest)) { "司机报价已更新或正在处理异议，请重新查看最新版本和金额后确认" }
  require(outcome in setOf("SUCCESS", "FAILED", "PENDING", "CANCEL"))
  return JSONObject()
    .put("id", id)
    .put("quoteVersion", shown.version)
    .put("amount", shown.amount)
    .put("outcome", outcome)
}

@Composable
fun NegotiationClaimScreen(
  store: PassengerStore,
  incomingCode: String?,
  consumed: () -> Unit,
  openOrder: (String) -> Unit,
) {
  // Deliberately not rememberSaveable: invitation codes do not survive process death.
  var input by remember { mutableStateOf("") }
  var localError by remember { mutableStateOf<String?>(null) }
  val busy by store.busy.collectAsState()
  val state by store.state.collectAsState()
  val unpaid = state.rows("orders").firstOrNull { it.text("stage") == "WAIT_PAY" }
  LaunchedEffect(incomingCode) {
    if (incomingCode != null) {
      input = incomingCode
      consumed()
    }
  }
  PageColumn {
    SectionTitle("线下议价 · 认领司机订单", "适用于您和司机已在线下见面的场景。")
    unpaid?.let { order ->
      Panel("请先完成待支付订单") {
        Text("行程已结束。完成支付后，才能再次叫车或认领线下议价订单。")
        ActionButton("去支付 ¥${money(order.optLong("fare"))}", !busy) { openOrder(order.text("id")) }
      }
    }
    Panel("输入一次配对码") {
      OutlinedTextField(
        value = input,
        onValueChange = {
          input = it.take(1024)
          localError = null
        },
        label = { Text("粘贴配对码或宜捷行配对链接") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Note("请向现场司机获取一次配对码，有效期 5 分钟。此页面只认领订单，下一步再核对司机、车牌、报价版本和金额，不会自动付款。")
      localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      ActionButton("认领并核对司机报价", !busy && unpaid == null && input.isNotBlank()) {
        runCatching { parseNegotiationCode(input) }
          .onSuccess { code ->
            store.action("negotiation.claim", JSONObject().put("pairCode", code)) { result ->
              input = ""
              result.optJSONObject("order")?.text("id")?.takeIf { it.isNotBlank() }?.let(openOrder)
            }
          }
          .onFailure { localError = it.message ?: "配对码无效" }
      }
    }
    Panel("如何使用配对链接") {
      Text("复制粘贴，或从外部扫码工具打开", fontWeight = FontWeight.Bold)
      Note("本应用不包含相机扫码功能。外部扫码工具识别 yijiexing://negotiation 配对链接后，如支持打开其他应用，可跳转至本页；也可复制一次配对码在这里粘贴。")
      Note("请勿公开分享配对码或未遮挡的二维码截图。一次码只能由一位乘客认领；过期、被撤销或已由其他乘客认领时，需要司机重新提供。")
    }
    Panel("报价确认与模拟支付") {
      Note("认领后可对报价提出异议并等待司机重新报价。每次付款都必须确认刚展示的报价版本与金额；司机若已改价，旧确认会被阻止。模拟支付不会真实扣款，成功后进入行程中。")
    }
  }
}
