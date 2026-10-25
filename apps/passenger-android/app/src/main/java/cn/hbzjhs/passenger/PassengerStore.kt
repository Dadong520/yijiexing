package cn.hbzjhs.passenger

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
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
import org.json.JSONArray
import org.json.JSONObject

internal class Vault(context: Context, private val name: String) {
  private val prefs = context.getSharedPreferences(name, 0)

  private fun key(): SecretKey {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    return ks.getKey(name, null) as? SecretKey
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
    val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
    check(
      prefs
        .edit()
        .putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP))
        .putString("data", Base64.encodeToString(c.doFinal(value.toByteArray()), Base64.NO_WRAP))
        .commit()
    ) {
      "无法安全保存本次操作，请检查设备存储空间后重试"
    }
  }

  fun load(): String = runCatching { loadStrict() }.getOrDefault("")

  fun loadStrict(): String {
    val data = prefs.getString("data", null)
    val iv = prefs.getString("iv", null)
    if (data == null && iv == null) return ""
    check(data != null && iv != null) { "加密记录不完整" }
    val c =
      Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
      }
    return String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)))
  }

  fun clear() {
    prefs.edit().clear().apply()
  }
}

class ApiError(val status: Int, message: String, val reason: String = "") : Exception(message)

object PassengerApi {
  @Volatile var access = ""
  @Volatile var token = ""

