package cn.hbzjhs.driver.preview

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import org.json.JSONObject

internal fun mOrder(raw: JSONObject): JSONObject =
    raw.optJSONObject("order") ?: mResult(raw).optJSONObject("order") ?: raw

internal fun mPaid(order: JSONObject): Boolean =
    order.mText("status").uppercase() in setOf("SUCCESS", "PAID")

internal fun mEntitlementsActive(order: JSONObject): Boolean =
    mPaid(order) && order.mText("entitlementsStatus", "PENDING").uppercase() == "ACTIVE"

internal fun mMethod(method: String): String =
    when (method) {
        "WECHAT" -> "微信支付（沙箱）"
        "ALIPAY" -> "支付宝（沙箱）"
        else -> method
    }

@Composable
internal fun ColumnScope.MembershipCheckoutScreen(
    catalog: JSONObject,
    initialLevel: String,
    store: ConnectedStore,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
    reloadCatalog: () -> Unit,
) {
    val city = catalog.optJSONObject("city") ?: JSONObject()
    val cityId = city.mText("id", "")
    var selected by rememberSaveable(cityId, initialLevel) { mutableStateOf(initialLevel) }
    var agreed by rememberSaveable(cityId, catalog.mText("version")) { mutableStateOf(false) }
    var autoRenew by rememberSaveable(cityId, catalog.mText("version")) { mutableStateOf(false) }
    var renewalDialog by remember { mutableStateOf(false) }
    var method by rememberSaveable { mutableStateOf("WECHAT") }
    var submitting by remember { mutableStateOf(false) }
    var activePage by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { activePage = false } }
    var failure by remember { mutableStateOf<String?>(null) }
    var idempotencyKey by
        rememberSaveable(cityId, selected, catalog.mText("version"), method, autoRenew) {
            mutableStateOf(UUID.randomUUID().toString())
        }
    val level = mLevel(catalog, selected)
    val existing = catalog.optJSONObject("subscription")
    val hasActive = existing != null && existing.mText("status", "ACTIVE") == "ACTIVE"
    val canBuy =
        level?.optBoolean("saleEnabled") == true && level.mLong("price") != null && !hasActive
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MembershipNotice(
                "下单地区：${city.mText("name")}",
                "请核对会员等级与年度应付金额，权益适用于所选城市。配置详情可在页面下方查看。",
            )
        }
        if (hasActive)
            item {
                MembershipNotice(
                    "该城市已有在期会员",
                    "${existing!!.mText("levelName")} 有效期至 ${mDate(existing.mText("endAt", ""))}。提前续费、升级及剩余期折算尚未开放；在期成交权益保留。",
                    true,
                )
                DriverTextButton(onClick = { navigate(mRoute("subscriptions")) }) { Text("管理已有订阅") }
            }
        item { SectionTitle("选择会员等级") }
        items(mLevels(catalog).count { mLevelId(it) != "TRIAL" }) { index ->
            val plan = mLevels(catalog).filter { mLevelId(it) != "TRIAL" }[index]
            val enabled = plan.optBoolean("saleEnabled")
            val chosen = mLevelId(plan) == selected
            Surface(
                shape = RoundedCornerShape(17.dp),
                color = if (chosen) Pale else Color.White,
                border =
                    androidx.compose.foundation.BorderStroke(
                        if (chosen) 1.5.dp else 1.dp,
                        if (chosen) Green else Line,
                    ),
                modifier =
                    Modifier.fillMaxWidth().clickable(enabled = enabled && !submitting) {
                        selected = mLevelId(plan)
                        failure = null
                    },
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        chosen,
                        onClick = {
                            selected = mLevelId(plan)
                            failure = null
                        },
                        enabled = enabled && !submitting,
                    )
                    Column(Modifier.weight(1f).padding(start = 5.dp, end = 8.dp)) {
                        Text(plan.mText("name"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${plan.mNumber("rightsCount", " 项权益")} · ${plan.optJSONObject("mapQuota")?.mText("label") ?: "地图参数未提供"}",
                            color = Muted,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                        if (!enabled) Text("本城市暂未开放", color = Orange, fontSize = 15.sp)
                    Text(
                        mMoney(plan.mLong("price")),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) Ink else Muted,
                    )
                    }

                }
            }
        }
        item { SectionTitle("套餐周期") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(
                    shape = RoundedCornerShape(13.dp),
                    color = Pale,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Green),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        Modifier.padding(vertical = 17.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("1 年", color = Green, fontWeight = FontWeight.Bold)
                        Text("年度套餐", color = Green, fontSize = 15.sp)
                    }
                }
                listOf("半年", "月度").forEach { period ->
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = Line,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            Modifier.padding(vertical = 17.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(period, color = Muted)
                            Text("暂未开放", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        item {
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("自动续费授权", fontWeight = FontWeight.SemiBold)
                        Text(
                            "默认关闭，可主动选择；本版仅保存沙箱状态，不签约、不扣款。",
                            color = Muted,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                    Switch(
                        autoRenew,
                        onCheckedChange = { if (it) renewalDialog = true else autoRenew = false },
                        enabled = !submitting,
                    )
                }
            }
        }
        item {
            SheetCard {
                DetailRow("${level?.mText("name") ?: "会员"}年度售价", mMoney(level?.mLong("price")))
                DetailRow("优惠与升级折算", "未自动使用")
                Text(
                    "首年价、优惠资格及升级剩余期折算待业务确认，当前按所选城市年度售价下单。",
                    color = Muted,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                )
                HorizontalDivider(color = Line, modifier = Modifier.padding(top = 12.dp))
                DetailRow("服务端报价", mMoney(level?.mLong("price")), Green)
            }
        }
        item { SectionTitle("支付方式") }
        item {
            SheetCard {
                listOf("WECHAT" to "微信支付", "ALIPAY" to "支付宝").forEachIndexed { index, (id, title) ->
                    if (index > 0) HorizontalDivider(color = Line)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !submitting) { method = id }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(
                            "wallet",
                            if (id == "WECHAT") Green else Color(0xFF3988C8),
                            Modifier.size(27.dp),
                        )
                        Column(Modifier.weight(1f).padding(start = 13.dp)) {
                            Text(title, fontSize = 15.sp)
                            Text("云端沙箱，不调用真实支付渠道", color = Muted, fontSize = 13.sp)
                        }
                        RadioButton(method == id, onClick = { method = id }, enabled = !submitting)
                    }
                }
                HorizontalDivider(color = Line)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Glyph("card", Muted, Modifier.size(27.dp))
                    Text(
                        "对公转账",
                        color = Muted,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f).padding(start = 13.dp),
                    )
                    Text("暂不支持", color = Muted, fontSize = 13.sp)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .clickable(enabled = !submitting) { agreed = !agreed }, verticalAlignment = Alignment.Top) {
                Checkbox(agreed, onCheckedChange = null, enabled = !submitting)
                Column(Modifier.weight(1f).padding(top = 9.dp)) {
                    Text("我已阅读并同意会员服务说明，知悉权益以所选地区和成交快照为准。", fontSize = 15.sp, lineHeight = 24.sp)
                    DriverTextButton(
                        onClick = { navigate(mRoute("agreement")) },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("查看《司机会员服务协议》", fontSize = 15.sp)
                    }
                    if (autoRenew)
                        DriverTextButton(
                            onClick = { navigate(mRoute("renewal-rules")) },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text("查看《自动续费说明》", fontSize = 15.sp)
                        }
                }
            }
        }
        failure?.let { reason ->
            item {
                MembershipNotice("未能创建订单", reason, true)
                DriverTextButton(
                    onClick = {
                        reloadCatalog()
                        failure = null
                    }
                ) {
                    Text("重新读取有效价格与可售状态")
                }
            }
        }
        item { MembershipVersionCard(catalog) }
    }
    MembershipFooter(
        level?.mLong("price"),
        if (submitting) "提交中…"
        else if (hasActive) "当前已有在期会员" else if (!canBuy) "本城市暂未开放" else "立即支付",
        canBuy && !submitting,
    ) {
        if (!agreed) {
            failure = "请先阅读并勾选会员服务协议。"
            onMessage("请先阅读并同意会员服务协议")
        } else {
            submitting = true
            failure = null
            val body =
                JSONObject()
                    .put("city", cityId)
                    .put("level", selected)
                    .put("version", catalog.opt("version"))
                    .put("agree", true)
                    .put("autoRenew", autoRenew)
                    .put("paymentMethod", method)
                    .put("idempotencyKey", idempotencyKey)
            store.perform(
                "benefits.orders",
                body,
                onFailure = { error ->
                    submitting = false
                    failure = error
                },
            ) { raw ->
                submitting = false
                val order = mResult(raw).optJSONObject("order")
                if (order == null || order.mText("id", "").isBlank()) failure = "服务端未返回订单编号，请刷新后重试。"
                else if (activePage) navigate(mRoute("payment", order = order.mText("id")))
            }
        }
    }
    if (renewalDialog)
        DriverDialog(
            onDismissRequest = { renewalDialog = false },
            title = { Text("开启沙箱续费授权？") },
            text = {
                Text(
                    "此选择只记录演示授权状态，不产生真实支付签约、扣款或财务承诺。正式续费价格、提前通知和扣款时间需在上线前确认。可在“我的订阅”关闭，关闭不会缩短已购买有效期。",
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                )
            },
            confirmButton = {
                DriverTextButton(
                    onClick = {
                        autoRenew = true
                        renewalDialog = false
                    }
                ) {
                    Text("确认主动开启")
                }
            },
            dismissButton = { DriverTextButton(onClick = { renewalDialog = false }) { Text("保持关闭") } },
        )
}

