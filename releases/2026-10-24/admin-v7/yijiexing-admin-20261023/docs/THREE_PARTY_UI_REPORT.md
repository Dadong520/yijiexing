# 后台、司机 App、乘客 App 三端实际联动报告

记录日期：2026-10-23，时间均为UTC+8。主流程由Codex各主任务协调操作专用双模拟器与后台浏览器，使用独立Google Cloud真实持久化体验数据。**标准单同一订单主闭环已实际完成**；本报告不代表全部管理模块、全部订单类型或反重力独立UI测试通过。

## 环境与版本

- 乘客设备 `emulator-5584`、司机设备 `emulator-5586`；体验乘客尾号0101、司机尾号0003。原主单记录的乘客版本为 `1.0.0-experience`、司机为 `0.3.2`。模拟定位在宜昌，不是道路实车验证。设备后台服务曾因系统ANR在这两个专用QA设备调整，详见乘客 `qa/dual-emulator/run-metadata.json`。
- 后台浏览器访问 `http://127.0.0.1:8790`，经本机入口保持Google Cloud IAM和独立管理令牌；网页仅持角色会话。管理端非公开调用。
- 最终v7云服务：`driver-demo-00004-pnn`、`driver-admin-00005-pwl`，100%流量；两服务同镜像SHA-256：`b3cb3e2f61e4a724dd95b6dfac0d357d2bbd793b9e225402965b8fedb5ff0d3d`。
- 服务级 `maxScale=1`、`scalingMode=automatic`；revision级 `maxScale=3`保留、`cpu-throttling=true`，不能描述为每一级最大实例均为1。每实例CPU1、512Mi、concurrency1。读回记录为 `../../yijiexing-passenger-20261023/qa/deployed-final.json`；公开手机服务拒绝管理接口404，管理服务匿名访问403。
- 主流程跨发布阶段持续进行，v7最终对保留订单与投诉做了云只读核对，不能把早期每张截图一律标v7。
- 后续司机0.3.3 + 乘客1.1.2线下议价配对UI已实际完成，订单NEG1bafda5d1f03，详见本报告新增实测记录。司机0.3.3 SHA-256 `3ad5f5570fea3a470fc27b638a6ee06b20ede8a324b040166b754f2be4579e7b`；乘客1.1.2 SHA-256 `eefc891e6d890b93f20a907385deb47c5f9e0e000e8fbf1c9101fb95b94998be`。这轮不等于乘客发起BID的竞价UI验证。

## 标准单完整主流程

订单 `PORD29d6c69f4de0`，起点“宜昌东站 · 出租车上客区”，终点“夷陵广场 · 东侧入口”，2926分（¥29.26）。乘客账户 `Pc16b96ec9027`，司机 `D40a1fd8faa0e`。两端共享订单ID，后台直接读取canonical记录。

下表中的P目录为 `../../yijiexing-passenger-20261023/qa/dual-emulator/`；A目录为本项目 `../qa/`。每张App图有同名XML供交互内容核对。早期run-metadata和main-order.json停留TRIP，仅是阶段快照；结束结论以main-order-final.json和后续截图为准。

| 时间/阶段 | 实际操作与结果 | 证据 |
|---|---|---|
| 21:47:00 AVAILABLE | 乘客App下单，司机大厅可见；后台同ID待接单及金额 | P/01..05、A/three-party-available.png |
| 21:49:06 PICKUP | 司机接单，乘客同步司机接驾 | P/06-driver-pickup.png、07-passenger-pickup.png及07b |
| 21:49:49 ARRIVED | 司机到达，乘客/后台均已到达 | P/08-passenger-arrived.png、09-driver-arrived.png、A/three-party-arrived.png |
| 开始行程校验 | 错误尾号9999被拒，正确0101才进入行程 | P/10-driver-wrong-tail-rejected.png |
| 21:51:13 TRIP | 双App及后台同单行程中，费用¥29.26 | P/11-driver-trip.png、12-passenger-trip.png、13-passenger-trip-driver-fee.png、A/three-party-trip.png |
| 21:55:31 WAIT_PAY | 司机完成行程，双App及后台转待付款 | P/14-driver-wait-pay.png、15-passenger-wait-pay.png、A/three-party-wait-pay.png |
| 21:56:51/21:57:30 | 乘客选择模拟支付，失败/处理中都保留可恢复状态，没有误标完成 | P/16-passenger-simulated-payment-options.png、17-passenger-payment-confirmation.png，18-payment-failed.png、19-payment-processing.png |
| 21:58:03 DONE | 乘客模拟支付成功，司机显示完成；后台DONE、¥29.26、模拟支付成功 | P/20-passenger-payment-success.png、21-driver-done.png、A/three-party-done.png |
| 22:00:03评价 | 乘客提交5星评价，服务端保留评分及内容 | P/23-passenger-evaluation-complaint.png、P/main-order-final.json；v7云读回评分5 |

