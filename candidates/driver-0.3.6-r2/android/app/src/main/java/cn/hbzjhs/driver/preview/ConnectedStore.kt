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
import kotlinx.coroutines.CancellationException
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
        check(prefs
            .edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(
                "value",
                Base64.encodeToString(
                    cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
                    Base64.NO_WRAP,
                ),
            )
            .commit()) { "无法保存登录凭证，请检查设备存储后重试" }
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

    @Volatile private var sessionRevision = 0L
    fun invalidateCredentials() { sessionRevision++ }

    data class Credentials(val token: String, val demoAccess: String, val revision: Long)

    fun credentials() = Credentials(token, demoAccess, sessionRevision)
    fun isCurrent(credentials: Credentials) = credentials == credentials()

    suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        authenticated: Boolean = true,
        credentials: Credentials = credentials(),
    ): JSONObject =
        withContext(Dispatchers.IO) {
            val origin = validateCloudEndpoint(baseUrl)
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
                if (authenticated && credentials.token.isNotBlank())
                    connection.setRequestProperty("Authorization", "Bearer ${credentials.token}")
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
    private val requests = StateRequestCoordinator()
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
        // Retain legacy preferences untouched, but never read or transmit their code.
        LocalApi.demoAccess = ""
        mutableAccessConfigured.value = true
        LocalApi.token = vault.load()
        mutableSessionAvailable.value = LocalApi.token.isNotBlank()
        if (LocalApi.token.isNotBlank()) refresh()
        else mutableInitialized.value = true
    }

    fun clearError() {
        mutableError.value = null
    }

    /** Source-compatible no-ops for old UI; access codes are no longer required. */
    @Suppress("UNUSED_PARAMETER")
    fun updateDemoAccess(value: String): Boolean {
        LocalApi.demoAccess = ""
        mutableAccessConfigured.value = true
        mutableError.value = null
        return true
    }

    fun clearDemoAccess() {
        LocalApi.demoAccess = ""
        mutableAccessConfigured.value = true
    }

    private fun resetRequestStatus() {
        LocalApi.invalidateCredentials()
        busyCount = 0
        refreshing = false
        mutableLoading.value = false
        mutableConnected.value = false
        mutableError.value = null
        automaticRefreshEnabled = true
        transientFailureCount = 0
        nextAutomaticRefreshAt = 0L
    }

    private fun launchRequest(
        showLoading: Boolean = true,
        onFailure: (String) -> Unit = {},
        authenticated: Boolean = true,
        onFinally: () -> Unit = {},
        block: suspend (Long, LocalApi.Credentials) -> Unit,
    ) {
        val epoch = requests.snapshot()
        val credentials = LocalApi.credentials()
        if (authenticated && credentials.token.isBlank()) return
        requests.commitIfCurrent(epoch) {
            if (showLoading) {
                busyCount++
                mutableLoading.value = true
                mutableError.value = null
            }
        }
        viewModelScope.launch {
            try {
                requests.runIfCurrent(epoch) {
                if (!requests.isCurrent(epoch)) return@runIfCurrent
                if (!LocalApi.isCurrent(credentials)) return@runIfCurrent
                try {
                    block(epoch, credentials)
                    requests.commitIfCurrent(epoch) {
                        mutableConnected.value = true
                        automaticRefreshEnabled = true
                        transientFailureCount = 0
                        nextAutomaticRefreshAt = 0L
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    requests.commitIfCurrent(epoch) {
                        val message =
                            when (e) {
                                is LocalApiException ->
                                    when (e.details.optString("reason")) {
                                        "DEMO_ACCESS_REQUIRED",
                                        "DEMO_ACCESS_INVALID" -> "体验服务连接失败，请重试连接或重新进入体验"
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
                        val unauthorized = authenticated && e is LocalApiException && e.status == 401 && !gateFailure
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
                        if (unauthorized && requests.isCurrent(epoch)) {
                            logoutLocal()
                            mutableError.value = message
                        }
                    }
                }
                }
            } finally {
                    requests.commitIfCurrent(epoch) {
                        if (showLoading) {
                            busyCount--
                            mutableLoading.value = busyCount > 0
                        }
                        mutableInitialized.value = true
                        onFinally()
                    }
            }
        }
    }

    fun requestCode(mobile: String, onFailure: (String) -> Unit = {}, done: (JSONObject) -> Unit) =
        launchRequest(onFailure = onFailure, authenticated = false) { epoch, credentials ->
            val data = LocalApi.request("POST", "/api/auth/code", JSONObject().put("mobile", mobile), false, credentials)
            requests.commitIfCurrent(epoch) { done(data) }
        }

    /** One serialized login operation; never nests the public request/login/refresh jobs. */
    fun loginExperience(phone: String) {
        if (mutableLoading.value) {
            mutableError.value = "上一项操作仍在处理中，请稍候再试"
            return
        }
        requests.invalidate()
        resetRequestStatus()
        pendingKeys.clear()
        launchRequest(authenticated = false) { epoch, credentials ->
            val mobile = phone.trim()
            require(Regex("1[0-9]{10}").matches(mobile)) { "请选择有效体验手机号" }
            val code = LocalApi.request("POST", "/api/auth/code", JSONObject().put("mobile", mobile), false, credentials)
                .getString("verificationCode")
            if (!requests.isCurrent(epoch)) return@launchRequest
            val login = LocalApi.request("POST", "/api/auth/login", JSONObject().put("mobile", mobile).put("code", code), false, credentials)
            val token = login.getString("token")
            require(token.isNotBlank()) { "服务未返回有效登录凭证" }
            if (!requests.isCurrent(epoch)) return@launchRequest
            val fresh = LocalApi.request("GET", "/api/state", credentials = credentials.copy(token = token))
            require(fresh.optJSONObject("state") != null && fresh.optJSONObject("profile") != null) { "体验账号资料尚未完整返回，请重试" }
            val parsed = parseDriverState(fresh.getJSONObject("state"))
            val freshPayload = JSONObject(fresh.toString())
            val expectedDriver = login.getJSONObject("profile").getJSONObject("driver").getString("id")
            require(parsed.loggedIn && fresh.getJSONObject("profile").getJSONObject("driver").getString("id") == expectedDriver) { "体验账号身份尚未正确同步，请重试" }
            requests.commitIfCurrent(epoch) {
                vault.save(token)
                LocalApi.token = token
                mutableState.value = parsed
                mutablePayload.value = freshPayload
                mutableSessionAvailable.value = true
            }
        }
    }

    fun login(mobile: String, code: String) {
        requests.invalidate()
        resetRequestStatus()
        pendingKeys.clear()
        launchRequest(authenticated = false) { epoch, credentials ->
            val data = LocalApi.request("POST", "/api/auth/login", JSONObject().put("mobile", mobile).put("code", code), false, credentials)
            requests.commitIfCurrent(epoch) {
                val token = data.getString("token")
                vault.save(token)
                LocalApi.token = token
                mutableSessionAvailable.value = true
                accept(epoch, data)
            }
        }
    }

    fun logoutLocal() {
        val epoch = requests.invalidate()
        requests.commitIfCurrent(epoch) {
            vault.clear()
            LocalApi.token = ""
            mutableSessionAvailable.value = false
            mutableState.value = emptyState()
            mutablePayload.value = JSONObject()
            AmapRuntime.configure(null)
            pendingKeys.clear()
            resetRequestStatus()
            mutableInitialized.value = true
        }
    }

    fun logout() {
        val credentials = LocalApi.credentials()
        logoutLocal()
        viewModelScope.launch {
            try {
                LocalApi.request("POST", "/api/auth/logout", JSONObject(), credentials = credentials)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The local session has already ended; this response cannot change a new one.
            }
        }
    }

    fun refresh(silent: Boolean = false) {
        if (refreshing || LocalApi.token.isBlank()) return
        if (silent && (!automaticRefreshEnabled || android.os.SystemClock.elapsedRealtime() < nextAutomaticRefreshAt)) return
        refreshing = true
        launchRequest(!silent, onFinally = { refreshing = false }) { epoch, credentials ->
            accept(epoch, LocalApi.request("GET", "/api/state", credentials = credentials))
        }
    }

    fun query(path: String, done: (JSONObject) -> Unit) = query(path, {}, done)

    fun query(path: String, onFailure: (String) -> Unit, done: (JSONObject) -> Unit) =
        launchRequest(onFailure = onFailure) { epoch, credentials ->
            val data = LocalApi.request("GET", path, credentials = credentials)
            requests.commitIfCurrent(epoch) {
                if (data.has("state")) accept(epoch, data)
                done(data)
            }
        }

    fun perform(action: String, payload: JSONObject = JSONObject(), done: (JSONObject) -> Unit = {}) =
        perform(action, payload, {}, done)

    fun perform(action: String, payload: JSONObject, onFailure: (String) -> Unit, done: (JSONObject) -> Unit) {
        val signature = action + payload.toString()
        val queuedEpoch = requests.snapshot()
        var key = ""
        if (!requests.commitIfCurrent(queuedEpoch) {
            key = pendingKeys.computeIfAbsent(signature) { UUID.randomUUID().toString() }
        }) return
        launchRequest(onFailure = onFailure) { epoch, credentials ->
            val path = when (action) {
                "benefits.orders" -> "/api/benefits/orders"
                "benefits.pay" -> "/api/benefits/pay"
                "benefits.renewal" -> "/api/benefits/renewal"
                "benefits.invoices" -> "/api/benefits/invoices"
                else -> "/api/action"
            }
            val body = if (path == "/api/action")
                JSONObject().put("action", action).put("payload", payload).put("requestId", key)
            else JSONObject(payload.toString()).put("idempotencyKey", payload.optString("idempotencyKey", key))
            val data = try {
                LocalApi.request("POST", path, body, credentials = credentials)
            } catch (e: LocalApiException) {
                if (e.status in 400..499) requests.commitIfCurrent(epoch) { pendingKeys.remove(signature) }
                throw e
            }
            if (!requests.isCurrent(epoch)) return@launchRequest
            if (data.has("state")) {
                val stateData = if (action == "order.pairOffer") JSONObject(data.toString()).apply { remove("result") } else data
                accept(epoch, stateData)
            } else {
                accept(epoch, LocalApi.request("GET", "/api/state", credentials = credentials))
            }
            requests.commitIfCurrent(epoch) {
                pendingKeys.remove(signature)
                done(data)
            }
        }
    }

    private fun accept(epoch: Long, data: JSONObject) {
        requests.commitIfCurrent(epoch) {
            if (LocalApi.token.isNotBlank()) {
                val s = data.optJSONObject("state")
                if (s != null) {
                    mutableState.value = parseDriverState(s)
                    mutablePayload.value = JSONObject(data.toString())
                    AmapRuntime.configure(data.optJSONObject("mapConfig"))
                }
            }
        }
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
                        synthetic = o.optBoolean("synthetic", false),
                        kind = OrderKind.valueOf(o.getString("kind")),
                        origin = o.getString("origin"),
                        destination = o.getString("destination"),
                        fare = o.getLong("fare"),
                        distance = o.optString("distance", ""),
                        mileage = o.optString("mileage", ""),
                        time = o.optString("time", ""),
                        scheduledAt = o.optString("scheduledAt", "").takeUnless { it == "null" }.orEmpty(),
                        settlementStatus = o.optString("settlementStatus", ""),
                        settlementLabel = o.optString("settlementLabel", ""),
                        netIncome = if (o.has("netIncome") && !o.isNull("netIncome")) o.optLong("netIncome", -1).takeIf { it >= 0 } else null,
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
