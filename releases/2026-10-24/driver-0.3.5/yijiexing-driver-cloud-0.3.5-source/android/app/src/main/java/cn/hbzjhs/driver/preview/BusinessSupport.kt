@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cn.hbzjhs.driver.preview

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

@Composable
private fun BusinessAttachments(
    images: List<String>,
    onImages: (List<String>) -> Unit,
    maximum: Int,
    store: ConnectedStore,
    onMessage: (String) -> Unit,
) {
    Text("图片附件 ${images.size}/$maximum", fontSize = 18.sp, fontWeight = FontWeight.Medium)
    images.forEachIndexed { index, base64 ->
        val bitmap = remember(base64) { bBitmap(base64) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            if (bitmap != null)
                Image(
                    bitmap.asImageBitmap(),
                    "附件 ${index + 1}",
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Fit,
                )
            Text(
                "图片 ${index + 1}",
                fontSize = 16.sp,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            BusinessTextButton(onClick = { onImages(images.filterIndexed { i, _ -> i != index }) }) {
                Text("移除")
            }
        }
    }
    if (images.size < maximum)
        BusinessImageSelector(
            "添加附件（可选）",
            "",
            { base64 -> if (base64.isNotBlank()) onImages(images + base64) },
            store,
            onMessage,
        )
}

@Composable
internal fun BusinessFeedback(
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var content by rememberSaveable { mutableStateOf("") }
    var images by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirm by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<JSONObject?>(null) }
    var imagePreview by remember { mutableStateOf("") }
    var contentError by rememberSaveable { mutableStateOf<String?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val busy by store.loading.collectAsState()
    val records = if (tab == 2) profile.bList("complaints") else profile.bList("feedback")
    val feedbackSubmitBar: (@Composable () -> Unit)? =
        if (tab == 0)
            ({
                BusinessSubmitBar("提交反馈工单", busy || submitting, submitError ?: contentError) {
                    focus.clearFocus()
                    submitError = null
                    contentError = if (content.trim().length < 5) "请描述至少 5 个字的问题或建议" else null
                    if (contentError == null) confirm = true
                }
            })
        else null
    BusinessPage("意见反馈", store, onBack, bottomBar = feedbackSubmitBar) {
        item {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("提交反馈", "我的反馈", "投诉记录").forEachIndexed { index, label ->
                    FilterChip(
                        tab == index,
                        { tab = index },
                        label = { Text(label, fontSize = 18.sp) },
                        modifier = Modifier.heightIn(min = 56.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Pale, selectedLabelColor = Green, labelColor = Ink),
                    )
                }
            }
        }
        if (tab == 0) {
            item {
                SheetCard {
                    Text("告诉我们遇到的问题", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    BusinessField(
                        content,
                        {
                            content = it.take(500)
                            contentError = null
                            submitError = null
                        },
                        "问题或建议",
                        minLines = 5,
                        help = "至少 5 个字，${content.length}/500",
                        enabled = !submitting,
                        error = contentError,
                    )
                    Spacer(Modifier.height(12.dp))
                    BusinessAttachments(images, { images = it }, 3, store, onMessage)
                }
            }
            item { BusinessHint("内容和附件保存在独立演示服务；可在“我的反馈”查看管理员回复。请勿提交真实敏感信息。") }
        } else {
            if (records.isEmpty())
                item { BusinessEmpty(if (tab == 2) "暂无投诉记录，可从订单详情进入投诉。" else "暂无反馈工单") }
            items(records, key = { it.bText("id") }) { entry ->
                SheetCard {
                    Pill(bStatus(entry.bText("status")))
                    DetailRow("工单编号", entry.bText("id"))
                    if (entry.optString("orderId").isNotBlank())
                        DetailRow("关联订单", entry.bText("orderId"))
                    Text(
                        entry.bText("content"),
                        fontSize = 18.sp,
                        lineHeight = 26.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Text(entry.bText("createdAt"), fontSize = 16.sp, color = Muted)
                    if (entry.bText("reply", fallback = "").isNotBlank())
                        Text(
                            "处理回复：${entry.bText("reply")}",
                            color = Green,
                            fontSize = 18.sp,
                            lineHeight = 26.sp,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    BusinessTextButton(onClick = { selected = entry }) { Text("查看工单详情") }
                }
            }
        }
    }
    if (confirm)
        BusinessConfirm("确认提交反馈", "此反馈将写入独立演示数据库，管理员处理后可查看回复。", "提交", { confirm = false }) {
            confirm = false
            submitting = true
            submitError = null
            store.perform(
                "feedback.submit",
                bJson(
                    "content" to content.trim(),
                    "imageBase64" to images.firstOrNull().orEmpty(),
                    "imagesBase64" to JSONArray(images),
                ),
                onFailure = {
                    submitError = it
                    submitting = false
                },
            ) {
                submitting = false
                content = ""
                images = emptyList()
                tab = 1
                onMessage("反馈工单已提交")
            }
        }
    selected?.let { entry ->
        val files =
            entry.optJSONArray("attachmentFileIds") ?: entry.optJSONArray("fileIds") ?: JSONArray()
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("工单详情", fontSize = 24.sp) },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    DetailRow("工单编号", entry.bText("id"))
                    DetailRow("状态", bStatus(entry.bText("status")))
                    Text(entry.bText("content"), lineHeight = 26.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("处理回复：${entry.bText("reply", fallback = "暂无回复")}", color = Green)
                    if (files.length() > 0)
                        (0 until files.length()).forEach { index ->
                            BusinessTextButton(
                                onClick = {
                                    store.query(
                                        "/api/files/${files.optString(index)}?encoding=base64"
                                    ) { json ->
                                        imagePreview =
                                            bData(json)
                                                .bText("imageBase64", "base64", fallback = "")
                                    }
                                }
                            ) {
                                Text("查看附件 ${index + 1}")
                            }
                        }
                }
            },
            confirmButton = { BusinessTextButton(onClick = { selected = null }) { Text("关闭") } },
        )
    }
    if (imagePreview.isNotBlank())
        AlertDialog(
            onDismissRequest = { imagePreview = "" },
            title = { Text("已上传的附件") },
            text = {
                val bitmap = remember(imagePreview) { bBitmap(imagePreview) }
                if (bitmap != null)
                    Image(
                        bitmap.asImageBitmap(),
                        "附件",
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        contentScale = ContentScale.Fit,
                    )
            },
            confirmButton = { BusinessTextButton(onClick = { imagePreview = "" }) { Text("关闭") } },
        )
}

