package cn.hbzjhs.driver.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
internal fun BusinessBlacklist(
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val busy by store.loading.collectAsState()
    var tail by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var remove by remember { mutableStateOf<JSONObject?>(null) }
    val entries = profile.bList("blacklist")
    BusinessPage("乘客黑名单", store, onBack, if (adding) "收起" else "添加", { adding = !adding }) {
        item { BusinessHint("从订单详情拉黑时按乘客账号屏蔽；手动添加时按手机尾号屏蔽，尾号相同的乘客都不会再派单给你。") }
        if (adding)
            item {
                SheetCard {
                    Text("添加屏蔽记录", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    BusinessField(
                        tail,
                        { tail = it.filter(Char::isDigit).take(4) },
                        "合成乘客手机尾号（4 位）",
                        numeric = true,
                    )
                    BusinessField(reason, { reason = it.take(100) }, "屏蔽原因", minLines = 2)
                    PrimaryButton(
                        "保存黑名单",
                        enabled = !busy && tail.length == 4 && reason.isNotBlank(),
                    ) {
                        store.perform(
                            "blacklist.add",
                            bJson("phoneTail" to tail, "reason" to reason.trim()),
                        ) {
                            adding = false
                            tail = ""
                            reason = ""
                            onMessage("黑名单已保存到演示服务")
                        }
                    }
                }
            }
        if (entries.isEmpty()) item { BusinessEmpty("暂无黑名单乘客") }
        items(entries, key = { it.bText("id") }) { entry ->
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "乘客尾号 ${entry.bText("phoneTail")}",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    BusinessTextButton(onClick = { remove = entry }, enabled = !busy) { Text("移除") }
                }
                Text(entry.bText("reason"), fontSize = 15.sp)
                Text(
                    entry.bText("createdAt"),
                    fontSize = 15.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    remove?.let { entry ->
        BusinessConfirm(
            "移除黑名单",
            "确认取消对尾号 ${entry.bText("phoneTail")} 的演示屏蔽？",
            "确认移除",
            { remove = null },
        ) {
            remove = null
            store.perform("blacklist.remove", bJson("id" to entry.bText("id"))) {
                onMessage("黑名单记录已移除")
            }
        }
    }
}

@Composable
internal fun BlockPassengerRow(orderId: String, store: ConnectedStore, onMessage: (String) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    MenuRow("face", "拉黑此乘客") { confirm = true }
    if (confirm)
        BusinessConfirm(
            "拉黑乘客",
            "拉黑后将不再收到该乘客账号的新订单，乘客不会收到通知。可在“乘客黑名单”中移除。",
            "确认拉黑",
            { confirm = false },
        ) {
            confirm = false
            store.perform("order.blockPassenger", bJson("id" to orderId, "reason" to "司机主动拉黑")) {
                onMessage("已拉黑，不再接收该乘客的新订单")
            }
        }
}

@Composable
private fun PointsMall(store: ConnectedStore, profile: JSONObject, balance: Long, onMessage: (String) -> Unit) {
    val busy by store.loading.collectAsState()
    val mall = profile.bObj("pointsMall")
    var redeem by remember { mutableStateOf<JSONObject?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("积分商城", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (mall.bList("products").isEmpty()) BusinessEmpty("暂无可兑换商品，上架后会显示在这里")
        mall.bList("products").forEach { product ->
            val cost = product.optLong("points")
            val stock = product.optInt("stock")
            SheetCard {
                Text(product.bText("name"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                if (product.optString("description").isNotBlank())
                    Text(product.optString("description"), fontSize = 15.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                DetailRow("所需积分", cost.toString(), Green)
                DetailRow("剩余可兑", "$stock 份")
                DetailRow("类型", if (product.optString("type") == "PHYSICAL") "实物 · 需填写收货信息" else "虚拟权益")
                PrimaryButton(
                    if (stock <= 0) "已兑完" else if (balance < cost) "积分不足" else "立即兑换",
                    enabled = !busy && stock > 0 && balance >= cost,
                ) { redeem = product }
            }
        }
        if (mall.bList("redemptions").isNotEmpty()) Text("兑换记录", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        mall.bList("redemptions").forEach { row ->
            SheetCard {
                DetailRow(row.bText("productName"), "-${row.optLong("points")} 积分")
                DetailRow(
                    "状态",
                    when (row.optString("status")) {
                        "PENDING_SHIPMENT" -> "待发货"
                        "SHIPPED" -> "已发货"
                        else -> "已完成"
                    },
                )
                if (row.optString("shipNote").isNotBlank()) DetailRow("物流", row.optString("shipNote"))
                DetailRow("兑换时间", row.bText("createdAt"))
            }
        }
        BusinessHint(mall.bText("notice", fallback = "积分不可提现或转让。"))
    }
    redeem?.let { product ->
        val physical = product.optString("type") == "PHYSICAL"
        val ready = !physical || (name.isNotBlank() && mobile.matches(Regex("1\\d{10}")) && address.trim().length >= 5)
        AlertDialog(
            onDismissRequest = { redeem = null },
            title = { Text("兑换 ${product.bText("name")}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("将扣除 ${product.optLong("points")} 积分，兑换后不可撤销。", fontSize = 15.sp)
                    if (physical) {
                        BusinessField(name, { name = it.take(30) }, "收货人")
                        BusinessField(mobile, { mobile = it.filter(Char::isDigit).take(11) }, "收货手机号", numeric = true)
                        BusinessField(address, { address = it.take(200) }, "收货地址", minLines = 2)
                    }
                }
            },
            confirmButton = {
                BusinessTextButton(enabled = !busy && ready, onClick = {
                    val payload = bJson("productId" to product.bText("id"))
                    if (physical) payload.put("receiverName", name.trim()).put("receiverMobile", mobile).put("receiverAddress", address.trim())
                    redeem = null
                    store.perform("points.redeem", payload) { onMessage("兑换成功") }
                }) { Text("确认兑换") }
            },
            dismissButton = { BusinessTextButton(onClick = { redeem = null }) { Text("取消") } },
        )
    }
}

@Composable
internal fun BusinessPoints(store: ConnectedStore, profile: JSONObject, onBack: () -> Unit, onMessage: (String) -> Unit) {
    val points = profile.bObj("points")
    val entries = points.bList("entries").ifEmpty { points.bList("records") }
    var selectedMonth by rememberSaveable { mutableStateOf("全部月份") }
    var monthMenu by remember { mutableStateOf(false) }
    val months =
        listOf("全部月份") +
            entries
                .map { it.bText("date", "createdAt", "time", fallback = "").take(7) }
                .filter { it.matches(Regex("\\d{4}-\\d{2}")) }
                .distinct()
                .sortedDescending()
    val filtered =
        entries.filter {
            selectedMonth == "全部月份" ||
                it.bText("date", "createdAt", "time", fallback = "").startsWith(selectedMonth)
        }
    BusinessPage("我的积分", store, onBack) {
        item {
            SheetCard {
                Text("当前积分余额", color = Muted, fontSize = 13.sp)
                Text(
                    points.optLong("balance").toString(),
                    fontSize = 46.sp,
                    color = Green,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                if (points.has("total")) DetailRow("累计获得", points.optLong("total").toString())
                BusinessHint("明细由服务端记账；完成订单后刷新可查看积分变化。")
            }
        }
        item { PointsMall(store, profile, points.optLong("balance"), onMessage) }
        item { Text("积分明细", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        item {
            Box {
                BusinessOutlinedButton(onClick = { monthMenu = true }) { Text("$selectedMonth ▾") }
                DropdownMenu(monthMenu, { monthMenu = false }) {
                    months.forEach { month ->
                        DropdownMenuItem(
                            text = { Text(month, fontSize = 15.sp) },
                            modifier = Modifier.heightIn(min = 52.dp),
                            onClick = {
                                selectedMonth = month
                                monthMenu = false
                            },
                        )
                    }
                }
            }
        }
        if (filtered.isEmpty()) item { BusinessEmpty("本月暂无积分记录", actionLabel = "刷新") { store.refresh() } }
        items(filtered) { entry -> BusinessLedgerRow(entry, "积分变动") }
    }
}

@Composable
internal fun BusinessServiceScore(store: ConnectedStore, profile: JSONObject, onBack: () -> Unit) {
    val score = profile.bObj("serviceScore")
    val entries = score.bList("entries").ifEmpty { score.bList("records") }
    BusinessPage("服务分明细", store, onBack) {
        item {
            SheetCard {
                Text("当前服务分", color = Muted)
                Text(
                    "${score.optInt("value", score.optInt("score"))} 分",
                    fontSize = 44.sp,
                    color = Green,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                Pill(score.bText("level", fallback = "演示服务评分"))
                Spacer(Modifier.height(14.dp))
                BusinessHint("评分及变动均读取演示服务记录；提交投诉或反馈不自动代表违规成立。")
            }
        }
        item { SheetCard {
            SectionTitle("服务分等级区间")
            score.bList("levels").forEach { level -> DetailRow(level.bText("name"), serviceScoreRangeText(level)) }
            if (score.bList("levels").isEmpty()) BusinessHint("等级区间待服务端同步")
        } }
        item { SectionTitle("评分变动记录") }
        if (entries.isEmpty()) item { BusinessEmpty("暂无评分变动", actionLabel = "刷新") { store.refresh() } }
        items(entries) { entry ->
            BusinessLedgerRow(entry, "服务分变动")
            if (entry.has("scoreAfter")) SheetCard { DetailRow("变动后分数", "${entry.optDouble("scoreAfter")} 分") }
        }
    }
}

@Composable
private fun BusinessLedgerRow(entry: JSONObject, fallback: String) {
    val delta =
        entry.optLong(
            "delta",
            entry.optLong("amount", entry.optLong("points", entry.optLong("score"))),
        )
    SheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    entry.bText("title", "reason", "sourceName", fallback = fallback),
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                )
                Text(
                    entry.bText("date", "createdAt", "time"),
                    fontSize = 15.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                "${if (delta > 0) "+" else ""}$delta",
                color = if (delta >= 0) Green else Orange,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun BusinessReferral(
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val referral = profile.bObj("referrals")
    val records = referral.bList("entries").ifEmpty { referral.bList("records") }
    val code = referral.bText("code", "inviteCode", fallback = "")
    val context = LocalContext.current
    BusinessPage("分享拉新", store, onBack) {
        item {
            SheetCard {
                Pill("模拟积分拉新")
                Spacer(Modifier.height(18.dp))
                Text("好服务，值得被分享", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(
                    code.ifBlank { "邀请码尚未生成" },
                    fontSize = 20.sp,
                    color = Green,
                    modifier = Modifier.padding(vertical = 24.dp),
                    fontWeight = FontWeight.Bold,
                )
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                            "邀请人数" to referral.optInt("invited", referral.optInt("count")),
                            "完成首单" to referral.optInt("completed"),
                            "奖励积分" to referral.optInt("awarded", referral.optInt("reward")),
                        )
                        .forEach { (title, value) ->
                            DetailRow(title, value.toString())
                        }
                }
                Spacer(Modifier.height(20.dp))
                PrimaryButton("复制邀请码", enabled = code.isNotBlank()) {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("宜捷行演示邀请码", code))
                    onMessage("演示邀请码已复制")
                }
                BusinessOutlinedButton(
                    onClick = {
                        runCatching {
                                val intent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "宜捷行司机邀请码：$code（模拟积分奖励，不产生真实现金奖励）",
                                        )
                                    }
                                context.startActivity(Intent.createChooser(intent, "选择分享方式"))
                            }
                            .onFailure { onMessage("设备暂无可用的分享应用，可复制邀请码") }
                    },
                    enabled = code.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("选择分享方式")
                }
            }
        }
        item { SectionTitle("拉新记录") }
        if (records.isEmpty()) item { BusinessEmpty("暂无拉新记录", actionLabel = "刷新") { store.refresh() } }
        items(records) { record ->
            SheetCard {
                DetailRow(
                    "受邀人",
                    record.bText("name", "phoneTail", "mobileMasked", fallback = "受邀用户"),
                )
                DetailRow("邀请时间", record.bText("date", "createdAt"))
                DetailRow(
                    "状态",
                    record.bText(
                        "status",
                        fallback = if (record.optBoolean("completed")) "已完成首单" else "待完成首单",
                    ),
                )
                if (record.has("points") || record.has("reward"))
                    DetailRow(
                        "奖励积分",
                        record.optLong("points", record.optLong("reward")).toString(),
                        Green,
                    )
            }
        }
        item {
            BusinessHint(
                driverReferralRuleText(referral.optJSONObject("rule"))
            )
        }
    }
}

@Composable
internal fun BusinessRanking(store: ConnectedStore, profile: JSONObject, onBack: () -> Unit) {
    var ranking by remember { mutableStateOf(JSONObject()) }
    var failure by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    var metric by rememberSaveable { mutableStateOf("orders") }
    var period by rememberSaveable { mutableStateOf("day") }
    val periods = if (metric == "orders") listOf("day", "month", "year") else listOf("day", "week", "month", "year")
    val periodNames = mapOf("day" to "日", "week" to "周", "month" to "月", "year" to "年")
    LaunchedEffect(revision, metric, period) {
        loaded = false
        failure = ""
        store.query(
            "/api/ranking?metric=$metric&period=$period",
            onFailure = {
                failure = it
                loaded = true
                ranking = JSONObject()
            },
        ) {
            ranking = bData(it)
            loaded = true
        }
    }
    val entries = ranking.bList("entries").ifEmpty { ranking.bList("records") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    BusinessPage("司机排行", store, onBack, "刷新", { revision++ }) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("orders" to "接单数", "online" to "在线时长").forEach { (key, label) ->
                        FilterChip(metric == key, {
                            metric = key
                            if (key == "orders" && period == "week") period = "day"
                        }, label = { Text(label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    periods.forEach { key ->
                        FilterChip(period == key, { period = key }, label = { Text(periodNames.getValue(key)) })
                    }
                }
            }
        }
        if (!loaded || failure.isNotBlank())
            item { BusinessEmpty(if (failure.isNotBlank()) failure else "正在校验权益并获取排行…") }
        if (loaded && failure.isBlank()) {
            item {
                SheetCard {
                    Pill(ranking.bText("period", fallback = "演示统计周期"))
                    Spacer(Modifier.height(14.dp))
                    DetailRow(
                        "我的名次",
                        if (ranking.optInt("myRank") > 0) "第 ${ranking.optInt("myRank")} 名"
                        else "暂无排名",
                        Green,
                    )
                    DetailRow(if (metric == "orders") "完成订单" else "在线时长", ranking.bText("myValueLabel"))
                    BusinessHint("${ranking.bText("cityName", fallback = "当前区域")} · ${ranking.bText("scope", fallback = "同区域司机排行")}")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部", "前 3 名").forEach { label ->
                        FilterChip(filter == label, { filter = label }, label = { Text(label) })
                    }
                }
            }
            if (entries.isEmpty()) item { BusinessEmpty("当前周期暂无排行") }
            items(if (filter == "前 3 名") entries.take(3) else entries) { entry ->
                SheetCard {
                    DetailRow(
                        "${entry.optInt("rank")}  ${entry.bText("name")}",
                        entry.bText("valueLabel"),
                        if (entry.optBoolean("isMe") || entry.optInt("rank") in 1..3) Green else Ink,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BusinessHelp(
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val support = profile.bObj("support")
    val faq = support.bList("faq")
    var open by rememberSaveable { mutableStateOf(-1) }
    BusinessPage("客服与帮助", store, onBack) {
        item {
            SheetCard {
                Glyph("support", modifier = Modifier.size(54.dp))
                Text(
                    "有问题，我们一起解决",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
                DetailRow(
                    "服务时间",
                    support.bText("serviceHours", "workingHours", fallback = "演示管理台处理"),
                )
                DriverCompanyContact(store)
                BusinessHint("公司电话由后台维护；反馈工单会保存，实时聊天及电话中继未接入。")
                Spacer(Modifier.height(14.dp))
                PrimaryButton("提交问题或查看回复") { navigate("feedback") }
                MenuRow("card", "提现和银行卡") { navigate("bank") }
                MenuRow("shield", "证件审核进度") { navigate("qualification") }
                MenuRow("gift", "区域司机权益") { navigate("benefits") }
            }
        }
        item { SectionTitle("常见问题") }
        if (faq.isEmpty()) item { BusinessEmpty("暂无帮助条目，可提交反馈工单。") }
        items(faq.size) { index ->
            val entry = faq[index]
            SheetCard(Modifier.clickable { open = if (open == index) -1 else index }) {
                Row {
                    Text(
                        entry.bText("question", "title"),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(if (open == index) "收起" else "展开", color = Green, fontSize = 15.sp)
                }
                if (open == index)
                    Text(
                        entry.bText("answer", "content"),
                        color = Muted,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
            }
        }
    }
}

@Composable
internal fun BusinessRules(
    route: String,
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    val rules = profile.bObj("rules")
    val selected = route.substringAfter('/', "")
    BusinessPage(
        if (selected == "privacy") "隐私政策" else if (selected == "terms") "服务协议" else "服务细则",
        store,
        onBack,
    ) {
        item {
            SheetCard {
                Text("宜捷行 · Android 原生版", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "云端独立体验版本",
                    color = Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                MenuRow("doc", "服务细则") { navigate("rules/detail") }
                MenuRow("shield", "隐私政策") { navigate("rules/privacy") }
                MenuRow("doc", "服务协议") { navigate("rules/terms") }
            }
        }
        item {
            SheetCard {
                Text(
                    if (selected == "privacy") rules.bText("privacyTitle", fallback = "云端沙箱数据说明")
                    else if (selected == "terms") rules.bText("termsTitle", fallback = "演示服务说明")
                    else rules.bText("title", fallback = "演示服务细则"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "版本：${rules.bText("version")}",
                    color = Muted,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(
                    when (selected) {
                        "privacy" ->
                            rules.bText(
                                "privacyContent",
                                fallback =
                                    "此版本只连接独立演示服务。账号与初始订单均为合成数据；主动上传的图片与反馈保存在独立云端数据库和文件目录中。请使用带水印样例图片。恢复出厂沙箱数据可清除测试记录。此说明不替代正式平台隐私政策。",
                            )
                        "terms" ->
                            rules.bText(
                                "termsContent",
                                fallback =
                                    "本版本供业务交互评审：会员支付、提现、证件审批、订单支付均为沙箱状态，不发生真实银行支付、收费、开票或营运交易。正式服务协议需由平台确认并发布。",
                            )
                        else ->
                            rules.bText("content", fallback = "服务端暂未发布服务细则。请联系演示管理员补充，或通过反馈工单咨询。")
                    },
                    lineHeight = 22.sp,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
