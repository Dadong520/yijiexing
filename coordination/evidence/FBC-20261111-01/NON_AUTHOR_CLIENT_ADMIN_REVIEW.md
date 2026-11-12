# 非作者独立交叉审查：管理后台及双Android

2026-11-12；backend session `01a182c6-c058-76e1-9584-38abfe813799`。
精确候选 **c46479ff3d03831d23ca281046065294fd4f1b0a**，base **571d939f0f7190e1d602b3b3a61f33ed5b203ffd**。
source `/Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029`，branch `claude/feedback3-20261110`。开始及结束HEAD精确/clean。本角色未编写所审三个客户端/后台目录；本人后端实现不作为本次独立验收对象。对照中央契约及BIZ-10/DEMO-01，逐文件审查三目录全部27个变化文件（后台3、乘客14、司机10）。

**结论：BLOCKING，5项P2需修复；另1项P3显示问题。无源码/构建产物/git状态修改，无提交、部署、云业务、APK下载或其他代理。**

## 阻断问题

### R1 [P2] 固定日期券使用了错误的有效期枚举
- 文件 `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/ParityScreens.kt:66`。
- 领券中心以 `validity == "FIXED"` 判断固定日期，契约和后台保存动作实际使用 `RANGE` / `DAYS`。RANGE模板未提供validDays，当前必走“领取后0天有效”，丢失真实截止时间，误导乘客选择与领取。
- 复现：后台制作任意固定起止时间优惠券，乘客打开领券中心。返回 `{validity:"RANGE",validEndAt:"..."}` 将显示“领取后0天有效”。
- 建议：使用RANGE并展示有效起止时间（至少真实截止时间）；覆盖RANGE/DAYS两类契约测试。

### R2 [P2] 退款后展开费用明细绕过入账状态保护，重新显示正司机收入
- 文件 `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/BusinessSupport.kt:313`，对照`:267`及`DriverDomain.kt:134`。
- 页面上方仍用 `order.incomeForDisplay(estimatedIncome)`，正确将REFUNDED/REVERSED/CLOSED_NO_INCOME归零。但新增“司机分账金额”明细直接显示 `fee.income`（或driverIncome），替换了原本使用displayedIncome的行。费用接口的income是原分账公式金额，退款后仍可能正数。因此同页可出现“已退款冲回/0.00”与绿色“司机分账金额9.42”。取消未入账订单同样可能显示原预估正收入。
- 复现：完成并模拟支付一单，后台全退，司机刷新该单并展开费用明细。例费用income=942分、order settlementStatus=REVERSED/netIncome=0，上方0.00，下方9.42。
- 建议：最终入账行继续采用displayedIncome+incomeLabel；如果保留原计算值，须单独明确标为“原分账金额（已冲回）”，不冒充当前入账。覆盖退款、取消未入账、已入账三种状态。

### R3 [P2] 独立执行冲红后，后台无法再重开该回执
- 文件 `apps/admin-console/web/admin.js:1210`（同一canHandle控制“模拟冲红”和“冲红并重开”）。
- canHandle限定invoiceStatus为SIMULATED_ISSUED。用户先点“模拟冲红”后，记录变RED_FLUSHED，再打开详情所有处理按钮都消失；未reissuedAs的已冲红原单没有入口调用后台已支持的ops.invoice.reissue。
- 冻结源码Node VM实跑：对 `{role:"PASSENGER",invoiceStatus:"RED_FLUSHED",reissuedAs:undefined}` 渲染并点击详情，按钮仅 `["关闭","查看关联订单"]`。
- 建议：分开canRedFlush和canReissue；未重开的RED_FLUSHED仍可重开，已reissuedAs的不能重复；文案区分直接重开与自动先冲红。补“先冲红→后重开”链路测试。

### R4 [P2] 司机端空车费展示未实现
- 重点文件 `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/BusinessSupport.kt:295`及`DriverDomain.kt:75`、ConnectedStore订单解析。
- 最新派单要求司机端“空车费展示”。冻结候选整个司机src/main/java中 `cancelFee` 和“空车费”均无匹配；DriverOrder无对应字段，费用页也未读取订单原始cancelFee。因此有责取消产生UNPAID/PAID空车费时，司机无法核对金额或结清状态。收到空车费的钱包流水不能替代订单详情展示；该单仍可能显示“未计入收入”，无法解释钱包补偿。
- 复现：区域配置空车费300分，司机到达后乘客取消；司机查看取消订单与费用。无3.00元空车费/待付状态；乘客结清后仍无对应订单补偿明细。
- 建议：从权威共享订单解析cancelFee，显示金额、UNPAID/PAID和模拟补偿状态；勿将空车费当普通行程分账或重复计入。覆盖未付与预付扣款/已结清。

### R5 [P2] 公司退款冲回流水被标成订单佣金入账
- 文件 `apps/admin-console/web/admin.js:1436`。
- 流水类型只分WITHDRAWAL和其他；其他全部绿色“订单佣金分账”。实际公司退款会产生 `{type:"REFUND_REVERSAL",amount:-248}`。后台把扣减/冲回误标为收入类型，财务无法按类型核对退款。
- 冻结源码Node VM实跑：仅输入REFUND_REVERSAL测试流水，companySplitLedgerAdmin输出包含绿色“订单佣金分账”。
- 建议：显式映射ORDER_SPLIT、WITHDRAWAL、REFUND_REVERSAL；未知类型保留原类型/待核对，不能默认为收入。