支付方式为SIMULATED，仅更新体验账务。最终记录paid=true、credited=true、paymentStatus=SUCCESS；不产生真实收款。

## 投诉和后台处理回到乘客

- 22:00:36乘客提交投诉 `PCfe37daedf4a7`，关联上述同一订单；截图P/22、23。
- 乘客先后追加两张图片，P/24-complaint-first-image.png、26-complaint-second-image.png；最终JSON保留两条附件记录。P/28记录图片预览。
- 22:03:35后台 `qa_operator` 回复：“三端联动验收：已核对本单29.26元模拟支付及行程记录，服务问题已登记并处理。”后台证据A/three-party-complaint-replied.png。
- 乘客App刷新后可见同投诉ID、REPLIED、完整回复、评分5/5，证据P/25-passenger-admin-reply.png。此项已形成实际UI处理闭环。
- P/27-driver-complaint-view.png及XML显示司机自己的“投诉与订单申诉”表单入口，不是乘客私人投诉内容或处理结果；另司机自己的投诉 `CPbc6ee5b8df7f`、反馈 `FB1eee7f3b5c44` 已在后台回复，证据A/cloud-driver-complaint-replied.png、cloud-driver-feedback-replied.png。司机0.3.2已实际回读两条“已回复”及回复正文，证据A/paired-app-evidence/codex-complaint-admin-reply-readback.png、codex-feedback-03-admin-reply-readback.png及同名XML。执行者为Codex，不能冒充反重力回复回读测试。

## 取消流程

另单 `PORD040f9c059534` 在22:10:09由乘客取消，原因“行程计划有变”。乘客取消前后见P/29-second-order-before-cancel.png、30-passenger-cancelled.png、31-cancelled-order-heading.png；后台见A/three-party-cancelled.png。此笔验证乘客最终取消及后台持久化同步，不替代司机取消后的WAIT_CONTINUE继续叫车分支。

司机出发前取消标准/预约/竞价单时，canonical是WAIT_CONTINUE，旧司机投影CANCELLED表示其参与已退出，乘客可决定继续或结束。兼容期两种语义必须分别记录，不以字面完全一致为结论；新版本配对是否消除该差异由两端负责人补验。

## 权限、失败恢复与统计导出

- `qa_viewer` 进入账号管理显示权限不足，A/cloud-viewer-users-denied.png；独立HTTP对写入及导出403、退出后401另有反重力记录。
- 浏览器在本机入口不可达时，原英文网络错误已修为清晰中文提示，明确恢复后刷新及先核对已提交操作；A/cloud-gateway-failure-fixed.png。恢复后正常读取见A/cloud-gateway-recovered.png。该验证不等于所有网络/CAS故障路径均已UI覆盖。
- v7云只读核对于22:11:34执行，`qa/cloud-admin-readback-initial-summary.json` **28项断言通过**，涵盖主单唯一性/DONE/2926分/5星/投诉回复、统计、费用台账与CSV一致。
- 该时点共7单、5完成、2取消、0进行中，完成且当前已支付金额合计10598分（¥105.98），模拟退款0。这是22:11初次核对历史快照，保留于cloud-admin-readback-initial-summary.json；当前CSV和summary已由23:10最终核对更新为11单，见本报告补测章节。
- 历史退款漏计ADM-007已修并随v7发布，统计累计当前及refundHistory每轮退款；管理单元测试覆盖多轮退款。初次样本退款0；23:07已实际完成一轮BID预付退款3600分，最终云退款合计3600分。多轮refundHistory继续叫车UI仍未实测。

## 自动化、独立测试和剩余范围

共享后端v7回归135项通过，日志在乘客qa/backend-v7-regression.log；后台单元13项通过（含历史退款），记录见乘客qa/admin-v6-unit.log及根任务最终回归记录；早期独立三角色temp DB 6项、mock UI 9页另列报告，不能相互叠加称为UI用例。

