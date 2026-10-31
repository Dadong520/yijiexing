# RIDEC-20261031-01 实现记录（claude）
会话 6c0ddf57-d854-4c5c-a8a9-43f7ee7e3063（取自task）；source /Users/dongge/.codex/tmp/yijiexing-ride-flow-polish-20261031，branch codex/ride-flow-polish-20261031，base c261d78。2026-10-31。
未commit/add；无Bash：编译、单测、GUI、native全部 NOT_RUN，交独立Codex验收。未改Gradle/版本/Store/backend/network/控制或task文件；两端MapPanel.kt未改。

## 改动文件（均在allow_paths）
乘客 apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/：
- PlaceSelection.kt（新）：纯逻辑。PickedPlace/PlaceResolution(Ready/SearchByName/Unusable)/PlaceRow；PlaceSelection.coordinate/resolve/resolveRow/pickerRows/nextStopSlot；PlacePickSession（每次打开最多交付一次，与文本搜索/地图共用PassengerLookupGate）。
- BookingScreens.kt：AddressDialog一击确认重写；首页路线/分段类型；途经点入口移到目的地页；订单列表扁平。
- OrderDetail.kt：目的地修改改为“点选地址→独立确认重新估价”；状态头/底栏扁平、按钮等高。
- ProfileScreens.kt：头部/快捷入口扁平；常用地址选址标题（保存仍需“保存”）。
- PassengerPages.kt：PassengerFullPage白底、轻返回、可传scrollState。
- PassengerPalette.kt / MainActivity.kt / UIOComponents.kt / UiComponents.kt / PassengerCards.kt：共享视觉令牌与组件。
- 测试 app/src/test/.../PlaceSelectionTest.kt（新，9例）。
司机 candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/：DriverPalette.kt、UiKit.kt、UIOComponents.kt、CoreScreens.kt。

## 历史地点“点击无反应”诊断（只读源码，未在设备复现）
后端 apps/cloud-service/passenger.py:195-202 历史行为 `{address, latitude, longitude, coordinateMetadata}`，坐标取自订单原值。旧AddressDialog三条路径都表现为“没反应”：
1. 有效行：只写入隐藏selection，可见反馈仅在页底固定栏“已选：…”，还需再点“使用此地点”。
2. 坐标缺失/0：静默调用searchFor(名称)，进度/结果/错误在页面上方查询区（已滚出视野）或页底，用户看不到。
3. 越界坐标：coordText只拒0/NaN，越界值被选中但“使用此地点”因routePointReady禁用，成为死点击。
修复：resolveRow严格校验（数值或数字文本、有限、非0、纬±90/经±180）；有效行一次点击交付并返回；缺失/越界/损坏 → 不使用、不填0，按名称搜索并显示琥珀色说明（“未提供坐标/坐标无效，不能直接使用…”），页面滚回顶部使查询区与结果可见；同名重复点击用retryTick重启搜索；无名行不显示。

## 行为
1. 一击确认（所有调用者）：搜索结果、常用/体验地点、有效历史一点即save并返回；地图点击→显示“正在识别地图位置…/取消”→仅当该次逆地理仍为最新请求(gate)、未提交且resolve为Ready才save；失败或无有效点则留页显示错误、可再点，不猜坐标。删除“使用此地点”按钮及页底栏。保留：查询初始空、地图在上/输入在中/结果在下、400ms debounce(PassengerAddressLookup.debounced+gate所有权)、城市框、取消/重新搜索、手动GPS（originTouched/gpsGeneration不变）。PlacePickSession保证单击去重：已提交后一切点击/迟到地图/迟到搜索都不能再写。
2. 调用者边界：首页草稿只替换对应地址字段，保存回调再校验 editor==target 且当前登录乘客==owner，否则丢弃；选址不下单/付款。常用地址：只回填地址字段，仍须“保存”。订单详情：点选后弹PassengerModal“确认修改/补充目的地？…确认并重新估价”，确认时按当前订单重新判断可改条件（AVAILABLE且无报价，或目的地待补且PICKUP/ARRIVED），否则reportError不提交；取消则不改。
3. 途经点：从“行程设置”删除途经点区块与showWaypoints；目的地页查询框旁“＋途经点”（最多2，nextStopSlot决定槽位，满2隐藏）；有途经点时目的地页显示“起点→途经点1/2(修改/删除)→终点”，删除1时2上移。途经点选择页点选即确认并回到目的地页，返回也回目的地页不改；目的地点选即回首页，不自动下单。首页路线卡在上车点与目的地间显示“途经点 · n/2 · 点此修改”摘要，点击进目的地页；已有草稿点保留。下单payload按名称非空带waypoints（不再依赖已删除的showWaypoints开关）。交换起终点在两端有效时同时反转两个途经点顺序（新增，见风险）。
4. 视觉（共享组件→全应用受益）：Paper/Page近白F7F8FA；Panel/SheetCard白底12dp圆角、发丝边、0阴影、标题去渐变竖条；主按钮(GradientButton/PrimaryButton)纯青绿、无阴影，禁用SurfaceMuted+可读DisabledText；次按钮/文字按钮为中性灰底品牌字；危险按钮白底红边红字；MenuRow单色图标去彩色渐变块；空状态中性圆；BrandGradient/HeroGradient改纯色（兼容保留）。弹窗16dp/轻阴影，确认按钮0阴影。乘客首页：紧凑分段(即刻/预约/竞价)、“您要去哪儿？”标题+轻量交换、线性路线卡、单行说明、扁平“更多行程设置”、底栏发丝分隔；进行中行程改轻量行。地址页白底扁平列表、历史/常用分组清晰。订单列表每单一张扁平卡（去卡中卡）。我的页头部与三入口扁平。司机：首页之家头部改白卡、钱包余额白卡+唯一主按钮、行程操作栏去阴影、会员卡去渐变/阴影、钱包分段轨道统一色。MapPanel/ChromeScope/全屏逻辑未动。保留所有模拟/未接服务说明文字。

## 测试
PlaceSelectionTest（NOT_RUN）：有效历史含metadata一次Ready/数字文本；缺失(null/JSON null/0/空串)=MISSING、越界/非数/NaN/Infinity=BROKEN且从不Ready；空名Unusable；pickerRows去重/保留未知历史/丢弃无名；二次点击不再提交；无效点不消耗session；地图查询中点历史后迟到地图结果不能提交；旧地图token失效、新token无效点不提交可重试；400ms等待中点选使debounced不begin/publish（用真实PassengerAddressLookup+gate）；途经点槽位。
编译/assemble/全部单测/GUI/native：NOT_RUN。

## 风险/待验收
- 编译未跑：重点看BookingScreens（可空@Composable ColumnScope lambda推断、return@AddressDialog、Modifier.border、Role.Tab）、PassengerPages新增ScrollState默认参数、OrderDetail PickedPlace/ PassengerModal、司机UIOComponents SolidColor导入、CoreScreens SheetCard(clickable)。
- 地图点选现直接提交，用户无二次核对；高德真实逆地理/POI仍NOT_RUN，坐标系沿用reverseAmapAddress归一化。
- 交换起终点时反转两途经点顺序是新增行为；如不需要可删该4行。
- 订单目的地新增确认弹窗（取代原“使用此地点”作为重新估价确认），多一步但为资金相关保护。
- 旧GUI脚本若查找“使用此地点”“含途经点/无途经点”“选择地点”标题、“更多行程设置”中途经点区块、Profile渐变头部，需要更新。
- 视觉仅源码级，未截图对比；大字号下分段控件/按钮等高、深色背景对比度需native核验。
