package cn.hbzjhs.driver.preview

import android.net.Uri
import androidx.compose.runtime.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Presentation-only adapters. Prices, grants and subscription snapshots are supplied by the
 * service.
 */
internal fun mObjects(array: JSONArray?): List<JSONObject> =
    if (array == null) emptyList()
    else (0 until array.length()).mapNotNull { array.optJSONObject(it) }

internal fun JSONObject.mText(key: String, fallback: String = "—"): String =
    if (!has(key) || isNull(key) || optString(key).isBlank()) fallback else optString(key)

internal fun JSONObject.mLong(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

internal fun JSONObject.mNumber(key: String, suffix: String = ""): String =
    mLong(key)?.let { "$it$suffix" } ?: "—"

internal fun mMoney(value: Long?): String = value?.let { "¥${money(it)}" } ?: "待服务端报价"

internal fun mDate(raw: String, time: Boolean = false): String =
    runCatching {
            OffsetDateTime.parse(raw)
                .format(
                    DateTimeFormatter.ofPattern(
                        if (time) "yyyy.MM.dd HH:mm" else "yyyy.MM.dd",
                        Locale.CHINA,
                    )
                )
        }
        .getOrDefault(raw.ifBlank { "—" })

internal fun mCatalog(raw: JSONObject): JSONObject =
    raw.optJSONObject("catalog") ?: raw.optJSONObject("benefits") ?: raw

internal fun mResult(raw: JSONObject): JSONObject = raw.optJSONObject("result") ?: raw

internal fun mLevelId(raw: JSONObject): String = raw.mText("id", raw.mText("level", ""))

internal fun mLevels(raw: JSONObject): List<JSONObject> = mObjects(raw.optJSONArray("levels"))

internal fun mLevel(raw: JSONObject, id: String): JSONObject? =
    mLevels(raw).firstOrNull { mLevelId(it) == id }

internal fun mRights(raw: JSONObject): List<JSONObject> = mObjects(raw.optJSONArray("rights"))

internal fun mSnapshot(raw: JSONObject): JSONObject =
    raw.optJSONObject("rightsSnapshot") ?: JSONObject()

internal fun mSource(value: String): String =
    when (value.uppercase()) {
        "CITY" -> "城市配置"
        "REGION" -> "大区继承"
        "NATIONAL",
        "DEFAULT" -> "全国默认"
        else -> value
    }

internal fun mStatus(value: String): String =
    when (value.uppercase()) {
        "ACTIVE" -> "生效中"
        "EXPIRED" -> "已到期"
        "SUCCESS",
        "PAID" -> "支付成功"
        "PROCESSING",
        "PAYING" -> "处理中"
        "PENDING" -> "待支付"
        "CREATED",
        "UNPAID",
        "WAIT_PAY" -> "待支付"
        "FAILED" -> "支付失败"
        "CANCELLED",
        "CANCEL" -> "已取消"
        "SUBMITTED",
        "SANDBOX_ACCEPTED" -> "已受理（沙箱）"
        "RENEWED" -> "已续期"
        "ENDED" -> "已结束"
        "NOT_STARTED" -> "未开始"
        else -> value
    }

internal fun mRoute(
    page: String = "",
    city: String = "",
    level: String = "",
    order: String = "",
    id: String = "",
): String {
    val params =
        listOf("city" to city, "level" to level, "order" to order, "id" to id).filter {
            it.second.isNotBlank()
        }
    val base = "benefits" + if (page.isBlank()) "" else "/$page"
    return base +
        if (params.isEmpty()) ""
        else
            params.joinToString(prefix = "?", separator = "&") {
                "${it.first}=${Uri.encode(it.second)}"
            }
}

internal fun mParam(route: String, key: String, fallback: String = ""): String =
    Uri.parse("https://local/$route").getQueryParameter(key) ?: fallback

@Stable
internal class MembershipResource {
    var data by mutableStateOf<JSONObject?>(null)
    var busy by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    private var generation = 0

    fun reload(store: ConnectedStore, path: String) {
        val request = ++generation
        busy = true
        error = null
        store.query(
            path,
            onFailure = { message ->
                if (request == generation) {
                    error = message
                    busy = false
                }
            },
        ) { response ->
            if (request == generation) {
                data = response
                busy = false
                error = null
            }
        }
    }
}

@Composable
internal fun rememberMembershipResource(
    store: ConnectedStore,
    path: String,
    key: Any = Unit,
): MembershipResource {
    val resource = remember(path) { MembershipResource() }
    LaunchedEffect(path, key) { resource.reload(store, path) }
    return resource
}
