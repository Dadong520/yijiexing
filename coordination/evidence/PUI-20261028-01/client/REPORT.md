# PUIU 原生乘客端改造：作者交付

状态：实现与作者适用测试完成，独立验收 NOT_RUN，由 coordinator 按 PUIR 验收。本轮未发布、未改版本；当前手机安装版仍以 STATE 为准。

- 负责人 frontend，真实会话 `01a167c8-a5eb-77e3-b77e-214927a029da`。
- 唯一源码树 `/Users/dongge/.codex/tmp/yijiexing-pui-clients-20261028`，分支 `codex/passenger-ui-clients-20261028`。
- 基线 `8e2d655bd58d33241d79d6da3ad82f29cdec7894`，产品提交 `d02f616f1dd69a4f54d73c86d18873fd9c905a70`。
- 依据为用户 UI设计-乘客端.docx 的 13 张原图/3段文字（逐张映射在 frontend-review），与 root 转发的 `contracts/PUI-DEFAULTS-API.md` v1。不补造缺失编号5/6。

## 原图到实际改动

| 原图 | 接线与改动 | 定位（乘客原生包） |
|---|---|---|
| 1 预约提示 | 待出发预约状态标记、时间和独立查看；保留即时/预约分离和欠款门禁 | BookingScreens:167 / PassengerCards:55 |
| 2 更多设置 | 整页容器+固定返回栏，预约时间/乘车人/备注/途经点沿用同一草稿；主屏保留摘要。返回不销毁 BookingDraft；账号变化则重建 | BookingScreens:321 / PassengerPages:18 |
| 3 详情 | 路线移到费用和动作之前，ID为次操作；费用字号收敛，取消入口合入费用区。报价/支付版本、尾号、300m、费用查询等原动作保留 | OrderDetail:78 / :270 |
| 4 取消 | 固定理由+其他输入2..200字，提交原 order.cancel reason；模态仍使用原 journal action | PassengerUiRules / PassengerCancelChoices |
| 5 列表 | 顺序全部/进行中/已完成/已取消，紧凑路线、时间、类型/状态与金额 | BookingScreens:606 |
| 6 个人 | 身份卡、3个资料快捷块、安全/回执入口、帮助与折叠体验范围，减少同形大卡片 | ProfileScreens:34 |
| 7 常用地址 | 标题添加次操作、紧凑地址卡/空态，坐标移入编辑，真实默认徽标、设置/清除/保存默认 | ProfileScreens:211 |
| 8 乘车人 | 真实当前账号本人卡与尾号；紧凑其他乘车人/空态，设默认与恢复本人建议 | ExtraScreens:28 |
| 9 抬头 | 保留并优化空态，类型/税号/邮箱紧凑展示、编辑完整资料、真实持久默认 | ExtraScreens:89 |
| 10 安全 | 联系人紧凑卡/标题添加，当前账号本机存储；110仅系统拨号、无自动呼叫；详细未接能力折叠仍可读 | ParityScreens:49 |
| 11 申请订单 | 长 FilterChip 换可读选择卡，路线/时间/金额分层；保留逐单 invoiceApplicationEnabled 门禁 | ExtraScreens:161 |
| 12 历史回执 | 抬头+金额时间摘要，展开完整已有抬头快照、编号复制与模拟说明 | ExtraScreens:209 |
| 13 投诉 | 正文与原因/状态/处理回复优先，关联行程及编号折叠；新增全局列表查看已有图片附件，不隐藏原文 | ProfileScreens:317 |

订单头部和司机区均可到整页“联系与帮助”。司机 mobileMasked 只展示，司机呼叫按钮明确不可用；订单 company 的既有 CONFIGURED 实际客服电话组件保留确认后 ACTION_DIAL，不制造中继/接通/聊天。mini仅在获准的 order-detail JS/WXML 修正“正在通过隐私号连接”假提示，复用既有公司确认拨号，不扩大其它UI。

## 默认项合同与草稿保护

读取顶层当前 state.defaults（验证引用仍在本人列表），不以首项或过期 isDefault/replay result 冒充当前默认。defaults.set 仅 kind/id|null；三个原 save 可选 isDefault 布尔，使用原 journal/认证。保存失败保留资料编辑窗口。
默认地址置顶初始地点建议，不填覆盖当前起终点/GPS；乘车人仅新草稿初次预选、提交显式 riderId。用户选本人/其它人后后台默认变更不覆盖；删除已选记录回本人。发票仅新打开申请初次建议，仍显式 titleId；现有选择不因后台默认变化覆盖，记录删除才清空。账号key隔离。服务器持久化/CAS/权限/历史快照由 backend 与 root 审核，本报告不代替。

