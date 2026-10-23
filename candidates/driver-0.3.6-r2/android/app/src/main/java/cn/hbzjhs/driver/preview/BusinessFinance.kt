@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cn.hbzjhs.driver.preview

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.delay
import org.json.JSONObject

private fun bCardNumber(card: JSONObject): String =
    card.bText("numberMasked", "number", fallback = "•••• ${card.bText("lastFour", fallback = "")}")

private fun bCardTail(card: JSONObject): String =
    card.bText("lastFour", fallback = bCardNumber(card).takeLast(4))

@Composable
internal fun BusinessBanks(
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val cards = profile.bList("bankCards")
    val busy by store.loading.collectAsState()
    var remove by remember { mutableStateOf<JSONObject?>(null) }
    BusinessPage("我的银行卡", store, onBack, "添加", { navigate("bank/add") }) {
        item { BusinessHint("演示仅接收 9000 开头的演示卡号，不调用银行，不绑定真实账户。") }
        if (cards.isEmpty()) item { BusinessEmpty("尚未添加演示银行卡，点击右上角添加。") }
        items(cards, key = { it.bText("id") }) { card ->
            Surface(shape = RoundedCornerShape(20.dp), color = Green) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Glyph("card", Color.White, Modifier.size(30.dp))
                        Text(
                            card.bText("bankName"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        if (card.optBoolean("isDefault"))
                            Pill("默认", Color.White, Color.White.copy(alpha = .2f))
                    }
                    Text(
                        bCardNumber(card),
                        color = Color.White,
                        fontSize = 23.sp,
                        modifier = Modifier.padding(vertical = 20.dp),
                    )
                    Text(
                        "持卡人 · ${card.bText("holder")}",
                        color = Color.White.copy(alpha = .85f),
                        fontSize = 16.sp,
                    )
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!card.optBoolean("isDefault"))
                            BusinessTextButton(
                                onClick = {
                                    store.perform("bank.default", bJson("id" to card.bText("id"))) {
                                        onMessage("默认提现卡已更新")
                                    }
                                },
                                enabled = !busy,
                            ) {
                                Text("设为默认", color = Color.White)
                            }
                        BusinessTextButton(onClick = { remove = card }, enabled = !busy) {
                            Text("解除绑定", color = Color.White)
                        }
                    }
                }
            }
        }
        item {
            BusinessOutlinedButton(onClick = { navigate("bank/add") }, modifier = Modifier.fillMaxWidth()) {
                Text("添加演示银行卡")
            }
        }
        item {
            SheetCard {
                MenuRow("wallet", "提现记录") { navigate("withdrawals") }
                MenuRow("shield", "钱包安全设置") { navigate("walletsettings") }
            }
        }
    }
    remove?.let { card ->
        BusinessConfirm(
            "解除银行卡绑定",
            "确认解除 ${card.bText("bankName")} 尾号 ${bCardTail(card)}？历史提现记录仍可查询。",
            "确认解绑",
            { remove = null },
        ) {
            remove = null
            store.perform("bank.remove", bJson("id" to card.bText("id"))) { onMessage("演示银行卡已解绑") }
        }
    }
}