反重力独立真实HTTP测试在v3范围内完成，最初unknown action 403与预期400不符，其自行纠正为有权限的support.reply缺参数得到400后，最新记录列出的断言均通过。报告与原轮结果由反重力保留；它仅检查截图文件属性，未读像素、未独立视觉审阅，也没有浏览器操作工具。Codex实际UI证据不得写成反重力独立UI通过。详见 `qa/antigravity/report.md`、`test_results.json`、`codex-review.md`；不能自动外推为v7独立全通过。

尚未取得全部UI证据的范围：BID行程完成分支（本轮实测预付取消）、司机取消后选择继续并重新配对分支、预约及协商等其余分支、资质审核全流程、会员发布与二级审批、提现、人工退款及多轮退款云实操、全部异常恢复、整环境暂停恢复。真实短信、导航、推送、实名、开票仍受外部配置限制，详见ISSUES.md。

### 司机提交端回读补充（22:23确认）

司机负责人已在0.3.2 App、5582/司机002实际刷新核对：投诉CPbc6ee5b8df7f与反馈FB1eee7f3b5c44均显示后台回复。证据已复制到本工程`qa/paired-app-evidence/codex-complaint-admin-reply-readback.png`与`codex-feedback-03-admin-reply-readback.png`及同名XML；provenance.json保留原始来源。因此两项状态由待回读更新为通过；这属于司机自己的提交，不等于司机能查看乘客的私人投诉。

### 管理界面补充（实际浏览器）

日志118条样本按每页15条成功从1/8翻到2/8，搜索PCfe37daedf4a7只命中一次后台回复审计（编号97，运营账号，22:03:35），详情同时保留PENDING前值和REPLIED后值。证据：`qa/cloud-audit-pagination.png`、`qa/cloud-complaint-audit.png`。角色权限表已显示中文角色及17项实际权限，配置必填原因校验通过；这些检查没有发布会员配置。

## 独立测试分层补充

反重力后台v3 HTTP通过但该会话无独立UI/像素审阅；司机0.3.2的HISTORY02真实投诉单次提交、刷新和交互校验独立通过，见qa/antigravity/driver-032-report.md。乘客1.1.2独立无终点UI两轮因按钮文案定位、复选框交互失败，误建带终点单PORDcecfc8298776并取消；其报告判FAIL/INCONCLUSIVE，不应当作已证实App业务缺陷。静态指出的幂等P1已修复；反重力后续生产组件源码走查和离线12/12单测独立通过（含4项P1专项），真实HTTP/UI故障注入仍未执行。完整版本/步骤边界见[独立测试汇总](INDEPENDENT_TEST_REPORT.md)。

司机0.3.3+乘客1.1.2实际线下议价UI单NEG1bafda5d1f03已由Codex主任务完成，下面单列步骤与云核对；这不改变反重力各轮的独立测试范围。

## 新版司机发起线下议价实际UI闭环

结论：`NEG1bafda5d1f03`已完成司机发起配对、乘客认领、价格异议、司机重报、模拟支付失败恢复及司机结束，最终三端DONE、2600分（¥26.00）、paid=true、报价V2。它是司机发起的NEG线下议价，**不是乘客下单的BID竞价流程**。

执行者为Codex两端主任务，司机003和乘客101分别在两台专用模拟器实际操作，后台各阶段真实浏览器查询。版本与哈希见本报告环境部分。前置通过API将司机003配置到武汉可用权益，设置模拟GPS经度114.30/纬度30.59；这两项是测试准备，不能称App界面城市切换或真实道路定位验证。实际选点使用App地图界面。

| 时间（2026-10-23 UTC+8） | 实际动作 | 状态与金额 |
|---|---|---|
| 22:39:09 | 乘客认领司机创建的配对订单 | V1报价2800分 |
| 22:41:08 | 乘客提交价格异议“Please revise fare to 26 yuan” | 保留V1异议记录，后台可查 |
| 22:42:28 | 司机实际重报 | V2报价2600分，V1/V2历史均保留 |
| 22:43:54 | 乘客模拟支付FAILED | 未误记成功，允许恢复 |
| 22:44:51 | 乘客模拟支付SUCCESS | TRIP、paid=true，按V2的2600分支付 |
| 22:45:46 | 司机实际结束 | DONE，仍为V2/2600分 |

后台截图：`qa/three-party-negotiation-disputed.png`、`three-party-negotiation-disputed-history.png`、`three-party-negotiation-requoted.png`、`three-party-negotiation-done.png`、`three-party-negotiation-done-list.png`。截图展示本单价格异议、报价历史、重报以及最终完成状态。

