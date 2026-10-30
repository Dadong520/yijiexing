package cn.hbzjhs.driver.preview

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import org.json.JSONObject

@Composable
fun BusinessEntry(
    route: String,
    store: ConnectedStore,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val payload by store.payload.collectAsState()
    val profile = bData(payload).bObj("profile")
    LaunchedEffect(route) { store.refresh() }
    when {
        route == "driver" -> BusinessDriver(store, profile, onBack, navigate, onMessage)
        route == "qualification" -> BusinessCertificates(store, profile, onBack, navigate)
        route.startsWith("qualification/") ->
            BusinessCertificateEdit(route.substringAfter('/'), store, profile, onBack, onMessage)
        route == "bank" -> BusinessBanks(store, profile, onBack, navigate, onMessage)
        route == "bank/add" -> BusinessBankAdd(store, profile, onBack, onMessage)
        route == "walletsettings" || route.startsWith("walletsettings/") ->
            BusinessPassword(route, store, profile, onBack, navigate, onMessage)
        route == "withdraw" -> BusinessWithdraw(store, profile, onBack, navigate, onMessage)
        route == "withdrawals" -> BusinessWithdrawals(store, profile, onBack)
        route == "blacklist" -> BusinessBlacklist(store, profile, onBack, onMessage)
        route == "feedback" -> BusinessFeedback(store, profile, onBack, onMessage)
        route == "help" -> BusinessHelp(store, profile, onBack, navigate, onMessage)
        route == "points" -> BusinessPoints(store, profile, onBack)
        route == "serviceScore" || route == "servicescore" ->
            BusinessServiceScore(store, profile, onBack)
        route == "referral" -> BusinessReferral(store, profile, onBack, onMessage)
        route == "ranking" -> BusinessRanking(store, profile, onBack)
        route == "rules" || route.startsWith("rules/") ->
            BusinessRules(route, store, profile, onBack, navigate)
        route.startsWith("fees/") ->
            BusinessFees(route.substringAfter('/'), store, onBack, navigate)
        route.startsWith("complaint/") ->
            BusinessComplaint(route.substringAfter('/'), store, profile, onBack, onMessage)
        else -> BusinessPage("平台服务", store, onBack) { item { BusinessEmpty("暂未找到此页面，可返回后重试") } }
    }
}

@Composable
private fun BusinessDriver(
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val driver = profile.bObj("driver")
    val state by store.state.collectAsState()
    val busy by store.loading.collectAsState()
    val displayName = driver.bText("name", fallback = "演示司机")
    var name by remember(displayName) { mutableStateOf(displayName) }
    var editing by remember { mutableStateOf(false) }
    var serviceCard by remember { mutableStateOf(false) }
    BusinessPage("驾驶员信息", store, onBack) {
        item {
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Pale, shape = CircleShape) {
                        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                            Glyph("profile", modifier = Modifier.size(30.dp))
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(displayName, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text(
                            driver.bText("organization", fallback = "宜捷行 · 独立演示服务"),
                            fontSize = 15.sp,
                            color = Muted,
                        )
                    }
                    BusinessTextButton(onClick = { serviceCard = true }) { Text("服务卡") }
                }
                Spacer(Modifier.height(14.dp))
                DetailRow("司机编号", driver.bText("id"))
                DetailRow("手机号", driver.bText("mobile"))
                DetailRow("车牌", driver.bText("plate", "carNo"))
                DetailRow("服务区域", driver.bText("cityName", "city"))
                DetailRow("车型", driver.bText("vehicleType", "vehicleModel"))
                DetailRow(
                    "资质状态",
                    bStatus(
                        driver.bText(
                            "certificationStatus",
                            "certification",
                            "status",
                            fallback = "",
                        )
                    ),
                    Green,
                )
                BusinessTextButton(onClick = { editing = !editing }) {
                    Text(if (editing) "取消编辑" else "修改显示姓名")
                }
                if (editing) {
                    BusinessField(
                        name,
                        { name = it.take(20) },
                        "显示姓名",
                        help = "此账号为合成测试资料，请勿填写真实身份信息",
                    )
                    PrimaryButton("保存到演示服务", enabled = !busy && name.trim().isNotBlank()) {
                        store.perform("profile.update", bJson("name" to name.trim())) {
                            editing = false
                            onMessage("司机资料已更新")
                        }
                    }
                }
            }
        }
        item {
            SheetCard {
                SectionTitle("服务表现")
                DetailRow("累计完成", "${state.todayCompleted} 单")
                DetailRow("累计收入", "¥${money(state.todayIncome)}", Green)
                MenuRow(
                    "shield",
                    "服务分",
                    "${profile.bObj("serviceScore").optInt("value", profile.bObj("serviceScore").optInt("score"))} 分",
                ) {
                    navigate("serviceScore")
                }
                MenuRow("doc", "资质证件") { navigate("qualification") }
                MenuRow("gift", "我的积分") { navigate("points") }
            }
        }
        item { BusinessHint("资料来自独立演示数据库；不接入真实司机身份库。") }
    }
    if (serviceCard)
        DriverDialog(
            onDismissRequest = { serviceCard = false },
            title = { Text("宜捷行司机服务卡") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    Text(displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green)
                    DetailRow("司机编号", driver.bText("id"))
                    DetailRow("车牌", driver.bText("plate", "carNo"))
                    DetailRow("区域", driver.bText("cityName", "city"))
                    BusinessHint("演示服务卡，不作为营运或资质凭证。")
                }
            },
            confirmButton = { BusinessTextButton(onClick = { serviceCard = false }) { Text("关闭") } },
        )
}

