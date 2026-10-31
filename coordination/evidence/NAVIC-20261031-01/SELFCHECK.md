# NAVIC-20261031-01 作者自检（claude，phase1）

session a6956536-07bf-4683-9cd9-7185a2cf43fa（取自task）；source /Users/dongge/.codex/tmp/yijiexing-in-app-navigation-20261031，branch codex/in-app-navigation-20261031，base d5d878e。未commit/stage/合并/部署；无Bash，**全部编译/单测/AVD/真机 NOT_RUN**，由root实跑。

## 改动文件（均在allow_paths）
- `app/build.gradle.kts`：旧`3dmap-location-search:11.2.100_loc11.2.100_sea9.8.1`**替换**为固定`navi-3dmap-location-search:11.2.100_3dmap11.2.100_loc11.2.100_sea9.8.1`；MapLibre保留；版本号未改。
- `AndroidManifest.xml`：仅新增`com.amap.api.location.APSService`(exported=false)。未加后台定位/WRITE_SETTINGS/CALL_PHONE；旧`geo` queries保留（androidTest仍用URI builder），runtime不再外跳。
- 新`NavigationSessionRules.kt`（纯函数）：NavigationLeg/LegDecision/NavigationOwner/StopRow、`legFor`、`exitReason`、`stopRows`、`reliableStart`、`endpointMatches`、`RouteRequestTracker`。
- 新`InAppNavigation.kt`：NaviOwnership单owner、InAppNaviController（AMapNaviListener+AMapNaviViewListener）、InAppNavigationPanel、导航隐私说明卡。
- 新`PassengerContact.kt`：PassengerContactRules + PassengerContactAction（点击取号→确认弹窗→ACTION_DIAL）。
- `ConnectedStore.kt`：新增`fetchPassengerContact`（仅此方法）。
- `AmapSupport.kt`：AmapRuntime新增独立`navigationConsent`/`NAVIGATION_NOTICE`/`agreeNavigation`/`prepareNavigation`。
- `NativeMap.kt`：删除`openExternalNavigation`/launchNavigation/AlertDialog外跳；`navigateForNewLeg`、`AutoNavigationObserver`改为回调订单id（保留AutoNavigationMemory持久去重与首个可用snapshot基线）；URI builder保留供旧测试。
- `MainActivity.kt`：ConnectedApp持唯一`navOwnerValue`（rememberSaveable，仅"account\norderId"）、`openNavigation`、退出LaunchedEffect；接单/观察者改走openNavigation；向ConnectedTrip传navigating/onNavigate/onExitNavigation。
- `CoreScreens.kt`：ConnectedTrip导航模式（上SDK、下独立业务区≤50%高度可滚动+原tripActions；BackHandler先退导航）；导航按钮改为应用内；出发接驾/确认上车后的自动导航改走onNavigate；动作区加"联系乘客"；首页/大厅/预约/订单列表/详情/竞价改用有序途经组件。
- `TripWaypoints.kt`：新增`OrderStopsList`、`OrderCardWithStops`；途经卡增加"下一站"标记。确认逻辑未改。
- `NavigationContract.kt`：仅加注释（legacy，runtime不调用）。
- 新`test/.../NavigationSessionRulesTest.kt`：13个测试。`NavigationContractTest.kt`未改。

