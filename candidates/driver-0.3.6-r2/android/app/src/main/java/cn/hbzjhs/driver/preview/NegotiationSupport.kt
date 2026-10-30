package cn.hbzjhs.driver.preview

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun NegotiationSupport(
    order: DriverOrder,
    store: ConnectedStore,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val busy by store.loading.collectAsState()
    val statePayload by store.payload.collectAsState()
    // Bearer invitations deliberately do not use rememberSaveable, preferences, files or logs.
    var offer by remember(order.id) { mutableStateOf<PairOffer?>(null) }
    var deadline by remember(order.id) { mutableLongStateOf(0L) }
    var remaining by remember(order.id) { mutableLongStateOf(0L) }
    var pairStarted by rememberSaveable(order.id) { mutableStateOf(false) }
    var pairExpired by remember(order.id) { mutableStateOf(false) }
    var pairingError by remember(order.id) { mutableStateOf<String?>(null) }
    var confirmPair by remember(order.id) { mutableStateOf(false) }
    var quoteOpen by remember(order.id) { mutableStateOf(false) }
    var shownQuote by remember(order.id) { mutableStateOf<RequoteSnapshot?>(null) }
    var quoteInput by rememberSaveable(order.id) { mutableStateOf("") }
    var quoteError by remember(order.id) { mutableStateOf<String?>(null) }

    DisposableEffect(owner, order.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                offer = null
                if (pairStarted) pairingError = "为保护短期邀请，后台时已隐藏配对码。请刷新订单，未认领时可重新生成。"
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(order.shared, order.stage, order.paid) {
        if (order.shared || !order.canPairWithPassenger) {
            offer = null
            confirmPair = false
        }
        if (!order.canRequote) quoteOpen = false
    }
    LaunchedEffect(offer) {
        while (offer != null) {
            remaining =
                ((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L) + 999L) / 1000L
            if (remaining <= 0L) {
                pairExpired = true
                offer = null
                break
            }
            delay(500L)
        }
    }
    ProtectPairingInvitation(offer)

    SheetCard {
        Text(
            "线下议价 · ${if (order.shared) "已与乘客配对" else "乘客配对"}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "报价 ¥ ${money(order.fare)}${if (order.quoteVersion > 0) " · 第 ${order.quoteVersion} 版" else ""}",
            color = Green,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            "支付均为模拟体验，不会实际收款。",
            fontSize = 12.sp,
            color = Muted,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (order.canPairWithPassenger) {
            val enabledPairing =
                pairStarted || order.pairingRequired || order.pairingStatus.isNotBlank()
            Text(
                if (enabledPairing) "等待现场乘客使用乘客 App 认领本单，再核对报价并模拟支付。"
                else "与现场乘客使用乘客 App 配对后，双方会共享同一订单；此操作需要确认。未启用配对的旧演示单保持原流程。",
                fontSize = 13.sp,
                color = Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
            val invitation = offer
            if (invitation != null && remaining > 0L) {
                val bitmap =
                    remember(invitation) {
                        runCatching {
                                val matrix = NegotiationContract.qrMatrix(invitation)
                                val pixels =
                                    IntArray(matrix.width * matrix.height) { index ->
                                        if (matrix[index % matrix.width, index / matrix.width])
                                            android.graphics.Color.BLACK
                                        else android.graphics.Color.WHITE
                                    }
                                Bitmap.createBitmap(
                                        pixels,
                                        matrix.width,
                                        matrix.height,
                                        Bitmap.Config.ARGB_8888,
                                    )
                                    .asImageBitmap()
                            }
                            .getOrNull()
                    }
                Column(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (bitmap != null)
                        Image(
                            bitmap,
                            "现场乘客一次配对二维码",
                            Modifier.size(220.dp).background(Color.White, RoundedCornerShape(8.dp)),
                        )
                    else Text("二维码生成失败，可复制下方配对码。", color = MaterialTheme.colorScheme.error)
                    Text(
                        "剩余 ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')} · 一次有效",
                        color = Green,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        invitation.pairCode,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "仅向现场乘客出示，请勿截图或公开分享。",
                        fontSize = 12.sp,
                        color = Muted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            copyInvitation(context, invitation.pairCode)
                            onMessage("已复制短期配对码，请仅发送给现场乘客")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("复制配对码")
                    }
                    OutlinedButton(
                        onClick = {
                            copyInvitation(context, invitation.pairUri)
                            onMessage("已复制短期配对链接")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("复制链接")
                    }
                }
            } else if (
                pairExpired ||
                    order.pairingStatus == "EXPIRED" ||
                    runCatching {
                            java.time.Instant.parse(order.pairingExpiresAt) <=
                                java.time.Instant.parse(statePayload.optString("serverTime"))
                        }
                        .getOrDefault(false)
            ) {
                Text(
                    "配对码已过期，请重新生成；旧码不能认领。",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else if (order.pairingRequired && invitation == null) {
                Text(
                    "本单已启用乘客配对，恢复页面不会回显邀请凭证。请先刷新认领状态；未认领时可重新生成一次码。",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            pairingError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                if (busy) "正在处理…" else if (enabledPairing) "重新生成配对码" else "与乘客 App 配对",
                enabled = !busy,
            ) {
                confirmPair = true
            }
            DriverTextButton(
                onClick = { store.refresh() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("刷新乘客认领状态")
            }
        } else if (order.shared) {
            Text(
                when {
                    order.stage == Stage.CANCELLED -> "本次配对议价已取消，不能付款或改价。"
                    order.paid -> "乘客已确认报价并完成模拟支付，行程状态由服务端同步。"
                    order.negotiationStatus == "DISPUTED" -> "乘客对价格提出异议。重新报价后，请乘客核对最新版本并确认模拟支付。"
                    order.stage == Stage.PREPAY ->
                        "报价已发给乘客。请乘客在乘客 App 核对第 ${order.quoteVersion} 版报价并确认模拟支付。"
                    else -> "两端共享同一订单，付款结果与行程状态由服务端同步。"
                },
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            order.priceDisputes.takeLast(3).asReversed().forEach { dispute ->
                Text(
                    "第 ${dispute.quoteVersion} 版价格异议：${dispute.content}",
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                if (dispute.createdAt.isNotBlank())
                    Text(dispute.createdAt, fontSize = 11.sp, color = Muted)
            }
            if (order.canRequote) {
                Spacer(Modifier.height(14.dp))
                PrimaryButton("重新报价", enabled = !busy) {
                    runCatching { NegotiationContract.snapshot(order) }
                        .onSuccess {
                            shownQuote = it
                            quoteError = null
                            quoteOpen = true
                        }
                        .onFailure { onMessage(it.message ?: "报价版本缺失，请刷新订单") }
                }
            }
            DriverTextButton(
                onClick = { store.refresh() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("刷新共享订单")
            }
        }
    }

    if (confirmPair && order.canPairWithPassenger)
        DriverDialog(
            onDismissRequest = { if (!busy) confirmPair = false },
            title = {
                Text(if (pairStarted || order.pairingRequired) "重新生成一次配对码" else "启用乘客 App 配对")
            },
            text = { Text("本单将等待乘客 App 认领和模拟支付，原沙箱付款不能再替代乘客。新码有效 5 分钟，重新生成会撤销旧码。请只向现场乘客出示。") },
            confirmButton = {
                DriverTextButton(
                    enabled = !busy,
                    onClick = {
                        confirmPair = false
                        offer = null
                        pairingError = null
                        pairExpired = false
                        store.perform(
                            "order.pairOffer",
                            JSONObject().put("id", order.id),
                            onFailure = { pairingError = "$it。生成结果尚未确认，请重试恢复本次操作；不要继续使用旧码。" },
                        ) { data ->
                            // A confirmed server response opts in; a failed request must not invent it.
                            pairStarted = true
                            runCatching { NegotiationContract.parsePairOffer(data, order.id) }
                                .onSuccess {
                                    if (
                                        owner.lifecycle.currentState.isAtLeast(
                                            Lifecycle.State.STARTED
                                        )
                                    ) {
                                        deadline =
                                            SystemClock.elapsedRealtime() +
                                                it.remainingAtReceipt * 1000L
                                        remaining = it.remainingAtReceipt
                                        offer = it
                                    } else pairingError = "配对信息已隐藏，请刷新订单或重新生成。"
                                }
                                .onFailure { pairingError = it.message ?: "配对信息解析失败，请重试" }
                        }
                    },
                ) {
                    Text("确认生成")
                }
            },
            dismissButton = { DriverTextButton(onClick = { confirmPair = false }) { Text("暂不开启") } },
        )

    if (quoteOpen && shownQuote != null && order.canRequote)
        DriverDialog(
            onDismissRequest = { if (!busy) quoteOpen = false },
            title = { Text("重新报价 · 第 ${shownQuote!!.version} 版") },
            text = {
                Column {
                    Text(
                        "已展示报价：¥ ${money(shownQuote!!.amount)}。新报价需乘客重新确认，支付为模拟。",
                        fontSize = 13.sp,
                        color = Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        quoteInput,
                        {
                            quoteInput = it
                            quoteError = null
                        },
                        label = { Text("新报价（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = quoteError != null,
                        supportingText = { quoteError?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (
                        order.quoteVersion != shownQuote!!.version ||
                            order.fare != shownQuote!!.amount
                    ) {
                        Text(
                            "云端已有第 ${order.quoteVersion} 版：¥ ${money(order.fare)}。输入金额已保留，请先核对。",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        DriverTextButton(
                            onClick = {
                                runCatching { NegotiationContract.snapshot(order) }
                                    .onSuccess {
                                        shownQuote = it
                                        quoteError = null
                                    }
                                    .onFailure { quoteError = it.message }
                            }
                        ) {
                            Text("已核对，使用最新报价版本")
                        }
                    }
                    DriverTextButton(onClick = { store.refresh() }, enabled = !busy) { Text("刷新订单，保留输入") }
                }
            },
            confirmButton = {
                DriverTextButton(
                    enabled = !busy,
                    onClick = {
                        val latest = store.state.value.orders.firstOrNull { it.id == order.id }
                        runCatching {
                                NegotiationContract.requotePayload(
                                    requireNotNull(latest) { "订单已不在列表，请刷新" },
                                    shownQuote!!,
                                    quoteInput,
                                )
                            }
                            .onSuccess { payload ->
                                store.perform(
                                    "order.requote",
                                    payload,
                                    onFailure = { quoteError = "$it（输入已保留，请刷新核对）" },
                                ) {
                                    quoteOpen = false
                                    quoteInput = ""
                                    quoteError = null
                                    onMessage("新报价已同步，等待乘客核对并确认模拟支付")
                                }
                            }
                            .onFailure { quoteError = it.message ?: "新报价无效" }
                    },
                ) {
                    Text(if (busy) "提交中…" else "提交新报价")
                }
            },
            dismissButton = {
                DriverTextButton(enabled = !busy, onClick = { quoteOpen = false }) { Text("保留输入，暂不报价") }
            },
        )
}

@Composable
internal fun SharedParticipationNotice(order: DriverOrder) {
    if (
        !order.shared ||
            !(order.canonicalStage == "WAIT_CONTINUE" ||
                order.participationCancelled && order.canonicalStage !in setOf("", "CANCELLED"))
    )
        return
    SheetCard {
        Text(
            if (order.participationCancelled) "你已退出本次接驾" else "乘客正在决定是否继续叫车",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            if (order.canonicalStage == "WAIT_CONTINUE")
                "乘客侧订单正在等待乘客决定继续叫车或取消。你不能继续推进该次接驾，后续状态以服务端同步为准。"
            else "本人接驾已取消。乘客侧当前状态：${canonicalStageLabel(order.canonicalStage)}，请勿继续操作旧接驾。",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

private fun canonicalStageLabel(stage: String): String =
    when (stage) {
        "AVAILABLE" -> "已重新叫车，等待司机"
        "WAIT_CONTINUE" -> "等待乘客选择继续或取消"
        "CANCELLED" -> "已取消"
        "DONE" -> "已完成"
        else -> "已更新"
    }

private fun copyInvitation(context: Context, value: String) {
    val clip = ClipData.newPlainText("宜捷行短期配对邀请", value)
    if (Build.VERSION.SDK_INT >= 33)
        clip.description.extras =
            PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

@Composable
private fun ProtectPairingInvitation(offer: PairOffer?) {
    val context = LocalContext.current
    DisposableEffect(offer) {
        val activity = context.negotiationActivity()
        val alreadySecure =
            activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        if (offer != null) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (offer != null && !alreadySecure)
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            if (offer != null)
                runCatching {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    val value =
                        clipboard.primaryClip
                            ?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)
                            ?.text
                            ?.toString()
                    if (value == offer.pairCode || value == offer.pairUri) {
                        if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
                        else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
        }
    }
}

private tailrec fun Context.negotiationActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> if (baseContext !== this) baseContext.negotiationActivity() else null
        else -> null
    }
