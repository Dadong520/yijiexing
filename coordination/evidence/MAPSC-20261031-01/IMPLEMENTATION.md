# MAPSC-20261031-01 实现记录（作者：claude）

- 任务：MAPSC-20261031-01（父任务 MAPS-20261031-01，root 唯一协调/验收/发布）
- session：9bc07c4c-731c-4b1f-840b-ce6a0c5dc85a（取自 task，未自行遥测）；claude-opus-5-5
- sourceRoot：/Users/dongge/.codex/tmp/yijiexing-map-panels-20261031，branch codex/map-panels-20261031，base 3587d5a
- 状态：源码改动完成，**未 commit/add**；未碰 Gradle/版本常量/Store/网络/业务接口/控制文件
- 测试：**编译 / 单测 / GUI / 真机 全部 NOT_RUN**（本会话无 Bash，不能声称编译通过）

## 更改文件（全部在 allow_paths 内）
| 文件 | 类型 |
|---|---|
| apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/MapPanel.kt | 新增（allow_paths 已列） |
| apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/BookingScreens.kt | 修改 |
| apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/OrderDetail.kt | 修改 |
| candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/MapPanel.kt | 新增（allow_paths 已列） |
| candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/CoreScreens.kt | 修改 |

未改：PassengerMap.kt、PassengerPickupDefaults.kt、NativeMap.kt（不需要）。没有新增其他文件。
**allow_paths 外的行为联动（未编辑该文件）**：ProfileScreens.kt 的“常用地址”编辑也调用 AddressDialog，会同样变成“地图在上 / 查询为空 / 选中才替换”。调用签名兼容：删掉的 `initialNormalization` 形参 ProfileScreens 本来就没有传。

## 实现行为
### 1. 拖动面板 MapPanelLayout（两端各一份，同构，只是包名、配色、按钮组件不同）
- 选型：BOM 2024.06 对应 Material3 1.2.1。它的 BottomSheetScaffold 只有 peek/expanded 两态，而且是实验 API，不满足三态需求。所以改用稳定的 `Modifier.draggable` + `animate()` + `NestedScrollConnection` 写了一个轻量原生组件，没有引入新依赖。
- 三态：Expanded（面板顶到 0，覆盖地图，内容可滚动）/ Half（默认）/ Collapsed（只剩把手条，地图全屏）。
- 拖动：拖把手条可以移动面板；松手时快速甩动（≥125dp/s）进到下一档，否则停到最近一档。内容区上滑会先把面板推到展开，再滚内容；下拉时内容先滚到顶，再把面板往下拉。fling 时如果面板在两档之间，由面板吃掉速度并归位。地图只接收面板以外的触摸，所以和地图手势不抢。
- 地图尺寸：地图底边对齐面板顶边（不被遮挡，自带控件、缩放、署名都可见）。只在越过半展线或停稳时 resize 一次，不会每帧 resize 原生 MapView。展开时地图仍留在组合中（不重建）。地图最小 120dp（极矮屏时部分在面板下），避免出现 0 尺寸地图。
- 健壮性：面板位置只在拖动/动画中用像素值，静止时跟随所在档位的锚点。屏幕高度、键盘、字号、把手/底栏实测高度变化时自动对齐，动画进行中会改瞄新锚点。档位用 rememberSaveable 按 saveKey 保存（旋转/重建后恢复）。
- 小屏大字：半展高度 = min(屏高×比例, 屏高−把手−固定底栏−最小内容)，在 0 和收起位之间截取，保证半展时把手、固定底栏和一段内容都可见。把手与底栏高度按实测值计算。
- 无障碍：把手可点（收起→半展→展开→半展），带 stateDescription 和 3 个 customActions（展开全部 / 半屏 / 收起显示全屏地图）。另有可见按钮：半展与展开时显示“看地图”和“展开全部/收起一半”，收起时显示一个居中的文字按钮（例如“显示订单与支付/取消操作”），三种状态行高一致。收起并停稳时，内容做 clearAndSetSemantics，读屏不会读到被裁掉的内容。
- 返回键：只在 Expanded 时拦截一次，回到 Half；其他状态不拦截。
- 重要操作可恢复：固定底栏（支付/取消、确认呼叫、司机下一步/导航/取消）在面板打开时始终固定显示，收起只是暂时隐藏，把手可以恢复。另外 `attentionKey` 变化（订单阶段、可支付/可取消、途经点/同意状态、听单状态、进行中订单、未支付单等）会把已收起的面板自动弹回半展，重要操作不会被永久藏住。
- systembars/键盘：沿用父级 Scaffold 的 padding 和 imePadding（乘客 Box 已有 imePadding）。面板按可用高度重算锚点。

### 2. 四个页面
- 乘客出行首页（BookingScreen）：地图在背景，叫车面板可拖；“确认呼叫/查看当前行程”是固定底栏。attentionKey = 进行中订单 id+stage、未支付单 id。
- 乘客订单地图页（OrderDetail）：原来嵌在“行程路线”卡片里 200dp 高的地图，改为整页背景地图。司机位置、精度、上报时间参数不变。支付/取消固定底栏原样移入面板底栏，条件与回调不变。attentionKey = stage、payable、cancellable。
- 司机听单首页（ConnectedHome）：资质横幅和身份栏仍固定在顶部、不被覆盖。地图加订单 LazyColumn 放进面板，半展时面板至少约 360dp（沿用原注释的约束）。attentionKey = 进行中订单 id/stage、听单在线、canAccept、网络在线。
- 司机行程地图页（ConnectedTrip）：只在 RESERVED/PICKUP/ARRIVED/TRIP 阶段使用面板（原来也只在这些阶段显示地图）。PageHeader 固定，原 190dp 列表内地图移为背景，原动作栏（导航、下一步、取消、途经点提示）作为固定底栏，逻辑一字不改。其余阶段保持“列表 + 底部动作栏”原布局。attentionKey = stage、hasPendingWaypoints、distancePending、canCancel。