## 非阻断改进

### R6 [P3] 开放服务分区间显示NaN
- 文件 `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/BusinessCommunity.kt:271`。
- 约定卓越max=null、风险min=null，当前无条件optDouble生成“110.0–NaN分”及“NaN–80.0分”。以项目同版本org.json 20240303在JShell17实际验证 `new JSONObject("{\"min\":110,\"max\":null}").optDouble("max")` 输出NaN。
- 建议显示“≥110分”“<80分”，有限区间明确包含下界/不含上界；无效数据不要渲染NaN。

## 已核验的通过点

- 两端版本26/1.1.24-experience和28/0.3.25-cloud符合约定；原包名、experience签名与非debug配置未在本差量改动（实际APK签名/升级仍需root验）。
- 两端邀请码传inviteCode，trim+Locale.ROOT大写，沿用会话generation/request epoch，不在客户端发奖励。乘客/司机拉新使用返回的rule和脱敏记录，100人上限及模拟积分说明保留。
- BookingPricing按sum-of-legs×1.3、分段进位、每单位BigDecimal HALF_UP、夜间05:00分钟包含、回空与夜间加成相加、城市tariff/serviceFee处理；优惠仅抵tripFare。调用couponId/领券templateId字段正确，费用最终由服务器决定。现有新测试7个方法覆盖主要计价边界，未在本次重复Gradle执行。
- 乘客欠空车费在首页/订单页可发现，跳详情后确认order.payCancelFee携带expectedAmount，提交前核对当前账号/订单/状态/金额；没有自动支付。重开申请带invoiceId/reason/type/title/taxNumber/email并核对当前归属和回执状态。真实税务/扣款未接入提示保留。
- 管理后台新增页面注册/nav/render均有对应入口；orderSettlements请求dataset名正确；queryListing仍走后端筛选/分页/CSV及permissions；各运营写动作与字段/expectedVersion对照契约吻合。
- 公司税号与地图费/平台比例按整数分/整数百分比提交；开户换绑/query/withdraw的companyId、expectedVersion、accountName/bankName/bankAccount等字段正确，DOM输出转义、脱敏状态和模拟银行声明保留。
- 资金池充值reference、5000/500阈值、冻结/核销/差额、模板RANGE/DAYS、新人模板组合字段一致；实时券面及最高成本乘法处于JS安全整数范围，保存由cents转换。
- 服务分24h覆盖及5级连续区间验证器、在线加扣并存表单字段、评分半分档与投诉周期正确对接；后台AMOUNT+TIERED明确“按当月完单序号选每实付整元倍率”，与已锁口径一致。
- 开票审核/平台开票方/公司月技术费字段及expectedAmount、税额预览，均区分模拟发票。客服、地图、积分、提现未发现新增虚构真实外部到账或税务接通的承诺。

## 实际验证与范围

- `node --test apps/admin-console/web/operations.test.js`：**8/8 PASS**，退出0，约61ms；覆盖时间、公告、券面成本、24h、五级与导航注册。这些测试不覆盖R1–R6，不能替代上述场景。
- 从 `git show c46479ff:apps/admin-console/web/admin.js` 提取真实函数，在Node VM内以合成DOM/数据执行R3/R5，两项实际输出如上；无网络/后端业务调用，非真实浏览器验收。
- 项目缓存org.json依赖在JShell17确认null→NaN；未启动Android设备。
- `git diff --check base candidate -- <三目录>`：PASS。开始与结束git status clean，HEAD仍精确候选，未改变源码或git状态。
- 中央check_coordination接单/结束PASS；context UNKNOWN，不是业务验收。
- Android编译/完整JUnit、真实浏览器布局和交互、模拟器原生流程、APK签名升级、CI/合并/发布全部 **NOT_RUN_BY_THIS_REVIEW**；由coordinator安排。未重跑本人编写的后端全套或假装独立验收本人代码。

## 审查覆盖文件

- `apps/admin-console/web/admin.css`
- `apps/admin-console/web/admin.js`
- `apps/admin-console/web/operations.test.js`
- `apps/passenger-android/app/build.gradle.kts`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/BookingScreens.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/ExtraScreens.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/Feedback3Rules.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/Feedback3Screens.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/MainActivity.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/OperationsSupport.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/OrderDetail.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/ParityScreens.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/PassengerConfirmPrompts.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/PassengerStore.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/PointsMall.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/ProfileScreens.kt`
- `apps/passenger-android/app/src/test/java/cn/hbzjhs/passenger/Feedback3PricingTest.kt`
- `candidates/driver-0.3.6-r2/android/app/build.gradle.kts`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/BusinessCommunity.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/BusinessScreens.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/BusinessSupport.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/ConnectedStore.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/CoreScreens.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/DriverDomain.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/Feedback3Presentation.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/NewOrderAlerts.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/UiKit.kt`

下一步：交还各文件作者修复R1–R5并自测，coordinator冻结新SHA后安排差量复核；R6可随同修复。当前候选不可据此标记独立验收PASS。
