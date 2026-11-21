# antigravity：UIF-20261120-01 双端 UI 优化与实测验收完成（待协调者合并发布）

ACK `2026-10-26-v3`；真实 `conversationId`：`b152769c-93ce-47d7-b74d-7ec5590080cf`；前序归档会话：`9cada792-824e-4702-a2f0-1ca27d03b098`；`context`：UNKNOWN（`measurementSource: not_exposed`）。
唯一协调、合并与发布负责人仍为 Codex root（`01a17cc7-e0a2-7791-bf1d-4611c09bab22`）；本角色未修改 `activeCoordinatorRole` 及 `sessions.coordinator`，未自行 push、提交 PR、合并 `main`、改动版本号或部署云端。

## 候选版本与允许修改范围
- 独立工作树：`/Users/dongge/.codex/tmp/yijiexing-gemini-ui-20261120`
- 任务分支：`gemini/ui-feedback-20261120`
- 基线（`origin/main`）：`a81779627cdba1b1f6119e2c16318d5a3c158550`
- 冻结候选 SHA：`2e2fc0508d743c99f283e4d04afbfe06907037c9`（`git status` clean）
- 修改范围（30 个文件，+2535 / -962）：仅限 `apps/passenger-android/`（13 源码 + 1 单测）与 `candidates/driver-0.3.6-r2/android/`（15 源码 + 1 单测）；未改动后端、管理后台、小程序、版本号、包名或签名配置，未引入 `material-icons-extended`。

## 24 项优化与修复摘要
1. **整体统一（3 项）**：收紧并统一全端间距 token `Gap(6/10/12/16.dp)` 与页面/卡片内边距；主标语放大至 `19–20.sp`（`TypeScale`/`DriverType`）；重绘双层正圆「起/终」徽标并关闭 `includeFontPadding` 实现单字几何居中。
2. **乘客端（10 项）**：选址页右上角新增 `38.dp` 图标化展开/收起全屏地图及操作图标化；已完成订单顶部重设计为实付金额 Hero 卡、底部新增「订单信息与帮助」卡；订单列表分类与数量左右排列且 0 单明确显示 `0`（含单测）；个人中心重绘流线型车辆插画；常用地址/乘车人/发票抬头左设默认高亮圆圈、右下紧凑间距 `编辑`/`删除`；添加地址弹窗接入快捷标签与统一描边选址框；编辑资料弹窗重设计为 2 列体验城市选择卡网格；全端替换为统一 `PassengerSegmentTabs` / `PassengerChoiceChip`（零 `FilterChip`）；积分商城“积分明细”拆入独立子页；我的投诉自动加载渲染真实 PNG 缩略图、支持点击大图预览与失败重试，且 `PENDING`/`REPLIED`/`REJECTED` 分色展示（保留 `413439a` `readForCurrentSession`/`feeRefresh`/`feeError`）。
3. **司机端（11 项，含 3a/3b）**：新增 `LocationUploadPolicy` 与 4 项单测，实现精准定位优先、粗定位不覆盖近期精准点、上报异常回滚节流、听单超 45s 静默 8s 保活取点（遵守 `RetryFreshness.isFresh`，无定位泄漏与伪造）、抢单前同步新鲜缓存点；抢单大厅补齐 5 秒竞抢倒计时进度条与等待态禁用重复点击（严格遵守 `DECISIONS.md`「5 秒最近合格唯一获派」服务端规则）；接驾联系乘客改为右下悬浮 `PassengerContactFab`，取消与到达按钮合并一排；彻底移除 `ARRIVED` 及投诉页乘客手机尾号展示；待付款「刷新支付状态」图标化并统一「乘客已同意确认」卡片风格；我的订单区分类型标签与状态圆点徽标并修复正圆单字居中；费用明细默认常驻展开；投诉图片附件收紧为一排图标按钮；我的钱包重设计为实体银行卡质感+内置「申请提现」并移除旧提现大卡与蓝色常驻横幅；热力分布未配置改柔和 `info` 提示卡；全端 Tab/筛选统一为 `DriverSegmentTabs`/`DriverScrollableTabs`（零 `FilterChip`）。

## 实测验收与独立复核证据
- **乘客端构建与单测**：`gradlew testExperienceUnitTest assembleExperience` **PASS**（14 suites，91 tests，0 failures/skipped）；正式体验 APK SHA256：`c3df8568b6963212a4768c1009e1fc58d59b68ff4fddf243aa30e1ead2a6292f`（`1.1.25-experience` / v27）。
- **司机端构建与单测**：`gradlew testExperienceUnitTest assembleExperience` **PASS**（18 suites，141 tests，0 failures/skipped）；正式体验 APK SHA256：`39e630d4419c780d122218d685bbc6d2601be1068f51e767e4bc230d6a7f43cf`（`0.3.26-cloud` / v29）。
- **小程序回归校验**：`npm test && npm run check` **PASS**（74/74 tests，0 failures，`check-project.js` PASS；`wechatDeveloperToolsCompilation: NOT_RUN_REQUIRES_DEVELOPER_TOOLS`）。
- **隔离模拟器实测（`UIF_20261120` / `emulator-5596`）**：旧版基线（v26/v28）登录态后覆盖升级至新版 QA APK（v27/v29），冷重启登录态保留；免手动重定位直接抢单成功；34 张实测 PNG 截图（乘客 19 + 司机 15）逐张检视无瑕疵；`logcat_final.txt` 检查 Fatal/ANR 均为 0；测试后端与模拟器已全部停机清理。
- **独立只读子代理交叉复核**：全新 `research` 子代理（`59d9aff9-42bb-4a4f-bea0-f28714cfce8c`）审查代码、红线与全部实测截图后出具 **`PASS`**（Blocker/High/Medium/Low 均 0）。
- **完整证据目录**：`/Users/dongge/.codex/tmp/yijiexing-gemini-native-20261120/`（`CANDIDATE-BUILD-PROOF.json`、`NATIVE_ACCEPTANCE_SUMMARY.json`、`INDEPENDENT_SUBAGENT_REVIEW.md`、`screenshots/`）。

## 下一步交接（由 Codex root 执行）
请协调者（`01a17cc7-e0a2-7791-bf1d-4611c09bab22`）审阅候选分支 `gemini/ui-feedback-20261120`（SHA `2e2fc0508d743c99f283e4d04afbfe06907037c9`），确认后提交 PR、运行全端 CI 并合并发布双端体验包；如后续业务决定将 5 秒竞抢改为即时秒抢，须由协调侧调整后端及 `DECISIONS.md`。
