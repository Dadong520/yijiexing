package cn.hbzjhs.driver.preview

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class OrderNotice(
    val id: String,
    val origin: String,
    val destination: String
)

object NewOrderAlerts {
    private val _notices = MutableStateFlow<List<OrderNotice>>(emptyList())
    val notices: StateFlow<List<OrderNotice>> = _notices.asStateFlow()

    private val _openRequested = MutableStateFlow<String?>(null)
    val openRequested: StateFlow<String?> = _openRequested.asStateFlow()

    fun publish(order: JSONObject) {
        val orderId = order.optString("orderId").ifEmpty { order.optString("id") }
        if (orderId.isBlank()) return

        val origin = order.optString("pickupAddress").ifEmpty {
            order.optString("origin", "上车点")
        }
        val destination = order.optString("destAddress").ifEmpty {
            order.optString("destination", "目的地")
        }

        val current = _notices.value
        if (current.any { it.id == orderId }) return
        if (current.size >= 5) return

        _notices.value = current + OrderNotice(orderId, origin, destination)
    }

    fun retainAvailable(ids: Set<String>) {
        _notices.value = _notices.value.filter { it.id in ids }
    }

    fun dismiss(id: String) {
        _notices.value = _notices.value.filter { it.id != id }
    }

    fun clear() {
        _notices.value = emptyList()
        _openRequested.value = null
    }

    fun open(id: String) {
        _openRequested.value = id
        dismiss(id)
    }

    fun consumeOpen(): String? {
        val requested = _openRequested.value
        _openRequested.value = null
        return requested
    }
}

@Composable
fun NewOrderDialog(
    orders: List<DriverOrder> = emptyList(),
    onOpen: (String) -> Unit
) {
    val noticeList by NewOrderAlerts.notices.collectAsState()
    if (noticeList.isEmpty()) return

    val first = noticeList.first()
    val remainingCount = noticeList.size - 1
    // Display-only lookup of the already-synced order; alert trigger and dedupe are unchanged.
    val detail = orders.firstOrNull { it.id == first.id }

    val primaryGreen = Color(0xFF00796B)
    val textDark = Color(0xFF212121)
    val textGrey = Color(0xFF757575)

    Dialog(
        onDismissRequest = { NewOrderAlerts.dismiss(first.id) },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "发现新订单",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryGreen
                    )
                    if (remainingCount > 0) {
                        Text(
                            text = "排队中: +${remainingCount}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = textGrey
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.Top) {
                    Text("起：", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = primaryGreen)
                    Text(
                        text = first.origin,
                        fontSize = 15.sp,
                        color = textDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Top) {
                    Text("终：", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    Text(
                        text = first.destination,
                        fontSize = 15.sp,
                        color = textDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Pale, RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val fareText = when {
                        detail == null -> "未提供"
                        detail.destinationPending -> "费用待定"
                        else -> "¥${money(detail.fare)}"
                    }
                    listOf(
                        "订单金额" to fareText,
                        "预估行程" to (detail?.let { kmOrMissing(it.mileage) } ?: "未提供"),
                        "距乘客" to (detail?.let { kmOrMissing(it.distance) } ?: "未提供"),
                    ).forEach { (label, value) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (label == "订单金额") Warm else textDark)
                            Text(label, fontSize = 12.sp, color = textGrey)
                        }
                    }
                }
                if (detail != null)
                    Text(newOrderSourceNote(detail.isPrivateExample), fontSize = 12.sp, lineHeight = 16.sp, color = Warm, modifier = Modifier.padding(top = 6.dp))

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { NewOrderAlerts.dismiss(first.id) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("稍后", fontSize = 14.sp, color = textGrey)
                    }

                    Button(
                        onClick = {
                            val id = first.id
                            NewOrderAlerts.dismiss(id)
                            onOpen(id)
                        },
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("查看订单", fontSize = 14.sp, color = Color.White)
                    }
                }
            }
        }
    }
}

class NewOrderOverlay(private val appContext: Context) {
    private val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentView: View? = null
    private var currentNoticeId: String? = null
    private val autoHideRunnable = Runnable { val id=currentNoticeId; hide(); id?.let(NewOrderAlerts::dismiss) }

    fun show(notice: OrderNotice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
            hide()
            return
        }

        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(notice) }
            return
        }

        hide()

        val wm = windowManager ?: return
        val dm = appContext.resources.displayMetrics
        val density = dm.density
        val screenWidth = dm.widthPixels

        val cardWidth = Math.min((340 * density).toInt(), screenWidth - (32 * density).toInt())
        val topMargin = (120 * density).toInt()

        val rootLayout = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            val padding16 = (16 * density).toInt()
            setPadding(padding16, padding16, padding16, padding16)
            background = GradientDrawable().apply {
                setColor(AndroidColor.WHITE)
                cornerRadius = 16 * density
                setStroke((2 * density).toInt(), AndroidColor.parseColor("#00796B"))
            }
        }

        val titleView = TextView(appContext).apply {
            text = "宜捷行 · 发现新订单"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(AndroidColor.parseColor("#00796B"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        rootLayout.addView(titleView)

        val originView = TextView(appContext).apply {
            text = "起：${notice.origin}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(AndroidColor.parseColor("#212121"))
            maxLines = 2
            setPadding(0, (8 * density).toInt(), 0, (4 * density).toInt())
        }
        rootLayout.addView(originView)

        val destView = TextView(appContext).apply {
            text = "终：${notice.destination}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(AndroidColor.parseColor("#212121"))
            maxLines = 2
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        rootLayout.addView(destView)

        val buttonBar = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val btnClose = Button(appContext).apply {
            text = "关闭"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(AndroidColor.parseColor("#757575"))
            background = null
            setOnClickListener {
                NewOrderAlerts.dismiss(notice.id)
                hide()
            }
        }
        buttonBar.addView(btnClose)

        val btnOpen = Button(appContext).apply {
            text = "查看订单"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(AndroidColor.WHITE)
            background = GradientDrawable().apply {
                setColor(AndroidColor.parseColor("#00796B"))
                cornerRadius = 8 * density
            }
            setOnClickListener {
                NewOrderAlerts.open(notice.id)
                hide()
                try {
                    val intent = Intent(appContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    }
                    appContext.startActivity(intent)
                } catch (e: Exception) {}
            }
        }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            (40 * density).toInt()
        ).apply {
            leftMargin = (8 * density).toInt()
        }
        buttonBar.addView(btnOpen, btnParams)
        rootLayout.addView(buttonBar)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topMargin
        }

        try {
            wm.addView(rootLayout, params)
            currentView = rootLayout
            currentNoticeId = notice.id
            android.util.Log.i("YJXOrderOverlay", "shown:"+notice.id)
            mainHandler.postDelayed(autoHideRunnable, 20000L)
        } catch (e: Exception) {
            android.util.Log.w("YJXOrderOverlay", "not-shown:"+e.javaClass.simpleName)
            currentNoticeId = null
            currentView = null
        }
    }

    fun hide() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide() }
            return
        }
        mainHandler.removeCallbacks(autoHideRunnable)
        currentNoticeId = null
        val view = currentView
        if (view != null) {
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {}
            currentView = null
        }
    }

    fun close() {
        hide()
        mainHandler.removeCallbacksAndMessages(null)
    }
}