@Composable
internal fun BusinessBankAdd(
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val busy by store.loading.collectAsState()
    var bank by rememberSaveable { mutableStateOf("沙箱演示银行") }
    var holder by rememberSaveable {
        mutableStateOf(profile.bObj("driver").bText("name", fallback = "演示司机"))
    }
    var number by rememberSaveable { mutableStateOf("9000000000000001") }
    var agreement by rememberSaveable { mutableStateOf(false) }
    var validation by remember { mutableStateOf("") }
    BusinessPage("添加演示银行卡", store, onBack, null) {
        item {
            SheetCard {
                BusinessHint("此表单用于沙箱绑卡和提现交互演练。请勿填写真实银行卡或真实身份资料。")
                Spacer(Modifier.height(12.dp))
                BusinessField(bank, { bank = it.take(30) }, "银行名称")
                BusinessField(holder, { holder = it.take(20) }, "合成持卡人姓名")
                BusinessField(
                    number,
                    { number = it.filter(Char::isDigit).take(19) },
                    "演示卡号",
                    numeric = true,
                    help = "9000 开头，共 16 至 19 位数字",
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(value = agreement, role = Role.Checkbox, onValueChange = { agreement = it })
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(agreement, onCheckedChange = null)
                    Text("确认使用演示卡号", fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                }
                if (validation.isNotBlank())
                    Text(validation, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                PrimaryButton("添加至演示账户", enabled = !busy && agreement) {
                    validation =
                        when {
                            bank.trim().isEmpty() || holder.trim().isEmpty() -> "请填写银行和合成持卡人"
                            !Regex("9000\\d{12,15}").matches(number) -> "仅接受 9000 开头的 16 至 19 位演示卡号"
                            else -> ""
                        }
                    if (validation.isBlank())
                        store.perform(
                            "bank.add",
                            bJson(
                                "bankName" to bank.trim(),
                                "holder" to holder.trim(),
                                "number" to number,
                            ),
                        ) {
                            onMessage("演示卡已绑定")
                            onBack()
                        }
                }
            }
        }
    }
}

@Composable
internal fun BusinessPassword(
    route: String,
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val wallet = profile.bObj("wallet")
    val hasPassword = wallet.optBoolean("passwordSet", wallet.optBoolean("hasPassword"))
    val busy by store.loading.collectAsState()
    val mode = route.substringAfter('/', "")
    var password by remember(mode) { mutableStateOf("") }
    var confirmation by remember(mode) { mutableStateOf("") }
    var oldPassword by remember(mode) { mutableStateOf("") }
    var code by remember(mode) { mutableStateOf("") }
    var sandboxCode by remember(mode) { mutableStateOf("") }
    var remaining by remember { mutableIntStateOf(0) }
    var validation by remember { mutableStateOf("") }
    LaunchedEffect(remaining) {
        if (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    val title =
        when (mode) {
            "set" -> "设置交易密码"
            "change" -> "修改交易密码"
            "reset" -> "找回交易密码"
            else -> "钱包安全设置"
        }
    BusinessPage(title, store, onBack) {
        if (mode.isEmpty()) {
            item {
                SheetCard {
                    DetailRow("钱包环境", "独立云端沙箱", Green)
                    DetailRow("交易密码", if (hasPassword) "已设置" else "未设置")
                    if (!hasPassword) MenuRow("shield", "设置交易密码") { navigate("walletsettings/set") }
                    else MenuRow("shield", "修改交易密码") { navigate("walletsettings/change") }
                    MenuRow("phone", "找回交易密码") { navigate("walletsettings/reset") }
                    MenuRow("card", "银行卡管理", "${profile.bList("bankCards").size} 张") {
                        navigate("bank")
                    }
                    MenuRow("orders", "提现申请记录") { navigate("withdrawals") }
                }
            }
            item { BusinessHint("交易密码为此沙箱独立设置的 6 位数字，用于验证提现操作；请勿使用真实支付密码。密码由服务端哈希保存。") }
        } else {
            item {
                SheetCard {
                    BusinessHint("请为此云端沙箱设置独立的 6 位交易密码，不要使用真实支付密码。")
                    Spacer(Modifier.height(12.dp))
                    if (mode == "change")
                        BusinessField(
                            oldPassword,
                            { oldPassword = it.filter(Char::isDigit).take(6) },
                            "原交易密码",
                            password = true,
                        )
                    if (mode == "reset") {
                        DetailRow("账号", profile.bObj("driver").bText("mobile"))
                        BusinessOutlinedButton(
                            onClick = {
                                store.perform("wallet.password.code") { reply ->
                                    val result = bResult(reply)
                                    sandboxCode = result.bText("verificationCode", fallback = "")
                                    remaining = result.optInt("expirySeconds", 300)
                                    onMessage("演示验证码已生成，不发送短信")
                                }
                            },
                            enabled = !busy && remaining <= 0,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (remaining > 0) "${remaining}s 后可重新获取" else "获取演示验证码")
                        }
                        if (sandboxCode.isNotBlank())
                            Text(
                                "沙箱验证码：$sandboxCode",
                                color = Green,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        BusinessField(
                            code,
                            { code = it.filter(Char::isDigit).take(8) },
                            "验证码",
                            numeric = true,
                        )
                    }
                    BusinessField(
                        password,
                        { password = it.filter(Char::isDigit).take(6) },
                        "新交易密码",
                        password = true,
                    )
                    BusinessField(
                        confirmation,
                        { confirmation = it.filter(Char::isDigit).take(6) },
                        "确认新交易密码",
                        password = true,
                    )
                    if (validation.isNotBlank())
                        Text(validation, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("确认${if (mode == "reset") "重置" else "保存"}", enabled = !busy) {
                        validation =
                            when {
                                password.length != 6 -> "交易密码需为 6 位数字"
                                confirmation != password -> "两次输入的密码不一致"
                                mode == "change" && oldPassword.length != 6 -> "请输入原 6 位交易密码"
                                mode == "reset" && code.isBlank() -> "请先获取并输入验证码"
                                else -> ""
                            }
                        if (validation.isBlank())
                            store.perform(
                                "wallet.password.$mode",
                                bJson(
                                    "password" to password,
                                    "oldPassword" to oldPassword,
                                    "code" to code,
                                ),
                            ) {
                                password = ""
                                confirmation = ""
                                oldPassword = ""
                                code = ""
                                sandboxCode = ""
                                onMessage("交易密码已更新")
                                onBack()
                            }
                    }
                }
            }
            item {
                BusinessHint(
                    if (mode == "reset") "此处演练演示验证码校验；真实找回密码仍需要平台身份校验和短信服务接入。"
                    else "设置结果由服务端校验，密码错误会显示原因。"
                )
            }
        }
    }
}

@Composable
internal fun BusinessWithdraw(
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val state by store.state.collectAsState()
    val busy by store.loading.collectAsState()
    val cards = profile.bList("bankCards")
    val wallet = profile.bObj("wallet")
    val hasPassword = wallet.optBoolean("passwordSet", wallet.optBoolean("hasPassword"))
    var amount by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf("") }
    var success by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(cards.toString()) {
        if (cards.none { it.bText("id") == selectedId })
            selectedId =
                cards.firstOrNull { it.optBoolean("isDefault") }?.bText("id")
                    ?: cards.firstOrNull()?.bText("id")
                    ?: ""
    }
    fun cents(): Long? =
        runCatching {
                require(Regex("\\d{1,7}(\\.\\d{1,2})?").matches(amount.trim()))
                BigDecimal(amount.trim())
                    .movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact()
            }
            .getOrNull()
    BusinessPage("余额提现", store, onBack, "记录", { navigate("withdrawals") }) {
        item {
            SheetCard {
                Text("可提现余额", color = Muted, fontSize = 16.sp)
                Text(
                    "¥${money(state.balance)}",
                    color = Green,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                BusinessField(amount, { amount = it.take(12) }, "提现金额（元）", numeric = true)
                BusinessTextButton(onClick = { amount = money(state.balance) }) { Text("全部提现") }
            }
        }
        item { SectionTitle("到账银行卡", action = "管理", onAction = { navigate("bank") }) }
        if (cards.isEmpty())
            item {
                SheetCard {
                    Text("请先添加演示银行卡")
                    BusinessTextButton(onClick = { navigate("bank/add") }) { Text("添加演示卡") }
                }
            }
        else
            items(cards, key = { it.bText("id") }) { card ->
                SheetCard(Modifier.selectable(
                    selected = selectedId == card.bText("id"),
                    role = Role.RadioButton,
                    onClick = { selectedId = card.bText("id") },
                )) {
                    Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedId == card.bText("id"),
                            onClick = null,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(card.bText("bankName"), fontWeight = FontWeight.Medium)
                            Text(
                                "尾号 ${bCardTail(card)} · ${card.bText("holder")}",
                                color = Muted,
                                fontSize = 16.sp,
                            )
                        }
                        if (card.optBoolean("isDefault")) Pill("默认")
                    }
                }
            }
        item {
            SheetCard {
                if (hasPassword)
                    BusinessField(
                        password,
                        { password = it.filter(Char::isDigit).take(6) },
                        "6 位交易密码",
                        password = true,
                    )
                else {
                    Text("尚未设置交易密码", color = Orange)
                    BusinessTextButton(onClick = { navigate("walletsettings/set") }) { Text("先设置交易密码") }
                }
                if (validation.isNotBlank())
                    Text(validation, color = MaterialTheme.colorScheme.error, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                PrimaryButton("提交提现申请", enabled = !busy && hasPassword && cards.isNotEmpty()) {
                    val value = cents()
                    validation =
                        when {
                            value == null || value < 1 -> "请输入至少 0.01 元，最多两位小数"
                            value > state.balance -> "提现金额超过可提现余额"
                            selectedId.isBlank() -> "请选择到账演示卡"
                            password.length != 6 -> "请输入 6 位交易密码"
                            else -> ""
                        }
                    if (validation.isBlank()) confirmation = true
                }
            }
        }
        item { BusinessHint("提交后，演示服务冻结并扣减沙箱余额，由演示管理台处理成功或驳回；驳回退回沙箱余额。不会进行真实银行转账。") }
    }
    if (confirmation)
        BusinessConfirm(
            "确认提现",
            "向所选演示卡申请提现 ¥${money(cents() ?: 0)}。演示处理后可在记录查看状态。",
            "确认提交",
            { confirmation = false },
        ) {
            confirmation = false
            store.perform(
                "wallet.withdraw",
                bJson(
                    "amount" to (cents() ?: 0),
                    "password" to password,
                    "bankCardId" to selectedId,
                ),
            ) { reply ->
                success = bResult(reply)
                amount = ""
                password = ""
                onMessage("提现申请已保存，尚未真实打款")
            }
        }
    if (success != null)
        AlertDialog(
            onDismissRequest = { success = null },
            title = { Text("提现申请已提交") },
            text = { Text("申请已写入独立演示数据库。请在提现记录查看待处理、成功或驳回状态；此过程不发生真实转账。", fontSize = 18.sp, lineHeight = 28.sp, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = {
                BusinessTextButton(
                    onClick = {
                        success = null
                        navigate("withdrawals")
                    }
                ) {
                    Text("查看记录")
                }
            },
            dismissButton = { BusinessTextButton(onClick = { success = null }) { Text("继续") } },
        )
}

@Composable
internal fun BusinessWithdrawals(store: ConnectedStore, profile: JSONObject, onBack: () -> Unit) {
    val records = profile.bObj("wallet").bList("withdrawals")
    var filter by rememberSaveable { mutableStateOf("全部") }
    val selected = records.filter { filter == "全部" || bStatus(it.bText("status")) == filter }
    BusinessPage("提现记录", store, onBack) {
        item {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("全部", "待处理", "沙箱已通过", "已驳回").forEach { f ->
                    FilterChip(
                        filter == f, { filter = f },
                        label = { Text(f, fontSize = 18.sp) },
                        modifier = Modifier.heightIn(min = 56.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pale, selectedLabelColor = Green, labelColor = Ink),
                    )
                }
            }
        }
        if (selected.isEmpty()) item { BusinessEmpty("当前分类暂无提现记录") }
        items(selected, key = { it.bText("id") }) { record ->
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "¥${money(record.optLong("amount"))}",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                    )
                    Pill(bStatus(record.bText("status")))
                }
                DetailRow("申请编号", record.bText("id"))
                DetailRow("申请时间", record.bText("createdAt"))
                DetailRow(
                    "到账卡",
                    "${record.bText("bankName", fallback = "演示银行卡")} ${record.bText("bankTail", "lastFour", fallback = "")}",
                )
                if (record.bText("reason", "reply", fallback = "").isNotBlank())
                    Text("处理说明：${record.bText("reason", "reply")}", fontSize = 16.sp, color = Muted)
            }
        }
        item { BusinessHint("处理结果由演示管理台生成，不是银行回执。刷新可查看最新状态。") }
    }
}
