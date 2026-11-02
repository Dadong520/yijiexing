# UIFC-20261102-01 Phase1 乘客端（作者自报，未验收）
session 43d13c15-717c-43bf-80c0-dfba56d72fc1 · claude-opus-5-5/high · 2026-11-02
source /Users/dongge/.codex/tmp/yijiexing-ui-feedback-20261102 · branch codex/ui-feedback-20261102 · base 874aad43e742e7719519758df36724f540b3a038 · 未commit/stage。

## 实际修改（仅phase1_allow_paths内）
- PassengerPalette.kt：新增克制辅助色 Indigo/Rose/Gold(+Soft)、BrandMist；iconAccent按模块分色（位置/通知=Sky，人/屏蔽/分享=Indigo，发票/列表=Warm，卡券=Gold，积分=Rose，反馈=Violet，安全/电话=Success，投诉=Danger，设置/说明=中性）。
- UIOComponents.kt：ButtonSize(主52dp/最小48dp/圆角14)；GradientButton改Teal→BrandStrong横向渐变+轻阴影，图标20dp间距8；SecondaryButton改BrandSoft+细描边，新增可选icon；DangerButton浅红底+可选icon；新增IconActionButton(图标+可见文字，≥48dp，role Button+onClickLabel，图标装饰不重复朗读)、EmptyHint(下一步指引)、RecordRow(左标签/右对齐值)；EmptyIllustration改Canvas矢量插画（模块色底、空白记录卡、点缀、白底徽标图标），无位图，经MainActivity.EmptyCard自动作用于全部缺省页。
- UiComponents.kt：MenuRow改多色圆角图标块(36dp)、行高≥52dp，有副标题时顶对齐向下换行；PassengerTextButton最小48dp、浅品牌底+细描边；PassengerModal/Dialog按钮最小48dp统一圆角。
- MapPanel.kt：handle加大（抓手48×5dp、滑动提示文字(不朗读)、两枚48dp图标+文字pill：看地图/展开全部|收起一半，收起态为整宽Teal“显示叫车面板/显示订单…”）；fling阈值125→90dp/s；默认handle估高96dp。保留3锚点、nested scroll、BackHandler、stateDescription与3个customActions、chrome claim逻辑不变。
- BookingScreens.kt OrdersScreen：FlowRow FilterChip改紧凑分段tabs（灰轨4dp内边距、等宽≥48dp、选中白底描边、selectable role=Tab、显示各分类数量，计数复用PassengerUiRules.matchesOrderFilter）；有订单但当前分类为空时加指引。
- ProfileScreens.kt：三快捷入口多色44dp图标块；头像渐变底；退出按钮改DangerButton+ExitToApp；反馈记录用RecordRow并加缺省指引；投诉空状态加指引。
- ExtraScreens.kt：发票“申请记录”KV→RecordRow（左右靠边）；回执空状态与已屏蔽司机空状态加指引；恢复匹配按钮加Refresh图标。
- ParityScreens.kt：卡券/积分/邀请/安全联系人空状态加真实指引（不虚构服务）；110按钮加Call图标；联系人“拨号”改IconActionButton。
- OrderDetail.kt：联系与帮助、司机服务卡、联系司机/客服、不再匹配、投诉与问题反馈改为图标+文字48dp按钮；添加投诉图片按钮加Add图标。事件/对话框/状态判断不变。
- PassengerContactPage.kt：禁用的“司机电话暂不可用”改为带电话图标的禁用IconActionButton；“反馈本次行程问题”改为带图标按钮。
未改：PassengerCards.kt（PassengerTextButton 48dp已间接作用）、MainActivity(EmptyCard/KV/Panel属禁区)、store/rules/manifest/Gradle/司机端。

## 未通过/风险（需root复核）
- 编译未运行：重点看 UIOComponents `Modifier.shadow(ambientColor/spotColor)`、`background(Brush)`、DrawScope `density`；OrderDetail 新增 icons import 与 `semantics.*` 通配无冲突；BookingScreens移除FilterChip后的未用import仅告警。
- MapPanel handle变高(约96dp)：小屏+大字体下Half锚点更低，minHalfContent仍保证；需AVD看收起态地图面积与按钮换行。
- PassengerTextButton全局48dp/浅底描边会使部分行（如联系人编辑/删除、地址管理行）变高。
- “申请记录居中”判定为KV按38%列宽对齐所致；仅在申请记录/反馈记录改为RecordRow，其他KV未动（KV在MainActivity，不在允许范围）。若需全局统一请root授权MainActivity.kt KV。
- 缺省页插画对EmptyCard全局生效；EmptyCard本身布局未改。

## NOT_RUN
编译、JUnit、原包/QA构建、AVD手势/TalkBack/大字体/截图 全部NOT_RUN（无Bash）。

## 剩余
Phase2司机端待root派发；司机palette未改。
