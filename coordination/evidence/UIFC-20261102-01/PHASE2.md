# UIFC-20261102-01 Phase2 司机端（作者自报，未验收）
session 43d13c15-717c-43bf-80c0-dfba56d72fc1 · claude-opus-5-5/high · 2026-11-02 · 未commit/stage。
乘客Phase1文件未再改动。仅改phase2_allow_paths：DriverPalette/UiKit/UIOComponents/BusinessKit/CoreScreens/MapPanel/NewOrderAlerts/MainActivity/DriverLocationService，新增CountdownPresentation.kt与CountdownPresentationTest.kt。未改CompetitionRules/GrabAlertRules/store/domain/manifest/Gradle/backend。

## 两个倒计时（分开）
- 新增纯 CountdownPresentation：ceilSeconds(null→null, ≤0→0, 向上取整)、fraction、competition(kind,status,remainingMs)→Hidden(BID/NEG/无competition)/NotStarted(READY)/Running/Closed(≤0或非OPEN)/Unknown(OPEN无anchor，不伪造数)。JUnit 5组用例。
- ①3秒提醒：NewOrderAlerts.reminderRemainingMs(key) 只读，校验book.current.key==key且key.account==book.account；返回book.remainingMs(now)。前台Dialog：顶部进度条+大号"N 秒后收起"徽标，卡片0.94宽/22sp标题/52dp按钮；100ms本地重组tick只读不写。后台Overlay：380dp卡、20sp标题、秒数徽标+水平ProgressBar、竞抢行；200ms Handler ticker只读，hide()/close()移除；ticker仅在ms非null时续排。book.deadline/owner expiry/sync未改。
- ②5秒竞抢：GrabActions.competitionRemainingMs(account, order)/(key) 只读，校验account==watch.account、status OPEN、key.round==row round，取既有anchors同一monotonic deadline−now；无anchor→null→Unknown。competitionCountdown()包装。CompetitionCard显示CompetitionCountdownBar(大秒数+进度/未开轮"首位司机抢单后开始5秒倒计时"/到0"已截止，等待服务器派单结果"/未知)；Dialog与Overlay对directGrab显示同一数据；大厅/详情按钮competitionLabel在CAN_GRAB追加"· 剩N秒"、COMPETING追加"· N秒出结果"，CLOSED_WAITING仍禁用。BID/NEG为Hidden、大厅BID仍"参与竞价"。无新网络轮询；大厅本地重绘tick 500→250ms、详情500→200ms（仅重组）。

## 取消/退出
- UiKit新增CancelActionButton(≥48dp，浅红底红描边+关闭图标，次于主按钮)；行程页"取消订单"改用它，onCancel不变（MainActivity cancelScope捕获/点击重验未动）。CompetitionCard"退出本轮竞抢"改整宽48dp描边危险按钮，onWithdraw不变。

## 重新定位（真实路径）
- DriverLocationService：新增ACTION_RETRY_LOCATION分支，在serviceGeneration++与flag读取之前return，不改listening/voice/floating/generation，不stop/restart。retryLocation()：校验权限、启用provider；R+ getCurrentLocation，旧版requestSingleUpdate；只接受elapsedRealtimeNanos晚于请求(容差1s)的新fix，经onLocationChanged并forceReport一次绕过14.5s节流走原location.report；20s无新fix→状态"暂未获取到新位置…"；retryToken防重复/迟到。companion running标志(onCreate权限通过后true，onDestroy false)。
- MainActivity：navigate("relocate")→relocate()：无权限→showPermissionGuide(既有指引)；系统定位关→ACTION_LOCATION_SOURCE_SETTINGS；未登录/未连接/既不在线也无行程→提示不启动(避免离线轮询)；service running→startService(retry action)；否则startForegroundService(locationServiceIntent()含全部extras)。原LaunchedEffect启动逻辑未改。
- CoreScreens：首页在线/在途且状态非"已定位/正在"时显示"定位异常"卡+重新定位按钮；我的→出车工具DetailRow状态着色+整宽"重新定位"。

## UI
- 调色：DriverPalette新增Indigo/Rose/Gold/BrandMist(与乘客同值)，moduleAccent重排分色；MenuRow改多色圆角图标块、≥52dp、有描述时顶对齐向下换行。
- 按钮：DriverButtonSize(主52/最小48/圆角14)；PrimaryButton渐变+轻阴影；DriverTextButton及对话框按钮≥48dp；BusinessOutlinedButton 46→48dp；钱包分段tab 40→48dp。
- DetailRow顶部对齐、label/value间距16dp、value右对齐weight换行。
- 会员卡：金色→品牌青渐变(Pale→BrandMist→白)+青描边，金色仅徽标；按钮48dp/Green主。
- 钱包：余额卡改青色渐变头部+装饰圆环+34sp白色金额，累计收入独立暖色条，数据不变。
- 缺省：EmptyState改Canvas插画(模块色，无位图，作用于所有EmptyState含我的订单/流水/消息)，动作改48dp描边按钮；新增EmptyHint组件(本阶段未逐页添加文案)。
- MapPanel：同乘客，抓手48×5、滑动提示(不朗读)、48dp图标+文字pill、fling阈值90dp/s；3锚点/nested scroll/customActions/BackHandler未改。

## 未完成/风险
- 卡券/发票/积分/邀请/反馈/屏蔽乘客等页面仅获得统一插画与48dp动作，未逐页加EmptyHint文案（BusinessFinance/Support/Community未改）。
- 编译点：NewOrderAlerts中`when(c)`混用is/对象、LinearProgressIndicator lambda progress(BOM 2024.06 → M3 1.2应可用)、ProgressBar tint需API21+；DriverLocationService getCurrentLocation(R+)/mainExecutor；MainActivity使用androidx.core LocationManagerCompat；UIOComponents MemberInk=GreenStrong跨文件顶层初始化。
- DriverDialog按钮由固定46dp改heightIn(min48)，同行不同换行时高度可能不等。
- retry在服务刚启动未建立listener时依赖onCreate；getCurrentLocation可能返回系统缓存，但已用elapsedRealtimeNanos过滤早于请求的fix。

## NOT_RUN
编译、CountdownPresentationTest及既有JUnit、两端原包/QA构建、隔离AVD(3s/5s计时、前后台handoff、重新定位权限/设置/数据、大字体、手势) 全部NOT_RUN（无Bash）。
