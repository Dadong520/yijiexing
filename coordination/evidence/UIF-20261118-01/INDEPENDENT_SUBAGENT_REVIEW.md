# 宜捷行 UI 优化候选提交独立非作者审查记录（UIF-20261118-01）

- **审查执行子会话 ID**：`2fb043bb-0262-4172-869a-9a2ff5e4cdff`（独立只读研究代理上下文）
- **仓库工作树**：`/Users/dongge/.codex/tmp/yijiexing-gemini-ui-20261118`
- **分支 / 基线**：`gemini/ui-feedback-20261118` / `42d3a6a94066fcd41480cf5faae7e52944763cb7`

---

## 第一轮审查（候选提交 `ab3440e7e80ab13e8a4d49de2c5a403acbe6ea08`）

- **结论**：`BLOCKING`（发现 1 项逻辑缺陷 + 5 项非阻断优化建议）
- **BLOCKING 缺陷**：司机端 `MainActivity.kt` 离线/休息态 `relocate()` 先于第 184 行消费 `<=120s` 缓存坐标，导致进入 8 秒 timeout 时的 `cached` 必定是 `>120s` 的陈旧缓存，但超时分支仍写入 `PositionBus` 并显示绿色已定位；另缺 `accuracy > 0`、`onLocationChanged` 非零校验、防连点代数与账户态守卫。
- **非阻断建议**：
  1. `BusinessFinance.kt`：`BusinessPassword` 失败清空 `oldPassword` 并清理 `validation`；去除提现/改密重复 `onMessage(err)`；
  2. `OperationsSupport.kt`：`qualificationSummary` 增加 `!certificateCurrentlyValid(it)` 兜底；`DriverQualificationBanner` 对 `EXPIRED` 标红；
  3. `BookingScreens.kt`：“展开全屏地图选点”胶囊挪至 `TopEnd` 避让高德右下角缩放按钮；
  4. `OrderDetail.kt`：`LaunchedEffect` 显式透传 `CancellationException`。

---

## 第二轮 V2 复核（最终冻结提交 `fa4ae6b718c5fa07704e6c0d89e00c228d294fc9`）

- **V2 结论**：**PASS（原 BLOCKING 缺陷及各项非阻断优化均已验证闭环，红线、单测、APK 哈希与实测证据完全一致）**

### 核验结果摘要
1. **离线定位安全性修复（原 BLOCKING 关闭）**：`MainActivity.kt` 新增 `isUsableDriverFix` 严格过滤非有限值、`(0, 0)`、`accuracy <= 0f`、`sampleTimeMs <= 0L` 与 `>120_000L` 陈旧坐标；移除 timeout 中的陈旧 `cached` 兜底；引入 `offlineRelocateGen`、`cancelOfflineRelocate`、`latestLoggedIn`/`latestDriverId` 绑定与 `DisposableEffect` 销毁。`CertificateDueStatusTest.kt` 已补测覆盖并在模拟器 `r2-d2-relocated-verified.png` 实证生效。
2. **四项非阻断建议同步关闭**：`BusinessFinance.kt`、`OperationsSupport.kt`、`BookingScreens.kt`、`OrderDetail.kt` 改动与复验截图（`03`、`03b`、`08`、`13`）逐一核对通过。
3. **红线与回归保护**：全部 22 文件改动严格限定在双端 Android 源码与单测；未触碰后端/小程序/`PassengerStore.kt`/`ConnectedStore.kt`/签名包名/计价分账公式/防连点盾/竞抢机制及 `CoreScreens.kt` 小程序静态文本断言。
4. **证据一致性**：
   - `CANDIDATE-BUILD-PROOF.json` 与 `NATIVE_ACCEPTANCE_SUMMARY.json` 的 `sourceSha` 严格等于 `fa4ae6b718c5fa07704e6c0d89e00c228d294fc9`；
   - 乘客端 14 套 **89** 项单测与司机端 16 套 **136** 项单测（XML 0 fail/error/skip）；
   - 双端 Release APK SHA256：
     - 乘客：`a040830a1df55d7937ba95ee25728322b5d6aca09cd40ad5b8fd512d66f8e3c5`
     - 司机：`fe14f3be7ea37b07e4f45f54cd6455d600513ca7089b87594b0a585083c4d4b2`
   - 隔离模拟器两轮实测零崩溃（24 张截图），干净关机释放。

> **权限与角色声明**：本文件记录当前会话体系内独立只读研究子代理的静态源码与证据复核；正式 PR 合入 `main`、云端部署及（如需）跨模型端到端验收仍由唯一协调者 Codex root（`01a17cc7-e0a2-7791-bf1d-4611c09bab22`）统筹执行。
