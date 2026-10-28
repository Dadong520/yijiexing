# PUIF-20261028-01 乘客UI意见：原图—现有代码映射

结论：13张原图均逐张打开并看完，3段正文及DOCX图片关系已复核。多数可先作为乘客Android布局改造实现；默认项持久化和真实司机沟通应另列服务能力任务。本报告是用户原图与源码的静态映射，**不是实机流程审计/功能验收**，未修改产品。

固定源树 `/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027`；branch `codex/passenger-ui-intake-20261028`；实际HEAD与基线均 `8e2d655bd58d33241d79d6da3ad82f29cdec7894`，tree `882ccd932e7ca0d565ad521511f7e8ecb9fe85d7`，git干净。派单所提更早5d检查点不作本轮代码依据。本版乘客1.1.14/code16已发布（按中央STATE），mini有源码测试但缺AppID，未微信运行/发布。

来源：`/Users/dongge/Downloads/UI设计-乘客端.docx`；manifest SHA256 `f95baea0b299883954aa86aa3ebd817d53444a4e384c7ee80d677d230ea3a2cc`。原图目录 `/Users/dongge/.codex/tmp/yijiexing-passenger-ui-intake-20261028/word/media/`。图号是文件名，不等于意见编号。正文显式编号1–4、7–15；另有未编号“取消订单固定理由及其他”。**未出现5、6，以下不替其补造内容。**

路径前缀：A=`apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/`；M=`candidates/passenger-miniprogram-0.1.0/miniprogram/`，相对上述固定源树；下列位置均可精确定位。