## 核心设计
1. **目标/分段**：`legFor`按服务端订单：RESERVED/PICKUP/ARRIVED→origin；TRIP→`pendingWaypoints.firstOrNull()`（原数组0-based index，非sortNo、不排序）→全部ACK后destination。WP只在服务端arrivedWaypoints变化后切下一站；A→B→A是两段。destinationPending/缺坐标/0,0→Unavailable可见提示，不跳站。RESERVED可手动看导航，出发仍需"出发接驾"。
2. **SDK事件只提示**：onArriveDestination仅设arrivalHint，onArrivedWayPoint空实现；从不perform/advance/标WP。尾号、距离同意、全部WP完成、取消、费率等原逻辑与门禁未改，导航模式直接复用ConnectedTrip原tripActions/尾号对话框/取消。
3. **坐标**：业务WGS84不变；目标与可选起点各经`amapForward`转一次GCJ。起点仅用≤120s且精度≤100m的DriverLocationService手机定位，否则调用无起点重载由SDK GPS定位；不造0/0；SDK定位不回写业务。
4. **请求隔离**：`RouteRequestTracker`代数+legKey；两种success/failure回调只首个生效；无pending的回调（SDK偏航重算/已释放）忽略；新目标/释放即invalidate；follow经main.post发起以排空孪生回调；启动前再校验`naviPath.endPoint`距本段GCJ目标≤1000m，防旧请求结果被误归属。所有回调切主线程。
5. **生命周期**：NaviOwnership单owner；controller按(sessionKey,key,attempt)remember一次，不随重组重建；DisposableEffect(controller)成对resume/pause，onDispose：移listener→stopNavi→view.setListener(null)+onDestroy→AMapNavi.destroy；released后所有回调丢弃。失败卡含重试/退出；初始化失败可重建。
6. **会话归属/退出**：exitReason：账号变/登出→退出；离开detail页→静默退出；订单消失/WAIT_PAY/DONE/CANCELLED/参与取消→退出并提示。自动打开只经AutoNavigationMemory.claim（每账号/订单/leg一次），手动退出后不会自动反复打开；手动按钮可再次进入。
7. **隐私**：导航有独立说明与同意（prefs键navigation_accepted_v1），旧地图同意不算导航同意；同意后才`NaviSetting.updatePrivacyShow/Agree`+`AMapNavi.setApiKey(context,key)`（key来自现有AmapRuntime服务端配置，未硬编码）。
8. **联系乘客**：仅点击调用`GET /api/driver/passenger-contact?orderId=<URL编码>`，Bearer当前credentials；不经launchRequest，不改state/loading/error/connected/不登出；结果仅在同session epoch+credentials时回调。号码仅在remember(scope=account|order|stage)内存；scope变即丢弃；拨号前重核最新owner/stage；ACTION_DIAL（非CALL）；不进DTO/日志/prefs/saveable/剪贴板；toString不含号码。401/404/409(UNAVAILABLE/INACTIVE)分别明确提示。

## NOT_RUN（全部）
Gradle编译、checkDebugDuplicateClasses、JUnit（新13+原有）、androidTest、APK、隔离arm AVD、真实key/服务授权、真实算路/GPS/语音/路况、导航View渲染遮挡触摸、旋转/前后台/来电返回、小屏360dp/font1.3/IME实测、后端联系接口联调。不声称任何算路/GPS/语音PASS。

## 风险/需root重点核
- Kotlin覆写Java数组参数（`Array<out T>?`）、`viewOptions`合成属性、`setSettingMenuEnabled(Boolean)`装箱：按javap编写，未编译。
- 孪生回调若在新请求发起后才到达，仍可能被归给新请求；已用post排空+终点距离校验兜底，阈值1000m为经验值。
- 旋转/进程重建会销毁并重建SDK会话后重新算路（navOwner可保存）；后台/锁屏持续导航未实现（无后台定位），需实测SDK行为。
- APSService是否必需未证实（官方配置要求加入）。
- 导航模式业务区高度上限50%，小屏font1.3下是否足够需实图。
- OrderCardWithStops为OrderCard下方附加卡（UiKit不在allow，未改OrderCard内部）；BusinessSupport/NewOrderDialog里的RouteStops不在allow未改。
- 后端联系接口实施中，返回字段/状态码按契约实现。

## 下一步
root：编译+duplicateClasses+JUnit；独立审查上述风险；隔离arm AVD验证导航页生命周期、失败可见/重试、业务按钮未遮挡；退回在同一session修。

---

# Phase2（root非作者源审修复，同SID a6956536，基于root导入backend HEAD 445787f之上的司机dirty树）
未commit/stage；无Bash：**编译/JUnit/APK/AVD全部NOT_RUN**。只改allow_paths：InAppNavigation.kt、NavigationSessionRules.kt、NavigationSessionRulesTest.kt、UiKit.kt（仅RouteStops函数体）、TripWaypoints.kt、CoreScreens.kt、build.gradle.kts、ConnectedStore.kt（仅fetchPassengerContact可见性）、PassengerContact.kt。backend/乘客/版本号未动。