@Composable
internal fun BusinessFees(
    id: String,
    store: ConnectedStore,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
) {
    var fees by remember(id) { mutableStateOf<JSONObject?>(null) }
    var failed by remember(id) { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    var showFeeDetails by rememberSaveable(id) { mutableStateOf(false) }
    LaunchedEffect(id, revision) {
        failed = ""
        fees = null
        store.query("/api/orders/$id/fees", onFailure = { failed = it }) { fees = bData(it) }
    }
    val state by store.state.collectAsState()
    val order = state.orders.firstOrNull { it.id == id }
    BusinessPage("订单费用明细", store, onBack, "刷新", { revision++ }) {
        if (fees == null) item { BusinessEmpty(if (failed.isBlank()) "正在加载服务端费用明细…" else failed) }
        fees?.let { fee ->
            val estimatedIncome = fee.optLong("income", fee.optLong("driverIncome"))
            val incomeLabel = order?.incomeLabel ?: "预计收入（未核对入账）"
            val displayedIncome = order?.incomeForDisplay(estimatedIncome) ?: estimatedIncome
            item {
                SheetCard {
                    if (order != null && order.paymentStatus == "REFUNDED") {
                        Text(order.paymentStatusLabel, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Green)
                        Text(
                            order.refundAmountText ?: "退款金额待同步",
                            fontSize = if (order.refundAmountText != null) 32.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Green,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                        )
                        HorizontalDivider(color = Line)
                    } else if (order != null) {
                        Text(order.paymentStatusLabel, fontSize = 18.sp, color = Muted)
                    }
                    Text(incomeLabel, color = Ink, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
                    Text(
                        "¥${money(displayedIncome)}",
                        fontSize = if (order?.paymentStatus == "REFUNDED") 28.sp else 36.sp,
                        color = Ink,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
                    )
                }
            }
            item {
                SheetCard {
                    SectionTitle("订单信息")
                    DetailRow("订单编号", id)
                    if (order != null) DetailRow("订单类型", order.kind.title)
                    DetailRow("订单金额", "¥${money(fee.optLong("fare"))}")
                }
            }
            item {
                SheetCard {
                    BusinessOutlinedButton(
                        onClick = { showFeeDetails = !showFeeDetails },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (showFeeDetails) "收起费用明细" else "展开费用明细") }
                    if (showFeeDetails) {
                        DetailRow("支付手续费", "¥${money(fee.optLong("paymentFee"))}")
                        DetailRow("分账手续费", "¥${money(fee.optLong("splitFee"))}")
                        DetailRow("地图服务费", "¥${money(fee.optLong("mapServiceFee"))}")
                        DetailRow("服务费", "¥${money(fee.optLong("serviceFee"))}")
                        HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 10.dp))
                        DetailRow(incomeLabel, "¥${money(displayedIncome)}", Green)
                        BusinessHint(fee.bText("policyLabel", fallback = "暂未返回计费政策说明"))
                    }
                }
            }
            item { SheetCard { MenuRow("support", "对此订单有疑问") { navigate("complaint/$id") } } }
        }
    }
}

