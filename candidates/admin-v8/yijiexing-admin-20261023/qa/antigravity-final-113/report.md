# 宜捷行乘客端 1.1.3 修复独立复测报告

- **报告角色**：执行成员 Antigravity（独立测试方，非总管，不替代 Codex 最终验收）
- **报告时间**：2026-10-23
- **复测范围**：仅限生产代码走查与本地离线单元测试强制重跑；严禁且未执行任何模拟器操作、adb 命令、云端接口调用或私有凭证访问。
- **输出落盘目录**：[`qa/antigravity-final-113/`](file:///Users/dongge/Documents/New%20project%202/yijiexing-passenger-20261023/qa/antigravity-final-113/)

---

## 一、 生产源码修复逻辑走查与判定

依据任务要求，实际阅读并核对了生产 Android 源码及测试实现，重点研判两处关键修复：

### 1. 车费展示逻辑修复：[`OrderFarePresentation.kt`](file:///Users/dongge/Documents/New%20project%202/yijiexing-passenger-20261023/android/app/src/main/java/cn/hbzjhs/passenger/OrderFarePresentation.kt)
- **修复背景**：在旧版竞价订单中，选定司机前后服务端返回的 `fareSource` 均保留为 `DEMO_ESTIMATE`，容易导致 UI 混淆“参考体验估价”与“真实选定司机报价”。
- **修复实现**：
  - 针对 `BID`（竞价）单：通过判断 `order.text("driverId").isBlank()` 进行严格语义分流。
    - **尚未选定司机**：展示 `label = "体验估价（参考）"`, `detailLabel = "参考车费（体验估价）"`，并显式提示：“尚未选定司机。各司机报价见报价列表；当前金额仅为体验估价，选定后才按该司机报价模拟预付。”
    - **已选定司机**：统一展示 `label = "选定司机报价"`，明晰计价来源。
  - 针对 `NEGOTIATED`（线下议价）单：展示 `label = "司机报价"`。
  - 针对普通即时/预约单：展示 `label = "体验估价"`。
- **走查判定**：修复逻辑严密，彻底消除了竞价与议价在不同生命周期节点的车费文案歧义，并具备向下兼容性。

### 2. P1 幂等键生命周期修复：[`PendingRequestJournal.kt`](file:///Users/dongge/Documents/New%20project%202/yijiexing-passenger-20261023/android/app/src/main/java/cn/hbzjhs/passenger/PendingRequestJournal.kt)
- **修复背景**：此前反重力静态初稿发现的 P1 问题——网络层 4xx/5xx/超时异常误清待决键，导致重试生成新 UUID 丢失幂等防护。
- **修复实现**：
  - **不确定结果与鉴权失效保护**：故意移除任何在网络异常时删除待决映射的逻辑。当遭遇 503 超时、401 会话过期或 403 访问码拒绝时，待决记录坚决不清除；仅当传输层收到明确成功确认时，才从持久层移除。
  - **稳定账号身份强隔离**：前置拦截未同步身份的请求（`require(accountId.isNotBlank())`），签名强制采用稳定 `accountId` 因子（`SHA-256("$account|$action|$payload")`），防止临时 token 轮换导致签名变化，并实现同机多乘客的强隔离。
  - **写盘失败前置守卫**：在向网络发送请求前先落盘，若遭遇存储异常直接抛出 `PendingStorageException`，阻断后续网络发送。
- **走查判定**：修复彻底切断了“因传输故障误删幂等键”的隐患，边界防护完备。

---

## 二、 独立离线单元测试强制重新执行结果

### 1. 执行命令与环境
- **命令**：
  ```bash
  cd "/Users/dongge/Documents/New project 2/yijiexing-passenger-20261023/android"
  JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
  ./gradlew testDebugUnitTest --offline --rerun-tasks --console=plain
  ```
- **执行属性**：离线模式（`--offline`）、强制全部任务重新执行（`--rerun-tasks`）、纯文本控制台输出（`--console=plain`）。
- **退出码**：`EXIT_CODE=0`（构建成功耗时 16s，25 个可执行任务全部强制重新执行）。
- **原始完整日志**：已落盘保存至 [`qa/antigravity-final-113/raw-test-execution.log`](file:///Users/dongge/Documents/New%20project%202/yijiexing-passenger-20261023/qa/antigravity-final-113/raw-test-execution.log)。

### 2. 实际通过用例明细清单（4 套测试套件，共 14 项，全部 PASS）

本次执行生成的 JUnit XML 已完整复制至 `qa/antigravity-final-113/` 目录下，逐套件逐项明细如下：

#### 套件 1：`cn.hbzjhs.passenger.OrderFarePresentationTest`（2 项通过 / 0 失败）
- `TEST-cn.hbzjhs.passenger.OrderFarePresentationTest.xml`
  1. `[PASS]` `unselectedBidKeepsEstimateDistinctFromActualDriverQuotes` (0.002s) —— 验证未选司机的竞价单保持参考估价语义且附带提示
  2. `[PASS]` `assignedBidUsesSelectedQuoteEvenWhenLegacyFareSourceRemainsEstimate` (0.001s) —— 验证选定司机后生命周期各阶段均展示选定司机报价

#### 套件 2：`cn.hbzjhs.passenger.PendingRequestJournalTest`（4 项通过 / 0 失败）
- `TEST-cn.hbzjhs.passenger.PendingRequestJournalTest.xml`
  1. `[PASS]` `stableAccountIdentitySeparatesDifferentPassengersAndDifferentActions` (0.028s) —— 验证稳定账号隔离不同乘客与不同动作，重登后复用旧键
  2. `[PASS]` `uncertainCommitThen401And403SurviveStorageRebuildAndAccountRelogin` (0.005s) —— 验证 503/401/403 异常下重构依然保留待决记录并在重登后复用
  3. `[PASS]` `currentTokenLegacyRecordMigratesWithoutInventingAKeyForAnUnavailableOldToken` (0.002s) —— 验证旧版 token 格式记录向新账号签名的平滑迁移
  4. `[PASS]` `storageFailuresBlockTransportAndPreserveAnAcknowledgedButUnclearedRequest` (0.003s) —— 验证写盘失败阻断网络发送，清除失败保留待决记录

#### 套件 3：`cn.hbzjhs.passenger.NegotiationTest`（4 项通过 / 0 失败）
- `TEST-cn.hbzjhs.passenger.NegotiationTest.xml`
  1. `[PASS]` `everySimulationOutcomeCarriesTheConfirmedQuote` (0.018s) —— 验证线下议价模拟结果携带确认报价
  2. `[PASS]` `disputedOrStartedOrderCannotBeConfirmedFromOldDialog` (0.000s) —— 验证价格异议或已启动订单阻断旧弹窗确认
  3. `[PASS]` `confirmationLocksBothAmountAndVersion` (0.001s) —— 验证确认动作同时锁定金额与版本号
  4. `[PASS]` `acceptsOnlyExactInvitationCodeOrOwnDeepLink` (0.006s) —— 验证严格校验配对码格式与合法深链接

#### 套件 4：`cn.hbzjhs.passenger.PassengerRulesTest`（4 项通过 / 0 失败）
- `TEST-cn.hbzjhs.passenger.PassengerRulesTest.xml`
  1. `[PASS]` `moneyUsesExactIntegerCents` (0.000s) —— 验证金额计算严格遵循整数分
  2. `[PASS]` `onlyPassengerIdentitiesAreAccepted` (0.000s) —— 验证仅接受白名单乘客身份
  3. `[PASS]` `importantTerminalAndPaymentStatesAreExplicit` (0.001s) —— 验证终端与支付状态显式映射
  4. `[PASS]` `coordinatesRejectNonFiniteAndOutOfRange` (0.004s) —— 验证拒绝非法或越界经纬度坐标

---

## 三、 测试界线与真实未测范围严格声明

为维护测试独立性与事实准确性，特此声明以下执行界线，绝不混淆或冒充测试结果：

1. **执行主体与成果归属明确**：
   - 本报告中 **14 项单元测试（4 套套件）为 Antigravity 亲自在本地离线重新编译执行并通过**。
   - 现存的双模拟器 UI 截图、录屏或联测记录，属于 **Android agent / Codex 主流程的测试产出**，**绝不作为 Antigravity 的独立 UI 执行结果**。

2. **独立 UI 真实执行状态与未完成声明**：
   - 此前 Antigravity 编写的无终点 UI 自动化脚本在执行时，因按钮匹配中断（首轮）及 CheckBox 联动未到位误发带终点订单 `PORDcecfc8298776` 后被硬断言拦截；
   - 原 UI 失败脚本与原始失败证据（XML/截屏）依然如实保留在 `qa/antigravity-ui/` 目录中，未作篡改；
   - **Antigravity 明确声明：本角色未独立完成双模拟器 UI 自动化全流程实测，不能宣布独立双模拟器全流程通过。**

3. **两端设备状态与权限**：
   - 本轮未执行任何 adb 指令，未连接模拟器；设备控制权全权由主流程掌控，本角色不对当前设备活动订单状态做越界推断。

---

## 四、 独立复测结论

- **生产源码走查**：车费展示语义分流（`OrderFarePresentation.kt`）与 P1 幂等键生命周期防护（`PendingRequestJournal.kt`）修复逻辑**合理、完备**。
- **本地单元测试**：离线强制重新运行 **4 套套件共 14 项测试全部独立通过 (14/14 PASS)**。
- **系统验收定位**：单元测试通过仅证明代码层逻辑约束成立，双端 UI 真实全流程验收仍以 Codex 主流程双设备实测结果为准。