`qa/negotiation-admin-final.json`于22:46:58真实云只读核对，7项断言及logout全部通过：DONE、fare=2600、paid、费用台账金额一致、quoteVersion=2、保留2轮quoteHistory、保留1次priceDispute。此文件与实际UI步骤互相补证，不把读回接口当作UI操作。司机最终账本由negotiation-done.json及negotiation-ui-results.md核对：50248→52848分恰增加2600分；FAILED时仍PREPAY且余额50248不变。以下补列两端App证据。

本轮实际UI通过由Codex执行，不是反重力对0.3.3/v7的独立复测，不改变INDEPENDENT_TEST_REPORT.md中的限制。

## NEG两端证据与账本补充

来源：乘客项目 `qa/dual-emulator/negotiation-ui-results.md`、`negotiation-done.json`（22:46:38只读捕获）；后者乘客和司机均为DONE/SUCCESS/V2/2600/credited=true、司机余额52848分。对照FAILED阶段的50248分，最终恰增2600分；失败支付未入账。该核对属于模拟余额，不是真实资金流水。

两端App证据（每个PNG有同名XML）：

| 阶段 | 乘客qa/dual-emulator文件 |
|---|---|
| V1认领 | 34-passenger-112-negotiation-claimed.png |
| 司机实际看到异议 | 36-driver-033-price-dispute.png |
| 乘客同步V2新价 | 38-passenger-112-requote-v2.png |
| 冻结V2/2600确认框 | 39-passenger-112-v2-payment-confirm.png |
| 模拟失败 | 40-passenger-112-negotiation-pay-failed.png |
| 模拟成功 | 41-passenger-112-negotiation-pay-success.png |
| 双端行程 | 42-driver-033-negotiation-trip.png、43-passenger-112-negotiation-trip-top.png |
| 双端完单 | 44-driver-033-negotiation-done.png、45-passenger-112-negotiation-done.png |

35/37为操作过渡截图，PNG与XML采样瞬间不同，不能单独用其PNG证明最终异议或新报价；以36/38和只读状态为准。旧报价拒付与重复支付幂等已有服务端测试，本UI轮未单独构造旧确认窗口，不能扩大覆盖。

测试完成后003已恢复宜昌420500并下线，见乘客qa/negotiation-test-precondition.json及执行报告。该恢复是测试环境清理，不标作App城市切换UI验收。未调整全局权益或用户001/002。反重力未独立执行此NEG UI场景；其后续独立P1代码/12项单测通过是另一层证据。

## 无终点费用待定与取消补测

Codex实际App创建PORD599ce232e019（乘客101，宜昌），明确选中无终点选项；后台22:51:32实际读到AVAILABLE和待补充目的地。乘客UI核对费用待定后22:52:02取消，后台同单CANCELLED/未支付/未分配司机；司机大厅相应移除。后台原¥0.00展示错误ADM-009已修，真云浏览器复测详情和账务显示“费用待定（目的地待补充）”。原图及修复图均保留。该轮为Codex补测，不改写反重力先前脚本失败结论，也未验证补目的地后整段行程。

## 司机0.3.4修复后BID预付取消退款实测

乘客发起BID订单`PORD5b52d7bea9c4`，司机003、乘客101，真实双App与后台浏览器操作。先用司机0.3.3报价和接驾；发现APP-010后，同签名覆盖安装司机`0.3.4-cloud`（code7）保留数据和原单继续复测，候选APK SHA256为`04c1ada0dafb4072a091003edfce45c71deccd5c5a838fd0d42a2cad1acb391c`。该哈希仅为业务取消退款回归的历史候选。随后发现司机退款展示P2，r2已修复、实际UI复测并公开发布cdaa7147…05a152ae；历史候选证据保留。

| 时间 | 实际UI操作与结果 |
|---|---|
| 22:55:44 | 司机003报价3600分，乘客可见同司机/同金额 |
| 22:56:45 | 乘客选定司机，进入PREPAY |
| 22:57:47 | 明确模拟预付SUCCESS，进入PICKUP/paid=true，余额仍52848分 |
| 修复前阻断 | 司机0.3.3的`!paid`条件隐藏合法预付出发前取消入口（APP-010），无法UI取消；保留原失败证据 |
| 修复与安装 | 新增8例先在旧逻辑触发2失败，最小修复后31例全通过；23:05同签名覆盖安装0.3.4保留原单，不用API代替取消点击 |
| 23:07:20 | 0.3.4司机实际UI取消，canonical WAIT_CONTINUE、REFUNDED3600分，paid=false、credited=false |
| 23:09:37 | 乘客选择不继续叫车，最终CANCELLED；退款时间仍23:07:20，退款金额仍3600，无二次退款；司机余额全程52848分 |

