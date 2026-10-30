package cn.hbzjhs.driver.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MembershipEntry(
    route: String,
    store: ConnectedStore,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val payload by store.payload.collectAsState()
    val page = route.substringBefore('?').removePrefix("benefits").trim('/')
    val city =
        mParam(
            route,
            "city",
            payload.optJSONObject("benefits")?.optJSONObject("city")?.mText("id", "") ?: "",
        )
    val titles =
        mapOf(
            "" to "会员中心",
            "city" to "开通司机等级",
            "compare" to "等级权益对比",
            "checkout" to "确认会员订单",
            "payment" to "支付结果",
            "subscriptions" to "我的订阅",
            "records" to "开通与申请记录",
            "snapshot" to "成交权益快照",
            "expiry" to "接单权限说明",
            "invoice" to "申请电子发票",
            "agreement" to "司机会员服务协议",
            "renewal-rules" to "自动续费说明",
        )
    Column(Modifier.fillMaxSize().background(Page)) {
        PageHeader(titles[page] ?: "会员服务", onBack, "客服") { navigate("support") }
        Row(
            Modifier.fillMaxWidth().background(Pale).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph("shield", Green, Modifier.size(16.dp))
            Text(
                "云端体验沙箱 · 无真实扣款和自动扣款",
                fontSize = 15.sp,
                color = Green,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
        when (page) {
            "agreement",
            "renewal-rules" -> MembershipTermsScreen(page)
            "payment" -> MembershipPaymentScreen(store, mParam(route, "order"), navigate, onMessage)
            "subscriptions",
            "records",
            "snapshot" ->
                MembershipSubscriptionsScreen(page, mParam(route, "id"), store, navigate, onMessage)
            "invoice" -> MembershipInvoiceScreen(store, mParam(route, "order"), navigate, onMessage)
            else -> {
                val path =
                    "/api/benefits/catalog" +
                        if (city.isBlank()) "" else "?city=${android.net.Uri.encode(city)}"
                val resource = rememberMembershipResource(store, path)
                MembershipLoad(resource, onRetry = { resource.reload(store, path) }) { raw ->
                    val catalog = mCatalog(raw)
                    when (page) {
                        "city" -> MembershipCityScreen(catalog, navigate)
                        "compare" ->
                            MembershipCompareScreen(
                                catalog,
                                mParam(route, "level", "NORMAL"),
                                navigate,
                            )
                        "checkout" ->
                            MembershipCheckoutScreen(
                                catalog,
                                mParam(route, "level", "NORMAL"),
                                store,
                                navigate,
                                onMessage,
                            ) {
                                resource.reload(store, path)
                            }
                        "expiry" -> MembershipExpiryScreen(catalog, navigate)
                        else ->
                            MembershipCenterScreen(
                                catalog,
                                payload.optJSONObject("profile") ?: JSONObjectEmpty,
                                navigate,
                            ) {
                                resource.reload(store, path)
                            }
                    }
                }
            }
        }
    }
}

private val JSONObjectEmpty
    get() = org.json.JSONObject()

@Composable
internal fun ColumnScope.MembershipLoad(
    resource: MembershipResource,
    onRetry: () -> Unit,
    content: @Composable ColumnScope.(org.json.JSONObject) -> Unit,
) {
    if (resource.busy && resource.data == null) {
        Column(
            Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
            Text(
                "正在读取已发布的会员配置",
                color = Muted,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 15.dp),
            )
        }
    } else if (resource.data == null) {
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Glyph("bell", Orange, Modifier.size(42.dp))
            Text(
                "暂时无法读取",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                resource.error ?: "请检查网络及演示服务是否已启动",
                color = Muted,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            PrimaryButton("重新加载", onClick = onRetry)
        }
    } else {
        if (resource.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        resource.error?.let { error ->
            Row(
                Modifier.fillMaxWidth().background(Color(0xFFFFF2E6)).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(error, color = Orange, fontSize = 15.sp, modifier = Modifier.weight(1f))
                DriverTextButton(onClick = onRetry) { Text("重试") }
            }
        }
        content(resource.data!!)
    }
}
