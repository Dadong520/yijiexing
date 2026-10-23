package cn.hbzjhs.driver.preview

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LocalApiException(val status: Int, message: String, val details: JSONObject = JSONObject()) :
    Exception(message)

/** Values stay in app-private preferences; the encryption key never leaves AndroidKeyStore. */
private class EncryptedValueVault(context: Context, private val name: String) {
    private val prefs = context.getSharedPreferences(name, 0)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (ks.getKey(name, null) as? SecretKey)
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                name,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                }
                .generateKey()
    }

    fun save(value: String) {
        val cipher =
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        prefs
            .edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(
                "value",
                Base64.encodeToString(
                    cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
                    Base64.NO_WRAP,
                ),
            )
            .apply()
    }

    fun load(): String =
        runCatching {
                val encoded = prefs.getString("value", null) ?: return ""
                val iv = prefs.getString("iv", null) ?: return ""
                String(
                    Cipher.getInstance("AES/GCM/NoPadding")
                        .apply {
                            init(
                                Cipher.DECRYPT_MODE,
                                key(),
                                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
                            )
                        }
                        .doFinal(Base64.decode(encoded, Base64.NO_WRAP)),
                    Charsets.UTF_8,
                )
            }
            .getOrDefault("")

    fun clear() {
        prefs.edit().clear().apply()
    }
}

internal class SessionVault(context: Context) {
    private val vault = EncryptedValueVault(context, "yij_cloud_session_v3")

    fun save(token: String) = vault.save(token)

    fun load(): String = vault.load()

    fun clear() = vault.clear()
}

internal class DemoAccessVault(context: Context) {
    private val vault = EncryptedValueVault(context, "yij_cloud_access_v3")

    fun save(value: String) = vault.save(value)

    fun load(): String = vault.load()

    fun clear() = vault.clear()
}

/** Only this build's exact, non-secret service origin is allowed. Empty builds fail closed. */
internal fun validateCloudEndpoint(
    value: String,
    expectedOrigin: String = BuildConfig.CLOUD_API_ORIGIN,
): String {
    require(expectedOrigin.isNotBlank()) { "此安装包尚未配置云端服务，请安装提供的正式体验包" }
    val uri = URI(value.trim().trimEnd('/'))
    val expected = URI(expectedOrigin)
    require(
        uri.scheme == "https" &&
            uri.host != null &&
            uri.host == expected.host &&
            uri.port == -1 &&
            uri.rawUserInfo == null &&
            uri.query == null &&
            uri.fragment == null &&
            uri.path.orEmpty().isEmpty() &&
            uri.toString() == expectedOrigin &&
            expected.scheme == "https" &&
            expected.port == -1 &&
            expected.path.orEmpty().isEmpty()
    ) {
        "云端体验版只能连接此安装包指定的独立 HTTPS 服务"
    }
    return uri.toString()
}

internal fun validateDemoAccess(value: String): String {
    val access = value.trim()
    require(access.length in 12..256 && access.all { it.code in 33..126 }) {
        "请完整粘贴演示访问码（12 至 256 位，不含空格）"
    }
    return access
}

object LocalApi {
    val baseUrl: String = BuildConfig.CLOUD_API_ORIGIN
    @Volatile var demoAccess: String = ""
    @Volatile var token: String = ""

    suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        authenticated: Boolean = true,
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val origin = validateCloudEndpoint(baseUrl)
            if (demoAccess.isBlank())
                throw LocalApiException(
                    403,
                    "请先在连接设置中输入演示访问码",
                    JSONObject().put("reason", "DEMO_ACCESS_REQUIRED"),
                )
            require(
                path.startsWith("/api/") &&
                    !path.startsWith("//") &&
                    !path.contains('\\') &&
                    !path.any { it.code < 32 }
            ) {
                "无效的服务请求路径"
            }
            val connection = URL(origin + path).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = method
                connection.connectTimeout = 20000
                connection.readTimeout = 75000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "YiJieXing-Android-Cloud/0.3.1")
                connection.setRequestProperty("X-Demo-Access", demoAccess)
                if (authenticated && token.isNotBlank())
                    connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                val text =
                    (if (status in 200..299) connection.inputStream else connection.errorStream)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        .orEmpty()
                val envelope =
                    runCatching { JSONObject(text) }
                        .getOrElse {
                            throw LocalApiException(
                                status,
                                if (status == 503) "云端服务暂未就绪，可能正在启动或已暂停，请稍后重试"
                                else "云端服务返回了无法识别的响应，请稍后重试",
                            )
                        }
                if (status !in 200..299 || envelope.optInt("code", -1) != 0)
                    throw LocalApiException(
                        status,
                        envelope.optString("message", envelope.optString("msg", "请求未完成")),
                        envelope.optJSONObject("details") ?: JSONObject(),
                    )
                envelope.optJSONObject("data") ?: JSONObject()
            } finally {
                connection.disconnect()
            }
        }
}

class ConnectedStore(app: Application) : AndroidViewModel(app) {
    private val vault = SessionVault(app)
    private val accessVault = DemoAccessVault(app)
    private val mutableAccessConfigured = MutableStateFlow(false)
    val accessConfigured = mutableAccessConfigured.asStateFlow()
    private val mutableSessionAvailable = MutableStateFlow(false)
    val sessionAvailable = mutableSessionAvailable.asStateFlow()
    private val mutableState = MutableStateFlow(emptyState())
    private val mutablePayload = MutableStateFlow(JSONObject())
    private val mutableLoading = MutableStateFlow(false)
    private val mutableError = MutableStateFlow<String?>(null)
    private val mutableConnected = MutableStateFlow(false)
    private val mutableInitialized = MutableStateFlow(false)
    private var busyCount = 0
    private var refreshing = false
    private var automaticRefreshEnabled = true
    private var nextAutomaticRefreshAt = 0L
    private var transientFailureCount = 0
    private val pendingKeys = ConcurrentHashMap<String, String>()
    val state = mutableState.asStateFlow()
    val payload = mutablePayload.asStateFlow()
    val loading = mutableLoading.asStateFlow()
    val error = mutableError.asStateFlow()
    val connected = mutableConnected.asStateFlow()
    val initialized = mutableInitialized.asStateFlow()
    val endpoint: String
        get() = LocalApi.baseUrl

    init {
        LocalApi.demoAccess = accessVault.load()
        mutableAccessConfigured.value = LocalApi.demoAccess.isNotBlank()
        LocalApi.token = vault.load()
        mutableSessionAvailable.value = LocalApi.token.isNotBlank()
        if (LocalApi.token.isNotBlank() && LocalApi.demoAccess.isNotBlank()) refresh()
        else mutableInitialized.value = true
    }

    fun clearError() {
        mutableError.value = null
    }

    fun updateDemoAccess(value: String): Boolean =
        runCatching {
                val access = validateDemoAccess(value)
                accessVault.save(access)
                LocalApi.demoAccess = access
                mutableAccessConfigured.value = true
                mutableError.value = null
                if (LocalApi.token.isNotBlank()) refresh()
                true
            }
            .getOrElse {
                mutableError.value = it.message ?: "演示访问码保存失败"
                false
            }

    fun clearDemoAccess() {
        accessVault.clear()
        LocalApi.demoAccess = ""
        mutableAccessConfigured.value = false
        logoutLocal()
    }