  suspend fun request(
    method: String,
    path: String,
    body: JSONObject? = null,
    auth: Boolean = true,
    tokenOverride: String? = null,
  ): JSONObject {
    val tokenSnapshot = tokenOverride ?: token
    return withContext(Dispatchers.IO) {
      require(path.startsWith("/api/passenger/") && !path.contains("..") && !path.contains('\\'))
      val c = URL(BuildConfig.CLOUD_API_ORIGIN + path).openConnection() as HttpURLConnection
      try {
        c.requestMethod = method
        c.connectTimeout = 20000
        c.readTimeout = 75000
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", "application/json")
        if (auth) c.setRequestProperty("Authorization", "Bearer $tokenSnapshot")
        if (body != null) {
          c.doOutput = true
          c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
          c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val status = c.responseCode
        val raw =
          (if (status in 200..299) c.inputStream else c.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        val json =
          runCatching { JSONObject(raw) }.getOrElse { throw ApiError(status, "服务暂未就绪，请等待冷启动后重试") }
        if (status !in 200..299 || json.optInt("code", -1) != 0)
          throw ApiError(
            status,
            json.optString("message", "请求未完成"),
            json.optJSONObject("details")?.optString("reason").orEmpty(),
          )
        json.optJSONObject("data") ?: JSONObject()
      } finally {
        c.disconnect()
      }
    }
  }
}

class PassengerStore(app: Application) : AndroidViewModel(app) {
  private val tokenVault = Vault(app, "passenger_session_v1")
  private val _state = MutableStateFlow(JSONObject())
  private val _loggedIn = MutableStateFlow(false)
  private val _configured = MutableStateFlow(false)
  private val _busy = MutableStateFlow(false)
  private val _error = MutableStateFlow<String?>(null)
  private val _notice = MutableStateFlow<String?>(null)
  private val _connected = MutableStateFlow(false)
  private val _syncAt = MutableStateFlow("")
  val state = _state.asStateFlow()
  val loggedIn = _loggedIn.asStateFlow()
  val configured = _configured.asStateFlow()
  val busy = _busy.asStateFlow()
  val error = _error.asStateFlow()
  val notice = _notice.asStateFlow()
  val connected = _connected.asStateFlow()
  val syncAt = _syncAt.asStateFlow()
  private var refreshing = false
  private var autoAllowed = true
  private var nextRefresh = 0L
  private var failureCount = 0
  private var sessionGeneration = 0
  private var stateEpoch = 0
  private val pendingVault = Vault(app, "passenger_pending_v1")
  private val pendingJournal by lazy {
    PendingRequestJournal(load = pendingVault::loadStrict, save = pendingVault::save)
  }

  private suspend fun submitJournaledAction(
    accountId: String,
    action: String,
    payload: JSONObject,
  ): JSONObject =
    pendingJournal.execute(accountId, action, payload.toString(), PassengerApi.token) { requestId ->
      PassengerApi.request(
        "POST",
        "/api/passenger/action",
        JSONObject().put("action", action).put("payload", payload).put("requestId", requestId),
      )
    }

  init {
    // Ignore legacy access-code preferences without clearing sessions or user data.
    PassengerApi.access = ""
    PassengerApi.token = tokenVault.load()
    _configured.value = true
    _loggedIn.value = PassengerApi.token.isNotBlank()
  }

  fun clearNotice() {
    _notice.value = null
  }

  fun clearError() {
    _error.value = null
  }

  fun reportError(value: String) {
    _error.value = value
  }

  /** Compatibility only: the fixed experience service no longer needs an access code. */
  @Suppress("UNUSED_PARAMETER")
  fun saveAccess(value: String): Boolean {
    PassengerApi.access = ""
    _configured.value = true
    _error.value = null
    return true
  }

  fun clearAccess() {
    PassengerApi.access = ""
    _configured.value = true
  }

  private fun logoutLocal() {
    sessionGeneration++
    tokenVault.clear()
    PassengerApi.token = ""
    _loggedIn.value = false
    _state.value = JSONObject()
    _connected.value = false
    _syncAt.value = ""
    stateEpoch++
  }

  private fun accept(data: JSONObject, generation: Int) {
    if (generation != sessionGeneration || PassengerApi.token.isBlank()) return
    if (data.has("passenger")) {
      _state.value = data
      _syncAt.value = java.time.LocalTime.now().withNano(0).toString()
    }
    _connected.value = true
  }

  private fun run(show: Boolean = true, clearSessionOnUnauthorized: Boolean = true, block: suspend () -> Unit) {
    if (show && _busy.value) {
      _error.value = "上一项操作仍在处理中，请稍候再试；本次操作尚未提交"
      return
    }
    val requestGeneration = sessionGeneration
    val requestEpoch = stateEpoch
    // Acquire before launch so two taps in the same UI frame cannot start two jobs.
    if (show) {
      _busy.value = true
      _error.value = null
    }
    viewModelScope.launch {
      try {
        block()
        if (requestGeneration == sessionGeneration && (show || requestEpoch == stateEpoch)) {
          failureCount = 0
          nextRefresh = 0L
          autoAllowed = true
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (requestGeneration != sessionGeneration || (!show && requestEpoch != stateEpoch))
          return@launch
        if (clearSessionOnUnauthorized && e is ApiError && e.status == 401) logoutLocal()
        val gate =
          e is ApiError &&
            e.reason in setOf("DEMO_ACCESS_REQUIRED", "DEMO_ACCESS_INVALID", "DEMO_PAUSED")
        if (gate || e is ApiError && e.status == 503) autoAllowed = false
        if (e !is ApiError) {
          failureCount = (failureCount + 1).coerceAtMost(4)
          nextRefresh = System.currentTimeMillis() + (10000L shl failureCount)
        }
        if (e !is ApiError || gate || e.status >= 500) _connected.value = false
        _error.value =
          when (e) {
            is ApiError -> e.message ?: "服务请求未完成"
            is PendingStorageException -> e.message ?: "重试记录未能保存，请检查设备存储"
            is IllegalArgumentException -> e.message ?: "输入内容有误"
            is java.net.SocketTimeoutException -> "云端启动或响应超时，请等待约 30 秒再重试。原订单可能已提交，请先刷新订单。"
            else -> "暂时无法连接云端，请检查网络或恢复已暂停的体验服务后重试。已有数据仅供查看。"
          }
      } finally {
        if (show) _busy.value = false
      }
    }
  }

  fun requestCode(mobile: String, done: (String) -> Unit) = run {
    val generation = sessionGeneration
    requireMobile(mobile)
    val d =
      PassengerApi.request(
        "POST",
        "/api/passenger/auth/code",
        JSONObject().put("mobile", mobile),
        false,
      )
    if (generation != sessionGeneration) return@run
    done(d.optString("verificationCode"))
    _notice.value = "体验验证码已返回；未发送真实短信"
  }

  /** Full auth chain runs inside one busy operation; it never calls public refresh(). */
  fun loginExperience(phone: String) {
    if (_busy.value) {
      _error.value = "上一项操作仍在处理中，请稍候再试"
      return
    }
    sessionGeneration++
    stateEpoch++
    run(clearSessionOnUnauthorized = false) {
      val generation = sessionGeneration
      val mobile = phone.trim()
      requireMobile(mobile)
      val code = PassengerApi.request("POST", "/api/passenger/auth/code", JSONObject().put("mobile", mobile), false)
        .getString("verificationCode")
      if (generation != sessionGeneration) return@run
      val login = PassengerApi.request("POST", "/api/passenger/auth/login", JSONObject().put("mobile", mobile).put("code", code), false)
      val token = login.getString("token")
      require(token.isNotBlank()) { "服务未返回有效登录凭证" }
      if (generation != sessionGeneration) return@run
      val fresh = PassengerApi.request("GET", "/api/passenger/state", tokenOverride = token)
      if (generation != sessionGeneration) return@run
      val identity = fresh.optJSONObject("passenger")
      require(identity != null && identity.optString("id").isNotBlank() && identity.optString("mobile") == mobile) { "体验乘客身份尚未正确同步，请重试" }
      tokenVault.save(token)
      PassengerApi.token = token
      _loggedIn.value = true
      accept(fresh, generation)
      _notice.value = "已登录独立乘客体验账号；未发送真实短信"
    }
  }

  fun login(mobile: String, code: String) = run {
    val generation = sessionGeneration
    requireMobile(mobile)
    val d =
      PassengerApi.request(
        "POST",
        "/api/passenger/auth/login",
        JSONObject().put("mobile", mobile).put("code", code),
        false,
      )
    if (generation != sessionGeneration) return@run
    PassengerApi.token = d.getString("token")
    tokenVault.save(PassengerApi.token)
    _loggedIn.value = true
    accept(d, sessionGeneration)
    _notice.value = "已登录独立乘客体验账号"
  }

  fun logout() = run {
    runCatching { PassengerApi.request("POST", "/api/passenger/auth/logout", JSONObject()) }
    logoutLocal()
  }

  fun refresh(silent: Boolean = false) {
    if (refreshing || !_loggedIn.value || !_configured.value || _busy.value) return
    if (silent && (!autoAllowed || System.currentTimeMillis() < nextRefresh)) return
    val generation = sessionGeneration
    val epoch = stateEpoch
    refreshing = true
    run(!silent) {
      try {
        val data = PassengerApi.request("GET", "/api/passenger/state")
        if (epoch == stateEpoch) accept(data, generation)
      } finally {
        refreshing = false
      }
    }
  }


  private var locationReportInFlight = false
  private var lastLocationReportKey = ""
  fun reportLocation(orderId: String, latitude: Double, longitude: Double, accuracy: Float, time: Long) {
    val generation = sessionGeneration
    val account = _state.value.optJSONObject("passenger")?.optString("id").orEmpty()
    val token = PassengerApi.token
    val orders = _state.value.optJSONArray("orders")
    val active = (0 until (orders?.length() ?: 0)).any { i ->
      val o=orders?.optJSONObject(i)
      o?.optString("id")==orderId && o.optString("stage") in setOf("AVAILABLE","PREPAY","RESERVED","PICKUP","ARRIVED","TRIP")
    }
    val key = "$account:$orderId:$time:$latitude:$longitude:$accuracy"
    if (account.isBlank() || token.isBlank() || !_loggedIn.value || !active || locationReportInFlight ||
      !latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0 ||
      !accuracy.isFinite() || accuracy<=0 || System.currentTimeMillis()-time !in -30000L..120000L || key==lastLocationReportKey) return
    locationReportInFlight = true
    viewModelScope.launch {
      try {
        if (generation!=sessionGeneration || token!=PassengerApi.token) return@launch
        val payload=JSONObject().put("id",orderId).put("latitude",latitude).put("longitude",longitude)
          .put("accuracy",accuracy).put("time",time).put("coordinateSystem","WGS84")
        PassengerApi.request("POST","/api/passenger/action",JSONObject().put("action","location.report")
          .put("payload",payload).put("requestId",java.util.UUID.nameUUIDFromBytes(key.toByteArray()).toString()), tokenOverride=token)
        if (generation==sessionGeneration && token==PassengerApi.token) lastLocationReportKey=key
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) {
        if (generation==sessionGeneration) PassengerPositionBus.updateStatus("位置暂未上传，稍后自动重试；定位不足时需您确认上车或送达")
      } finally { locationReportInFlight=false }
    }
  }

  fun action(action: String, payload: JSONObject = JSONObject(), done: (JSONObject) -> Unit = {}) {
    if (_state.value.optJSONObject("passenger")?.text("id").isNullOrBlank()) {
      _error.value = "乘客资料尚未同步，请先刷新后再提交"
      return
    }
    val account = _state.value.optJSONObject("passenger")!!.text("id")
    val generation = sessionGeneration
    run {
      stateEpoch++
      val data = submitJournaledAction(account, action, payload)
      accept(data, generation)
      if (generation == sessionGeneration) {
        done(data.optJSONObject("result") ?: JSONObject())
        _notice.value = "操作已保存到云端"
      }
    }
  }

  fun uploadComplaint(orderId: String, complaintId: String, uri: android.net.Uri) = run {
    val generation = sessionGeneration
    stateEpoch++
    val encoded =
      withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        val raw =
          resolver.openInputStream(uri)?.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (buffer.size() <= 20 * 1024 * 1024) {
              val count = input.read(chunk)
              if (count < 0) break
              buffer.write(chunk, 0, count)
            }
            val bytes = buffer.toByteArray()
            require(bytes.size <= 20 * 1024 * 1024) { "图片过大，请选择 20 MB 以内的图片" }
            bytes
          } ?: throw IllegalArgumentException("无法读取所选图片")
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "所选文件不是可读取的图片" }
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap =
          android.graphics.BitmapFactory.decodeByteArray(
            raw,
            0,
            raw.size,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
          ) ?: throw IllegalArgumentException("无法解码图片")
        try {
          var quality = 85
          var bytes: ByteArray
          do {
            val buffer = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, buffer)
            bytes = buffer.toByteArray()
            quality -= 15
          } while (bytes.size > 512 * 1024 && quality >= 25)
          require(bytes.size <= 512 * 1024) { "图片压缩后仍超过 512 KB，请裁剪后重试" }
          Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
          bitmap.recycle()
        }
      }
    val payload =
      JSONObject()
        .put("id", orderId)
        .put("complaintId", complaintId)
        .put("imagesBase64", JSONArray().put(encoded))
    if (generation != sessionGeneration) return@run
    val account = _state.value.optJSONObject("passenger")?.text("id").orEmpty()
    val data = submitJournaledAction(account, "order.complaint.attach", payload)
    accept(data, generation)
    if (generation == sessionGeneration) _notice.value = "投诉图片已上传并保存"
  }

  fun query(path: String, done: (JSONObject) -> Unit) = run {
    val generation = sessionGeneration
    val data = PassengerApi.request("GET", path)
    if (generation == sessionGeneration) done(data)
  }
}

