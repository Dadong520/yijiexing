package cn.hbzjhs.driver.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.json.JSONObject

/** Reuses the server snapshot already held by ConnectedStore; safe to embed on home/profile. */
@Composable
fun MembershipSummaryCard(store: ConnectedStore, navigate: (String) -> Unit) {
    val payload by store.payload.collectAsState()
    val catalog = payload.optJSONObject("benefits")
    val subscription = catalog?.optJSONObject("subscription")
    val trial = catalog?.optJSONObject("trial")
    val active = subscription != null && subscription.mText("status", "ACTIVE") == "ACTIVE"
    SheetCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Glyph("gift", Green, Modifier.size(26.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (active) subscription!!.mText("levelName") else "司机会员权益",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (active)
                        "${subscription!!.mText("cityName")} · ${mDate(subscription.mText("endAt", ""))} 到期"
                    else if (trial == null) "查看所在城市的有效价格与权益"
                    else if (trial.mText("status") == "ENDED") "体验已结束 · 历史订单与钱包保留"
                    else
                        "体验剩余 ${trial.mNumber("daysRemaining")} 天 · ${trial.mNumber("ordersRemaining")} 单",
                    color = Muted,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
            DriverTextButton(
                onClick = {
                    navigate(
                        if (trial?.mText("status") == "ENDED" && !active) mRoute("expiry")
                        else mRoute()
                    )
                }
            ) {
                Text(if (active) "管理" else "查看")
            }
        }
    }
}

@Composable
internal fun ColumnScope.MembershipInvoiceScreen(
    store: ConnectedStore,
    orderId: String,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    if (orderId.isBlank() || orderId == "—") {
        Box(Modifier.weight(1f).padding(18.dp)) {
            MembershipEmpty("请选择已支付订单", "发票申请需关联已支付的会员订单。", "查看开通记录") {
                navigate(mRoute("records"))
            }
        }
        return
    }
    val path = "/api/benefits/orders/${android.net.Uri.encode(orderId)}"
    val resource = rememberMembershipResource(store, path)
    var kind by rememberSaveable(orderId) { mutableStateOf("COMPANY") }
    var title by rememberSaveable(orderId) { mutableStateOf("") }
    var taxNumber by rememberSaveable(orderId) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var invoice by remember { mutableStateOf<JSONObject?>(null) }
    MembershipLoad(resource, { resource.reload(store, path) }) { raw ->
        val order = mOrder(raw)
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { MembershipNotice("云端沙箱发票申请", "这里只保存申请信息，不连接税务平台，不开具或发送真实电子发票。请使用示例抬头信息。", true) }
            item {
                SheetCard {
                    DetailRow("会员订单", order.mText("levelName", order.mText("level")))
                    DetailRow("下单城市", order.mText("cityName"))
                    DetailRow("实付金额", mMoney(order.mLong("amount")))
                    DetailRow("支付状态", mStatus(order.mText("status")))
                    Text(order.mText("id"), color = Muted, fontSize = 13.sp)
                }
            }
            if (!mPaid(order))
                item {
                    MembershipEmpty("订单尚未支付成功", "只有服务端已确认支付成功的会员订单可提交发票申请。", "查看支付结果") {
                        navigate(mRoute("payment", order = orderId))
                    }
                }
            else if (invoice != null)
                item {
                    SheetCard {
                        Glyph("check", Green, Modifier.size(36.dp))
                        Text(
                            "沙箱申请已保存",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 13.dp),
                        )
                        DetailRow("申请编号", invoice!!.mText("id"))
                        DetailRow("状态", mStatus(invoice!!.mText("status")), Green)
                        DetailRow("发票抬头", invoice!!.mText("title"))
                        Text(
                            "保存成功不代表真实发票已开具。可在开通与申请记录查看。",
                            fontSize = 15.sp,
                            color = Muted,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton("查看申请记录") { navigate(mRoute("records")) }
                    }
                }
            else {
                item { SectionTitle("填写发票信息") }
                item {
                    SheetCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChip(
                                kind == "COMPANY",
                                onClick = { kind = "COMPANY" },
                                label = { Text("企业抬头") },
                                enabled = !submitting,
                            )
                            FilterChip(
                                kind == "PERSONAL",
                                onClick = { kind = "PERSONAL" },
                                label = { Text("个人抬头") },
                                enabled = !submitting,
                            )
                        }
                        OutlinedTextField(
                            value = title,
                            onValueChange = {
                                title = it.take(100)
                                error = null
                            },
                            label = { Text(if (kind == "COMPANY") "企业全称" else "个人姓名") },
                            singleLine = true,
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            supportingText = { Text("云端体验请使用示例信息") },
                        )
                        if (kind == "COMPANY")
                            OutlinedTextField(
                                value = taxNumber,
                                onValueChange = {
                                    taxNumber =
                                        it.uppercase(Locale.ROOT)
                                            .filter { char -> char.isLetterOrDigit() }
                                            .take(20)
                                    error = null
                                },
                                label = { Text("纳税人识别号") },
                                singleLine = true,
                                enabled = !submitting,
                                keyboardOptions =
                                    KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Characters,
                                        keyboardType = KeyboardType.Ascii,
                                    ),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                supportingText = { Text("15、18 或 20 位字母与数字") },
                            )
                        error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(if (submitting) "提交中…" else "提交沙箱申请", enabled = !submitting) {
                            if (title.trim().length < 2) error = "请填写不少于 2 个字符的发票抬头。"
                            else if (
                                kind == "COMPANY" &&
                                    !taxNumber.matches(
                                        Regex("[A-Z0-9]{15}|[A-Z0-9]{18}|[A-Z0-9]{20}")
                                    )
                            )
                                error = "企业税号需为 15、18 或 20 位字母与数字。"
                            else {
                                submitting = true
                                error = null
                                store.perform(
                                    "benefits.invoices",
                                    JSONObject()
                                        .put("orderId", orderId)
                                        .put("type", kind)
                                        .put("title", title.trim())
                                        .put("taxNumber", if (kind == "COMPANY") taxNumber else ""),
                                    onFailure = { message ->
                                        submitting = false
                                        error = message
                                    },
                                ) { response ->
                                    submitting = false
                                    invoice = mResult(response).optJSONObject("invoice")
                                    if (invoice == null) error = "服务端没有返回申请编号，请刷新记录核查。"
                                    else onMessage("云端沙箱发票申请已保存")
                                }
                            }
                        }
                    }
                }
            }
            item {
                DriverTextButton(
                    onClick = { navigate(mRoute("records")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("查看全部开通与发票申请记录")
                }
            }
        }
    }
}