@Composable
internal fun ColumnScope.MembershipPaymentScreen(
    store: ConnectedStore,
    orderId: String,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    if (orderId.isBlank()) {
        Box(Modifier.weight(1f).padding(18.dp)) {
            MembershipEmpty("未选择支付订单", "从开通记录选择待支付订单，或重新选择会员等级。", "查看记录") {
                navigate(mRoute("records"))
            }
        }
        return
    }
    val path = "/api/benefits/orders/${android.net.Uri.encode(orderId)}"
    val resource = rememberMembershipResource(store, path)
    var paying by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable(orderId) { mutableStateOf(true) }
    var method by rememberSaveable(orderId) { mutableStateOf("") }
    MembershipLoad(resource, { resource.reload(store, path) }) { raw ->
        val order = mOrder(raw)
        val status = order.mText("status").uppercase()
        val active = mEntitlementsActive(order)
        val paid = mPaid(order)
        val snapshot = mSnapshot(order)
        val pending = status in setOf("PROCESSING", "PAYING")
        val title =
            if (active) "支付成功，权益已生效"
            else if (paid) "支付成功，权益开通中"
            else if (pending) "支付结果处理中"
            else
                when (status) {
                    "FAILED" -> "支付未成功"
                    "CANCEL",
                    "CANCELLED" -> "已取消本次支付"
                    else -> "等待支付"
                }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                MembershipHero(gold = !active) {
                    Glyph(
                        if (active) "check" else if (pending || paid) "settings" else "wallet",
                        Color.White,
                        Modifier.size(43.dp),
                    )
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        if (active) "对应城市会员已开通，可按等级权益接单。"
                        else if (paid) "请刷新确认开通结果；当前不宣称已恢复接单。"
                        else if (pending) "等待服务端确认，未确认前不会显示权益生效。" else "当前为云端体验沙箱，不会发起真实扣款。",
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                    Text(
                        "${if (paid) "实付" else "应付"} ${mMoney(order.mLong("amount"))}",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }
            }
            item {
                SheetCard {
                    DetailRow("开通等级", order.mText("levelName", order.mText("level")))
                    DetailRow("下单地区", order.mText("cityName"))
                    DetailRow("支付方式", mMethod(order.mText("paymentMethod")))
                    DetailRow("订单状态", mStatus(status))
                    DetailRow(
                        "权益状态",
                        if (active) "已生效" else if (paid) "待确认" else "未开通",
                        if (active) Green else Muted,
                    )
                    if (order.mText("startAt", "").isNotBlank())
                        DetailRow("会员开始", mDate(order.mText("startAt")))
                    if (order.mText("endAt", "").isNotBlank())
                        DetailRow("会员到期", mDate(order.mText("endAt")))
                    MembershipVersionDetail(order.mText("version"))
                    Text(
                        "订单编号 ${order.mText("id")}",
                        color = Muted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                }
            }
            if (active) {
                item { SectionTitle("已生效的成交权益") }
                item {
                    SheetCard {
                        DetailRow("开放权益", snapshot.mNumber("rightsCount", " 项"), Green)
                        if (snapshot.optBoolean("dictionaryIncomplete"))
                            Text(
                                snapshot.mText("rightsCountNotice"),
                                color = Muted,
                                fontSize = 15.sp,
                                lineHeight = 24.sp,
                            )
                        Text(
                            snapshot.optJSONObject("mapQuota")?.mText("label") ?: "地图额度见成交权益快照",
                            color = Green,
                            fontSize = 15.sp,
                        )
                        mRights(snapshot)
                            .filter { it.optBoolean("enabled") }
                            .take(5)
                            .forEach { right ->
                                Row(
                                    Modifier.padding(top = 12.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Glyph("check", Green, Modifier.size(16.dp))
                                    Text(
                                        right.mText("label") + " · " + right.mText("value", "支持"),
                                        fontSize = 15.sp,
                                        modifier = Modifier.padding(start = 7.dp),
                                    )
                                }
                            }
                    }
                }
                item { PrimaryButton("去接单") { navigate("home") } }
                item {
                    SheetCard {
                        MenuRow("doc", "我的订阅", "有效期与续费管理") { navigate(mRoute("subscriptions")) }
                        HorizontalDivider(color = Line)
                        MenuRow("card", "申请电子发票", "云端沙箱受理") {
                            navigate(mRoute("invoice", order = order.mText("id")))
                        }
                        HorizontalDivider(color = Line)
                        MenuRow("shield", "查看成交权益快照") {
                            navigate(
                                mRoute(
                                    "snapshot",
                                    id = order.mText("subscriptionId", order.mText("id")),
                                )
                            )
                        }
                    }
                }
            } else {
                item {
                    if (pending || paid) {
                        PrimaryButton(
                            if (resource.busy) "查询中…" else "刷新支付与权益结果",
                            enabled = !resource.busy && !paying,
                        ) {
                            resource.reload(store, path)
                        }
                        Text(
                            "沙箱中的“处理中”不会自动伪造成功，可返回沙箱收银台提交明确结果。",
                            color = Muted,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 9.dp),
                        )
                    }
                    if (!paid) {
                        PrimaryButton(
                            if (paying) "正在提交结果…"
                            else if (status in setOf("FAILED", "CANCEL", "CANCELLED")) "重试 / 更换支付方式"
                            else "打开沙箱收银台",
                            modifier = Modifier.padding(top = if (pending) 12.dp else 0.dp),
                            enabled = !paying,
                        ) {
                            sheet = true
                        }
                    }
                }
                item {
                    DriverTextButton(
                        onClick = { navigate(mRoute("records")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("返回开通记录，稍后继续")
                    }
                }
            }
            failure?.let { error ->
                item {
                    MembershipNotice("操作未完成", error, true)
                    DriverTextButton(
                        onClick = { navigate(mRoute("city", city = order.mText("city", ""))) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("按最新城市配置重新选购")
                    }
                }
            }
            item {
                MembershipNotice(
                    "支付与开通由服务端确认",
                    "重复提交同一支付结果不会重复开通。若价格或售卖状态发生变化，请返回选购页重新确认；系统不会自动扣旧金额。",
                )
            }
        }
        if (sheet && !paid && !paying)
            DriverDialog(
                onDismissRequest = { sheet = false },
                title = { Text("云端沙箱收银台") },
                text = {
                    Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                        Text(
                            "订单 ${order.mText("id")}\n应付 ${mMoney(order.mLong("amount"))}\n选择结果以验证完整支付状态流程，不会真实扣款。",
                            fontSize = 15.sp,
                            color = Muted,
                            lineHeight = 21.sp,
                        )
                        listOf("WECHAT" to "微信（沙箱）", "ALIPAY" to "支付宝（沙箱）").forEach { (value, label)
                            ->
                            Row(
                                Modifier.fillMaxWidth().clickable { method = value },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    (method.ifBlank { order.mText("paymentMethod") }) == value,
                                    onClick = { method = value },
                                )
                                Text(label, fontSize = 15.sp)
                            }
                        }
                        listOf(
                                "SUCCESS" to "模拟支付成功",
                                "PENDING" to "模拟结果处理中",
                                "FAILED" to "模拟支付失败",
                                "CANCEL" to "模拟取消支付",
                            )
                            .forEach { (outcome, label) ->
                                val action = {
                                    paying = true
                                    sheet = false
                                    failure = null
                                    store.perform(
                                        "benefits.pay",
                                        JSONObject()
                                            .put("orderId", orderId)
                                            .put("outcome", outcome)
                                            .put(
                                                "paymentMethod",
                                                method.ifBlank { order.mText("paymentMethod") },
                                            ),
                                        onFailure = { error ->
                                            paying = false
                                            failure = error
                                            resource.reload(store, path)
                                        },
                                    ) { _ ->
                                        paying = false
                                        resource.reload(store, path)
                                        onMessage(
                                            if (outcome == "SUCCESS") "服务端已确认沙箱支付结果"
                                            else "支付状态已保存，可查询或重试"
                                        )
                                    }
                                }
                                if (outcome == "SUCCESS")
                                    Button(onClick = action, modifier = Modifier.fillMaxWidth()) {
                                        Text(label)
                                    }
                                else
                                    OutlinedButton(
                                        onClick = action,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(label)
                                    }
                            }
                    }
                },
                confirmButton = { DriverTextButton(onClick = { sheet = false }) { Text("暂不支付") } },
            )
    }
}