## P1 路线请求归属（替换phase1的RouteRequestTracker，已删除）
phase1缺陷确认：begin(B)覆盖未完成的pendingA，A迟到回调会complete出B；main.post不能排空网络回调；1000m不能区分100m内两个途经点。phase2改为**每leg（及每次重试）独占一个引擎+view+listener+一次请求**：
- `InAppNavigationPanel`按`remember(sessionKey, apiKey, leg.key, attempt)`创建`InAppNaviController`；`DisposableEffect(controller)`内`open()`：acquire NaviOwnership→prepareNavigation→`AMapNavi.getInstance`→注册**该leg专属的`LegRouteListener`对象**→创建AMapNaviView。onDispose：pause→`release()`：gate.release→移除该listener→stopNavi→view.setListener(null)+onDestroy→`AMapNavi.destroy()`。Compose对换key先执行旧effect的onDispose再执行新effect，所以新leg的`getInstance`发生在旧实例destroy之后，新listener只注册在新实例上。旧引擎的迟到回调只可能到达旧listener，其gate已RELEASED→一律Ignore。切leg/重试/退出/旋转/账号切换同路径。Unavailable时不创建引擎。
- `LegRouteGate`（纯类，synchronized）：CREATED→REQUESTED仅`claimRequest()`一次（引擎ready与3s兜底定时器重复调用也只发一个请求）；只有REQUESTED下**第一个modern结果**决定Start/Fail；之后的结果（SDK偏航/拥堵重算、超时后迟到、成功后迟到失败）全部Ignore；RELEASED什么也不决定。
- `LegRouteListener`（真实SDK适配器，实现AMapNaviListener）：只把`onCalculateRouteSuccess/Failure(AMapCalcRouteResult)`送入gate；deprecated `int[]`/`int`孪生**明确不路由**；onInitNaviSuccess→EngineReady；到达→仅在NAVIGATING时发NearTarget提示；onArrivedWayPoint空。controller把事件经本controller的Handler切主线程，handle时再查gate.live；startGuidance前再查phase==NAVIGATING。
- 定时器（3s无init回调则请求、30s规划超时）只负责不无限等待，归属不依赖任何时序；超时后迟到成功被gate忽略。
- 1000m终点检查保留，仅作本leg路线的道路吸附合理性检查，代码注释明确不作归属证据。
- 依赖的SDK层前提（需root/AVD实证）：destroy后getInstance得到新实例且旧实例不再回调新注册的listener；11.2.100每次算路都会回调modern `AMapCalcRouteResult`版本（若不回调，30s显示超时可重试，不会误启动）。每leg重建引擎带来的TTS/初始化开销与短暂黑屏未实测。

## reliableStart
accuracy须`>0且<=100`（0/负/NaN拒绝），另含过时>120s/未来时间/NaN坐标/0,0。DriverLocationService未改。

## P2 途经点共享展示
UiKit.kt只改`RouteStops`函数体→委托`OrderStopsList`（起/途N/终、已完成、下一站；原数组0-based index）。OrderCard、NewOrderDialog、BusinessSupport等已有RouteStops调用方自动共享。删除`OrderCardWithStops`，CoreScreens恢复`OrderCard`/`RouteStops`调用。重复消除：详情页概要RouteStops已列全部点，TripWaypoints确认卡只在TRIP显示；导航业务区TRIP且有途经点时只显示TripWaypoints确认卡，否则显示RouteStops。确认动作的index/sortNo语义未改。

## APK体积
`packaging { jniLibs { useLegacyPackaging = true } }`：JNI在APK内压缩，安装时提取；未删任何ABI、未pickFirst、未改服务器限制。实际APK大小/是否≤128MiB/native装载NOT_RUN由root构建与AVD核。若仍超限，安全替代需root决策（如按ABI拆分分发），作者未静默删ARM架构。

## 联系乘客
`ConnectedStore.fetchPassengerContact`改internal（不再public暴露internal类型）。parse只接受ASCII `1[0-9]{10}`（不trim；掩码/短号/空格/+86/全角拒绝）。PassengerContactAction新增scope绑定alive标志：离开页面或scope变→清号码、迟到响应不弹窗；拨号前再核alive+最新owner/stage。仍ACTION_DIAL、无CALL、无持久化/日志。

## 测试（新增/改写，NOT_RUN）
NavigationSessionRulesTest共17项：保留原leg/stopRows/exit/owner/endpoint；删tracker测试；新增gate+真实LegRouteListener trace：A未完成切到相距~100m的B后A的两种success/failure任意顺序+init均不产生事件、B状态不变且只由自己结果启动；两种回调任意顺序只决定一次；失败/超时后迟到成功被忽略、启动后迟到失败不改状态；偏航重算不算新请求、重复claim只一次；连续重试只最新会话决定、释放（账号切换）后静默；到达只提示。reliableStart补0/负/NaN/未来/过时/NaN坐标；联系号码补8种非法格式。测试以null传AMapCalcRouteResult，需SDK接口类在JVM单测classpath可加载（未验证）。