## Gemini/反重力参与

两次短独立上下文：`9b453ce4-da83-43a3-88d5-5e0caa287476` 生成状态条/卡片/标题等展示候选；`9cada792-824e-4702-a2f0-1ca27d03b098` 生成整页容器/取消理由候选。均90s、有SUCCESS/DONE、无工具事件，无模型/全局权限改动；提取代码、summary与原始stream hash在本目录。集成时调整父级padding、固定返回栏、文字/触控与草稿/API规则。无在跑 CLI，可释放 lease；不是独立作者验收。

## 实际作者测试

1. Android最终：`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ANDROID_HOME=/opt/homebrew/share/android-commandlinetools bash gradlew --offline --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx1536m' -Pkotlin.compiler.execution.strategy=in-process -I /Users/dongge/.codex/tmp/yijiexing-pui-build-20261028/passenger.init.gradle :app:testDebugUnitTest :app:assembleDebug`，cwd apps/passenger-android。`android-build-03.log`，exit0，31测试/0失败，assemble SUCCESS。新增默认项5场景、取消理由/订单筛选2场景；既有版本/账务/隐私等单测一并通过。
2. mini：`node --test tests/*.test.js`，cwd candidates/passenger-miniprogram-0.1.0/miniprogram。`mini-tests-02.log`，exit0，74/74，新增未配置/停用/空号码禁止假连接、配置公司需主动选择且不拨司机2场景；原72继续通过。初次测试文件写入因cwd错误未落盘，改绝对路径后执行本次有效测试。
3. `git diff --check`、中央 memory-role/frontend/真实session gate 与提交前 `python3 tools/check_coordination.py` 均PASS。结构检查不是业务验收。
4. 首轮build01在用户中断时未完成assemble，不能当PASS；build02与03完整SUCCESS。原始日志保留中央；仓内构建日志仅规范化行尾空白，未改变结果或诊断。

构建输出 `/Users/dongge/.codex/tmp/yijiexing-pui-build-20261028/passenger/_app/outputs/apk/debug/app-debug.apk`，仅作者debug产物，hash/大小在result.json，未入仓/未作为用户安装包发布。root可在隔离TLS fixture重构建并实测。

## 未测与交接

Android GUI/键盘返回与旋转恢复、真实设备拨号、三类默认项与新建业务实际端到端、WeChat编译/真机/发布、Amap外部导航、云发布均 NOT_RUN（本角色未操作设备/云）。Amap既有1008/10008、微信缺AppID、支付退款与发票为模拟等边界不改称成功。
下一步 root 取精确产品SHA+backend实现后按PUIR做非作者HTTP/实际原生视觉检查，重点整页返回保留、首个默认不覆盖后续选择、删除/换账号、取消其他输入、联系不可拨/公司确认、投诉附件与跨城逐单回执门禁。问题返回作者定点修正；未通过前不发布。

## PUIR首屏定点补丁（2026-10-28）

root独立TLSfixture原生截图07-order-detail/17-detail-fees显示：原route首卡正确，但金额位于首屏底，取消需下滑。仅改OrderDetail一文件21增/1删，提交 `f3c487885e3c1f5dd8de9b02409e7503ef4d0229`（父d06e407）。状态/编号下、route首个主卡之前新增紧凑金额/支付状态/取消行；沿用原orderFarePresentation、destinationPending非免费显示和cancellable/busy判断，重用原cancel模态，移除下方重复入口。PaymentSnapshot/QuoteSnapshot、金额计算、锁价/阶段门禁与请求均未改。

增量 `:app:compileDebugKotlin` exit0/36s SUCCESS（其余参数与前述Android命令相同），证据android-compile-04-fee-summary.log与fee-summary-patch.json。diff-check/中央真实ID与提交前结构gate PASS。不为布局变动重跑无变化全套；本补丁assemble/作者GUI均NOT_RUN，由root统一重建并复验首屏。此前result.json的debug APK仍绑定旧d02，不冒充本补丁安装包。未改版本/未部署。
