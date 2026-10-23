# 宜捷行乘客端 P1 幂等修复源码走查与独立单元测试复核报告

- **报告角色**：执行成员 Antigravity（独立测试方，非总管，不替代 Codex 最终验收）
- **复测范围**：仅本地源码、日志与离线单元测试；未执行 adb、未操作模拟器、未调用云端 API。
- **复测对象**：
  - 源码：[`PendingRequestJournal.kt`](file:///Users/dongge/Documents/New%20project%202/yijiexing-passenger-20261023/android/app/src/main/java/cn/hbzjhs/passenger/PendingRequestJournal.kt)
  - 单元测试：[`PendingRequestJournalTest.kt`](file:///Users/dongge/Documents/New%20project%202/yijiexing-passenger-20261023/android/app/src/test/java/cn/hbzjhs/passenger/PendingRequestJournalTest.kt)
  - 测试命令：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew testDebugUnitTest --offline`
  - 原始日志保存路径：[`qa/antigravity-ui/p1-unit-retest.log`](file:///Users/dongge/Documents/New%20project%202/yijiexing-passenger-20261023/qa/antigravity-ui/p1-unit-retest.log)

---

## 一、 静态逻辑与边界场景核查

针对此前反重力静态初稿指出的“4xx 异常清未决键破坏幂等”问题，重新走查新实现的 `PendingRequestJournal.kt`，重点核查三项关键边界：

### 1. 503 未知结果 → 401/403 鉴权失败 → 持久化恢复与同账号重登复用同一幂等键
- **代码实现**：
  - `PendingRequestJournal.kt` 第 33-35 行明确设计：故意不在网络调用外包裹任何清除待决键的 `catch`/`finally`；
  - 无论是 503 超时未知结果，还是随后的 401 会话失效、403 访问码拒绝，`entries` 中的映射记录始终得到保留；
  - 记录只有在网络传输层**显式返回 2xx/确认成功响应后**（第 36-39 行），才会从持久层安全移除；
  - 构造重置（进程重启）时，`readEntries` 重新从持久盘反序列化读回待决映射，确保崩溃或重登后依然复用既有 `requestId`。
- **单测核验**：
  - `uncertainCommitThen401And403SurviveStorageRebuildAndAccountRelogin` 模拟连续经历 503 -> 401 -> 403 后重建 Journal 实例，同账号重新发起请求依然使用首次生成的同唯一 `requestId`，并在成功确认后才被清盘（通过）。

### 2. 异账号身份强隔离（按稳定 passenger.id 签名）
- **代码实现**：
  - `PendingRequestJournal.kt` 第 30 行通过 `require(accountId.isNotBlank())` 严格前置断言，避免未拉取到用户资料时使用临时 token 导致签名失真；
  - 签名算法采用 `SHA-256("$account|$action|$payload")`，账号标识为最高权重因子；不同乘客即使在同一设备先后登录并执行相同 payload 的请求，其签名哈希也天然完全不同；
- **单测核验**：
  - `stableAccountIdentitySeparatesDifferentPassengersAndDifferentActions` 验证了 `passenger-A` 与 `passenger-B` 提交同一 payload 时生成的键互不相同，同乘客不同 action 亦严格隔离，且 `passenger-A` 重登轮换 token 后依然精确命中自身旧键（通过）。

### 3. 写盘失败阻止网络发送
- **代码实现**：
  - 第 56-62 行在向网络分发请求前，先在本地写盘持久化待决记录（`persist(next, "无法安全保存重试记录...")`）；
  - 若设备存储已满或写盘出现 `IOException`，直接在第 68-70 行包裹抛出 `PendingStorageException`，网络 `transport(requestId)` 根本不会被触发，彻底封堵“先发请求但未落盘导致无法幂等追踪”的漏洞；
- **单测核验**：
  - `storageFailuresBlockTransportAndPreserveAnAcknowledgedButUnclearedRequest` 验证了首次写入故障时网络调用次数为 0（`calls == 0`），成功响应后写盘清除失败时记录依然在内存和持久层完整保留（通过）。

---

## 二、 实际单元测试执行结果

离线执行 `./gradlew testDebugUnitTest --offline`，实际编译并执行测试套件，解析结果汇总如下：

| 测试套件 (Suite) | 测试用例数 | 实际通过数 | 失败 (Failures) | 错误 (Errors) | 耗时 |
|---|---|---|---|---|---|
| `cn.hbzjhs.passenger.PendingRequestJournalTest` | 4 | 4 | 0 | 0 | 0.048s |
| `cn.hbzjhs.passenger.NegotiationTest` | 4 | 4 | 0 | 0 | 0.069s |
| `cn.hbzjhs.passenger.PassengerRulesTest` | 4 | 4 | 0 | 0 | 0.010s |
| **总计** | **12** | **12** | **0** | **0** | **0.127s** |

- **实际通过率**：12 / 12 (100% PASS)
- **4 项 P1 专项目标用例全部通过**：
  1. `stableAccountIdentitySeparatesDifferentPassengersAndDifferentActions` [PASS]
  2. `uncertainCommitThen401And403SurviveStorageRebuildAndAccountRelogin` [PASS]
  3. `currentTokenLegacyRecordMigratesWithoutInventingAKeyForAnUnavailableOldToken` [PASS]
  4. `storageFailuresBlockTransportAndPreserveAnAcknowledgedButUnclearedRequest` [PASS]

---

## 三、 覆盖限制与最终判定

### 1. 覆盖限制声明
- **本地单元测试 ≠ 真机与网络集成压测**：本次测试是在 JVM 进程内通过模拟 `Disk` 与模拟 `transport` 闭环完成的逻辑断言，证明代码层幂等状态机逻辑正确且严格；但未在双模拟器上通过真实 HTTP 代理注入 503/401/超时等网络故障进行 UI 端的真机重试验证。

### 2. 独立判定结论
- **P1 幂等修复判定**：**代码逻辑与单元测试独立复测通过 (PASS)**。修复方案已有效解决原初稿指出的 4xx/鉴权错误误清待决键导致重试丢失幂等标识的问题，且具备强账号隔离与写盘前置守卫。

---

## 四、 先前报告越界推断纠偏声明

- **事实澄清**：在上一份报告中，反重力记录了“两端当前均处于空闲状态”。此表述属于**越界推断**。
- **事实纠偏**：在 Antigravity 成员受限操作 `emulator-5584` 期间，Codex 正在主流程控制 `emulator-5586` 执行线下议价（NEG）单创建与联测。因此，Antigravity 不得、亦未对司机端状态进行全局断言，上一报告的设备状态描述以本次纠偏为准。设备与网络控制权始终保持由 Codex 全权接管。
