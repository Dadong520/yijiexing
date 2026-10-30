@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package cn.hbzjhs.driver.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

@Composable
internal fun ColumnScope.MembershipCenterScreen(
    catalog: JSONObject,
    profile: JSONObject,
    navigate: (String) -> Unit,
    refresh: () -> Unit,
) {
    val city = catalog.optJSONObject("city") ?: JSONObject()
    val trial = catalog.optJSONObject("trial") ?: JSONObject()
    val subscription = catalog.optJSONObject("subscription")
    val active = subscription != null && subscription.mText("status", "ACTIVE") == "ACTIVE"
    val currentLevel =
        if (active) mSnapshot(subscription!!) else mLevel(catalog, "TRIAL") ?: JSONObject()
    var detail by remember { mutableStateOf<Pair<String, String>?>(null) }
    val driver = profile.optJSONObject("driver") ?: profile
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MembershipCityBar(catalog, "", navigate) }
        item {
            MembershipHero(gold = active && subscription?.mText("level") == "VIP") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = .14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Glyph("profile", Color.White, Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            driver.mText("name", "司机师傅"),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "${city.mText("name")} · ${if (active) subscription!!.mText("levelName") else "体验司机"}",
                            color = Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Pill(if (active) "会员生效中" else mStatus(trial.mText("status")), Green, Pale)
                Spacer(Modifier.height(22.dp))
                if (active) {
                    Row(Modifier.fillMaxWidth()) {
                        MembershipStat(
                            subscription!!.mNumber("daysRemaining", " 天"),
                            "剩余有效期",
                            Modifier.weight(1f),
                        )
                        MembershipStat(
                            currentLevel.mNumber("rightsCount", " 项"),
                            "成交权益",
                            Modifier.weight(1f),
                        )
                    }
                    Text("订阅地区：${subscription!!.mText("cityName")}", color = Color.White,
                        fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp))
                    Text(
                        "有效期至 ${mDate(subscription!!.mText("endAt", ""))}",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        MembershipStat(
                            trial.mNumber("daysRemaining", " 天"),
                            "剩余体验",
                            Modifier.weight(1f),
                        )
                        MembershipStat(
                            trial.mNumber("ordersRemaining", " 单"),
                            "剩余完单额度",
                            Modifier.weight(1f),
                        )
                    }
                    val days =
                        trial.optDouble("daysUsed", 0.0) /
                            trial.optDouble("daysTotal", 1.0).coerceAtLeast(1.0)
                    val rides =
                        trial.optDouble("ordersCompleted", 0.0) /
                            trial.optDouble("ordersLimit", 1.0).coerceAtLeast(1.0)
                    LinearProgressIndicator(
                        progress = maxOf(days, rides).toFloat().coerceIn(0f, 1f),
                        modifier =
                            Modifier.fillMaxWidth()
                                .padding(top = 18.dp)
                                .height(5.dp)
                                .clip(CircleShape),
                        color = Color(0xFFB1E9D6),
                        trackColor = Color.White.copy(alpha = .16f),
                    )
                    Text(
                        "${trial.mNumber("daysTotal")} 天或 ${trial.mNumber("ordersLimit")} 单，先达到者结束体验",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                OutlinedButton(
                    onClick = {
                        navigate(
                            mRoute(
                                if (active) "subscriptions" else "city",
                                city = city.mText("id", ""),
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).heightIn(min = 52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = .38f),
                        ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (active) "管理我的订阅" else "选择年度会员，延续接单权益")
                }
            }
        }
        if (!active && trial.mText("status") == "ENDED")
            item { MembershipNotice("体验已结束，暂停新增接单", "完成中的行程可以继续。订单、钱包、提现、资质及客服保持可访问。", true) }
        item {
            SectionTitle("选择司机等级", "价格以当前城市生效配置为准", "权益对比") {
                navigate(mRoute("compare", city = city.mText("id", "")))
            }
        }
        items(mLevels(catalog).count { mLevelId(it) != "TRIAL" }) { index ->
            MembershipPlanCard(
                mLevels(catalog).filter { mLevelId(it) != "TRIAL" }[index],
                city.mText("id", ""),
                navigate,
            )
        }
        item { SectionTitle("当前核心权益", "按当前等级及所在地区实际开放") }
        item {
            SheetCard {
                val visibleRights = mRights(currentLevel)
                val rights =
                    listOf(
                            "map",
                            "withdraw",
                            "heatmap",
                            "ranking",
                            "blacklist",
                            "vipBadge",
                            "vehicleChange",
                            "support",
                        )
                        .mapNotNull { key -> visibleRights.firstOrNull { it.mText("key") == key } }
                        .ifEmpty { visibleRights.take(8) }
                if (rights.isEmpty()) Text("当前未返回可见权益，请刷新配置。", color = Muted, fontSize = 13.sp)
                rights.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { right ->
                            Column(
                                Modifier.weight(1f)
                                    .clickable {
                                        detail =
                                            right.mText("label") to
                                                ("${if (right.optBoolean("enabled")) "当前可用" else "当前未开放"}\n${right.mText("value", "以该城市已发布权益配置为准")}\n配置来源：${mSource(right.mText("source", catalog.mText("source")))}")
                                    }
                                    .padding(vertical = 12.dp, horizontal = 5.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Glyph(
                                    when (right.mText("key").lowercase()) {
                                        "map",
                                        "map_service" -> "location"
                                        "wallet",
                                        "withdraw",
                                        "withdrawal" -> "wallet"
                                        "ranking",
                                        "heatmap" -> "chart"
                                        "vipbadge" -> "gift"
                                        "vehiclechange" -> "car"
                                        "support" -> "support"
                                        else -> "shield"
                                    },
                                    if (right.optBoolean("enabled")) Green else Muted,
                                    Modifier.size(25.dp),
                                )
                                Text(
                                    right.mText("label"),
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 9.dp),
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    if (right.optBoolean("enabled")) "已开放" else "未开放",
                                    color = if (right.optBoolean("enabled")) Green else Muted,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            SheetCard {
                MenuRow(
                    "doc",
                    "我的订阅",
                    if (active) subscription!!.mText("levelName") else "查看订阅与记录",
                ) {
                    navigate(mRoute("subscriptions"))
                }
                HorizontalDivider(color = Line)
                MenuRow("orders", "开通与发票记录") { navigate(mRoute("records")) }
                HorizontalDivider(color = Line)
                MenuRow("bell", "体验结束与保留功能") {
                    navigate(mRoute("expiry", city = city.mText("id", "")))
                }
            }
        }
        item { SectionTitle("常见问题") }
        item {
            SheetCard {
                listOf(
                        "开通后多久生效？" to
                            "支付状态和权益开通结果由服务端确认。只有返回支付成功且订阅生效，才恢复对应地区与等级的接单能力。状态未知时可以在支付结果页刷新。",
                        "体验结束后还能访问什么？" to
                            "历史订单、钱包、提现、资质和客服继续保留。新增接单、抢单工具、热力图和排名暂停；正在服务的订单允许完成。开通后无需重复提交已通过审核的资质。",
                        "切换城市会改变现有订阅吗？" to "切换的是浏览城市。既有会员的地区、成交金额、到期时间和权益按购买时快照保留；新购根据所选城市的有效配置报价。",
                        "退款与升级如何办理？" to "首年优惠、升级补差、7天未使用退款及停售退款时限尚待运营确认，本版不自动折算或承诺退款。需要业务协助可进入客服。",
                    )
                    .forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = Line)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { detail = item }
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(item.first, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Glyph("right", Muted, Modifier.size(16.dp))
                        }
                    }
            }
        }
        item { MembershipVersionCard(catalog) }
        item {
            OutlinedButton(onClick = refresh, modifier = Modifier.fillMaxWidth()) {
                Text("刷新已发布配置")
            }
        }
    }
    detail?.let { item ->
        DriverDialog(
            onDismissRequest = { detail = null },
            title = { Text(item.first) },
            text = { Text(item.second, fontSize = 14.sp, lineHeight = 24.sp) },
            confirmButton = { DriverTextButton(onClick = { detail = null }) { Text("知道了") } },
        )
    }
}

@Composable
internal fun ColumnScope.MembershipCityScreen(catalog: JSONObject, navigate: (String) -> Unit) {
    val city = catalog.optJSONObject("city") ?: JSONObject()
    val trial = catalog.optJSONObject("trial") ?: JSONObject()
    val subscription = catalog.optJSONObject("subscription")
    val active = subscription != null && subscription.mText("status", "ACTIVE") == "ACTIVE"
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MembershipCityBar(catalog, "city", navigate) }
        item {
            if (active) {
                MembershipNotice(
                    "已开通${subscription!!.mText("levelName")} · ${subscription.mText("cityName")}",
                    "有效期至 ${mDate(subscription.mText("endAt", ""))}。在期权益按原成交快照保留；升级与提前续期规则以服务端校验为准。",
                )
                DriverTextButton(
                    onClick = { navigate(mRoute("subscriptions")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("管理已有订阅")
                }
            } else
                MembershipNotice(
                    "体验${if (trial.mText("status") == "ENDED") "已结束" else "进度"} · 剩余 ${trial.mNumber("daysRemaining")} 天 / ${trial.mNumber("ordersRemaining")} 单",
                    "${trial.mNumber("daysTotal")} 天或 ${trial.mNumber("ordersLimit")} 单先达到者结束；体验结束保留钱包及历史订单。",
                    trial.mText("status") == "ENDED",
                )
        }
        item {
            SectionTitle("选择适合你的等级", "年付 · 由${city.mText("name")}运营配置决定", "完整对比") {
                navigate(mRoute("compare", city = city.mText("id", "")))
            }
        }
        items(mLevels(catalog).count { mLevelId(it) != "TRIAL" }) { index ->
            MembershipPlanCard(
                mLevels(catalog).filter { mLevelId(it) != "TRIAL" }[index],
                city.mText("id", ""),
                navigate,
            )
        }
        item {
            SheetCard {
                Text("购买说明", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "• 仅支持年度套餐，半年与月度尚未开放。\n• 所有金额由服务端计算；选购、对比、订单使用同一发布版本。\n• 未确认优惠资格前不自动使用首年折扣或升级补差。\n• 自动续费需主动选择；本版仅记录沙箱授权状态。\n• 在期会员权益和成交价按购买快照保留。",
                    color = Muted,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
        item { MembershipVersionCard(catalog) }
    }
}

@Composable
internal fun ColumnScope.MembershipCompareScreen(
    catalog: JSONObject,
    initialLevel: String,
    navigate: (String) -> Unit,
) {
    val levels = mLevels(catalog)
    var selected by rememberSaveable(initialLevel) { mutableStateOf(initialLevel) }
    val current = levels.firstOrNull { mLevelId(it) == selected } ?: levels.firstOrNull()
    val city = catalog.optJSONObject("city") ?: JSONObject()
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MembershipCityBar(catalog, "compare", navigate) }
        item {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                levels.forEach { level ->
                    FilterChip(
                        selected = mLevelId(level) == selected,
                        onClick = { selected = mLevelId(level) },
                        label = { Text(level.mText("name"), fontSize = 15.sp) },
                        modifier = Modifier.heightIn(min = 52.dp),
                    )
                }
            }
        }
        current?.let { level ->
            item {
                MembershipHero(gold = selected == "VIP") {
                    Column(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                level.mText("name"),
                                color = Color.White,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                level.mNumber("rightsCount", " 项权益"),
                                color = Color.White,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(top = 7.dp),
                            )
                        }
                        Text(
                            mMoney(level.mLong("price")),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                        )
                    }
                    Text(
                        level.optJSONObject("mapQuota")?.mText("label") ?: "地图参数未提供",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 17.dp),
                    )
                }
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("等级权益明细", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val rights = levels.flatMap { mRights(it) }.distinctBy { it.mText("key") }
                    val groups = rights.groupBy { it.mText("group", "等级权益") }
                    groups.forEach { (group, rows) ->
                        Text(group, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Green,
                            modifier = Modifier.padding(top = 20.dp, bottom = 12.dp))
                        rows.forEach { right ->
                            Text(right.mText("label"), fontSize = 15.sp, lineHeight = 22.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                            levels.forEach { level ->
                                val value = mRights(level).firstOrNull { it.mText("key") == right.mText("key") }
                                val enabled = value?.optBoolean("enabled") == true
                                DetailRow(level.mText("name"), if (enabled) value!!.mText("value", "支持") else "未包含",
                                    if (mLevelId(level) == selected) Green else Ink)
                            }
                            HorizontalDivider(color = Line)
                        }
                    }
                    if (rights.isEmpty())
                        Text("暂无可见权益字典，请刷新配置。", color = Muted, modifier = Modifier.padding(16.dp))
                }
            }
        }
        current
            ?.mText("rightsCountNotice", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { note -> item { MembershipNotice("可见权益字典范围", note) } }
        item {
            MembershipNotice(
                "地区限制优先",
                "矩阵已经包含${city.mText("name")}的地区限制。某项权益为“—”时，不因购买VIP而越过地区关闭规则。",
            )
        }
        item { MembershipVersionCard(catalog) }
    }
    MembershipFooter(
        current?.mLong("price"),
        if (selected == "TRIAL") "体验按规则生效"
        else if (current?.optBoolean("saleEnabled") == true) "立即开通" else "本城市暂未开放",
        selected != "TRIAL" && current?.optBoolean("saleEnabled") == true,
    ) {
        navigate(mRoute("checkout", city = city.mText("id", ""), level = selected))
    }
}

@Composable
internal fun ColumnScope.MembershipExpiryScreen(catalog: JSONObject, navigate: (String) -> Unit) {
    val trial = catalog.optJSONObject("trial") ?: JSONObject()
    val city = catalog.optJSONObject("city") ?: JSONObject()
    val ended = trial.mText("status") == "ENDED"
    val subscription = catalog.optJSONObject("subscription")
    val active = subscription != null && subscription.mText("status", "ACTIVE") == "ACTIVE"
    LazyColumn(
        Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            MembershipHero {
                Glyph("bell", Color(0xFFFFD49A), Modifier.size(38.dp))
                Text(
                    if (active) "会员权益已恢复" else if (ended) "体验期已结束" else "体验结束后的接单规则",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 15.dp),
                )
                Text(
                    if (active)
                        "${subscription!!.mText("levelName")} · ${subscription.mText("cityName")} · 有效期至 ${mDate(subscription.mText("endAt", ""))}"
                    else if (ended) trial.mText("reason", "已达到体验期规则上限")
                    else "当前体验中，达到天数或完成单量任一阈值时结束。",
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    MembershipStat(
                        "${trial.mNumber("daysUsed")}/${trial.mNumber("daysTotal")}",
                        "体验天数",
                        Modifier.weight(1f),
                    )
                    MembershipStat(
                        "${trial.mNumber("ordersCompleted")}/${trial.mNumber("ordersLimit")}",
                        "已完成单量",
                        Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            MembershipNotice(
                if (active) "当前城市会员已生效" else "服务中的订单允许完成",
                if (active) "当前按已购等级及所在地区开放接单权益。下方介绍体验结束的一般规则，已有付费会员以生效订阅为准。"
                else "体验结束且无有效付费订阅时，暂停新增接单、抢单工具、热力图和排名。是否可接单由服务端检查，界面刷新不会绕过限制。",
                !active,
            )
        }
        item { SectionTitle("以下功能继续保留") }
        item {
            SheetCard {
                MenuRow("orders", "历史订单", "查看已有行程") { navigate("orders") }
                HorizontalDivider(color = Line)
                MenuRow("wallet", "钱包与提现", "已有余额继续可用") { navigate("wallet") }
                HorizontalDivider(color = Line)
                MenuRow("shield", "资质与证件", "已通过审核的资质保留") { navigate("certificates") }
                HorizontalDivider(color = Line)
                MenuRow("support", "联系客服", "查询规则或反馈问题") { navigate("support") }
            }
        }
        item { SectionTitle("恢复对应等级的接单能力", "开通后无需重复提交已通过审核的资质") }
        items(mLevels(catalog).count { mLevelId(it) != "TRIAL" }) { index ->
            MembershipPlanCard(
                mLevels(catalog).filter { mLevelId(it) != "TRIAL" }[index],
                city.mText("id", ""),
                navigate,
            )
        }
        item {
            DriverTextButton(
                onClick = { navigate(mRoute("subscriptions")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("已有订阅？查看生效状态")
            }
        }
        if (trial.mText("policy", "").isNotBlank())
            item { MembershipNotice("当前联调体验计数规则", trial.mText("policy")) }
        item { MembershipVersionCard(catalog) }
    }
}