fun requireMobile(value: String) {
  if (value !in listOf("13800000101", "13800000102", "13800000103"))
    throw ApiError(400, "请使用体验账号 13800000101、13800000102 或 13800000103")
}

fun JSONObject.rows(key: String): List<JSONObject> = optJSONArray(key)?.objects() ?: emptyList()

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.text(key: String, fallback: String = ""): String =
  optString(key, fallback).takeUnless { it == "null" } ?: fallback

fun money(cents: Long): String = java.math.BigDecimal.valueOf(cents, 2).setScale(2).toPlainString()

fun stageName(stage: String): String =
  mapOf(
    "WAIT_CONTINUE" to "司机已取消 · 待确认续叫",
    "AVAILABLE" to "等待司机接单 / 报价",
    "BID_SENT" to "等待选择司机",
    "PREPAY" to "待模拟预付",
    "RESERVED" to "已预约 · 待出发",
    "PICKUP" to "司机接驾中",
    "ARRIVED" to "司机已到达",
    "TRIP" to "行程中",
    "WAIT_PAY" to "待支付",
    "DONE" to "已完成",
    "CANCELLED" to "已取消",
  )[stage] ?: stage

fun kindName(kind: String): String =
  mapOf("STANDARD" to "即时用车", "RESERVED" to "预约用车", "BID" to "司机报价", "NEGOTIATED" to "线下议价")[kind]
    ?: kind

fun paymentName(value: String): String =
  mapOf(
    "UNPAID" to "未支付",
    "PROCESSING" to "模拟支付处理中",
    "PENDING" to "模拟支付处理中",
    "FAILED" to "模拟支付失败",
    "SUCCESS" to "模拟支付成功",
    "PAID" to "模拟支付成功",
    "REFUNDED" to "已模拟退款",
    "CANCELLED" to "已取消支付",
  )[value] ?: value

fun formatTime(value: String): String =
  runCatching {
      java.time.Instant.parse(value)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
    }
    .getOrDefault(value)

fun coordinate(value: String, latitude: Boolean): Double {
  val n = value.trim().toDoubleOrNull()
  require(n != null && n.isFinite() && n in (if (latitude) -90.0..90.0 else -180.0..180.0)) {
    if (latitude) "请输入有效纬度（-90 至 90）" else "请输入有效经度（-180 至 180）"
  }
  return n
}

fun supportStatus(value: String): String =
  when (value) {
    "PENDING" -> "待处理"
    "REPLIED" -> "已回复"
    "PROCESSING" -> "处理中"
    "RESOLVED" -> "已解决"
    "CLOSED" -> "已关闭"
    else -> value
  }