## 风险
Kotlin覆写Java数组参数与`viewOptions`合成属性仍未编译；每leg销毁重建引擎的体验与TTS未实测；上述SDK层前提需实证；业务区50%高度小屏实图待核。

---

# Phase3（root D1冻结83c6fde原生实测后修复，同SID a6956536）
已有事实只引用root：83c6fde（version24/0.3.21）原包/QA编译成功、94 JUnit零失败、原包133014362 bytes，以及隔离ARM64 D1-native实测结果（见evidence/NAVI-20261031-01/D1-native/）。**本阶段新改动未编译、未测试，全部NOT_RUN**；未commit/stage；version、backend、MapPanel.kt等非allow文件未改。

## 修改
1. **新单弹窗/接单确认显示全路线**（NewOrderAlerts.kt、MainActivity.kt、NavigationSessionRules.kt）：NewOrderDialog在已同步订单详情可用时改用共享`RouteStops(detail, compact=true)`（起/途N/终，原数组顺序）；无详情时保留原起/终两行。路线+金额区放进`weight(1f, fill=false)+verticalScroll`，按钮`heightIn(min=44dp)`，大字体小屏时按钮不被挤出。通知渠道/浮窗/触发/去重/接单回调未改。确认接单CoreConfirm的message改为`NavigationSessionRules.routeSummary(order)`（"起：…\n途1：…\n终：…"），确认/接单action不变。
2. **导航页三态面板**（CoreScreens.kt）：navigating分支删除固定50%布局，改为调用现有`MapPanelLayout`（MapPanel.kt未改）：saveKey=`driverTripMapScope(order.id)`；map槽固定调用同一个`InAppNavigationPanel`（controller按account|order|leg.key|attempt remember，拖动只改尺寸、不重建/不重新算路/不执行动作）；content为可滚动LazyColumn（shared/私有演示提示、TRIP途经确认卡或RouteStops、距离同意卡、ARRIVED尾号）；bottomBar复用原tripActions。全屏（collapsed）时chromeHidden隐藏应用标题行（含"退出导航"）；此时仍可通过面板把手恢复、SDK自带退出按钮或系统返回退出。attentionKey=阶段、下一途经index、待确认途经、距离同意、canCancel、参与取消。返回顺序：面板展开→半屏（MapPanelLayout内）→退出导航→离开页面。chrome claim由MapPanelLayout按token在onDispose释放。
3. **native装载失败**（InAppNavigation.kt）：`open()`在原`catch (Exception)`之外只增加`catch (LinkageError)`（含UnsatisfiedLinkError/NoClassDefFoundError/ExceptionInInitializerError），置新`NaviStatus.Unsupported`：显示"此设备暂不支持应用内导航…订单操作不受影响"+退出（无重试），并release。不捕获其它Error，不外跳。ARM成功路径代码不变。
4. **导航模式演示提示与联系**：导航content加入`SharedParticipationNotice`与私有演示"未关联乘客App"卡；`PassengerContactAction`对`isPrivateExample`订单只显示"演示订单未关联乘客，无可联系电话"，不请求服务端、不打开拨号。stage资格和共享单联系流程不变。
5. **DriverTripDistance.kt**：仅把提示文案改为"提示：订单暂未更新。等待乘客确认后，订单才会更新。"；阈值/原因/有效期/按钮/规则未动。

## 测试
新增`acceptSummaryListsRoundTripStopsInOrder`（往返单A→B→C2→A摘要顺序）；共18项，NOT_RUN。

## 风险/待root实证
- MapPanelLayout在Expanded时导航view仍保持halfTop高度位于面板下方；全屏时SDK SurfaceView与面板把手的叠放和触摸、拖动中SDK view尺寸变化后的绘制，需实图确认。
- 全屏时应用"退出导航"随标题隐藏，退出依赖SDK按钮/把手恢复/系统返回。
- NewOrderDialog在Dialog最大高度内的滚动及font1.3按钮可达需实图。
- LinkageError路径只能在x86 AVD或人为缺库时验证。