private fun bCertificateTitle(type: String) =
    when (type) {
        "insurance" -> "车辆保单"
        "license" -> "驾驶证信息"
        "qualification" -> "从业资格证"
        else -> type
    }

private fun bCanEditCertificate(cert: JSONObject, today: LocalDate): Boolean =
    CertificateDueStatus.canUpdate(cert, today)

@Composable
private fun BusinessCertificates(
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    val certificates = profile.bList("certificates")
    BusinessPage("资质证件", store, onBack) {
        item { BusinessHint("支持车辆保单、驾驶证正反面、从业资格证。图片上传至独立演示服务后，进入待审核状态。请使用样例图片。") }
        items(listOf("insurance", "license", "qualification")) { type ->
            val cert = certificates.firstOrNull { it.bText("type") == type } ?: JSONObject()
            SheetCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("shield", modifier = Modifier.size(30.dp))
                    Text(
                        cert.bText("title", "name", fallback = bCertificateTitle(type)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    Pill(bStatus(cert.bText("status", fallback = "")))
                }
                Spacer(Modifier.height(12.dp))
                DetailRow("生效日期", cert.bText("validFrom"))
                val until = cert.bText("validUntil")
                DetailRow("当前有效期限", if (until == "LONG_TERM") "长期有效" else until)
                DriverCertificateStatus(cert)
                cert.optJSONObject("pending")?.let { pending ->
                    // A pending replacement never changes what the current material allows.
                    BusinessHint(
                        "新材料待审核：${pending.optString("validFrom")} 至 ${pending.optString("validUntil")}。" +
                            when {
                                certificateCurrentlyValid(cert) -> "旧证仍在有效期内可继续接单，过期将暂停新增接单。在途订单可完成。"
                                certificateOutOfDate(cert) -> "当前材料不在有效期内，审核通过前仍暂停新增接单。在途订单可完成。"
                                else -> "当前材料有效期未确认或尚未审核通过，不能视为有效证件，请以审核结果为准。"
                            },
                    )
                }
                if (cert.optString("submittedAt").isNotBlank())
                    DetailRow("提交时间", cert.bText("submittedAt"))
                if (cert.bText("rejectionReason", "reply", fallback = "").isNotBlank())
                    Text(
                        "审核说明：${cert.bText("rejectionReason", "reply")}",
                        color = Orange,
                        fontSize = 15.sp,
                    )
                if (bCanEditCertificate(cert, CertificateDueStatus.today(store.payload.value.optString("serverTime"))))
                    PrimaryButton("更新并提交审核") { navigate("qualification/$type") }
                else
                    BusinessOutlinedButton(
                        onClick = { navigate("qualification/$type") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("查看已提交资料")
                    }
            }
        }
        item { BusinessHint("通过且有效期超过 30 天的证件仅可查看；审核中不能重复提交。即将过期、已过期或驳回后可更新。") }
    }
}

@Composable
private fun BusinessDateField(
    label: String,
    value: String,
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val date = runCatching { LocalDate.parse(value) }.getOrDefault(CertificateDueStatus.today())
    BusinessOutlinedButton(
        onClick = {
            DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onChange(LocalDate.of(year, month + 1, day).toString())
                    },
                    date.year,
                    date.monthValue - 1,
                    date.dayOfMonth,
                )
                .show()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("$label：${if (value.isBlank()) "选择日期" else value}")
    }
}

