# antigravity：UI 设计反馈优化与隔离实测已完成（待协调者合并发布）

ACK `2026-10-26-v3`；真实 `conversationId`：`b152769c-93ce-47d7-b74d-7ec5590080cf`；前序已归档会话：`9cada792-824e-4702-a2f0-1ca27d03b098`；`context`：UNKNOWN（`measurementSource: not_exposed`）。
唯一协调、合并与发布负责人仍为 Codex root（`01a17cc7-e0a2-7791-bf1d-4611c09bab22`）；本角色未修改 `activeCoordinatorRole`，未自行提交 PR、合并 `main` 或部署云端。

## 候选版本与允许路径范围
- 独立工作树：`/Users/dongge/.codex/tmp/yijiexing-gemini-ui-20261118`
- 分支：`gemini/ui-feedback-20261118`
- 基线（`origin/main`）：`42d3a6a94066fcd41480cf5faae7e52944763cb7`
- 最终候选提交 SHA：`fa4ae6b718c5fa07704e6c0d89e00c228d294fc9`（`git status` clean，`git diff --check` 无告警）
- 修改范围（22 个文件）：仅限 `apps/passenger-android/`（10 源码 + 1 单测）与 `candidates/driver-0.3.6-r2/android/`（10 源码 + 1 单测）；未改动后端、管理后台、小程序、既有签名配置、包名、防连点盾、竞抢与计价分账契约。

## 关键优化与缺陷修复清单
1. **全局视觉与字号**：统一页面底色 `#EEF2F5`、`Line = #DDE3E9`、`14.dp` 卡片、主题竖条小标题，字号统一下调一号（17/15/14/12.sp），地图面板移除底部大按钮仅保留拖拽抓手与手势切换。
2. **乘客端核心优化**：
   - `BookingScreens.kt`：自定义起终点互换图标、融合底栏预估与优惠摘要、顶栏城市切换胶囊、分类白卡；修复 Dialog 内 `WindowInsets.isImeVisible` 误判导致高度锁死的缺陷，默认 `310.dp` 并在点击“展开全屏地图选点”切换至 `560.dp`（右上角避让缩放按钮）。
   - `Feedback3Rules.kt` & `Feedback3Screens.kt`：新增 `parseTimestamp` 兼容 `+08:00` 与 `Z`，修复优惠券全部被过滤无法自动选中最大优惠的问题（已补充 `+08:00` 单测）。
   - `OrderDetail.kt`：进入页面通过 `LaunchedEffect` 静默自动拉取 `/fees`（显式透传 `CancellationException`），时间轴路线卡直接展示乘车人、尾号与明细。
   - `ProfileScreens.kt`：升级顶部青绿渐变个人名片卡与车辆插画。
3. **司机端核心优化**：
   - `OperationsSupport.kt` & `BusinessScreens.kt` & `CoreScreens.kt`：新增 `certificateEffectiveBadge`、`qualificationSummary`（含 `!certificateCurrentlyValid` 兜底）及 `DriverQualificationBanner` 红色过期横幅，杜绝过期证件因历史 `APPROVED` 残留误显正常（已增补 4 项单测场景）。
   - `MainActivity.kt` & `DriverLocationService.kt` & `DriverTripDistance.kt`：重构休息/离线态单次重定位及运行时定位超时策略（`10s` + 120s 有效协调兜底），新增 `isUsableDriverFix`、代数防重入及登出/切号校验，彻底禁止超时放行陈旧缓存。
   - `CoreScreens.kt` & `UIOComponents.kt`：在线空闲底栏固定“点击下线 · 停止听单”、青绿渐变名片卡、黑金鎏金 VIP 会员卡、2x2 出车工具网格、我的订单单排筛选条及我的钱包累计收入整合。
   - `BusinessFinance.kt`：深绿银行卡白底红字“解除绑定”与高对比“设为默认”；提现与改密 `onFailure` 增加内联红框报错并清空错误密码。

## 全量验证与实测证据
- **乘客 Android**：89 项单测全部通过（0 fail/skip）；Release APK SHA256 `a040830a1df55d7937ba95ee25728322b5d6aca09cd40ad5b8fd512d66f8e3c5`（42,629,269 bytes）。
- **司机 Android**：136 项单测全部通过（0 fail/skip）；Release APK SHA256 `fe14f3be7ea37b07e4f45f54cd6455d600513ca7089b87594b0a585083c4d4b2`（115,468,462 bytes）。
- **其他端与回归**：业务后端 `344/344` PASS、后台规则 `10/10` PASS、小程序 `74/74` PASS + 配置检查 PASS、协调校验与单测 `27/27` PASS；后台 IAP 测试 `NOT_RUN_MISSING_DEPENDENCY`（本机 Python 环境缺第三方包 `cryptography`/`google-auth`）。
- **隔离模拟器（`UIF_20261118` / `emulator-5594`）**：同证书覆盖升级（v26/v28 -> 新正式包 -> QA 包）PASS，24 张实机截图归档完备，`FATAL EXCEPTION | E AndroidRuntime = 0`，模拟器和本地 TLS Fixture 已全部关停回收。
- **独立非作者审查**：子会话 `2fb043bb-0262-4172-869a-9a2ff5e4cdff` 首轮发现 `MainActivity.kt` 离线兜底 BLOCKING 并在 `fa4ae6b` 修复后出具 V2 PASS。
- **证据目录**：`/Users/dongge/Documents/New project 2/coordination/evidence/UIF-20261118-01/`（含 `CANDIDATE-BUILD-PROOF.json`、`INDEPENDENT_SUBAGENT_REVIEW.md` 及 `native/NATIVE_ACCEPTANCE_SUMMARY.json` + 24 张截图）。

## 下一步交接（由 Codex root 执行）
请协调者（`01a17cc7-e0a2-7791-bf1d-4611c09bab22`）审阅或按需发起跨模型验收，确认后将 `/Users/dongge/.codex/tmp/yijiexing-gemini-ui-20261118` 分支 `gemini/ui-feedback-20261118`（SHA `fa4ae6b718c5fa07704e6c0d89e00c228d294fc9`）提交 PR、经 CI 校验并合并至 `main`，按既有包名与体验签名执行双端体验发布与公开回读。
