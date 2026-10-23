package cn.hbzjhs.driver.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
internal fun ColumnScope.MembershipSubscriptionsScreen(
    page: String,
    snapshotId: String,
    store: ConnectedStore,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val path = "/api/benefits/subscriptions"
    val resource = rememberMembershipResource(store, path, page + snapshotId)
    var renewalTarget by remember { mutableStateOf<JSONObject?>(null) }
    var renewing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    MembershipLoad(resource, { resource.reload(store, path) }) { response ->
        val active = response.optJSONObject("active")
        val history = mObjects(response.optJSONArray("history"))
        val orders = mObjects(response.optJSONArray("orders"))
        val invoices = mObjects(response.optJSONArray("invoices"))
        val subscriptions = (listOfNotNull(active) + history).distinctBy { it.mText("id") }
        val snapshotRecord =
            subscriptions.firstOrNull {
                it.mText("id") == snapshotId || it.mText("orderId") == snapshotId
            }
                ?: orders.firstOrNull {
                    it.mText("id") == snapshotId || it.mText("subscriptionId", "") == snapshotId
                }
        if (page == "snapshot") {
            MembershipSnapshotScreen(snapshotRecord, navigate)
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                if (page == "subscriptions") {
                    val activeRows = subscriptions.filter { it.mText("status") == "ACTIVE" }
                    if (activeRows.isEmpty())
                        item {
                            MembershipEmpty(
                                "尚未开通付费会员",
                                "开通后可查看生效地区、到期时间、成交权益和续费状态。历史订单、钱包、提现和已审核资质继续保留。",
                                "选择会员等级",
                            ) {
                                navigate(mRoute("city"))
                            }
                        }
                    activeRows.forEach { subscription ->
                        val snapshot = mSnapshot(subscription)
                        item {
                            MembershipHero(gold = subscription.mText("level") == "VIP") {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Glyph("gift", Color.White, Modifier.size(34.dp))
                                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
                                        Text(
                                            subscription.mText(
                                                "levelName",
                                                subscription.mText("level"),
                                            ),
                                            color = Color.White,
                                            fontSize = 23.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "${subscription.mText("cityName")} · 生效中",
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(top = 6.dp),
                                        )
                                    }
                                    Pill("在期会员")
                                }
                                Row(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                                    MembershipStat(
                                        subscription.mNumber("daysRemaining", " 天"),
                                        "剩余有效期",
                                        Modifier.weight(1f),
                                    )
                                    MembershipStat(
                                        snapshot.mNumber("rightsCount", " 项"),
                                        "成交权益",
                                        Modifier.weight(1f),
                                    )
                                }
                                Text(
                                    "${mDate(subscription.mText("startAt", ""))} 至 ${mDate(subscription.mText("endAt", ""))}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(top = 18.dp),
                                )
                            }
                        }
                        item {
                            SheetCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                        Text("沙箱自动续费状态", fontWeight = FontWeight.SemiBold)
                                        Text(
                                            if (subscription.optBoolean("autoRenew")) "已主动开启演示授权状态"
                                            else "未开启，不安排沙箱续费",
                                            color = Muted,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(top = 5.dp),
                                        )
                                    }
                                    Switch(
                                        subscription.optBoolean("autoRenew"),
                                        onCheckedChange = { renewalTarget = subscription },
                                        enabled = !renewing,
                                    )
                                }
                                if (subscription.optBoolean("autoRenew"))
                                    Text(
                                        "服务端下次计划时间：${mDate(subscription.mText("nextChargeAt", ""), true)}\n仅用于联调，不产生真实签约和自动扣款。",
                                        color = Muted,
                                        fontSize = 16.sp,
                                        lineHeight = 24.sp,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                else
                                    Text(
                                        "关闭续费不会缩短当前已付费的会员有效期。",
                                        color = Muted,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                HorizontalDivider(
                                    color = Line,
                                    modifier = Modifier.padding(vertical = 12.dp),
                                )
                                DetailRow(
                                    "地图服务额度",
                                    snapshot.optJSONObject("mapQuota")?.mText("label") ?: "见权益快照",
                                )
                                DetailRow("成交金额", mMoney(subscription.mLong("amount")))
                                MembershipVersionDetail(
                                    subscription.mText("version", snapshot.mText("version"))
                                )
                                MenuRow("shield", "查看完整成交权益", "购买时快照") {
                                    navigate(mRoute("snapshot", id = subscription.mText("id")))
                                }
                                HorizontalDivider(color = Line)
                                MenuRow("card", "申请电子发票", "沙箱申请") {
                                    navigate(
                                        mRoute("invoice", order = subscription.mText("orderId"))
                                    )
                                }
                            }
                        }
                    }
                    item { SectionTitle("订阅管理") }
                    item {
                        SheetCard {
                            MenuRow("orders", "开通与发票记录", "全部支付状态") { navigate(mRoute("records")) }
                            HorizontalDivider(color = Line)
                            MenuRow("doc", "司机会员服务协议") { navigate(mRoute("agreement")) }
                            HorizontalDivider(color = Line)
                            MenuRow("doc", "自动续费说明") { navigate(mRoute("renewal-rules")) }
                            HorizontalDivider(color = Line)
                            MenuRow("location", "浏览其他城市等级", "不改变已有订阅") { navigate(mRoute("city")) }
                        }
                    }
                    val expired = subscriptions.filter { it.mText("status") != "ACTIVE" }
                    if (expired.isNotEmpty()) item { SectionTitle("历史订阅") }
                    expired.forEach { subscription ->
                        item {
                            SheetCard {
                                Text(
                                    "${subscription.mText("levelName")} · ${subscription.mText("cityName")}",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                DetailRow("状态", mStatus(subscription.mText("status")))
                                DetailRow("成交金额", mMoney(subscription.mLong("amount")))
                                DetailRow("有效期至", mDate(subscription.mText("endAt", "")))
                                TextButton(
                                    onClick = {
                                        navigate(mRoute("snapshot", id = subscription.mText("id")))
                                    }
                                ) {
                                    Text("查看历史权益快照")
                                }
                            }
                        }
                    }
                } else {
                    item {
                        MembershipNotice("订单与快照可追溯", "成交金额、地区和权益版本随订单保留。失败或取消不生成生效会员；未完成订单可以继续支付。")
                    }
                    item { SectionTitle("会员开通订单", "共 ${orders.size} 笔") }
                    if (orders.isEmpty())
                        item {
                            MembershipEmpty("暂无开通记录", "成功、待支付、失败及取消的订单都会记录在此。", "去选择会员") {
                                navigate(mRoute("city"))
                            }
                        }
                    orders.forEach { order ->
                        item {
                            SheetCard {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            order.mText("levelName", order.mText("level")),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            order.mText("cityName"),
                                            fontSize = 16.sp,
                                            color = Muted,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                    Pill(
                                        mStatus(order.mText("status")),
                                        if (mPaid(order)) Green else Orange,
                                        if (mPaid(order)) Pale else MembershipGoldPale,
                                    )
                                }
                                DetailRow("成交金额", mMoney(order.mLong("amount")))
                                DetailRow("下单时间", mDate(order.mText("createdAt", ""), true))
                                MembershipVersionDetail(order.mText("version"))
                                Text(order.mText("id"), color = Muted, fontSize = 16.sp)
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            navigate(mRoute("payment", order = order.mText("id")))
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            if (mPaid(order)) "支付详情" else "继续 / 重试",
                                            fontSize = 16.sp,
                                        )
                                    }
                                    if (mPaid(order))
                                        OutlinedButton(
                                            onClick = {
                                                navigate(
                                                    mRoute(
                                                        "snapshot",
                                                        id =
                                                            order.mText(
                                                                "subscriptionId",
                                                                order.mText("id"),
                                                            ),
                                                    )
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text("权益快照", fontSize = 16.sp)
                                        }
                                }
                                if (mPaid(order))
                                    TextButton(
                                        onClick = {
                                            navigate(mRoute("invoice", order = order.mText("id")))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("申请电子发票（沙箱）", fontSize = 16.sp)
                                    }
                            }
                        }
                    }
                    item { SectionTitle("发票申请记录", "共 ${invoices.size} 条 · 不开具真实发票") }
                    if (invoices.isEmpty())
                        item { MembershipEmpty("暂无发票申请", "在已支付的会员订单中填写抬头信息，可提交云端沙箱申请。") }
                    invoices.forEach { invoice ->
                        item {
                            SheetCard {
                                Text(
                                    invoice.mText("title"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                DetailRow("申请状态", mStatus(invoice.mText("status")), Green)
                                DetailRow("订单金额", mMoney(invoice.mLong("amount")))
                                if (invoice.mText("taxNumber", "").isNotBlank())
                                    DetailRow("纳税人识别号", invoice.mText("taxNumber"))
                                Text(
                                    "申请编号 ${invoice.mText("id")}\n订单 ${invoice.mText("orderId")}\n${mDate(invoice.mText("createdAt", ""), true)}",
                                    color = Muted,
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                )
                                Text(
                                    "仅保存沙箱申请，不代表真实发票开具。",
                                    color = Orange,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                }
                error?.let { message -> item { MembershipNotice("续费状态未更新", message, true) } }
                item {
                    OutlinedButton(
                        onClick = { resource.reload(store, path) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !resource.busy && !renewing,
                    ) {
                        Text(if (resource.busy) "刷新中…" else "刷新服务端记录")
                    }
                }
            }
        }
    }
    renewalTarget?.let { subscription ->
        val enabled = !subscription.optBoolean("autoRenew")
        AlertDialog(
            onDismissRequest = { if (!renewing) renewalTarget = null },
            title = { Text(if (enabled) "主动开启沙箱续费授权？" else "关闭自动续费？") },
            text = {
                Text(
                    if (enabled) "仅记录演示授权状态，不产生真实扣款。正式续费价格、通知与扣款规则需上线前确认，可随时关闭。"
                    else
                        "关闭后不再安排后续沙箱续费，已购买的会员权益仍可使用到 ${mDate(subscription.mText("endAt", ""))}，不会缩短有效期。",
                    fontSize = 14.sp,
                    lineHeight = 24.sp,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !renewing,
                    onClick = {
                        renewing = true
                        error = null
                        store.perform(
                            "benefits.renewal",
                            JSONObject()
                                .put("subscriptionId", subscription.mText("id"))
                                .put("enabled", enabled),
                            onFailure = { message ->
                                renewing = false
                                error = message
                                renewalTarget = null
                            },
                        ) {
                            renewing = false
                            renewalTarget = null
                            resource.reload(store, path)
                            onMessage(if (enabled) "沙箱续费状态已开启" else "续费已关闭，当前有效期保留")
                        }
                    },
                ) {
                    Text(if (renewing) "提交中…" else if (enabled) "确认主动开启" else "确认关闭续费")
                }
            },
            dismissButton = {
                TextButton(enabled = !renewing, onClick = { renewalTarget = null }) { Text("返回") }
            },
        )
    }
}

@Composable
private fun ColumnScope.MembershipSnapshotScreen(record: JSONObject?, navigate: (String) -> Unit) {
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        if (record == null)
            item {
                MembershipEmpty("未找到成交快照", "请选择已支付订单或订阅查看购买时的权益记录。", "查看开通记录") {
                    navigate(mRoute("records"))
                }
            }
        else {
            val snapshot = mSnapshot(record)
            item { MembershipNotice("购买时的权益快照", "此页使用成交版本；浏览其他城市或后续调价不会改写本页的成交金额和权益。") }
            item {
                SheetCard {
                    DetailRow("订阅等级", record.mText("levelName", snapshot.mText("name")))
                    DetailRow("生效地区", record.mText("cityName"))
                    DetailRow("成交金额", mMoney(record.mLong("amount")))
                    MembershipVersionDetail(record.mText("version", snapshot.mText("version")))
                    DetailRow("配置来源", mSource(snapshot.mText("source", record.mText("source"))))
                    DetailRow("权益总数", snapshot.mNumber("rightsCount", " 项"))
                    if (snapshot.optBoolean("dictionaryIncomplete"))
                        Text(
                            snapshot.mText("rightsCountNotice"),
                            color = Muted,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                        )
                    DetailRow("开始日期", mDate(record.mText("startAt", "")))
                    DetailRow("结束日期", mDate(record.mText("endAt", "")))
                    Text(
                        snapshot.optJSONObject("mapQuota")?.mText("label") ?: "地图额度未提供",
                        color = Green,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            mRights(snapshot)
                .groupBy { it.mText("group", "等级权益") }
                .forEach { (group, rights) ->
                    item { SectionTitle(group) }
                    item {
                        SheetCard {
                            rights.forEachIndexed { index, right ->
                                if (index > 0)
                                    HorizontalDivider(
                                        color = Line,
                                        modifier = Modifier.padding(vertical = 9.dp),
                                    )
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Glyph(
                                        if (right.optBoolean("enabled")) "check" else "close",
                                        if (right.optBoolean("enabled")) Green else Muted,
                                        Modifier.size(18.dp),
                                    )
                                    Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                        Text(
                                            right.mText("label"),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            if (right.optBoolean("enabled"))
                                                right.mText("value", "支持")
                                            else "当前成交版本未开放",
                                            color = Muted,
                                            fontSize = 16.sp,
                                            lineHeight = 24.sp,
                                            modifier = Modifier.padding(top = 5.dp),
                                        )
                                        Text(
                                            "来源：${mSource(right.mText("source", snapshot.mText("source")))}",
                                            color = Muted,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            if (mRights(snapshot).isEmpty())
                item { MembershipEmpty("暂无可见权益明细", "服务端未提供该成交记录的权益字典，可联系客服核查。") }
        }
    }
}
