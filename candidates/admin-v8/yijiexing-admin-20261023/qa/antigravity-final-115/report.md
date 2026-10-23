# 宜捷行乘客端 1.1.5 UI 优化候选独立复测报告

- **报告角色**：执行成员 Antigravity（独立测试方，非总管，不替代 Codex 最终验收）
- **报告时间**：2026-10-24
- **测试环境与限制**：
  - 候选版本：`1.1.5-experience`（`versionCode=7`，APK SHA256: `4f9f2b40d435a905690a4675efaa52c721783e6eb69017eb14e704405bb217fd`）
  - 执行约束：严格禁止 adb、模拟器与云端接口调用；未读取私有凭证；未修改生产源码与测试。
- **输出落盘目录**：[`qa/antigravity-final-115/`](file:///Users/dongge/Documents/New%20project%202/yijiexing-passenger-20261023/qa/antigravity-final-115/)

---

## 一、 实际独立执行结果（离线单元测试强制重跑）

### 1. 执行命令与环境
- **命令**：
  ```bash
  cd "/Users/dongge/Documents/New project 2/yijiexing-passenger-20261023/android"
  JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
  ./gradlew testDebugUnitTest --offline --rerun-tasks --console=plain
  ```
- **执行属性**：离线模式（`--offline`）、强制所有任务重新执行（`--rerun-tasks`）、标准控制台纯文本输出（`--console=plain`）。
- **执行耗时与退出码**：`BUILD SUCCESSFUL in 9m 42s`，`25 actionable tasks: 25 executed`，`EXIT_CODE=0`。
- **控制台原始日志**：完整 stdout/stderr 已保存至 [`qa/antigravity-final-115/raw-test-execution.log`](file:///Users/dongge/Documents/New%20project%202/yijiexing-passenger-20261023/qa/antigravity-final-115/raw-test-execution.log)。

### 2. 真实 JUnit XML 统计与明细（5 套测试套件，共 16 项全部 PASS）

根据本次实际运行生成的 XML 统计，测试结果为 **16 tests, 0 failures, 0 errors, 0 skipped**。本次生成的 5 份 XML 文件已全部复制归档到 `qa/antigravity-final-115/` 目录：

| 测试套件 (Suite) | 测试用例数 | 实际通过数 | 失败 (Failures) | 错误 (Errors) | 耗时 | 复制归档 XML 文件 |
|---|---|---|---|---|---|---|
| `cn.hbzjhs.passenger.NegotiationTest` | 4 | 4 | 0 | 0 | 0.046s | `TEST-cn.hbzjhs.passenger.NegotiationTest.xml` |
| `cn.hbzjhs.passenger.OrderFarePresentationTest` | 2 | 2 | 0 | 0 | 0.004s | `TEST-cn.hbzjhs.passenger.OrderFarePresentationTest.xml` |
| `cn.hbzjhs.passenger.OrderIdentityTest` | 2 | 2 | 0 | 0 | 0.020s | `TEST-cn.hbzjhs.passenger.OrderIdentityTest.xml` |
| `cn.hbzjhs.passenger.PassengerRulesTest` | 4 | 4 | 0 | 0 | 0.003s | `TEST-cn.hbzjhs.passenger.PassengerRulesTest.xml` |
| `cn.hbzjhs.passenger.PendingRequestJournalTest` | 4 | 4 | 0 | 0 | 0.125s | `TEST-cn.hbzjhs.passenger.PendingRequestJournalTest.xml` |
| **总计** | **16** | **16** | **0** | **0** | **0.198s** | **5 套全部归档** |

#### 各测试用例具体执行明细：
1. **`NegotiationTest`** (4 项):
   - `[PASS]` `everySimulationOutcomeCarriesTheConfirmedQuote` (0.026s)
   - `[PASS]` `disputedOrStartedOrderCannotBeConfirmedFromOldDialog` (0.000s)
   - `[PASS]` `confirmationLocksBothAmountAndVersion` (0.001s)
   - `[PASS]` `acceptsOnlyExactInvitationCodeOrOwnDeepLink` (0.019s)
2. **`OrderFarePresentationTest`** (2 项):
   - `[PASS]` `unselectedBidKeepsEstimateDistinctFromActualDriverQuotes` (0.003s)
   - `[PASS]` `assignedBidUsesSelectedQuoteEvenWhenLegacyFareSourceRemainsEstimate` (0.001s)
3. **`OrderIdentityTest`** (2 项):
   - `[PASS]` `summaryShowsChineseKindAndPlacedTimeInsteadOfInternalIdentifier` (0.019s)
   - `[PASS]` `invalidOrMissingTimeNeverLeaksRawMetadataIntoTitle` (0.001s)
4. **`PassengerRulesTest`** (4 项):
   - `[PASS]` `moneyUsesExactIntegerCents` (0.000s)
   - `[PASS]` `onlyPassengerIdentitiesAreAccepted` (0.001s)
   - `[PASS]` `importantTerminalAndPaymentStatesAreExplicit` (0.000s)
   - `[PASS]` `coordinatesRejectNonFiniteAndOutOfRange` (0.002s)
5. **`PendingRequestJournalTest`** (4 项):
   - `[PASS]` `stableAccountIdentitySeparatesDifferentPassengersAndDifferentActions` (0.110s)
   - `[PASS]` `uncertainCommitThen401And403SurviveStorageRebuildAndAccountRelogin` (0.005s)
   - `[PASS]` `currentTokenLegacyRecordMigratesWithoutInventingAKeyForAnUnavailableOldToken` (0.005s)
   - `[PASS]` `storageFailuresBlockTransportAndPreserveAnAcknowledgedButUnclearedRequest` (0.004s)

---

## 二、 生产代码走查与只读审阅结论

### 1. 核心业务与幂等机制未受改版影响
- 经 [`qa/ui-redesign-candidate/protected-source-check.json`](file:///Users/dongge/Documents/New%20project%202/yijiexing-passenger-20261023/qa/ui-redesign-candidate/protected-source-check.json) 与逐文件比对：
  - `PendingRequestJournal.kt`：继续保持基于稳定 `accountId` 的 SHA-256 签名，未向 payload 注入动态非业务字段，持久化前置守卫与 4xx/5xx/超时不删待决键机制原样保持；
  - `Negotiation.kt`：报价版本（`quoteVersion`）与金额（`amount`）双重冻结与快照校验保持不变；
  - `PassengerStore.kt`、`OrderFarePresentation.kt`、`OrderIdentity.kt`、`ExtraScreens.kt` 核心逻辑未发生退化。

### 2. UI 改版重点审查
- **订单创建入口（`BookingScreens.kt`）**：
  - `order.create` payload 字段构造（`kind`, `origin`, `destination`, 四坐标, `city`, `remark`, 条件追加 `scheduledAt`/`destinationPending`/`riderId`/`waypoints`）与业务合约严格一致；
  - **无终点交互优化**：原 CheckBox 与 TextView 独立分离的问题已修复，现采用整行 `Row.toggleable(role = Role.Checkbox, onValueChange = { destinationPending = it })`，内部子 Checkbox 的 `onCheckedChange = null`，点击整行或图标均能正确联动；
  - **文案提示优化**：无终点模式下提示更新为“补充目的地后生成体验估价，费用待定”，消除了“免费/0元锁价”歧义。
- **订单详情与主要动作门禁（`OrderDetail.kt`）**：
  - 取消动作：保留 7 种允许取消阶段判断（`cancellable`），TRIP 行程中不开放普通取消；
  - 模拟支付：弹窗中保留冻结快照核对（`quotationMatches`）与金额展示；
  - 价格异议、评价、投诉改用统一样式 `PassengerModal`，输入字数、状态校验与前置拦截逻辑保持完备。

### 3. 他人截图与记录只读审阅声明
- 审阅了 `qa/ui-redesign-candidate/` 下由其他 agent 捕获的截图（`01-home-default.png` 至 `07-home-font150-final.png`）及 XML：
  - **首屏与键盘可达性**：默认字号下起终点卡片与底部呼叫按钮在首屏完整呈现；地址选择弹窗打开软键盘时，顶部取消/确认操作区固定可见且候选列表可单独滚动；
  - **字体放大表现**：在 1.3 倍与 1.5 倍大字号下，固定呼叫按钮与底栏文字未见截断；
  - **【严格界线】**：以上截图为**只读审阅**他人成果，**绝不冒充为 Antigravity 亲自操作 UI 的执行结果**。

---

## 三、 历史 UI 失败记录保留与未测范围声明

1. **保留此前无终点 UI 自动化失败记录**：
   - 在此前独立 UI 尝试中，由于测试脚本定位点击了不可点击的文本节点而非同行 CheckBox，导致无终点状态未勾选并误建了带终点订单 `PORDcecfc8298776`；相关失败截图与 XML 仍如实保存在 `qa/antigravity-ui/` 目录下；
   - **Antigravity 明确声明：本角色未独立完成双模拟器 UI 自动化端到端实测，不能宣布双模拟器 UI 全流程独立通过**。
2. **本次未测范围**：
   - 未在设备上运行 TalkBack 读屏无障碍焦点遍历；
   - 未发起真实网络请求连接外部云端。

---

## 四、 独立复测综合判定

1. **本地单元测试**：强制离线重跑 **5 套测试套件共 16 项全部独立通过 (16/16 PASS)**，退出码为 0。
2. **源码业务守卫**：1.1.5 UI 改版未破坏 `order.create`、取消、支付、异议、幂等及金额冻结逻辑；无终点整行交互与文案得到针对性优化。
3. **交付定位**：本地逻辑与单元测试独立复测通过；双模拟器最终端到端全流程验收继续由 Codex 主流程判定。
