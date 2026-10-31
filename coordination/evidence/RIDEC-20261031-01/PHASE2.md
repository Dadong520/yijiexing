# RIDEC-20261031-01 Phase2：修复 RIDEF-R1（P1 第二tap穿透叫车）
会话 6c0ddf57-d854-4c5c-a8a9-43f7ee7e3063（exact，取自task）；source /Users/dongge/.codex/tmp/yijiexing-ride-flow-polish-20261031；基于冻结 ff53f570296d0bd97d8dc7ee4e16c915987a8542。2026-10-31。
原FAIL保留：evidence/RIDEF-20261031-01/FINDINGS_ff53.json（49ms第二tap击中“确认呼叫”，fixture订单0→1）未改动。未commit/stage/deploy。无Bash：编译/单测/native全部 NOT_RUN，交frontend独立编译与原生复验，不声称PASS。

## 根因
PlacePickSession只保证同一选址页最多回调一次；选址页一点即关闭后，同坐标的第二次原生tap落到新暴露的调用方页面（首页底栏CTA[44,1948][1036,2086]与历史06行(269,1971)重叠），而调用方按钮没有任何返回后保护。

## 实现（仅允许的5个产品/测试文件）
- PlaceSelection.kt：新增纯逻辑 `ReturnTapGuard(windowMs = WINDOW_MS = 700, clock = System.nanoTime()/1e6 单调毫秒)`：`arm()`从当前时刻开始/重新开始窗口；`remainingMs()`剩余（≤windowMs，0为开放）；`allows()`。未arm即开放；超过窗口自动清除；时钟倒退视为开放（保证有界，不会无限锁）。
- BookingScreens.kt：新增Compose包装 `ReturnTapShield`(@Stable：guard + active状态 + generation) 与 `rememberReturnTapShield()`（每次arm重启LaunchedEffect，按guard剩余时间delay后settle，active自动复位）。`allows()`是真正入口判定（按时钟），`active`仅用于enabled外观，二者同源。
  - 首页：`returnShield`在PassengerBookingDraft内（随owner key重建，不跨账号泄漏）。选址页离开到首页时（点选成功的save回调，或返回/系统返回）先`arm()`再`editor=""`。途经点页返回目的地页不arm（仍是全屏选址页，首页未暴露）。
  - 受保护入口：底栏主按钮（“确认呼叫”→order.create / “查看当前行程”）enabled加`!active`且onClick首行`if (!returnShield.allows()) return`；“交换起终点”同样enabled+点击时检查（防第二tap静默改路线）。
- ProfileScreens.kt（常用地址编辑器）：`pickShield`；选址点选或返回时arm。受保护：“保存”(favorite.save) enabled+点击检查；“设为默认”勾选点击检查；“取消”与对话框外点/返回(onDismissRequest)窗口内忽略——防第二tap丢弃刚选的地址。
- OrderDetail.kt：`pickShield`；目的地选址点选（在设dialog="destinationConfirm"之前）或返回时arm。受保护：确认弹窗“确认并重新估价”(order.update) enabled+onConfirm首行检查；“暂不修改”及外点/返回窗口内忽略（不丢刚选目的地）；底部固定“取消订单”“去支付/核对并支付”入口enabled+点击检查（窗口内不打开取消/支付流程）。
窗口：700ms（Android标准double-tap timeout 300ms的2倍以上，满足要求600–750ms），之后用户可正常点原按钮。

## 未改
未恢复“使用此地点”；点选仍立即回填并返回（不延迟）；未新增通用确认层；地图、搜索、列表、路线行、MapPanel、行程设置等其他操作不受窗口影响；业务门禁（busy、enabledKinds、可改目的地条件、支付/取消流程、owner/editor校验、PlacePickSession）不变；取消/返回不保存、不改草稿。未改Store/backend/网络/版本/控制/task文件及其他产品文件。

## 测试（PlaceSelectionTest，原9例保留，新增5例，NOT_RUN）
未arm开放；arm后49ms拒绝、剩余651、699ms仍拒、700ms边界允许、之后保持开放；600ms重新arm后从新时刻计窗；时钟倒退立即开放（有界）；默认窗口∈600..750且在真实单调时钟上arm后立即拒绝。

## 风险
- 编译未跑：注意`ReturnTapGuard`构造默认值引用companion常量、`@Stable`/`mutableIntStateOf`委托、`return@ActionButton/@PassengerTextButton/@PassengerModal/@GradientButton`标签、BookingScreens composable内局部`fun leave()`。
- 真实时钟用例理论上若测试线程在arm与断言间停顿>700ms会偶发失败（极低）。
- 窗口内首页主按钮/交换、常用地址保存/取消/默认、订单确认/暂不修改/取消/支付短暂显示为禁用，第一次正常点可能需等≤0.7s。窗口内系统返回在常用地址编辑器和订单确认弹窗中被忽略。
- 残余（未保护，非资金）：从途经点页点选两下，第二tap可能落在重新打开的目的地页的同位置列表行上并选为目的地后回首页（首页CTA此时已被arm保护）；按指令不禁用列表操作，留待验收判断。
- 原生行为、模拟器49ms复测NOT_RUN，由frontend独立复验。