|原意见|原图 / 所见证据|Android位置及复用|mini差别 / 建议实现界限|
|---|---|---|---|
|1 预约提示明显化|image1：单行描边胶囊像普通按钮|A/BookingScreens.kt:148-160，已有预约列表与scheduledAt；改状态横幅、预约时间和次级“查看”，保留跳转|M/pages/home/index.wxml:31-35已有“预约保留，可继续即时叫车”副标题，比原生更明确；保持BIZ04预约待出发不占即时，不引入强制出发限制|
|2 更多设置移子页|image2：展开区域占满可视区|A/BookingScreens.kt:48-80的rememberSaveable草稿及:288起ExpandablePanel可复用字段；MainActivity.kt:279页面路由可扩展，需要先上提同一份草稿再导航|M/pages/home/index.wxml:99-148把预约时间/乘车人/备注直接排在首页，并非同一折叠组件；两端需分别做子页或设置面板，往返/旋转/切类型不丢草稿，不新增提交或改payload|
|3 卡片风、少量辅助色|image3/5/6显示重复白色大卡，视觉重心弱|A/MainActivity.kt:55-59已有Teal/Ink/Muted/Paper/Amber及Panel(:318)；A/UiComponents.kt:41/84/129已有菜单/折叠/地址行。优先统一密度、状态色与信息层级|mini复用pages/shared.wxss和各页wxss；现有个人中心已经有网格/统计卡，不能逐字照搬原生长菜单。无须新品牌/更换全部图标；主色保持，辅助色表达状态且不单靠颜色|
|4 详情路线优先、费用和取消收敛|image3：费用大卡在路线之前；image4见取消入口在更下方|A/OrderDetail.kt:68-92头部与编号、:178费用、:224-293路线、:294司机/:307取消；用同一权威o对象重排路线为首要内容，金额/付款状态/取消用紧凑摘要和动作区|mini详情已在M/pages/order-detail/index.wxml:67地图、:73司机、:97路线之后才:126费用，层级与原生不同；只补密度/信息关联。保持付款金额/版本确认、距离同意、竞价/议价/预约等特殊状态，不让固定底栏盖住未完流程|
|未编号 取消理由|image4：仅“行程计划有变”输入框|A/OrderDetail.kt:555-601复用PassengerModal；固定选项+“其他”输入，仍写order.cancel.reason，空/超长/双击需拦截|M/pages/order-detail/index.js:236-244目前确认后固定“乘客主动取消”，无用户输入。后端apps/cloud-service/passenger.py:335-337已经接受2–200字reason；前端即可提供选择+其他。不要自行增加有责/罚金/退款承诺；同一原请求重试保持幂等|
|7 筛选顺序|image5：进行中、完成、取消、全部|A/BookingScreens.kt:593-611当前默认进行中；仅重排tabs为全部/进行中/完成/取消。默认选中是否也改“全部”，原文未明确，应作为实施默认显式交代|mini M/pages/orders/index.wxml:1目前没有四类筛选，JS只有open；需新增客户端派生筛选，并保留WAIT_PAY和WAIT_CONTINUE归进行中、预约列表可见，不改后台订单排序/并发规则|
|8 订单卡重排|image5：类型、状态、路线、时间、费用、编号散开|A/BookingScreens.kt:637起订单Card，复用orderSummary、kindName、formatTime、IdentifierButton。建议状态/时间为一行，起终点为主，金额与预约时间为紧凑尾行，编号移详情更多|mini订单卡已更紧凑但预约时间未如Android单列；M/pages/orders/index.wxml:1和pages/shared.js:decorate可复用，不凭界面造新字段或删历史编号|
|9 司机沟通入口|未单独提供沟通页面图，image3/4为详情上下文|A/OrderDetail.kt:294-303仅司机服务卡、mobileMasked及“未配置真实电话中继”；A/OperationsSupport.kt公司客服可确认系统拨号，但**公司客服不是司机**|mini已有M/pages/order-detail/index.wxml:88“模拟联系”，JS:122-131却称“正在通过隐私号连接”，与未接中继不符，优先修正边界文案。可先统一“联系与帮助”入口，展示司机/订单、明确服务未接、提供公司客服/问题反馈；真实聊天、消息记录、司机在线送达、隐私号需后端/第三方，不可伪造接通或拨脱敏号|
|10 个人中心分组变化|image6：多个大类是同款长白卡|A/ProfileScreens.kt:34-151；复用身份卡、MenuRow、各真实路由；出行服务/帮助/演示能力分层，常用入口可用小网格，其余紧凑列表|mini M/pages/profile/index.wxml已有统计/网格/分组，与原图不是同一布局；应保持现有积分卡“演示”边界及区域开票入口，避免为了外观承诺发券/积分到账|
|11 常用地址按钮/密度/默认|image7：全宽添加按钮，两张大卡，地址+坐标占位|A/ProfileScreens.kt:207-276；已有favorites、EmptyCard、favorite.save/remove和地图选点。可先右上角添加、紧凑地址卡、坐标移编辑/详情；真实空态已有，需视觉升级|mini M/pages/places/index.{js,wxml}:1混合历史位置、收藏、选点。后端passenger.py:387-393只存id/label/address/latitude/longitude，无isDefault。不能把第一项或“家”标签当默认；跨设备默认地址/自动填入须先定唯一默认+删除回退契约。无默认数据不插假地址|
|12 缺省/默认卡片与美化|image8乘车人、image9抬头空态、image10安全联系人；需分别处理|A/ExtraScreens.kt:27 Riders、:83 InvoiceTitles，A/ParityScreens.kt:48 SafetyCenter。riders空态/抬头空态已存在，图9“暂无抬头”也可见；安全联系人需补空态。可先紧凑卡/编辑菜单/添加入口。默认本人只表示未选其他rider，不是默认保存卡|mini M/pages/settings/index.wxml:1承载riders/invoices，无独立默认标记；M/lib/safety.js:46-61为本机账号隔离联系人，原生PassengerLocalExtras.kt:20同为本机数据。后端rider.save/invoice.title.save(:59-93)无默认字段；默认乘车人/抬头和默认联系人不是一件事，不默认为首项、不擅自云同步安全联系人。未填数据只空态，不生成样例资料冒充用户记录|
|13 选择回执订单样式|image11：多行文字塞在FilterChip，像代码块|A/ExtraScreens.kt:158-205；用单选订单卡/勾选行展示路线、日期、金额和选中态，复用orderSummary+titleId/orderId；紧凑抬头选择独立分区|mini M/pages/settings/index.{js,wxml}:1已有订单列表+抬头picker，做同语义单选而非复制FilterChip。**保留每单invoiceApplicationEnabled优先、缺字段回退、DONE/paid和已申请排除**，不退回按profile.city一刀切|
|14 回执记录密度|image12：两个大卡占满屏，重复编号入口/说明|A/ExtraScreens.kt:204-219；标题/金额/状态/日期做摘要一行，抬头次行，编号和详细说明折叠。保留复制/历史回执快照，模拟不可报销提示可页级+明确状态而非每条长段重复|mini settings同一页已有简短回执卡，可补金额/时间等有效摘要；不改invoice.apply/金额/税务状态，不把模拟回执说成正式开票|
|15 投诉正文优先|image13两卡中正文实际显示，编号入口最抢眼|A/ProfileScreens.kt:315-330现有reason、orderSummary、content、status、reply、createdAt；OrderDetail.kt:369亦显示正文。应将投诉内容/处理状态/回复为主，订单编号并入次级关联行；保留长文折叠展开和空正文兼容|mini投诉目前在M/pages/order-detail/index.wxml:248-266，没有独立“我的投诉”列表页；可先保证订单内历史正文和回复清晰，若要全局列表可从本人orders.complaints派生，不改存储/伪造服务处理结果|

