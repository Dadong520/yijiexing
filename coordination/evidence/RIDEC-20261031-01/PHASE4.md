# RIDEC-20261031-01 Phase4：订单顶部金额小屏大字被拆行
会话 6c0ddf57-d854-4c5c-a8a9-43f7ee7e3063（exact）；source /Users/dongge/.codex/tmp/yijiexing-ride-flow-polish-20261031；基于 HEAD b3bc07dbecbd49d3b5213d96cb11aef53e9ad644。2026-10-31。
未stage/commit/deploy。无Bash：编译/单测/截图全部 NOT_RUN，由frontend重编乘客单测+原包QA并补正常/360dp font1.3截图与确认取消smoke。原FAIL记录保留。

## 问题
OrderDetail 顶部摘要原为一个 Row：[IdentifierButton 订单信息][联系与帮助][weight Spacer][金额 Text]。360dp/font1.3 下两按钮占满宽度，金额只剩极窄宽度，被折成“¥8.0”/“0”。

## 精确修改（仅 apps/passenger-android/.../OrderDetail.kt，原约163-175行）
- 删除该 Row 与 `Spacer(Modifier.weight(1f))`、金额上的 `Modifier.align(Alignment.CenterVertically)`。
- 金额 Text 改为父 Column 中独立整行：`modifier = Modifier.fillMaxWidth()`，新增 `lineHeight = 28.sp`（与 TypeScale.Page 20sp 配套，大字时行高随缩放）；文本表达式 `if (o.optBoolean("destinationPending")) "费用待定" else "¥${money(o.optLong("fare"))}"`、fontSize=TypeScale.Page、Bold、Ink 一字未改。
- 两个辅助按钮移到其下的 `FlowRow(fillMaxWidth, spacedBy(Gap.S) 横/纵)`：`IdentifierButton(id, "订单信息")` 与 `PassengerTextButton(onClick = { contactPage = true }) { Text("联系与帮助") }` 原样移动，窄屏可自动换行，不再占用金额宽度。
- OrderDetail 已有 `@OptIn(ExperimentalLayoutApi::class)` 且文件内已用 FlowRow；无新增 import。

## 未改
金额数据/money()/文案/费用待定提示、所有 onClick、pickShield/returnShield、modal、业务/报价/确认谓词、固定底部取消/支付栏、MapPanel 及其他文件均未改。

## 可读性推算（未截图验证）
360dp 减 Gap.L×2 = 328dp 可用；20sp×1.3≈26sp 粗体，“¥12345.67”约 9 个字形≈150dp 以内，“费用待定”4 字≈110dp，均在单行内完整显示；未设 maxLines/软换行关闭，因此任何极端情况只会整行换行而不会裁切或省略。

## 风险
- 摘要区多一行（金额行+按钮行），面板半展时内容下移约一行高度。
- 编译与实际截图 NOT_RUN；极窄（<300dp）+超大字时理论上仍可能在“¥”与数字间换行，但不会裁切。