@Composable
internal fun ColumnScope.MembershipTermsScreen(page: String) {
    val renewal = page == "renewal-rules"
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MembershipNotice("联调版规则说明 · 非正式合同", "正式服务提供方、协议文本、支付签约、退款和自动扣款规则需运营、研发及财务确认后发布。", true)
        }
        item {
            SheetCard {
                Text(
                    if (renewal) "自动续费说明" else "司机会员服务说明",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                val sections =
                    if (renewal)
                        listOf(
                            "一、由用户主动选择" to
                                "下单页自动续费默认关闭。主动开启时另行展示说明；本版仅记录沙箱授权状态，不连接支付签约机构，也不会在到期时自动扣款。",
                            "二、价格与时间" to
                                "下次计划时间由演示服务返回，仅用于验证数据流程。真实续费价格、扣款时间、提前通知、失败重试和售卖关闭后的签约终止规则仍待确认，不沿用静态原型的时间承诺。",
                            "三、关闭续费" to "可以在“我的订阅”主动关闭。服务端保存状态后不再安排后续沙箱续费；已购买会员的权益与到期日保持不变。",
                            "四、服务边界" to
                                "关闭售卖、到期与退款等业务条件由服务端判断。演示授权状态不等同于真实支付渠道签约凭证，正式上线必须接入签约、撤销及回调核验。",
                        )
                    else
                        listOf(
                            "一、地区与权益" to
                                "会员按下单城市及所选司机等级提供年度权益，价格、可售状态、地图额度和开放权限由服务端同一有效版本决定。城市字段覆盖优先于大区及全国默认。",
                            "二、支付与生效" to
                                "只有服务端确认支付成功并完成权益开通后，应用才显示会员生效。支付中、失败或取消不会直接产生生效权益，重复成功结果不能重复开通。此版本全部支付为云端沙箱，不会真实扣款。",
                            "三、期限及记录" to
                                "会员订单保存成交时的金额、地区、配置版本及权益快照。切换浏览城市和后续调价不会改写已有成交快照。实际开始及到期时间由服务端返回。",
                            "四、体验结束" to
                                "体验天数或完成单量先达到阈值时结束体验。暂停新增接单、抢单工具、热力图和排名；历史订单、钱包、提现、资质和客服保留。正在服务中的订单允许完成。",
                            "五、续费与优惠" to
                                "协议与自动续费默认不勾选。首年优惠资格、升级差价及剩余期折算未确认前不自动执行。续费需按有效配置和服务端规则处理。",
                            "六、退款及发票" to
                                "原型中的7天未使用退款与停售退款到账时间仍待业务和财务确认，本版不将其作为承诺。发票入口仅保存云端沙箱申请，不开具真实发票。",
                            "七、规则待确认" to "本说明仅供功能和接口联调评审。正式运营前需补齐主体、完整权益字典、隐私说明、争议处理及真实支付与财务服务规则。",
                        )
                sections.forEach { (title, body) ->
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 22.dp),
                    )
                    Text(
                        body,
                        color = Muted,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                }
            }
        }
    }
}