后台实际截图为`qa/three-party-bid-selected.png`、`three-party-bid-paid-pickup.png`、`three-party-bid-wait-continue.png`、`three-party-bid-final-cancelled.png`。两端原证据在乘客`qa/final-extra-ui/`：08—18为发单/报价/选择/模拟预付，19—21为0.3.4取消入口/确认/原因，22—24为司机退出/乘客WAIT_CONTINUE/退款36元，25—27为最终取消与司机退款明细；均有同名XML。最终状态JSON为bid-driver-cancelled-wait-continue-state.json及bid-final-cancelled-state.json，安装记录driver-034-install.json。早期results.json的22:59阻断状态为历史记录，不能盖过后续23:09成功复测。

`PASSENGER_CONTINUE`事件兼用于选择继续或不继续，后台标签改为“乘客决定是否继续叫车”；本单最终选择是不继续，依据CANCELLED及cancelReason判断，不能仅凭事件名写成继续叫车。状态数据未因标签修改变化。

本轮通过的是BID报价→选择→预付→司机取消→一次模拟退款→乘客结束；BID行程完成仍仅API验证，重新叫车及多轮refundHistory仍未UI实测。此前0.3.3+1.1.2的NEG已完成历史结论保留，0.3.4只增加本次取消回归证据。0.3.4独立静态审查来自Codex，不是反重力；AG0.3.2范围不扩大。

## 23:10阶段云统计和导出复核

`qa/cloud-admin-readback-summary.json`在23:10:23重新核对28项断言全部通过并退出成功，该时点11单、6完成、5取消、0活动，完成且当前已支付13198分（¥131.98）、累计模拟退款3600分（¥36.00），订单/费用CSV同额。最初7单、10598分、0退款保留于cloud-admin-readback-initial-summary.json，不能再称当前数据。当前证明一轮真实体验退款，多轮历史退款仅自动化覆盖。

## 司机0.3.4 r2退款展示P2已修复、实测并发布

历史04c1ada0…acb391c候选曾正确完成取消退款业务，但详情/费用页误显示“未支付”和收入36元；原缺陷及失败证据保留。r2已修正paymentStatus、退款金额与未计入收入显示，司机40项自动化通过。实际已公开发布司机0.3.4/code7 r2 SHA256 `cdaa71477675407d6fc57647cccd48da6e314ac791c3d4d2a145c80b05a152ae`，不是旧04c1候选。

旧单只读两页复测qa/final-display-ui/result.json为PASS；新单PORDfe4ce3005b17在乘客1.1.3+司机r2上再次实际UI完成，详情/费用页正确显示模拟退款36元、本单未计入收入0元。证据在乘客qa/final-release-ui/12-driver-034r2-refunded-detail.png、13-driver-034r2-refunded-fees.png、15-passenger-113-final-cancelled.png、16-passenger-113-final-refund-36.png、17-driver-034r2-final-refunded-detail.png及同名XML和final-cancelled-state.json。两端最终CANCELLED/REFUNDED3600，退款时间23:30:40不变，paid=false/credited=false，余额52848不变，DRIVER_CANCEL与PASSENGER_CONTINUE各1条。此为Codex实际UI验证，不扩展AG司机0.3.2独立范围。

## 后台退款台账最终实际UI核对

`qa/three-party-bid-refund-ledger.png`为后台实际打开PORD5b52d7bea9c4账务详情的证据：累计退款¥36、当前轮¥36、历史¥0，只有一条当前轮退款记录；支付时间22:57:47、退款时间23:07:20，订单关联乘客/司机姓名与ID一致。该界面核对通过，与司机App退款展示P2是不同界面，后者现已由r2旧单/新单真实UI复测通过，见r2章节。

PASSENGER_CONTINUE中性标签“乘客决定是否继续叫车”已实际浏览器复测，更新后的`qa/three-party-bid-final-cancelled.png`显示最终取消，不能将本次不继续选择误记为继续叫车。

## 乘客1.1.4中文可读性回归