## 建议第一批（纯UI/已有服务可完成）

优先Android：首页预约横幅、更多设置独立页并保留草稿、订单详情路线优先与摘要、列表顺序/卡片密度、取消理由选择+其他、投诉正文和回复层级。接着做个人中心/地址与资料卡/空态/回执选择及记录密度。mini按实际差异做对应页面，不把已存在的卡片和地图顺序当成同一缺口。司机沟通先清理mini假接通话术，Android新增如实“联系与帮助”入口；真实IM/隐私号另派接口任务。

默认地址/乘车人/抬头如需真实持久化，应与UI阶段分开：backend先给默认标记、唯一性、删除/切账号/跨设备回读契约，再接两端；安全联系人保持现有本机隔离规则，除非另有明确授权。不要把“缺省页”和“默认项”混作一个实现。

## 实施后的验收场景（本轮全部NOT_RUN）

1. 新/多/待出发预约与即时并存，横幅可认且不改变BIZ04；更多设置进出/返回/旋转/切类型不丢备注、代叫人、预约、途经点，不重复下单。
2. 全部/进行中/完成/取消四tab空态与归类正确；待付、待继续叫车和预约不被重排丢失。特殊订单详情保留金额版本确认、预付/欠款、距离同意及取消可用阶段。
3. 固定取消理由与“其他”必填2–200字、返回不取消、双击/断网原请求重试一次；不变更有责或退款规则。
4. 每个资料页0/1/多条、长地址/长姓名/长抬头、无默认字段，删除/编辑不串账号；联系人上限3和本机范围不变。
5. 回执逐单跨城许可true/false、已开历史始终可读、金额与编号复制正确；投诉长正文/空正文/待处理/已回复及订单关联清晰。
6. 联系入口无假号码/假连接；有公司电话仅本人确认拨号，未接服务明确提示。大字体、TalkBack、点击区域、底栏遮挡和滚动实测；不能从截图声称无障碍合规。

## 证据与限制

全部13原图的逐图观察、尺寸、散列及原路径保存在result.json，未以拼图代替原图阅读。原图出现黑底高德地图，与STATE既有1008/10008缺口一致，但本轮未复测因果，不能把布局优化称地图修复。图中真实文本/测试号码不作为新演示数据写回产品。

只读命令：真实frontend门禁PASS；git status/HEAD/tree核对；读取3段DOCX关系和13张原图；定向rg/sed追踪。Product Design preflight仅只读，未找到保存上下文；本轮不用新生成图/画布/网页代替Android。**设备GUI、交互、单测、构建、微信、服务/云端均NOT_RUN**，没有改源码/版本/部署、没有AGY/代理。后续由root给唯一实施任务和非作者验收。

最新范围补充（完成本只读报告后收到）：用户确认需要保存默认地址/乘车人/抬头，司机沟通仅联系页/电话入口，不做文字聊天。转PUIU由Android绑定PUIB固定默认项契约；本报告的无字段判断是已发布8e基线事实，不再作为待用户确认项。