@Composable
private fun BusinessCertificateEdit(
    type: String,
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val cert =
        profile.bList("certificates").firstOrNull { it.bText("type") == type } ?: JSONObject()
    val editVersion = remember(type) { cert.optInt("version", 0) }
    val editable = bCanEditCertificate(cert, CertificateDueStatus.today(store.payload.value.optString("serverTime")))
    val busy by store.loading.collectAsState()
    var start by
        rememberSaveable(type) {
            mutableStateOf(cert.bText("validFrom", fallback = CertificateDueStatus.today().toString()))
        }
    var end by
        rememberSaveable(type) {
            mutableStateOf(
                cert
                    .bText("validUntil", fallback = CertificateDueStatus.today().plusYears(1).toString())
                    .takeUnless { it == "LONG_TERM" } ?: ""
            )
        }
    var longTerm by
        rememberSaveable(type) { mutableStateOf(cert.optString("validUntil") == "LONG_TERM") }
    // Image bytes stay transient and never enter saved instance state or Android preferences.
    var image by remember(type) { mutableStateOf("") }
    var backImage by remember(type) { mutableStateOf("") }
    var preview by remember(type) { mutableStateOf("") }
    var previewTitle by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var validation by remember { mutableStateOf("") }
    fun showSaved(fileId: String, title: String) {
        if (fileId.isBlank()) {
            onMessage("该初始合成资料没有上传图片，请使用临期证件测试更新")
            return
        }
        store.query("/api/files/$fileId?encoding=base64") { reply ->
            preview = bData(reply).bText("imageBase64", "base64", fallback = "")
            previewTitle = title
        }
    }
    BusinessPage(bCertificateTitle(type), store, onBack) {
        item {
            SheetCard {
                Pill(bStatus(cert.bText("status", fallback = "")))
                DriverCertificateStatus(cert)
                cert.optJSONObject("pending")?.let { pending ->
                    BusinessHint("新材料待审核：${pending.optString("validFrom")} 至 ${pending.optString("validUntil")}。通过后才替换当前材料。")
                    BusinessTextButton(onClick = { showSaved(pending.optString("fileId"), "待审新材料") }) { Text("查看待审新材料") }
                    if (type == "license") BusinessTextButton(onClick = { showSaved(pending.optString("backFileId"), "待审背面") }) { Text("查看待审背面") }
                }
                Spacer(Modifier.height(12.dp))
                if (editable) {
                    BusinessImageSelector(
                        if (type == "license") "驾驶证正面" else "证件图片",
                        image,
                        { image = it },
                        store,
                        onMessage,
                    )
                    if (type == "license") {
                        Spacer(Modifier.height(18.dp))
                        BusinessImageSelector(
                            "驾驶证反面",
                            backImage,
                            { backImage = it },
                            store,
                            onMessage,
                        )
                    }
                }
                if (cert.length() > 0) {
                    BusinessTextButton(
                        onClick = {
                            showSaved(cert.bText("fileId", "imageId", fallback = ""), "已提交正面")
                        }
                    ) {
                        Text("查看已提交图片")
                    }
                    if (type == "license")
                        BusinessTextButton(
                            onClick = {
                                showSaved(cert.bText("backFileId", fallback = ""), "已提交反面")
                            }
                        ) {
                            Text("查看已提交反面")
                        }
                }
            }
        }
        item {
            SheetCard {
                Text("证件有效期", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                BusinessDateField("开始日期", start, editable) { start = it }
                if (!longTerm) BusinessDateField("截止日期", end, editable) { end = it }
                if (type != "insurance")
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .toggleable(value = longTerm, enabled = editable, role = Role.Checkbox, onValueChange = { longTerm = it })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(longTerm, onCheckedChange = null, enabled = editable)
                        Text("长期有效", fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    }
                if (validation.isNotBlank())
                    Text(validation, color = MaterialTheme.colorScheme.error, fontSize = 15.sp)
                if (editable)
                    PrimaryButton("提交演示审核", enabled = !busy) {
                        validation =
                            when {
                                image.isBlank() -> "请上传证件图片"
                                type == "license" && backImage.isBlank() -> "驾驶证须上传正反两面"
                                start.isBlank() || (!longTerm && end.isBlank()) -> "请选择完整有效期"
                                start > CertificateDueStatus.today().toString() -> "有效起始日期不能晚于今天"
                                !longTerm && end <= start -> "结束日期须晚于开始日期"
                                !longTerm && end <= CertificateDueStatus.today().toString() -> "结束日期须晚于今天"
                                else -> ""
                            }
                        if (validation.isBlank()) confirm = true
                    }
                else
                    BusinessHint(
                        if (cert.optJSONObject("pending") != null || cert.optString("status") == "PENDING") "新材料已提交，等待审核；有效旧证继续使用，审核通过后才替换。"
                        else "资料仍在有效期内，到期前 60 天可更新。"
                    )
            }
        }
        item { BusinessHint("图片经本机压缩后上传；沙箱不调用真实身份核验。审核结果来自演示管理台，提交不会自动通过。") }
    }
    if (confirm)
        BusinessConfirm("确认提交资料", "提交新材料等待审核；旧证在有效期内继续可用，审核通过后才替换。", "提交", { confirm = false }) {
            confirm = false
            store.perform(
                "certificate.submit",
                bJson(
                    "type" to type,
                    "expectedVersion" to editVersion,
                    "validFrom" to start,
                    "validUntil" to if (longTerm) "LONG_TERM" else end,
                    "imageBase64" to image,
                    "backImageBase64" to backImage,
                ),
            ) {
                image = ""
                backImage = ""
                onMessage("证件已提交，等待演示审核")
                onBack()
            }
        }
    if (preview.isNotBlank())
        DriverDialog(
            onDismissRequest = { preview = "" },
            title = { Text(previewTitle) },
            text = {
                val bmp = remember(preview) { bBitmap(preview) }
                if (bmp != null)
                    androidx.compose.foundation.Image(
                        bmp.asImageBitmap(),
                        contentDescription = previewTitle,
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                else Text("无法显示此图片")
            },
            confirmButton = { BusinessTextButton(onClick = { preview = "" }) { Text("关闭") } },
        )
}
