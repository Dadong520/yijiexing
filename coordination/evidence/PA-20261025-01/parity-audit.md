# PA-20261025-01：乘客 Android 与最新小程序只读差异核对

2026-10-25（UTC+8）；backend；ACK 规则 v2。仅契约/源码核对，未实现、未构建、未启动设备、未访问云、未部署、未派代理。当前任务 JSON 尚未出现，按 coordinator 本轮直接限定任务执行；只写本文件，不更新 STATE/BOARD/产品或角色卡。

基线：Android=`yijiexing-passenger-20261023/android`，app/build.gradle.kts:11–12 确认为 **1.1.6-experience / code8**，不能以旧 1.1.5 仓库快照覆盖。小程序=`.coordination/integration-ag-final/candidates/passenger-miniprogram-0.1.0/miniprogram`，该 Git 工作目录读取时 HEAD=`ea8273957c02fe2bcd77b4ffd63260ef791fae94`。下方 A 指 Android 的 `app/src/main/java/cn/hbzjhs/passenger/`；M 指小程序根。

## 短矩阵（源码覆盖，不等于本轮实际验收）

| 功能 | 原生 1.1.6 当前覆盖 | 对齐边界/差异 |
| --- | --- | --- |
| 同一后端、身份和订单同步 | 已有乘客 code/login/state/action/logout；PassengerStore.kt:167–171、295–337；MainActivity.kt:152–159 前台 5 秒刷新 | 继续同一独立体验后端/账户/订单 ID；短信验证码仍模拟。不得新增本地“业务订单库”替代共享订单。 |
| 即时/预约/竞价、代叫、无终点、两途经点 | BookingScreens.kt:38、65–106 已接 order.create，含 riderId/waypoints/scheduledAt/destinationPending | 核心已具备；日期当前使用设备 ZoneId.systemDefault，跨时区预约需回归，不能只做 UI 替换。 |
| 报价、选司机、议价认领/异议 | OrderDetail.kt:156–171、405–406；Negotiation.kt:55–65、115 | 已接真实共享订单接口。议价支持输入配对码/链接入口；不要把微信扫码 API 原样移植 Android。 |
| 接驾/行程、WAIT_PAY、欠费门禁 | BookingScreens.kt:61 把所有非 DONE/CANCELLED 单视为当前单，包含 WAIT_PAY；OrderDetail.kt:83、206–216 | 已覆盖乘客待付与状态同步；后端仍为权威，保留司机待付可接新单、乘客未付不能新叫车规则。 |
| 司机取消后同单继续/取消 | OrderDetail.kt:90–96 已有 order.continue type1/2 | 原生已有；新 UI 必须保留 WAIT_CONTINUE 双操作，不能随小程序旧回归丢失。 |
| 费用/预付/模拟支付退款 | OrderDetail.kt:174–216、435–460；PaymentConfirmation.kt:16–43 保留 fareVersion/expectedFare；Negotiation.kt 保留 quoteVersion/amount | 费用查询与退款金额展示已接后端；真实支付渠道未接。新界面继续冻结金额/版本，防 ABA，禁止 PREPAY 成功即发完单奖励。 |
| 评价、投诉、图片与处理回复 | OrderDetail.kt:292–332、370、405；PassengerStore.kt:363；ExtraScreens.kt:258 | 已有 DONE 评价、结束订单投诉、最多3图、读取附件与后台回复。必须保留失败提示/附件鉴权，不能只做空表单。 |
| 常用/历史地址、乘车人、发票抬头/模拟回执 | ProfileScreens.kt:62–77、137–210；ExtraScreens.kt:27–247 | 已调用 favorite/rider/invoice 等持久化接口；发票是模拟回执，非正式发票。 |
| 反馈、屏蔽司机、公告客服说明 | ProfileScreens.kt:71–83、217–286；OrderDetail.kt:353；ExtraScreens.kt:247 | 已有服务端资料/屏蔽/反馈；人工客服、推送、电话中继未接，不应新增已服务承诺。 |
| 券包/领取/积分/拉新/调度加价 | 原生未发现相关入口或调用（全 Kotlin 搜索 coupon/积分/邀请码 为0） | 最新 M 是本地演示/待服务端接入；home/index.js:172 已停止向真实订单发送本地优惠与加价。Android 可补明确演示入口，不能宣称真实资产、抵扣或到账；真正营销按 BE 契约另行实现。 |
| 安全联系人、安全中心、求助 | 原生无安全联系人/安全中心/110/ACTION_DIAL 入口；仅 INTERNET 权限 | M/lib/safety.js 为按 owner 的本机存储，不跨设备云同步；报警只是调起系统电话，短信只是本地展示，录音/警方联动未接。Android 如补联系人需按 passengerId 隔离；由用户主动拨号，不把 rider 当安全联系人。 |
| 司机服务卡 | OrderDetail.kt:248–257 已展示后端姓名/车牌/车型/掩码电话/上报位置；没有新卡片弹层 | 可复用这些真实投影字段补卡片；M 评级4.9、860单、白色为硬编码，不能照搬作事实。企业/服务分无值显示未提供，不编造审查通过或真实联系能力。 |
| 地图选点/自动定位 | 原生 BookingScreens.kt:166、372：体验地点/手动 WGS84；详情:241 示意连线；Manifest 无定位权限 | 小程序已有 wx.chooseLocation/可选腾讯插件及 GCJ02↔WGS84 适配，但无 AppID/真机验证。Android 缺地图搜索选点与设备定位；需独立选择 Android 地图接入/Key/权限方案，不能把手填坐标包装成自动定位，也不能把 GCJ02 直接送现有 WGS84 后端。 |