@Composable
internal fun BusinessComplaint(
    id: String,
    store: ConnectedStore,
    profile: JSONObject,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    var reason by rememberSaveable(id) { mutableStateOf("") }
    var content by rememberSaveable(id) { mutableStateOf("") }
    var images by remember(id) { mutableStateOf<List<String>>(emptyList()) }
    var blockUser by rememberSaveable(id) { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var reasonError by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var contentError by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var submitError by remember(id) { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current
    val busy by store.loading.collectAsState()
    val state by store.state.collectAsState()
    val order = state.orders.firstOrNull { it.id == id }
    val records = profile.bList("complaints").filter { it.bText("orderId") == id }
    BusinessPage(
        "投诉与订单申诉",
        store,
        onBack,
        bottomBar = {
            if (submitted) Text("投诉已提交，可在上方查看处理结果", color = Green, fontSize = 16.sp)
            else
                BusinessSubmitBar(
                    "确认提交投诉",
                    busy || submitting,
                    submitError
                        ?: listOfNotNull(reasonError, contentError).joinToString("；").takeIf {
                            it.isNotBlank()
                        },
                ) {
                    focus.clearFocus()
                    reasonError = if (reason.isBlank()) "请选择投诉类型，可点击选项整行" else null
                    contentError = if (content.trim().length < 5) "请描述至少 5 个字的具体问题" else null
                    submitError =
                        when {
                            order == null -> "未读取到当前账号的订单，请点右上角刷新后重试"
                            order.stage !in setOf(Stage.DONE, Stage.CANCELLED, Stage.WAIT_PAY) ->
                                "请先完成或结束订单后投诉，当前状态为${order.stage.title}"
                            else -> null
                        }
                    if (reasonError == null && contentError == null && submitError == null)
                        confirm = true
                }
        },
    ) {
        item {
            SheetCard {
                DetailRow("订单编号", id)
                if (order != null) {
                    DetailRow("乘客尾号", order.phoneTail)
                    RouteStops(order, compact = true)
                    BusinessHint(
                        if (order.stage in setOf(Stage.DONE, Stage.CANCELLED, Stage.WAIT_PAY))
                            "本订单可提交投诉，审核结论以服务端为准。"
                        else "当前状态：${order.stage.title}。需先完成或结束订单后再投诉。"
                    )
                } else BusinessHint("正在核对当前账号订单；若无法读取，请点右上角刷新。")
            }
        }
        if (!submitted) {
            item {
                SheetCard {
                    Text("选择投诉类型", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "请选择一项，可点击文字或整行",
                        color = Muted,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                    )
                    Column(Modifier.selectableGroup()) {
                        listOf("乘客未到达上车点", "乘客取消或爽约", "乘客不当行为", "支付或费用问题", "其他问题").forEach { type ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .background(
                                        if (reason == type) Pale else Color.Transparent,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .selectable(
                                        selected = reason == type,
                                        enabled = !submitting,
                                        role = Role.RadioButton,
                                        onClick = {
                                            reason = type
                                            reasonError = null
                                            submitError = null
                                        },
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = reason == type, onClick = null)
                                Text(
                                    type,
                                    fontSize = 18.sp,
                                    lineHeight = 28.sp,
                                    modifier = Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                                )
                            }
                        }
                    }
                    reasonError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    BusinessField(
                        content,
                        {
                            content = it.take(200)
                            contentError = null
                            submitError = null
                        },
                        "请描述具体问题",
                        minLines = 4,
                        help = "至少 5 个字，${content.length}/200",
                        enabled = !submitting,
                        error = contentError,
                    )
                    Spacer(Modifier.height(12.dp))
                    BusinessAttachments(images, { images = it }, 9, store, onMessage)
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .toggleable(
                                value = blockUser,
                                enabled = !submitting,
                                role = Role.Checkbox,
                                onValueChange = { blockUser = it },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(blockUser, onCheckedChange = null)
                        Text(
                            "同时将此合成乘客加入黑名单",
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                    }
                }
            }
        } else item { BusinessEmpty("投诉已提交到演示服务，可在下面查看处理结果。") }
        item { SectionTitle("本订单的投诉记录") }
        if (records.isEmpty()) item { BusinessEmpty("暂无投诉记录") }
        items(records, key = { it.bText("id") }) { record ->
            SheetCard {
                Row {
                    Text(
                        record.bText("reason"),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Pill(bStatus(record.bText("status")))
                }
                Text(
                    record.bText("content"),
                    fontSize = 18.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(record.bText("createdAt"), color = Muted, fontSize = 16.sp)
                if (record.bText("reply", fallback = "").isNotBlank())
                    Text(
                        "处理回复：${record.bText("reply")}",
                        color = Green,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
            }
        }
        item { BusinessHint("投诉仅提交演示管理台，审核结论由管理员确认。上传图片与拉黑操作不会发送给真实平台。") }
    }
    if (confirm)
        BusinessConfirm(
            "确认提交投诉",
            "将所填内容和附件提交演示审核${if (blockUser) "，并保存此乘客的沙箱黑名单记录" else ""}。",
            "提交",
            { confirm = false },
        ) {
            confirm = false
            submitting = true
            submitError = null
            store.perform(
                "order.complaint",
                bJson(
                    "id" to id,
                    "reason" to reason,
                    "content" to content.trim(),
                    "blockUser" to blockUser,
                    "imageBase64" to images.firstOrNull().orEmpty(),
                    "imagesBase64" to JSONArray(images),
                ),
                onFailure = {
                    submitError = it
                    submitting = false
                },
            ) {
                submitting = false
                submitted = true
                images = emptyList()
                onMessage("投诉已提交，可刷新查看处理结果")
            }
        }
}
