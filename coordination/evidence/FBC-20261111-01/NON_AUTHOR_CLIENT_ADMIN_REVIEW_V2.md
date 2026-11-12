# 管理端与客户端非作者增量复核 V2

日期：2026-11-12；审查角色 backend；真实会话 01a182c6-c058-76e1-9584-38abfe813799。

结论：**PASS（限定本次客户端/管理端增量源码及定向验证）**。R1–R6 原问题均关闭，未发现新增阻断项。此结论不代表三端真实业务总体验收或发布许可；本人编写的后端差量不在独立验收范围。

## 冻结版本及范围

- 源目录：/Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029
- 分支：claude/feedback3-20261110
- 审查基线：c46479ff3d03831d23ca281046065294fd4f1b0a
- 精确候选/待协调者合并 SHA：6548ceca164f9abd74e516a577b2e78c5edb27a5
- 增量：apps/admin-console/**、apps/passenger-android/**、candidates/driver-0.3.6-r2/android/**，13 文件，200 插入/11 删除。已逐项审阅 diff 及相关调用上下文。
- 本轮没有修改源代码、提交、切换分支、合并、调用云服务或部署。源目录核验 HEAD 为上述候选，git status --short 无输出。

## 原审查项复核

| 项 | 复核结果 | 修复与证据 |
| --- | --- | --- |
| R1 乘客 RANGE 有效期误显示领取后0天 | 关闭 | Feedback3Rules.kt 的 couponValidityText 明确处理 RANGE/FIXED/DAYS，缺起止字段用待同步文字；ParityScreens.kt 使用该方法。CouponValidityTest.kt 覆盖区间、单侧截止、旧 FIXED、DAYS 和缺失值。测试源码已审阅，Android/JUnit 未执行。 |
| R2 司机费用详情退款后仍显示旧收入 | 关闭 | BusinessSupport.kt 的头部与展开详情均使用 driverFeeIncome；该方法经 incomeForDisplay 处理退款/冲回/关闭状态，并据状态决定收入标签及颜色。已审阅退款、取消、已入账和待入账测试分支。 |
| R3 先冲红后无法重开 | 关闭 | admin.js 的 invoiceActionPermissions 分离冲红与重开权限，RED_FLUSHED 且无 reissuedAs 的乘客回执可重开；已重开、司机、无写权限均不能重开。实际 invoicesAdmin 菜单 VM 覆盖5个权限/状态分支。 |
| R4 司机缺空车费展示 | 关闭 | DriverDomain 字段、ConnectedStore 当前订单 cancelFee 解析、订单卡片、订单详情及费用页均接通；以独立模拟补偿说明展示 UNPAID/PAID，未混入正常完单收入。费用接口显式给出 cancelFee:null 时不回退旧值。已审阅解析及展示测试；后端字段正确性仍须其他会话独立验收。 |
| R5 退款分账冲回误标绿色佣金 | 关闭 | companySplitLedgerTypeInfo 显式区分 ORDER_SPLIT/WITHDRAWAL/REFUND_REVERSAL；退款冲回使用 bad 样式和专有标签，未知类型标待核对。实际 companySplitLedgerAdmin 渲染 VM 验证退款及未知类型均不标作佣金。 |
| R6 服务分开放边界显示 NaN | 关闭 | BusinessCommunity 使用 serviceScoreRangeText；辅助方法先处理 null/非有限数，开放区间分别显示 ≥min / <max，缺双方显示待同步。新增测试覆盖开放两端及普通区间，测试源码已审阅。 |

## 本人实际验证

1. `node --test apps/admin-console/web/operations.test.js`：**10/10 PASS，0 fail，0 skipped**（末次67.383ms）。包含新增回执权限矩阵、四种分账流水类型断言。
2. `node` 标准输入 VM 验证：通过 `git show 6548ceca164f9abd74e516a577b2e78c5edb27a5:apps/admin-console/web/admin.js` 获取冻结原文，提取 invoiceActionPermissions / companySplitLedgerTypeInfo 及真实 invoicesAdmin / companySplitLedgerAdmin，在最小 DOM/数据桩中运行。**PASS**：5种回执菜单分支，退款冲回与未知流水实际渲染。终端输出：`Frozen admin VM PASS: 5 invoice menu branches; refund reversal and unknown ledger rendering.`
3. `git diff --check c46479ff3d03831d23ca281046065294fd4f1b0a 6548ceca164f9abd74e516a577b2e78c5edb27a5 -- apps/admin-console apps/passenger-android candidates/driver-0.3.6-r2/android`：**PASS，无输出**。
4. `git status --short`：无输出；`git rev-parse HEAD`：6548ceca164f9abd74e516a577b2e78c5edb27a5。
5. 中央记忆检查：本轮恢复及交付写入后均 **PASS**；context UNKNOWN（无模型遥测），结构检查不代表业务验收。

## 限制、未通过项与下一步

- 新增阻断项：无。原报告保留，V2 仅更新本次冻结版本 R1–R6 的关闭状态。
- Android Gradle/JUnit、APK构建、模拟器/真机、完整浏览器交互、云端发布回读：**NOT_RUN（本审查会话）**。没有把测试文件存在当成测试已通过。
- 本人后端实现及金额/数据规则：**不由本人独立验收**；既有344项作者自测不替代非作者复核。
- 发票、公司分账与空车费展示均保留模拟含义；没有连接真实税务、银行或支付入账的声明。
- 下一步由唯一协调者收齐其他会话后端验收和集成/构建证据，确认合并 SHA 与 deploy_owner，再统一决定发布。本角色完成本次限定审查后待命，不自行派单或轮询。