## 关键风险，供 Gemini 实现与 frontend 验收

1. **M 个人页订阅被覆盖（已按源码确认）**：M/pages/profile/index.js:19 自定义 onShow 只调用 refreshPoints；M/pages/shared.js 的 base 通过 Object.assign(defaults,extra)，因此默认订阅 store/初始化 authenticated/state/轮询的 onShow 被替换。并非订阅后追加钩子，当前页可能始终使用空 state、匿名积分或旧 UI，登录/退出后未同步。应组合基础生命周期并保留 onHide/unload 清理；Android 不必复制此问题，原生现有 StateFlow + 生命周期刷新应保留。
2. **owner 和演示初值仍不一致**：M/profile:10–13 仍有380分/邀请3人初值；refreshPoints:25 用手机号或 anonymous，而 M/shared.getAccountOwner、coupons/safety 页按 passenger.id。M/lib/points.js 的新默认已为0且标演示，但 anonymous 可读遗留无后缀存储。需统一 passengerId、禁读遗留通用资产并在切号时刷新；原生新增功能不要复制手机号尾号作为权威账户 ID。
3. **邀请剪贴板仍假承诺（确定文本）**：M/profile:83 复制文案为“注册即送 ¥20…完成首单双方均享豪礼”；与未接营销服务不符，页内演示标识不能纠正外发文案。邀请码由手机号尾号拼接也非服务端唯一邀请码。Android 分享/复制应明确体验演示、无实际奖励；真实规则与绑定待后端批准。
4. **司机卡残留装饰数据**：M/order-detail/index.wxml:69–72 固定4.9/860单/白色；337 已有体验资质说明。原生应以真实字段/未知态替代，不能由卡片外观推出实际资质已核验。
5. **安全求助范围**：M/safety/index.js:157 使用固定鄂A·88888/“当前行程位置”生成示例短信，159 仅弹窗；订单详情 SOS 使用当前订单资料但也只是模拟。若 Android 在真实订单里提供求助，要取该订单真实投影，说明复制/拨号/发送各自实际动作，不声称短信已送达、全程录音或警方获定位。
6. **保护 1.1.6 已有防护**：PendingRequestJournal 的原 requestId、账号隔离、PassengerStore sessionGeneration/stateEpoch、费用版本确认、WAIT_CONTINUE、投诉图片不能在整屏重写时退化；升级需同 applicationId/签名并保留账户状态。尚未完成的云端旧单晚付不影响新单回归，不得写为通过。

本轮方法：只读 rg、限定源代码区段与版本/HEAD 核对；未运行单测、构建、微信编译或设备测试，全部运行验收 **NOT_RUN**。未确认原始营销需求表和外部服务正式资格。建议先保住核心同单流程，再补“明确演示”的券积分/安全/卡片入口；真实营销和地图分别有后端/外部配置门禁。待合并 SHA：N/A（只提供本审计，由 coordinator 归档）。完成后 idle。
