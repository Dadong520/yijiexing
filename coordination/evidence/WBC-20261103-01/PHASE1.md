# WBC-20261103-01 Phase 1 — claude 作者回执
- 角色 claude；session 466f8898-78a4-4285-b31f-33738a2a8600；claude-opus-5-5；更新 2026-11-03。
- source /Users/dongge/.codex/tmp/yijiexing-workbuddy-20261103，branch codex/workbuddy-fixes-20261103，base 22dfb6a。
- 工具只有 Read/Glob/Grep/Edit/Write。**编译、JUnit、构建、AVD 全部 NOT_RUN**，由 root 执行。未 commit/stage/deploy，没有调用其他代理。
- 未改：Gradle/签名/版本/key、后端/API、计价/取消/竞抢规则、3s/5s 逻辑、scope/round/contact fences、STATE/BOARD/REGISTRY/DECISIONS。

## 逐项结论（报告 /Users/dongge/WorkBuddy/2026-11-03-16-18-37/宜捷行打车App测试报告.md）
| # | 结论 | 依据 / 改动 |
|---|---|---|
| 1/2/10 P0 同步/金额 | 证据不足，交 backend 核同一 orderId | 按 root 任务说明，两端截图的起点不一样（作者本轮没有逐张复核 p14/d06/d11），不能证明是同一订单；本轮不改同步、计价、取消、轮询。乘客端已有每 5s 自动刷新（MainActivity `delay(5000)`），报告里"无轮询"的说法与源码不符。为便于双方核对，乘客订单详情的"订单动态"（非主视野、可展开）首行加了真实`订单编号`；司机端原本就有"订单编号（客服查询用）"，未改。 |
| 3 定位漂移 | 成立（部分） | 根因：GPS 填入的上车点还在 120s 内时，新的 fix 只有精度提升 ≥10m 才会替换，所以首个漂移的 fix 会一直保留到过期。修复：`PassengerPickupDefaults.moved`，更新的 fix 离旧点超过 200m+双方精度时视为手机真的移动，自动替换；手动选的点仍不会被覆盖。首页新增 `PickupFarNotice`：起终点直线距离确定超过 301km 时，显示原因和两个按钮【重新定位】【手动选上车点】。重新定位会清空上车点，只接受比被重试 fix 更新的定位（`freshAfter`/`gpsFloor`），不填假坐标。下单前本地同样拦截并提示，服务端 300km 校验和终点必填保持不变。顶栏城市改为"服务城市 宜昌市"。地图同时显示加州 POI 和"宜昌市"，根因在于后者是服务城市，不是当前定位城市。 |
| 4 遮挡 | 首页成立，"我的"页不成立 | 首页：主按钮固定在内容下方，并没有盖住内容；但半展开时可滚动区约 227dp（按 Pixel7 截图估算），装不下起终点卡片，目的地行被滚动边缘截断。修复：测量起终点卡片底边，作为 `MapPanelLayout(minHalfContent)`，限制在 160–360dp，地图相应让位。"我的"页：PageColumn 本身可滚动，Scaffold padding 已包含底栏，后面还有"帮助与反馈/应用与连接/退出"等卡片，截图只是截在滚动边缘，未改。 |
| 5/6 debuggable / PreviewActivity | 交 root | 公开包是 assembleDebug，ui-tooling 已是 debugImplementation。nondebuggable 体验构建由 root 负责，作者不改 Gradle。 |
| 7 历史重复 | 成立 | 原规则按 (名称, 精确 lat, 精确 lng) 去重，坐标有微小差异就会显示两条。新规则 `PlaceSelection.samePlace`：名称去首尾空白、合并空白后相同，且两点都有效、相距 ≤30m，才合并（保留第一条）；两点都无坐标的同名也合并。异地同名、同城相距 200m 的分店、有坐标与无坐标的同名都不合并。 |
| 8 文案截断 | 根因不成立（不是固定行高），UX 做了微调 | 司机登录页"本版不连接…"在 verticalScroll 内且没有高度限制，targetSdk34 不强制 edge-to-edge，8px 是 uiautomator 截在滚动边缘的可见 bounds。地图同意页的高度跟地图区域一样，内部已可滚动并自然换行；两端都把"同意/阅读政策"改成同一个可换行 FlowRow，主按钮最小 48dp，让末句说明更容易留在可见区。乘客权限指引按钮由固定 44dp 改为 min 48dp。高德 logo 与 POI 重叠是 SDK 地图渲染，按要求保留 logo，未改。 |
| 9 包体/maplibre | 交 root | 有 fallback，不删；ABI/AAB 由 root 决定。 |
| 11 顶栏拥挤 | 成立 | 登录后顶栏只留品牌、"服务城市 X"和刷新；"定位权限"移到"我的 → 应用与连接"（和"连接状态"放在一起）。登录前顶栏保留"定位权限""连接设置"，登录页"连接状态"按钮也保留。连接异常横幅、错误条和忙碌提示均未改动，故障不会被隐藏。 |
| 12 暗色 | 功能缺口，不阻塞 | 两端用固定浅色品牌 `lightColorScheme`，整套暗色未适配；本轮按要求不擅自改品牌色。 |
| 13 横屏 | 成立 | 两端主 Activity 加 `android:screenOrientation="portrait"`（原来没有锁）。不承诺折叠屏/平板全适配；Android 16 大屏会忽略该锁。请 root 留意 lint `LockedOrientationActivity` 是否作为 warning 出现。 |
| 14 触控 <48dp | 不成立 | `PassengerTextButton` 本身 heightIn(min=48.dp)，并且 Material Button 有 minimumInteractiveComponentSize。按 p05 截图量，"交换起终点"约 78×48dp、"权限"约 48dp 高；报告的 69×42 应是文字 bounds。未改（"权限"已移出顶栏）。 |

## 改动文件
- apps/passenger-android/app/src/main/AndroidManifest.xml（竖屏）
- apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/{PlaceSelection, PassengerPickupDefaults, BookingScreens, MainActivity, ProfileScreens, OrderDetail, AmapSupport, PassengerPermissions}.kt
- apps/passenger-android/app/src/test/java/cn/hbzjhs/passenger/{PlaceSelectionTest, PassengerPickupDefaultsTest}.kt（新增 4+5 个纯 JUnit 用例，无新依赖）
- candidates/driver-0.3.6-r2/android/app/src/main/AndroidManifest.xml（竖屏）
- candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/AmapSupport.kt（同意页 FlowRow）

## 请 root 重点核
1. 编译：BookingScreens 新 import（onGloballyPositioned/positionInParent/LocalDensity）、两端 AmapCanvas 的 `@OptIn(ExperimentalLayoutApi::class)`。
2. JUnit：PlaceSelectionTest、PassengerPickupDefaultsTest（含已有用例回归）。
3. AVD：geo fix 先设加州再设宜昌，上车点应在下一次新 fix 时自动恢复；超距时卡片显示，两个按钮可用；手动选点后再来新 GPS 也不覆盖。半展开首页（Pixel7 和大字体）起终点应完整显示在主按钮上方。

## 未做 / 遗留
- P0 同步、金额口径（#1/#2/#10）等 backend 用同一 orderId 给出结论后再定。
- 暗色模式、折叠屏/平板、包体与构建变体不在本轮范围。
- 首页没有单独的"定位权限"快捷入口；权限缺失时上车点行仍会显示"未获取手机定位（原因）"。
