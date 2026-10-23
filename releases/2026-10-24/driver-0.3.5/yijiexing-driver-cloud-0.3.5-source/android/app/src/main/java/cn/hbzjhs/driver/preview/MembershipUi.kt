package cn.hbzjhs.driver.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

internal val MembershipGold = Color(0xFF80501C)
internal val MembershipGoldPale = Color(0xFFFFF6E6)

@Composable
internal fun MembershipNotice(title: String, description: String = "", warning: Boolean = false) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (warning) MembershipGoldPale else Pale)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Glyph(
            if (warning) "bell" else "shield",
            if (warning) MembershipGold else Green,
            Modifier.size(19.dp),
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (warning) MembershipGold else Green,
            )
            if (description.isNotBlank())
                Text(
                    description,
                    color = Muted,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
        }
    }
}

@Composable
internal fun MembershipHero(
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    if (gold) listOf(Color(0xFF433824), Color(0xFF766044))
                    else listOf(Color(0xFF124C46), Color(0xFF1D786C))
                )
            )
            .padding(21.dp),
        content = content,
    )
}

@Composable
internal fun MembershipStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(
            label,
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@Composable
internal fun MembershipPlanCard(level: JSONObject, city: String, navigate: (String) -> Unit) {
    val id = mLevelId(level)
    val vip = id == "VIP"
    val enabled = level.optBoolean("saleEnabled", false)
    Surface(
        color = if (vip) Color(0xFFFFFCF7) else Color.White,
        shape = RoundedCornerShape(20.dp),
        border =
            androidx.compose.foundation.BorderStroke(1.dp, if (vip) Color(0xFFE9D6B7) else Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (vip) MembershipGoldPale else Pale),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(
                        if (vip) "gift" else "shield",
                        if (vip) MembershipGold else Green,
                        Modifier.size(25.dp),
                    )
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(level.mText("name"), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${level.mNumber("rightsCount", " 项权益")} · 年度会员",
                        color = Muted,
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!enabled) Pill("暂未开放", Muted, Line)
            else Pill(if (vip) "进阶服务" else "基础接单", if (vip) MembershipGold else Green,
                if (vip) MembershipGoldPale else Pale)
            Row(Modifier.padding(top = 17.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    mMoney(level.mLong("price")),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (vip) MembershipGold else Ink,
                )
                Text(
                    " / 年",
                    color = Muted,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                level.optJSONObject("mapQuota")?.mText("label") ?: "地图额度待配置",
                color = Muted,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(13.dp))
            mRights(level)
                .filter { it.optBoolean("enabled") }
                .take(3)
                .forEach { right ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Glyph("check", if (vip) MembershipGold else Green, Modifier.size(15.dp))
                        Text(
                            right.mText("label") +
                                right.mText("value", "").let { if (it.isBlank()) "" else " · $it" },
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 7.dp),
                        )
                    }
                }
            if (level.optBoolean("dictionaryIncomplete"))
                Text(
                    level.mText("rightsCountNotice", "完整权益字典待确认"),
                    color = Muted,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            Spacer(Modifier.height(15.dp))
            Button(
                onClick = { navigate(mRoute("checkout", city = city, level = id)) },
                enabled = enabled && level.mLong("price") != null,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (vip) MembershipGold else Green
                    ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Text(if (enabled) "开通${level.mText("name")}" else "本城市暂不可购买", fontSize = 20.sp, lineHeight = 28.sp)
            }
        }
    }
}

@Composable
internal fun MembershipCityBar(catalog: JSONObject, page: String, navigate: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    val city = catalog.optJSONObject("city") ?: JSONObject()
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { choosing = true }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph("location", Green, Modifier.size(21.dp))
            Text(
                city.mText("name"),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 7.dp),
            )
            Text("切换 ▾", color = Green, fontSize = 16.sp, modifier = Modifier.padding(start = 9.dp))
        }
        Pill("已发布配置")
    }
    if (choosing)
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("选择浏览城市") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("重新读取该城市的有效价格和权益。浏览城市不会改变已有订阅的生效地区。", fontSize = 16.sp, color = Muted)
                    mObjects(catalog.optJSONArray("cities")).forEach { item ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    choosing = false
                                    navigate(mRoute(page, city = item.mText("id", "")))
                                }
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.mText("name"), fontWeight = FontWeight.Medium)
                                Text(
                                    item.mText("regionName", item.mText("region", "")),
                                    fontSize = 16.sp,
                                    color = Muted,
                                )
                            }
                            if (item.mText("id") == city.mText("id"))
                                Glyph("check", Green, Modifier.size(20.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("关闭") } },
        )
}

@Composable
internal fun MembershipVersionCard(catalog: JSONObject) {
    var expanded by remember { mutableStateOf(false) }
    val sources = catalog.optJSONObject("sources")
    SheetCard {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("价格与权益使用同一发布版本", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "v${catalog.mText("version")} · ${mSource(catalog.mText("source"))}",
                    fontSize = 16.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(if (expanded) "收起" else "来源", color = Green, fontSize = 16.sp)
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                "按字段继承：城市配置 → 大区配置 → 全国默认。下单和支付时由服务端再次校验可售状态及版本。",
                color = Muted,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            )
            val fields =
                listOf(
                    "levels.NORMAL.price" to "普通年费",
                    "levels.VIP.price" to "VIP年费",
                    "trialDays" to "体验天数",
                    "trialOrders" to "体验单量",
                    "levels.NORMAL.params.nearbyKm" to "普通近距离范围",
                    "levels.NORMAL.params.mapQuota.freeOrders" to "普通地图免费额度",
                    "levels.VIP.params.mapQuota.type" to "VIP地图额度规则",
                    "levels.NORMAL.params.offlineNegotiation" to "本城市线下议价",
                )
            fields.forEach { (field, label) ->
                if (sources?.has(field) == true) {
                    val value = sources.opt(field)
                    val text =
                        if (value is JSONObject) value.mText("label", value.mText("source"))
                        else value.toString()
                    Row(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                        Text(label, color = Muted, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(
                            mSource(text),
                            color = Ink,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            val policies = catalog.optJSONArray("pendingPolicies")
            if (policies != null)
                (0 until policies.length()).forEach { i ->
                    Text(
                        "• ${policies.optString(i)}",
                        color = Muted,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            Text(
                "服务时间 ${mDate(catalog.mText("serverTime", ""), true)}",
                color = Muted,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 9.dp),
            )
        }
    }
}

@Composable
internal fun MembershipEmpty(
    title: String,
    text: String,
    button: String? = null,
    onClick: () -> Unit = {},
) {
    SheetCard {
        Glyph("doc", Muted, Modifier.size(32.dp))
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text,
            color = Muted,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (button != null) {
            Spacer(Modifier.height(15.dp))
            PrimaryButton(button, onClick = onClick)
        }
    }
}

@Composable
internal fun MembershipFooter(amount: Long?, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("年度应付  ${mMoney(amount)}", color = Ink, fontWeight = FontWeight.Bold,
                fontSize = 24.sp, lineHeight = 32.sp)
            PrimaryButton(label, enabled = enabled, onClick = onClick)
        }
    }
}

@Composable
internal fun MembershipVersionDetail(version: String, label: String = "成交配置版本") {
    Text(label, color = Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp))
    Text(
        version,
        color = Ink,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}
