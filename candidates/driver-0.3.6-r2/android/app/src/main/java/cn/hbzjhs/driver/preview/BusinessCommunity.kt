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
        item { BusinessHint("云端沙箱按合成乘客尾号模拟屏蔽。真实平台应按乘客账号标识处理，避免尾号相同造成误匹配。") }
        if (adding)
            item {
                SheetCard {
                    Text("添加屏蔽记录", fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
                Text(entry.bText("reason"), fontSize = 18.sp)
                Text(
                    entry.bText("createdAt"),
                    fontSize = 16.sp,
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
internal fun BusinessPoints(store: ConnectedStore, profile: JSONObject, onBack: () -> Unit) {
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
                Text("当前积分余额", color = Muted, fontSize = 16.sp)
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
        item {
            Box {
                BusinessOutlinedButton(onClick = { monthMenu = true }) { Text("$selectedMonth ▾") }
                DropdownMenu(monthMenu, { monthMenu = false }) {
                    months.forEach { month ->
                        DropdownMenuItem(
                            text = { Text(month, fontSize = 18.sp) },
                            modifier = Modifier.heightIn(min = 56.dp),
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
        item { BusinessHint("奖励规则属于沙箱演练配置，不代表正式平台发放承诺。") }
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
        item { SectionTitle("评分变动记录") }
        if (entries.isEmpty()) item { BusinessEmpty("暂无评分变动", actionLabel = "刷新") { store.refresh() } }
        items(entries) { entry -> BusinessLedgerRow(entry, "服务分变动") }
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
                    fontSize = 18.sp,
                )
                Text(
                    entry.bText("date", "createdAt", "time"),
                    fontSize = 16.sp,
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
    val code = referral.bText("code", fallback = "")
    val context = LocalContext.current
    BusinessPage("分享拉新", store, onBack) {
        item {
            SheetCard {
                Pill("演示活动演练")
                Spacer(Modifier.height(18.dp))
                Text("好服务，值得被分享", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(
                    code.ifBlank { "邀请码尚未生成" },
                    fontSize = 24.sp,
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
                                            "宜捷行司机端云端沙箱邀请码：$code（仅供演练，非正式活动）",
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
                    record.bText("name", "phoneTail", "mobileMasked", fallback = "演示合成乘客"),
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
                referral.bText(
                    "rules",
                    fallback = "邀请码由演示服务生成，数据为合成活动记录。正式推广链接、奖励核算和乘客注册仍需接入平台活动系统。",
                )
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
    LaunchedEffect(revision) {
        loaded = false
        failure = ""
        store.query(
            "/api/ranking",
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
    BusinessPage("接单排行", store, onBack, "刷新", { revision++ }) {
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
                    DetailRow("完成订单", "${ranking.optInt("completed")} 单")
                    BusinessHint("数据由隔离服务提供；同名合成司机不对应真实人员。")
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
                        "${entry.optInt("completed")} 单",
                        if (entry.optInt("rank") in 1..3) Green else Ink,
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
                    Text(if (open == index) "收起" else "展开", color = Green, fontSize = 16.sp)
                }
                if (open == index)
                    Text(
                        entry.bText("answer", "content"),
                        color = Muted,
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
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
                Text("宜捷行 · Android 原生版", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    "云端独立体验版本",
                    color = Muted,
                    fontSize = 16.sp,
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
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "版本：${rules.bText("version")}",
                    color = Muted,
                    fontSize = 16.sp,
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
                    lineHeight = 27.sp,
                    fontSize = 18.sp,
                )
            }
        }
    }
}
