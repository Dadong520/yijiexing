package cn.hbzjhs.driver.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal fun bJson(vararg values: Pair<String, Any?>): JSONObject =
    JSONObject().apply { values.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }

internal fun bData(json: JSONObject): JSONObject = json.optJSONObject("data") ?: json

internal fun bResult(json: JSONObject): JSONObject =
    bData(json).optJSONObject("result") ?: bData(json)

internal fun JSONObject.bObj(key: String): JSONObject = optJSONObject(key) ?: JSONObject()

internal fun JSONObject.bList(key: String): List<JSONObject> {
    val items = optJSONArray(key) ?: JSONArray()
    return (0 until items.length()).mapNotNull { items.optJSONObject(it) }
}

internal fun JSONObject.bText(vararg keys: String, fallback: String = "—"): String =
    keys.firstNotNullOfOrNull { key ->
        optString(key, "").takeIf { it.isNotBlank() && it != "null" }
    } ?: fallback

internal fun bStatus(status: String): String =
    when (status.uppercase()) {
        "APPROVED",
        "VALID",
        "1",
        "ACTIVE" -> "已通过"
        "PENDING",
        "SUBMITTED",
        "PROCESSING",
        "3" -> "待处理"
        "REJECTED",
        "FAILED",
        "4" -> "已驳回"
        "EXPIRED",
        "2" -> "已过期"
        "SUCCESS",
        "SUCCEEDED",
        "COMPLETED",
        "DONE" -> "已完成"
        "REPLIED",
        "RESOLVED" -> "已回复"
        "CANCELLED",
        "CANCELED" -> "已取消"
        "SANDBOX_APPROVED" -> "沙箱已通过"
        "SANDBOX_RECORDED" -> "沙箱已记录"
        "OPEN",
        "NEW" -> "待处理"
        "" -> "未提交"
        else -> status
    }

@Composable
internal fun BusinessPage(
    title: String,
    store: ConnectedStore,
    onBack: () -> Unit,
    action: String? = "刷新",
    onAction: () -> Unit = { store.refresh() },
    bottomBar: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val loading by store.loading.collectAsState()
    val error by store.error.collectAsState()
    ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp)) {
        Column(
            Modifier.fillMaxSize()
                .background(Page)
                .then(if (bottomBar != null) Modifier.imePadding() else Modifier)
        ) {
            PageHeader(title, onBack, action, onAction)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(
                Modifier.weight(1f).then(if (bottomBar == null) Modifier.imePadding() else Modifier),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!error.isNullOrBlank())
                    item {
                        Surface(color = Color(0xFFFFEDE9), shape = RoundedCornerShape(12.dp)) {
                            Text(
                                error ?: "请求未成功",
                                color = Color(0xFFAA3A29),
                                fontSize = 16.sp,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                content()
            }
            bottomBar?.let { bar ->
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
                        bar()
                    }
                }
            }
        }
    }
}

@Composable
internal fun BusinessSubmitBar(
    label: String,
    busy: Boolean,
    error: String? = null,
    onSubmit: () -> Unit,
) {
    error
        ?.takeIf { it.isNotBlank() }
        ?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                modifier = Modifier.heightIn(max = 132.dp).verticalScroll(rememberScrollState()).padding(bottom = 9.dp),
            )
        }
    PrimaryButton(if (busy) "正在处理…" else label, enabled = !busy, onClick = onSubmit)
}

@Composable
internal fun BusinessHint(text: String) {
    Text(text, color = Muted, fontSize = 16.sp, lineHeight = 26.sp)
}

@Composable
internal fun BusinessEmpty(text: String) {
    SheetCard {
        Text(
            text,
            color = Muted,
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            fontSize = 18.sp,
        )
    }
}

@Composable
internal fun BusinessField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    password: Boolean = false,
    minLines: Int = 1,
    help: String? = null,
    enabled: Boolean = true,
    error: String? = null,
) {
    OutlinedTextField(
        value,
        onValue,
        label = { Text(label, fontSize = 16.sp) },
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp),
        enabled = enabled,
        isError = error != null,
        minLines = minLines,
        maxLines = if (minLines == 1) 1 else 8,
        singleLine = minLines == 1,
        keyboardOptions =
            KeyboardOptions(
                keyboardType =
                    when {
                        password -> KeyboardType.NumberPassword
                        numeric -> KeyboardType.Decimal
                        else -> KeyboardType.Text
                    }
            ),
        visualTransformation =
            if (password) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = { (error ?: help)?.let { Text(it, fontSize = 16.sp, lineHeight = 24.sp) } },
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Muted, unfocusedLabelColor = Muted),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
internal fun BusinessConfirm(
    title: String,
    text: String,
    confirm: String,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 24.sp) },
        text = {
            Text(text, fontSize = 18.sp, lineHeight = 28.sp,
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
        },
        confirmButton = { BusinessTextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { BusinessTextButton(onClick = onClose) { Text("取消") } },
    )
}