乘客1.1.4/code6中文显示已实际UI回归：列表与详情显示中文订单类型和下单时间，“查看订单编号”可查看/复制完整编号；102账号用宜昌东站→夷陵广场创建PORD6b4bdcb702d8后于23:46:50取消，未付款且未分配司机。后台23:47:58浏览器核对13单6完成7取消0活单、已付¥131.98不变。qa/passenger-readable-114保留PNG/XML、复制核验和清理JSON；16项开发方单测及lint通过。该显示修正不改变后端业务，完整竞价退款证据仍按034r2+113记录，AG14项独立离线范围也仍为113。

## 已发布034r2与乘客113最终新BID回归

订单PORDfe4ce3005b17使用司机0.3.4/code7 r2 cdaa7147…05a152ae与乘客1.1.3/code5 dca3ecee…d92fab实际UI完成。23:25:56创建，未选司机阶段显示参考估价2926分，司机23:27:27报价3600分，乘客23:29:05选择后明确显示选定报价3600分；23:29:59模拟预付成功，司机合法取消入口仍可见；23:30:40司机取消触发WAIT_CONTINUE/REFUNDED3600，23:31:49乘客选择不再用车，最终两端CANCELLED。

乘客qa/final-release-ui/final-cancelled-state.json于23:32:47确认退款时间仍23:30:40、paid=false、credited=false、DRIVER_CANCEL和PASSENGER_CONTINUE各1，余额52848与基线一致。乘客退款展示、司机详情及费用页退款36元/本单收入0元均通过。证据01—11覆盖参考价、报价、选定、支付及取消，12/13覆盖司机退款两页，14为等待继续，15/16为乘客最终取消和36元退款，17为司机最终退款详情，均有同名XML；results.json及evidence-review.md保存执行/复核范围。

司机r2已经公开发布（根任务确认），该轮版本归属不与历史033议价和04c1候选取消回归混淆。1.1.3生产代码/14项离线单测反重力独立通过见INDEPENDENT_TEST_REPORT.md；上述新单UI为Codex，BID行程DONE、继续后重新匹配、多轮历史退款仍未借此验收。1.1.4中文可读性已单独UI回归并发布，公开下载完整GET/签名/哈希验证见qa/passenger-114-public-download.json；不改变本轮113业务测试归属。

## 23:41最新后台只读复核

qa/final-ui-order-readback.json于23:41:40核对最终新单PORDfe4ce3005b17，8项断言及退出全部通过；真实后台截图qa/final-bid-cancelled.png。该时点12单、6完成、6取消、0活动，paidFare=13198分（¥131.98）、累计退款7200分（两笔订单各3600分）。这是两单各一次退款，不是同单多轮refundHistory实测。23:10的11单/3600退款仅为历史快照。qa/final-release-ui已整体复制至本后台，含两端新包截图、XML、状态JSON及执行复核记录。


## 2026-10-24 UI优化版实际联合回归

司机0.3.5-cloud r3/code8与乘客1.1.5-experience/code7的冻结安装包已在5586/5584实际联测。标准单PORD97596bf0cc68从下单、接单、到达、错尾号拒绝、正确核验、行程、模拟付款FAILED后SUCCESS到DONE/¥29.26；失败未入账，成功余额52848→55774，恰增加2926分。5星评价、乘客PCa6f2b8b98513与司机CP59d6e38495d3投诉均由App实际提交，后台有确认和审计回复，两端均刷新读回。

竞价单PORD2a42ed9559c3实际报价¥36、选择司机、模拟预付、司机取消退款、乘客不再用车，最终三端CANCELLED/REFUNDED；退款3600分仅一次，余额55774未增，退款时间保持00:49:46。00:51:04根任务独立只读对账61项全部通过：15单、7完成、8取消、0活单，已付合计¥161.24、退款合计¥108。退款合计包含三笔不同订单各¥36，不是同单重复退款。统计是该时点全量共享订单快照。

详见[本轮完整联合报告](UI_035_115_THREE_PARTY.md)、`qa/ui-redesign-readback.json`及`qa/ui-redesign-three-party/`、`qa/ui-redesign-mobile-regression/`。00:51:32统一放行公开发布，最终公开文件核验以[交付指引](UI_FINAL_DELIVERY.md)与`qa/delivery-components.json`为准。此前各版本的历史测试保持原归属。本轮未覆盖的业务分支、外部服务和司机首页“今日”累计统计限制仍保留，不据此宣布全部功能或全机型无障碍通过。
