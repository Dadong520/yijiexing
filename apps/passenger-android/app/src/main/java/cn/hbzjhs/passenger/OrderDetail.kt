package cn.hbzjhs.passenger

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OrderDetail(o: JSONObject, store: PassengerStore, busy: Boolean) {
  val id = o.text("id")
  val stage = o.text("stage")
  val driver = o.optJSONObject("driver")
  val negotiated = o.text("kind") == "NEGOTIATED"
  var shownQuote by remember(id) { mutableStateOf<QuoteSnapshot?>(null) }
  var shownPayment by remember(id) { mutableStateOf<PaymentSnapshot?>(null) }
  var dialog by remember(id) { mutableStateOf("") }
  var content by remember(id) { mutableStateOf("") }
  var rating by remember(id) { mutableStateOf(5) }
  var reason by remember { mutableStateOf("行程计划有变") }
  var fees by
    remember(id, o.optLong("fare"), o.optBoolean("paid")) { mutableStateOf<JSONObject?>(null) }
  var sortLow by remember { mutableStateOf(true) }
  var showRoute by remember(id) { mutableStateOf(false) }
  var driverCard by remember(id) { mutableStateOf(false) }
  var choose by remember { mutableStateOf<JSONObject?>(null) }
  var complaintForImage by remember { mutableStateOf("") }
  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null && complaintForImage.isNotBlank())
        store.uploadComplaint(id, complaintForImage, uri)
    }
  var countdown by remember(o.text("expiresAt")) { mutableStateOf(0L) }
  LaunchedEffect(o.text("expiresAt"), stage) {
    if (stage == "AVAILABLE" && o.text("kind") == "BID") {
      while (true) {
        countdown =
          runCatching {
              java.time.Duration.between(
                  java.time.Instant.now(),
                  java.time.Instant.parse(o.text("expiresAt")),
                )
                .seconds
                .coerceAtLeast(0)
            }
            .getOrDefault(0)
        delay(1000)
      }
    }
  }
  if (driverCard && driver != null) PassengerDriverCard(driver) { driverCard = false }
  val cancellable =
    stage in
      listOf("AVAILABLE", "BID_SENT", "PREPAY", "RESERVED", "PICKUP", "ARRIVED", "WAIT_CONTINUE")
  PageColumn {
    SectionTitle(stageName(stage), orderSummary(o))
    IdentifierButton(id)
    Panel {
      Text(
        when (stage) {
          "WAIT_CONTINUE" -> "司机已取消接驾。您可以继续呼叫其他司机，或取消此次行程。若已预付，系统已记录模拟退款。"
          "AVAILABLE" -> if (o.text("kind") == "BID") "司机报价后，请在下方比较并选定司机。" else "订单已发送到司机端，等待司机接单。"
          "PREPAY" ->
            if (negotiated) {
              if (o.text("negotiationStatus") == "DISPUTED") "您已提出价格异议，等待原司机重新报价。异议未解决前不可付款。"
              else "您与司机已线下配对。请核对车牌、报价版本和金额；明确确认后模拟支付，成功即开始行程。"
            } else "已选定司机，请模拟预付后等待接驾。"
          "RESERVED" -> "司机已接单，将按预约时间前来接您。"
          "PICKUP" -> "司机正在前往上车点，请在安全位置等待。"
          "ARRIVED" -> "司机已到达。上车时请告知乘车手机号尾号 ${o.text("phoneTail")}，由司机核验后开始行程。"
          "TRIP" -> "行程已开始，请系好安全带。司机到达终点后会结束行程。"
          "WAIT_PAY" -> "行程已结束，请核对费用后进行模拟支付。"
          "DONE" -> "感谢乘坐，欢迎评价本次行程。"
          else -> "本次订单已结束，您可以返回出行页面再次叫车。"
        },
        lineHeight = 23.sp,
      )
    }
    if (stage == "WAIT_CONTINUE")
      Panel("是否继续用车？") {
        ActionButton("继续呼叫其他司机", !busy) {
          store.action("order.continue", JSONObject().put("id", id).put("type", 1))
        }
        OutlinedButton(
          onClick = { store.action("order.continue", JSONObject().put("id", id).put("type", 2)) },
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("不再用车 · 取消订单")
        }
      }
    if (negotiated)
      Panel("线下议价 · 报价版本 ${o.optInt("quoteVersion")}") {
        Text(
          "¥${money(o.optLong("fare"))}",
          fontSize = 30.sp,
          fontWeight = FontWeight.Bold,
          color = Teal,
        )
        KV(
          "报价状态",
          when (o.text("negotiationStatus")) {
            "DISPUTED" -> "已提出异议 · 等待司机重新报价"
            "ACCEPTED" -> "该版本报价已确认并模拟支付"
            else -> "待您核对并确认"
          },
        )
        Note("该订单已配对现场司机，不进入大厅抢单。付款时必须确认本页展示的版本和金额。")
        if (stage == "PREPAY" && o.text("negotiationStatus") == "OFFERED")
          OutlinedButton(
            onClick = {
              shownQuote = QuoteSnapshot.from(o)
              content = ""
              dialog = "dispute"
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("对当前报价提出异议")
          }
        o.rows("quoteHistory").reversed().forEach { q ->
          Note(
            "第 ${q.optInt("version")} 版 · ¥${money(q.optLong("amount"))} · ${formatTime(q.text("createdAt"))}"
          )
        }
        o.rows("priceDisputes").reversed().forEach { d ->
          Text(
            "第 ${d.optInt("quoteVersion")} 版异议：${d.text("content")}",
            color = Muted,
            fontSize = 13.sp,
          )
        }
      }
    if (o.text("kind") == "BID" && stage == "AVAILABLE")
      Panel("司机报价 · ${o.rows("bids").size} 位") {
        if (o.text("expiresAt").isNotBlank()) {
          Text(
            if (countdown > 0) "剩余 ${countdown/60} 分 ${countdown%60} 秒" else "等待云端确认是否已超时",
            color = Amber,
            fontWeight = FontWeight.Bold,
          )
          Note("本次体验报价有效期 10 分钟 · 截止 ${formatTime(o.text("expiresAt"))}；以云端订单状态为准。")
        }
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          FilterChip(sortLow, { sortLow = true }, label = { Text("价格从低到高") })
          FilterChip(!sortLow, { sortLow = false }, label = { Text("最新报价") })
        }
        val bids =
          if (sortLow) o.rows("bids").sortedBy { it.optLong("amount") }
          else o.rows("bids").sortedByDescending { it.text("createdAt") }
        if (bids.isEmpty()) Note("暂无司机报价；保持此页即可自动获取新报价。")
        bids.forEach { b ->
          HorizontalDivider()
          Text(b.text("plate"), fontWeight = FontWeight.Bold, fontSize = 21.sp)
          Text("${b.text("name")} · ${b.text("vehicleModel")}", color = Muted)
          Text("¥${money(b.optLong("amount"))}", fontSize = 24.sp, color = Teal)
          ActionButton("选择此司机", !busy) { choose = b }
        }
      }
    Panel("费用明细") {
      val farePresentation = orderFarePresentation(o)
      Note(farePresentation.label)
      Text(
        if (o.optBoolean("destinationPending")) "费用待定（并非免费）" else "¥${money(o.optLong("fare"))}",
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        color = Ink,
      )
      farePresentation.notice?.let { Note(it) }
      KV(
        "支付状态",
        paymentName(o.text("paymentStatus", if (o.optBoolean("paid")) "SUCCESS" else "UNPAID")),
      )
      if (o.optLong("refundAmount") > 0) KV("模拟退款金额", "¥${money(o.optLong("refundAmount"))}")
      Note("金额为独立环境体验计价，不作为真实营运价格或付款凭证。所有支付、退款均明确为模拟。")
      TextButton(
        onClick = { store.query("/api/passenger/orders/$id/fees") { fees = it } },
        enabled = !busy,
      ) {
        Text(if (fees == null) "展开费用明细" else "刷新费用明细")
      }
      fees?.let { f ->
        KV(
          farePresentation.detailLabel,
          if (o.optBoolean("destinationPending")) "待定（目的地未补充）" else "¥${money(f.optLong("fare"))}",
        )
        KV("服务费", "¥${money(f.optLong("serviceFee"))}")
        KV("支付手续费", "¥${money(f.optLong("paymentFee"))}")
        Note(f.text("policyLabel", "独立体验估价"))
      }
      if (stage in listOf("PREPAY", "WAIT_PAY")) {
        if (negotiated && o.text("negotiationStatus") != "OFFERED") Note("请等待司机重新报价，之后再次核对金额。")
        else
          ActionButton(
            if (negotiated) "核对第 ${o.optInt("quoteVersion")} 版并模拟支付"
            else "模拟支付 ¥${money(o.optLong("fare"))}",
            !busy,
          ) {
            shownQuote = if (negotiated) QuoteSnapshot.from(o) else null
            shownPayment = if (negotiated) null else PaymentSnapshot.from(o)
            dialog = "pay"
          }
      }
    }
    Panel("行程路线") {
      Text("出发  ${o.text("origin")}", fontWeight = FontWeight.Bold)
      o.rows("waypoints").forEachIndexed { index, w ->
        val arrived =
          o.optJSONArray("arrivedWaypoints")?.let { a ->
            (0 until a.length()).any { a.optInt(it) == index }
          } == true
        Text(
          "途经 ${index+1}  ${w.text("name",w.text("waypointName"))}  ${if(arrived)"✓ 已到达" else "待到达"}",
          color = Muted,
        )
      }
      Text(
        if (o.optBoolean("destinationPending")) "到达  目的地待补充" else "到达  ${o.text("destination")}",
        fontWeight = FontWeight.Bold,
      )
      TextButton(onClick = { showRoute = !showRoute }) {
        Text(if (showRoute) "收起路线与乘车信息" else "路线示意与乘车信息")
      }
      if (showRoute) {
        RouteDiagram(o)
        Note("坐标连线示意 · 非真实道路导航；不包含实时路况、自动定位或正式地图搜索。")
        if (o.text("scheduledAt").isNotBlank()) KV("预约时间", formatTime(o.text("scheduledAt")))
        if (o.text("remark").isNotBlank()) KV("乘客备注", o.text("remark"))
        o.optJSONObject("rider")?.let { KV("乘车人", it.text("name")) }
        KV("乘车手机号尾号", o.text("phoneTail"))
      }
    }
    if (driver != null)
      Panel("司机与车辆") {
        TextButton(onClick = { driverCard = true }) { Text("查看司机服务卡") }
        Text("${driver.text("name")} · ${driver.text("plate")}", fontWeight = FontWeight.Bold)
        KV("车型", driver.text("vehicleModel", "体验车辆"))
        KV("司机联系方式", driver.text("mobileMasked", "未提供"))
        driver.optJSONObject("location")?.let { loc ->
          KV("最新位置", "${loc.optDouble("latitude")}, ${loc.optDouble("longitude")}")
          Note("上报于 ${formatTime(loc.text("receivedAt"))}；仅展示司机端上报，不代表实时持续定位。")
        }
        Note("未配置真实电话中继，无法在本体验版直接联系司机。")
        if (stage in listOf("DONE", "CANCELLED", "WAIT_PAY"))
          TextButton(onClick = { dialog = "block" }, enabled = !busy) { Text("以后不再匹配此司机") }
      }
    if (cancellable)
      Panel("行程管理") {
        OutlinedButton(
          onClick = {
            reason = "行程计划有变"
            dialog = "cancel"
          },
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("取消本次订单")
        }
        Note(if (o.optBoolean("paid")) "取消后云端记录模拟退款，不产生实际资金转账。" else "出发前可取消；开始行程后不允许直接取消。")
        TextButton(
          onClick = {
            content = o.text("remark")
            dialog = "remark"
          },
          enabled = !busy,
        ) {
          Text("修改用车备注")
        }
        if (
          (stage == "AVAILABLE" && o.rows("bids").isEmpty()) ||
            (o.optBoolean("destinationPending") && stage in listOf("PICKUP", "ARRIVED"))
        )
          TextButton(onClick = { dialog = "destination" }, enabled = !busy) {
            Text(if (o.optBoolean("destinationPending")) "补充目的地并确认体验估价" else "修改目的地并重新估价")
          }
        else Note("司机接单或报价后目的地与估价已锁定，需要变更请取消后重新下单。")
      }
    if (stage == "DONE")
      Panel("评价本次行程") {
        val evaluation = o.optJSONObject("evaluation")
        if (evaluation != null) {
          Text("${evaluation.optInt("rating")} / 5 分", color = Teal, fontWeight = FontWeight.Bold)
          Text(evaluation.text("content", "未填写文字评价"))
        } else
          ActionButton("评价司机", !busy) {
            content = ""
            rating = 5
            dialog = "evaluate"
          }
      }
    if (stage in listOf("DONE", "CANCELLED", "WAIT_PAY"))
      OutlinedButton(
        onClick = {
          content = ""
          reason = "服务问题"
          dialog = "complaint"
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Text("投诉与问题反馈")
      }
    o.rows("complaints").forEach { c ->
      Panel("投诉记录") {
        IdentifierButton(c.text("id"), "投诉编号")
        KV("状态", supportStatus(c.text("status")))
        Text(c.text("content"))
        Note(c.text("reply").ifBlank { "暂无回复，后台回复后会同步显示在这里。" })
        c.rows("attachments").forEach { attachment -> ComplaintImage(attachment, store) }
        if (c.rows("attachments").size < 3)
          OutlinedButton(
            onClick = {
              complaintForImage = c.text("id")
              imagePicker.launch("image/*")
            },
            enabled = !busy,
          ) {
            Text("从相册添加投诉图片 (${c.rows("attachments").size}/3)")
          }
        Note("图片会压缩至 512 KB 以内并上传到本订单投诉，供您和有权限的后台处理人员查看。")
      }
    }
    ExpandablePanel("订单动态") {
      KV("创建时间", formatTime(o.text("createdAt")))
      o.rows("events").takeLast(12).reversed().forEach { e ->
        Note("${formatTime(e.text("at"))} · ${eventName(e.text("type"))}")
      }
    }
    Spacer(Modifier.height(10.dp))
  }
  if (dialog == "block")
    AlertDialog(
      onDismissRequest = { dialog = "" },
      title = { Text("不再匹配此司机？") },
      text = { Text("后续新订单会过滤此司机。您可在“我的 → 已屏蔽司机”中恢复匹配。") },
      confirmButton = {
        TextButton(
          onClick = {
            store.action("order.blockDriver", JSONObject().put("id", id).put("reason", "乘客选择不再匹配"))
            dialog = ""
          }
        ) {
          Text("确认屏蔽")
        }
      },
      dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
    )
  if (dialog == "destination")
    AddressDialog(
      o.text("destination"),
      o.optDouble("destinationLat").toString(),
      o.optDouble("destinationLng").toString(),
      emptyList(),
      { dialog = "" },
    ) { name, lat, lng ->
      store.action(
        "order.update",
        JSONObject()
          .put("id", id)
          .put("destination", name)
          .put("destinationLat", coordinate(lat, true))
          .put("destinationLng", coordinate(lng, false)),
      )
      dialog = ""
    }
  if (dialog == "remark")
    PassengerModal(
      title = "修改用车备注",
      onDismiss = { dialog = "" },
      confirmLabel = "保存",
      confirmEnabled = content.trim().isNotEmpty() && !busy,
      onConfirm = {
        store.action("order.remark", JSONObject().put("id", id).put("remark", content.trim()))
        dialog = ""
      },
    ) {
      Field(content, { content = it.take(500) }, "备注（1–500 字）", lines = 3)
    }
  choose?.let { b ->
    AlertDialog(
      onDismissRequest = { choose = null },
      title = { Text("确认选择司机") },
      text = {
        Text(
          "${b.text("name")} · ${b.text("plate")}\n报价 ¥${money(b.optLong("amount"))}\n选定后需要模拟预付才能开始接驾。"
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            store.action(
              "order.selectDriver",
              JSONObject().put("id", id).put("driverId", b.text("driverId")),
            )
            choose = null
          },
          enabled = !busy,
        ) {
          Text("确认选择")
        }
      },
      dismissButton = { TextButton(onClick = { choose = null }) { Text("继续比较") } },
    )
  }
  if (dialog == "pay")
    AlertDialog(
      onDismissRequest = { dialog = "" },
      title = { Text("模拟支付 · 不会真实扣款") },
      text = {
        Column(
          Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          val shownAmount = if (negotiated) shownQuote?.amount ?: 0 else shownPayment?.fare ?: 0
          if (negotiated) {
            Text("确认第 ${shownQuote?.version} 版报价", fontWeight = FontWeight.Bold)
            Text("${driver?.text("name").orEmpty()} · ${driver?.text("plate").orEmpty()}")
          }
          Text("本次确认金额 ¥${money(shownAmount)}", fontWeight = FontWeight.Bold)
          val quoteCurrent =
            if (negotiated) quotationMatches(shownQuote, o) else paymentMatches(shownPayment, o)
          if (!quoteCurrent)
            Text(
              if (negotiated) "司机报价已更新或当前报价不可付款。请关闭本窗口，重新核对最新版本与金额。"
              else "订单金额、版本或支付状态已变化。请关闭本窗口，重新核对金额后确认。",
              color = MaterialTheme.colorScheme.error,
            )
          Note(
            if (negotiated) "仅确认本窗口冻结的报价版本与金额。模拟成功后开始行程；失败、处理中、取消均不会开始行程。"
            else "仅确认本窗口展示的金额。金额或版本变化后需重新核对；模拟成功不会真实扣款，也不会重复入账。"
          )
          listOf(
              "SUCCESS" to if (negotiated) "确认此版本金额 · 模拟支付成功" else "模拟支付成功",
              "FAILED" to "模拟支付失败",
              "PENDING" to "模拟处理中",
              "CANCEL" to "取消本次支付",
            )
            .forEach { (v, t) ->
              OutlinedButton(
                onClick = {
                  runCatching {
                      if (negotiated) negotiatedPaymentPayload(id, requireNotNull(shownQuote), o, v)
                      else confirmedPaymentPayload(requireNotNull(shownPayment), o, v)
                    }
                    .onSuccess { payload ->
                      store.action("order.pay", payload)
                      dialog = ""
                    }
                    .onFailure {
                      store.reportError(it.message ?: "报价已变化，请重新核对")
                      dialog = ""
                    }
                },
                enabled = !busy && quoteCurrent,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(t)
              }
            }
        }
      },
      confirmButton = { TextButton(onClick = { dialog = "" }) { Text("关闭") } },
    )
  if (dialog == "dispute")
    PassengerModal(
      title = "对第 ${shownQuote?.version} 版报价提出异议",
      onDismiss = { dialog = "" },
      confirmLabel = "提交价格异议",
      confirmEnabled = !busy && content.trim().length >= 2 && quotationMatches(shownQuote, o),
      onConfirm = {
        store.action(
          "negotiation.dispute",
          JSONObject()
            .put("id", id)
            .put("quoteVersion", shownQuote!!.version)
            .put("content", content.trim()),
        )
        dialog = ""
      },
    ) {
      Text("当前核对金额 ¥${money(shownQuote?.amount ?: 0)}")
      Field(content, { content = it.take(500) }, "异议理由（2–500 字）", lines = 3)
      if (!quotationMatches(shownQuote, o))
        Text("报价版本或状态已更新，请关闭后重新核对。", color = MaterialTheme.colorScheme.error)
      Note("异议提交后暂停付款，等待原司机重新报价；也可取消此订单。")
    }
  if (dialog in listOf("cancel", "evaluate", "complaint"))
    PassengerModal(
      title =
        mapOf("cancel" to "取消订单", "evaluate" to "评价司机", "complaint" to "提交订单投诉")[dialog].orEmpty(),
      onDismiss = { dialog = "" },
      confirmLabel = "确认提交",
      dismissLabel = "返回",
      confirmEnabled =
        !busy &&
          (dialog != "complaint" || content.trim().length >= 5) &&
          (dialog == "evaluate" || reason.trim().length >= 2),
      onConfirm = {
        val action = dialog
        val p = JSONObject().put("id", id)
        when (action) {
          "cancel" -> p.put("reason", reason)
          "evaluate" -> p.put("rating", rating).put("content", content)
          "complaint" -> p.put("reason", reason).put("content", content)
        }
        store.action("order.$action", p)
        dialog = ""
      },
    ) {
      if (dialog == "evaluate") {
        Text("评分：$rating 分")
        Row {
          (1..5).forEach { n ->
            TextButton(
              onClick = { rating = n },
              contentPadding = PaddingValues(5.dp),
              modifier =
                Modifier.weight(1f).clearAndSetSemantics {
                  contentDescription = "$n 分"
                  role = Role.Button
                  selected = n == rating
                  onClick(label = "选择 $n 分") {
                    rating = n
                    true
                  }
                },
            ) {
              Text(if (n <= rating) "★" else "☆", fontSize = 24.sp)
            }
          }
        }
      } else Field(reason, { reason = it }, if (dialog == "cancel") "取消原因" else "投诉类别 / 原因")
      if (dialog != "cancel")
        Field(
          content,
          { content = it.take(if (dialog == "evaluate") 1000 else 2000) },
          if (dialog == "evaluate") "评价内容（可选）" else "请描述问题（至少 5 字）",
          lines = 3,
        )
      if (dialog == "complaint") Note("投诉会保存到云端，后台工作人员可查看并回复，您可在本页查看回复内容。尚未提供实时人工客服，不会自动产生处理结论。")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteDiagram(o: JSONObject) {
  Canvas(Modifier.fillMaxWidth().height(130.dp)) {
    val coords = mutableListOf(o.optDouble("originLng") to o.optDouble("originLat"))
    o.rows("waypoints").forEach {
      coords.add(it.optDouble("longitude") to it.optDouble("latitude"))
    }
    coords.add(o.optDouble("destinationLng") to o.optDouble("destinationLat"))
    val loc = o.optJSONObject("driver")?.optJSONObject("location")
    val car = loc?.let { it.optDouble("longitude") to it.optDouble("latitude") }
    val all = (coords + listOfNotNull(car)).filter { it.first.isFinite() && it.second.isFinite() }
    if (all.size >= 2) {
      val minLng = all.minOf { it.first } - .005
      val maxLng = all.maxOf { it.first } + .005
      val minLat = all.minOf { it.second } - .005
      val maxLat = all.maxOf { it.second } + .005
      fun position(p: Pair<Double, Double>) =
        Offset(
          (.08 * size.width + (p.first - minLng) / (maxLng - minLng) * .84 * size.width).toFloat(),
          (.08 * size.height + (maxLat - p.second) / (maxLat - minLat) * .84 * size.height)
            .toFloat(),
        )
      val route = coords.filter { it.first.isFinite() && it.second.isFinite() }.map { position(it) }
      route.zipWithNext().forEach { (a, b) -> drawLine(Color(0xFFB7DAD5), a, b, 8f) }
      route.forEachIndexed { index, p ->
        drawCircle(
          if (index == 0) Teal else if (index == route.lastIndex) Color(0xFFDA9E3B) else Muted,
          if (index == 0 || index == route.lastIndex) 12f else 7f,
          p,
        )
      }
      car
        ?.takeIf { it.first.isFinite() && it.second.isFinite() }
        ?.let { drawCircle(Color(0xFF3D6977), 9f, position(it)) }
    } else if (all.size == 1) drawCircle(Teal, 12f, Offset(size.width / 2, size.height / 2))
  }
  FlowRow(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Note("● 起点")
    Note("● 司机上报位置（如有）")
    Note("● 终点")
  }
}

fun eventName(value: String): String =
  mapOf(
    "PASSENGER_NEGOTIATION_CLAIMED" to "乘客已认领线下议价订单",
    "PASSENGER_PRICE_DISPUTE" to "乘客已提出价格异议",
    "DRIVER_REQUOTE" to "司机已重新报价",
    "PASSENGER_CREATED" to "乘客已创建订单",
    "PASSENGER_SELECTDRIVER" to "乘客已选择司机",
    "PASSENGER_PAY" to "乘客已更新模拟支付结果",
    "PASSENGER_CANCEL" to "乘客已取消订单",
    "PASSENGER_UPDATE" to "乘客已更新行程信息",
    "PASSENGER_CONTINUE" to "乘客已处理续叫",
    "PASSENGER_EVALUATE" to "乘客已评价",
    "PASSENGER_COMPLAINT" to "乘客已提交投诉",
    "PASSENGER_COMPLAINT_ATTACHMENT" to "已添加投诉图片",
    "ADMIN_COMPLAINT_REPLIED" to "后台已回复投诉",
    "ADMIN_CANCEL" to "后台已取消订单",
    "ADMIN_ADJUSTFARE" to "后台已调整订单费用",
    "ADMIN_REFUND" to "后台已处理模拟退款",
    "DRIVER_ACCEPT" to "司机已接单",
    "DRIVER_BID" to "司机已报价",
    "DRIVER_ADVANCE" to "司机已推进行程",
    "DRIVER_WAYPOINT" to "司机已到达途经点",
    "DRIVER_CANCEL" to "司机已取消接驾",
    "CREATED" to "乘客已创建订单",
    "DRIVER_ACCEPTED" to "司机已接单",
    "DRIVER_BID" to "司机已报价",
    "DRIVER_SELECTED" to "乘客已选择司机",
    "PAYMENT_SUCCESS" to "模拟支付成功",
    "PAYMENT_FAILED" to "模拟支付失败",
    "PAYMENT_PENDING" to "模拟支付处理中",
    "PICKUP" to "开始接驾",
    "ARRIVED" to "到达上车点",
    "TRIP" to "开始行程",
    "WAIT_PAY" to "行程结束待支付",
    "DONE" to "订单完成",
    "CANCELLED" to "订单已取消",
    "EVALUATED" to "已评价",
    "COMPLAINT" to "已提交投诉",
  )[value] ?: if (value.startsWith("ADMIN_")) "后台已更新订单" else "订单动态已更新"