internal fun bBitmap(base64: String): Bitmap? =
    runCatching {
            val bytes = Base64.decode(base64.substringAfter("base64,", base64), Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        .getOrNull()

private fun bCompress(bitmap: Bitmap): String {
    val factor = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height).toFloat())
    val small =
        if (factor < 1f)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * factor).toInt().coerceAtLeast(1),
                (bitmap.height * factor).toInt().coerceAtLeast(1),
                true,
            )
        else bitmap
    var quality = 86
    val out = ByteArrayOutputStream()
    do {
        out.reset()
        small.compress(Bitmap.CompressFormat.JPEG, quality, out)
        quality -= 12
    } while (out.size() > 700_000 && quality > 20)
    require(out.size() <= 750_000) { "图片过大，请选择较小的图片" }
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

@Composable
internal fun BusinessImageSelector(
    label: String,
    image: String,
    onImage: (String) -> Unit,
    store: ConnectedStore,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var processing by remember { mutableStateOf(false) }
    var capturePath by rememberSaveable { mutableStateOf("") }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val path = capturePath
            capturePath = ""
            if (success && path.isNotBlank())
                scope.launch {
                    processing = true
                    runCatching {
                            withContext(Dispatchers.IO) {
                                File(path).inputStream().use { bReadImage(it) }
                            }
                        }
                        .onSuccess(onImage)
                        .onFailure { onMessage(it.message ?: "图片处理失败") }
                    File(path).delete()
                    processing = false
                }
            else if (path.isNotBlank()) File(path).delete()
        }
    val album =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                scope.launch {
                    processing = true
                    runCatching {
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { bReadImage(it) }
                                    ?: error("无法读取图片，请重选")
                            }
                        }
                        .onSuccess(onImage)
                        .onFailure { onMessage(it.message ?: "图片处理失败") }
                    processing = false
                }
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        val bitmap = remember(image) { if (image.isBlank()) null else bBitmap(image) }
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxWidth().height(160.dp),
                contentScale = ContentScale.Fit,
            )
            BusinessTextButton(onClick = { onImage("") }) { Text("移除此图片") }
        } else
            Box(Modifier.fillMaxWidth().heightIn(min = 80.dp).background(Pale, RoundedCornerShape(12.dp))) {
                Text(
                    if (processing) "正在处理图片…" else "待选择图片",
                    modifier = Modifier.padding(20.dp),
                    color = Muted,
                    fontSize = 16.sp,
                )
            }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BusinessOutlinedButton(
                onClick = { album.launch("image/*") },
                enabled = !processing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("相册选择")
            }
            BusinessOutlinedButton(
                onClick = {
                    runCatching {
                            val directory =
                                File(context.cacheDir, "business-captures").apply { mkdirs() }
                            // A capture interrupted by an app/process exit is cleared next time
                            // this feature opens.
                            directory
                                .listFiles()
                                ?.filter {
                                    System.currentTimeMillis() - it.lastModified() > 86_400_000L
                                }
                                ?.forEach { it.delete() }
                            val file = File.createTempFile("certificate-", ".jpg", directory)
                            capturePath = file.absolutePath
                            val uri =
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.business-files",
                                    file,
                                )
                            camera.launch(uri)
                        }
                        .onFailure {
                            if (capturePath.isNotBlank()) File(capturePath).delete()
                            capturePath = ""
                            onMessage("此设备没有可用相机或拍摄权限，请从相册选择")
                        }
                },
                enabled = !processing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("拍照")
            }
        }
        BusinessTextButton(
            onClick = {
                store.query("/api/local/sample-image-base64") { reply ->
                    val source = bData(reply).bText("imageBase64", "base64", fallback = "")
                    if (source.isNotBlank()) onImage(source) else onMessage("未获取到样例图片")
                }
            }
        ) {
            Text("使用带水印的沙箱样例", fontSize = 16.sp)
        }
    }
}

private fun bReadImage(stream: InputStream): String {
    val bytes = stream.readBytesLimited(8_000_000)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "请选择 JPG 或 PNG 图片" }
    val options =
        BitmapFactory.Options().apply {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
            inSampleSize = sample
        }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("图片解码失败")
    return bCompress(bitmap)
}

private fun InputStream.readBytesLimited(max: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        require(total <= max) { "原图需小于 8 MB" }
        out.write(buffer, 0, count)
    }
    return out.toByteArray()
}


@Composable
internal fun BusinessTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, lineHeight = 26.sp)) {
            content()
        }
    }
}

@Composable
internal fun BusinessOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        border = BorderStroke(1.dp, if (enabled) Muted else Line),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontSize = 18.sp, lineHeight = 26.sp)) {
            content()
        }
    }
}