    private fun launchRequest(
        showLoading: Boolean = true,
        onFailure: (String) -> Unit = {},
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            if (showLoading) {
                busyCount++
                mutableLoading.value = true
            }
            try {
                if (showLoading) mutableError.value = null
                block()
                mutableConnected.value = true
                automaticRefreshEnabled = true
                transientFailureCount = 0
                nextAutomaticRefreshAt = 0L
            } catch (e: Exception) {
                val message =
                    when (e) {
                        is LocalApiException ->
                            when (e.details.optString("reason")) {
                                "DEMO_ACCESS_REQUIRED",
                                "DEMO_ACCESS_INVALID" -> "演示访问码未设置或已失效，请在连接设置中更新"
                                "DEMO_PAUSED" -> "云端演示服务已暂停，请在电脑上恢复服务后重试"
                                else -> e.message ?: "请求失败"
                            }
                        is java.net.SocketTimeoutException ->
                            "云端服务启动或响应超时，请等待约 30 秒后重试。请勿重复提交支付或提现。"
                        is IllegalArgumentException -> e.message ?: "云端连接配置有误"
                        else -> "暂时无法连接云端服务。请检查网络；若服务已停止，请先在电脑上恢复后重试"
                    }
                val gateFailure =
                    e is LocalApiException &&
                        e.details.optString("reason") in
                            setOf("DEMO_ACCESS_REQUIRED", "DEMO_ACCESS_INVALID")
                if (e is LocalApiException && e.status == 401 && !gateFailure) logoutLocal()
                val wasConnected = mutableConnected.value
                if (e !is LocalApiException || gateFailure || e.status == 503)
                    mutableConnected.value = false
                if (gateFailure || e is LocalApiException && e.status == 503) {
                    // A paused demo must not be kept awake by the app's polling loop.
                    automaticRefreshEnabled = false
                } else if (e !is LocalApiException) {
                    transientFailureCount = (transientFailureCount + 1).coerceAtMost(3)
                    nextAutomaticRefreshAt =
                        android.os.SystemClock.elapsedRealtime() +
                            (10000L shl transientFailureCount)
                }
                if (showLoading || wasConnected || e is LocalApiException && e.status == 401)
                    mutableError.value = message
                onFailure(message)
            } finally {
                if (showLoading) {
                    busyCount--
                    mutableLoading.value = busyCount > 0
                }
                mutableInitialized.value = true
            }
        }
    }

    fun requestCode(mobile: String, onFailure: (String) -> Unit = {}, done: (JSONObject) -> Unit) =
        launchRequest(onFailure = onFailure) {
            val data =
                LocalApi.request(
                    "POST",
                    "/api/auth/code",
                    JSONObject().put("mobile", mobile),
                    false,
                )
            done(data)
        }

    fun login(mobile: String, code: String) = launchRequest {
        val data =
            LocalApi.request(
                "POST",
                "/api/auth/login",
                JSONObject().put("mobile", mobile).put("code", code),
                false,
            )
        val token = data.getString("token")
        vault.save(token)
        LocalApi.token = token
        mutableSessionAvailable.value = true
        accept(data)
    }

    fun logoutLocal() {
        vault.clear()
        LocalApi.token = ""
        mutableSessionAvailable.value = false
        mutableState.value = emptyState()
        mutablePayload.value = JSONObject()
        pendingKeys.clear()
    }

    fun logout() = launchRequest {
        runCatching { LocalApi.request("POST", "/api/auth/logout", JSONObject()) }
        logoutLocal()
    }

    fun refresh(silent: Boolean = false) {
        if (refreshing || LocalApi.token.isBlank() || LocalApi.demoAccess.isBlank()) return
        if (
            silent &&
                (!automaticRefreshEnabled ||
                    android.os.SystemClock.elapsedRealtime() < nextAutomaticRefreshAt)
        )
            return
        refreshing = true
        launchRequest(!silent) {
            try {
                accept(LocalApi.request("GET", "/api/state"))
            } finally {
                refreshing = false
            }
        }
    }

    fun query(path: String, done: (JSONObject) -> Unit) = query(path, {}, done)

    fun query(path: String, onFailure: (String) -> Unit, done: (JSONObject) -> Unit) =
        launchRequest(onFailure = onFailure) {
            val data = LocalApi.request("GET", path)
            if (data.has("state")) accept(data)
            done(data)
        }

    fun perform(
        action: String,
        payload: JSONObject = JSONObject(),
        done: (JSONObject) -> Unit = {},
    ) = perform(action, payload, {}, done)

    fun perform(
        action: String,
        payload: JSONObject,
        onFailure: (String) -> Unit,
        done: (JSONObject) -> Unit,
    ) {
        val signature = action + payload.toString()
        val key = pendingKeys.computeIfAbsent(signature) { UUID.randomUUID().toString() }
        launchRequest(onFailure = { message -> onFailure(message) }) {
            val path =
                when (action) {
                    "benefits.orders" -> "/api/benefits/orders"
                    "benefits.pay" -> "/api/benefits/pay"
                    "benefits.renewal" -> "/api/benefits/renewal"
                    "benefits.invoices" -> "/api/benefits/invoices"
                    else -> "/api/action"
                }
            val body =
                if (path == "/api/action")
                    JSONObject().put("action", action).put("payload", payload).put("requestId", key)
                else
                    JSONObject(payload.toString())
                        .put("idempotencyKey", payload.optString("idempotencyKey", key))
            val data =
                try {
                    LocalApi.request("POST", path, body)
                } catch (e: LocalApiException) {
                    // A 5xx response can be transient. Keep the same operation ID for retries.
                    if (e.status in 400..499) pendingKeys.remove(signature)
                    throw e
                }
            if (data.has("state")) {
                // Invitations are shown only by their owning UI and must not enter general UI
                // payloads.
                val stateData =
                    if (action == "order.pairOffer")
                        JSONObject(data.toString()).apply { remove("result") }
                    else data
                accept(stateData)
            } else accept(LocalApi.request("GET", "/api/state"))
            pendingKeys.remove(signature)
            done(data)
        }
    }

    private fun accept(data: JSONObject) {
        // A request started before logout may finish after the local session was cleared.
        if (LocalApi.token.isBlank()) return
        val s = data.optJSONObject("state") ?: return
        mutableState.value = parseDriverState(s)
        mutablePayload.value = JSONObject(data.toString())
    }

    companion object {
        fun emptyState() =
            DriverState(
                loggedIn = false,
                orders = emptyList(),
                balance = 0,
                todayIncome = 0,
                todayAccepted = 0,
                todayCompleted = 0,
                entries = emptyList(),
            )

        fun parseDriverState(s: JSONObject): DriverState {
            val rows = s.optJSONArray("orders")
            val orders =
                (0 until (rows?.length() ?: 0)).map { i ->
                    val o = rows!!.getJSONObject(i)
                    val rawWaypoints = o.optJSONArray("waypoints")
                    val waypoints =
                        (0 until (rawWaypoints?.length() ?: 0)).map { index ->
                            val raw = rawWaypoints!!.opt(index)
                            val point = raw as? JSONObject
                            val name =
                                listOf("waypointName", "name", "address", "title")
                                    .firstNotNullOfOrNull { key ->
                                        point?.optString(key, "")?.takeIf {
                                            it.isNotBlank() && it != "null"
                                        }
                                    }
                                    ?: (raw as? String)?.takeIf { it.isNotBlank() }
                                    ?: "途经点 ${index + 1}"
                            val latitude =
                                point
                                    ?.let {
                                        it.optDouble(
                                            "waypointLat",
                                            it.optDouble("latitude", Double.NaN),
                                        )
                                    }
                                    ?.takeIf { it.isFinite() && it in -90.0..90.0 }
                            val longitude =
                                point
                                    ?.let {
                                        it.optDouble(
                                            "waypointLng",
                                            it.optDouble("longitude", Double.NaN),
                                        )
                                    }
                                    ?.takeIf { it.isFinite() && it in -180.0..180.0 }
                            DriverWaypoint(
                                index = index,
                                name = name,
                                id =
                                    point?.optString("id")?.takeIf {
                                        it.isNotBlank() && it != "null"
                                    },
                                latitude = latitude,
                                longitude = longitude,
                            )
                        }
                    val rawArrived = o.optJSONArray("arrivedWaypoints")
                    val arrivedWaypoints =
                        (0 until (rawArrived?.length() ?: 0))
                            .mapNotNull { index ->
                                rawArrived!!.opt(index).toString().toIntOrNull()?.takeIf {
                                    it in waypoints.indices
                                }
                            }
                            .toSet()
                    DriverOrder(
                        id = o.getString("id"),
                        kind = OrderKind.valueOf(o.getString("kind")),
                        origin = o.getString("origin"),
                        destination = o.getString("destination"),
                        fare = o.getLong("fare"),
                        distance = o.optString("distance", ""),
                        mileage = o.optString("mileage", ""),
                        time = o.optString("time", ""),
                        stage = Stage.valueOf(o.getString("stage")),
                        remark = o.optString("remark"),
                        phoneTail = o.optString("phoneTail"),
                        credited = o.optBoolean("credited"),
                        paid = o.optBoolean("paid"),
                        originLat = o.optDouble("originLat", Double.NaN),
                        originLng = o.optDouble("originLng", Double.NaN),
                        destinationLat = o.optDouble("destinationLat", Double.NaN),
                        destinationLng = o.optDouble("destinationLng", Double.NaN),
                        waypoints = waypoints,
                        arrivedWaypoints = arrivedWaypoints,
                        shared = o.optBoolean("shared", false),
                        canonicalStage = o.optString("canonicalStage", ""),
                        driverParticipationStage = o.optString("driverParticipationStage", ""),
                        destinationPending = o.optBoolean("destinationPending", false),
                        negotiationStatus = o.optString("negotiationStatus", ""),
                        quoteVersion = o.optInt("quoteVersion", 0),
                        priceDisputes =
                            o.optJSONArray("priceDisputes")?.let { disputes ->
                                (0 until disputes.length()).mapNotNull { index ->
                                    disputes.optJSONObject(index)?.let { dispute ->
                                        PriceDispute(
                                            dispute.optInt("quoteVersion"),
                                            dispute.optString("content"),
                                            dispute.optString("createdAt"),
                                        )
                                    }
                                }
                            } ?: emptyList(),
                        paymentStatus = o.optString("paymentStatus", ""),
                        refundAmount =
                            if (o.has("refundAmount") && !o.isNull("refundAmount"))
                                o.optLong("refundAmount", -1).takeIf { it >= 0 }
                            else null,
                        pairingStatus = o.optString("pairingStatus", ""),
                        pairingRequired =
                            o.optBoolean(
                                "negotiationPairingRequired",
                                o.optBoolean("pairingRequired", false),
                            ),
                        pairingExpiresAt = o.optString("pairingExpiresAt", ""),
                    )
                }
            val e = s.optJSONArray("entries")
            return DriverState(
                loggedIn = s.optBoolean("loggedIn", true),
                online = s.optBoolean("online"),
                voice = s.optBoolean("voice", true),
                largeText = s.optBoolean("largeText"),
                floating = s.optBoolean("floating"),
                range = s.optInt("range", 5),
                instant = s.optBoolean("instant", true),
                reservation = s.optBoolean("reservation", true),
                bidding = s.optBoolean("bidding", true),
                orders = orders,
                balance = s.optLong("balance"),
                todayIncome = s.optLong("todayIncome"),
                todayAccepted = s.optInt("todayAccepted"),
                todayCompleted = s.optInt("todayCompleted"),
                entries =
                    (0 until (e?.length() ?: 0)).map { i ->
                        val entry = e!!.getJSONObject(i)
                        WalletEntry(
                            entry.getString("title"),
                            entry.getLong("amount"),
                            entry.optString("note"),
                        )
                    },
            )
        }
    }
}