### 3. 乘客新草稿默认值
- 目的地默认改为空（删除硬编码默认“夷陵广场 · 东侧入口”和 30.7040/111.2929）。空时显示“您要去哪儿？点此选择目的地”。提交时先检查“请先选择目的地”，原有的名称和坐标校验保留（空或 NaN 坐标经 `coordinate()` 拒绝）。
- 起点：沿用现有可靠 GPS 默认链路（PassengerPickupDefaults.usable/shouldReplace、owner/generation/fix 令牌、originTouched 手动优先、逆地理只写同一 owner/generation/fix），没有改动。新增一点：无定位权限或系统定位关闭时，占位文字显示“未获取手机定位（状态），点此选择上车点”，不会一直显示“正在获取”，也不会伪造位置。
- 不改：默认常用地址的保存与“默认常用地址”标记、默认乘车人（draftSelection）、已填草稿（rememberSaveable）、已有订单、“再走一次”、交换起终点、途经点。

### 4. 选址页 AddressDialog
- 布局：地图（有 AMap key 时是可点选的 AmapCanvas；没有 key 时是 PassengerMap 预览）→ 查询输入框 → 搜索进度/取消/重试 → 模糊搜索结果列表 → 城市框（可选，仅 AMap 时显示）→ 常用与历史地点。
- 每次打开查询框为空（BookingScreens 中用 `key(editor)` 隔离每个目标；对话框内部状态不再按 initial 预填）。已有坐标只用来在地图上定位（标记“当前地点”/原名称），不预填查询，也不会作为选择被重新提交。原名称只显示一条 Note“当前：X · 选择新地点后才会替换”。
- 只有选中（搜索结果 / 地图点选的逆地理结果 / 有坐标的常用或历史地点）才形成待用地点。“使用此地点”在未选中时禁用，并再次校验名称、坐标是否有限且在范围内。“返回”和系统返回只调用 close，草稿不变。只输入文字不会改变已选地点。
- 保护保留：400ms debounce（PassengerAddressLookup.debounced 未改）、PassengerLookupGate generation（输入、选中、地图点选、取消、关闭都会 invalidate）、stillWanted 改为比较查询框文本，过期结果不写。地图逆地理的 token 门控不变。新增 `pending` 只用于判断何时显示“重新搜索”。
- 无坐标的常用/历史地点：改为把名称填进查询框发起搜索，不会把未知坐标写成 0 或 NaN。

## 风险 / 待核
1. **未编译**。用到的 API 按 Compose BOM 2024.06 判断：foundation 1.6 的 draggable/rememberDraggableState、NestedScrollSource.Drag、mutableFloatStateOf/mutableIntStateOf、`WindowInsets.isImeVisible`（ExperimentalLayoutApi，已 OptIn）、activity-compose BackHandler、animate()。需要 root 实际 assemble 验证。
2. 原生 MapView（GLSurfaceView）在越过半展线时 resize 一次，需要在真机或AVD 上观察是否闪烁、黑边。展开覆盖 SurfaceView 的绘制顺序也需要实机确认。
3. AMap `newLatLngBounds(…,70)` 在最小 120dp 地图上的表现；原来地图 ≥150dp。
4. 司机 MapLibre 旧版地图 Column 下方的“© OpenFreeMap…”文本会落在面板下（首页原本就被挤掉，行程页是新出现的情况）。MapLibre 自带署名按钮仍在地图内，可见。
5. Expanded 时返回键先收到半展，属于交互变化。
6. 新草稿目的地为空时点“交换起终点”，会得到空起点并标记手动，GPS 不再回填（沿用原交换语义）。
7. ProfileScreens 常用地址编辑联动（见上）：编辑已有地址需要重新选点。
8. 无 AMap key 时选址页会显示 MapLibre 预览地图（会请求 openfreemap 瓦片），原来这种情况下不显示地图。
9. 嵌套滚动：内容在顶部向下快速 fling 时，只有手指确实拖动了面板才会换档；内容自身的 fling 不会带动面板。

## 验收建议（非作者执行）
- 两端 `assembleDebug` 加现有单测（PassengerAddressLookup/PickupDefaults/Defaults 等应不受影响）。
- AVD：360×640 小屏、字号 1.3/2.0、三键导航与手势导航、横竖屏切换、进程重建。四个页面逐一验证：拖把手、甩动、内容滚动与面板联动、地图平移缩放不抢手势、三个按钮、TalkBack customActions、Expanded 时返回键、收起后由 attentionKey 自动弹回（用 fixture 改订单阶段或可支付状态）。
- 乘客新草稿：mock 可靠定位时起点自动填入；无权限或定位关闭时显示提示，不伪造位置；手动起点不被 GPS 覆盖；目的地为空时提交提示“请先选择目的地”。
- 选址：每次打开查询框为空；地图在输入框上方，结果在输入框下方；debounce、取消、重试、过期结果、owner 保护；返回不改草稿；选中后“使用此地点”才生效；常用、历史、途经点、无坐标常用地点按名称搜索；订单页“修改/补充目的地”；“我的 → 常用地址”编辑。
- 不做实时业务写入，不使用个人手机或原测试/生产环境；资金、订单业务和高德问题不在本轮范围。
