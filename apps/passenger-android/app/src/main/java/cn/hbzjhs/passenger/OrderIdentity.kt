package cn.hbzjhs.passenger

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

fun orderPlacedTime(createdAt: String, zone: ZoneId = ZoneId.systemDefault()): String? =
  runCatching {
      Instant.parse(createdAt).atZone(zone).format(DateTimeFormatter.ofPattern("MM月dd日 HH:mm"))
    }
    .getOrNull()

fun orderSummary(order: JSONObject, zone: ZoneId = ZoneId.systemDefault()): String {
  val kind = order.text("kind")
  val title =
    if (kind in listOf("STANDARD", "RESERVED", "BID", "NEGOTIATED")) kindName(kind) else "出行订单"
  return orderPlacedTime(order.text("createdAt"), zone)?.let { "$title · $it 下单" } ?: title
}

@Composable
fun IdentifierButton(value: String, label: String = "订单编号") {
  if (value.isBlank()) return
  var visible by remember(value) { mutableStateOf(false) }
  val context = LocalContext.current
  PassengerTextButton(onClick = { visible = true }) { Text("查看$label") }
  PassengerModalSlot(visible)
  if (visible)
    PassengerDialog(
      onDismissRequest = { visible = false },
      title = { Text(label) },
      text = { SelectionContainer { Text(value) } },
      confirmButton = {
        PassengerTextButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, "${label}已复制", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("复制$label")
        }
      },
      dismissButton = { PassengerTextButton(onClick = { visible = false }) { Text("关闭") } },
    )
}
