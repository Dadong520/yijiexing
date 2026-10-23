# 三端独立测试汇总与证据边界

日期：2026-10-23；2026-10-24增量见下。本文由Codex汇总既有原始报告、结果文件、截图及XML，不改写反重力的原始报告，不新增设备操作、业务提交或测试执行。独立测试的对象和版本分别列出，**不能概括为后台v7、司机0.3.4和乘客1.1.3三端均独立UI全通过**。

## 分项结论

| 对象与版本 | 独立执行内容 | 独立结论 | 不包括的范围 |
|---|---|---|---|
| 后台管理v3，真实云接口经本机入口8790 | Host/Origin等入口边界、登录/会话、OPERATOR/VIEWER权限、缺参数、订单及投诉真实读回、退出失效 | 最新实际HTTP结果所列断言通过；首轮错误与修正过程保留 | 后台独立浏览器操作不可用；未读取截图像素，未独立视觉审阅；不外推v7 |
| 司机0.3.2-cloud，versionCode5，专用5582/司机002 | HISTORY02订单投诉的空表校验、文字整行选择、键盘上方提交、确认取消保留草稿、单次提交与刷新 | 已执行真实App UI投诉流程通过，提交内容AG-QA-032 complaint keyboard test | 不包括0.3.3、真实导航、意见反馈独立提交、所有共享订单流程 |
| 乘客1.1.2-experience，versionCode4，5584；配对司机仍0.3.2/5586 | 无终点即时单独立UI尝试 | FAIL / INCONCLUSIVE：首轮发单按钮文案定位失败；次轮未正确处理复选框，误建带终点标准单并被断言拦截；随后已取消清理 | 不据此判定App无终点功能缺陷；竞价全流程、线下议价、历史评价投诉均未独立执行 |
| 乘客P1请求幂等修复，1.1.2生产组件 | 反重力重新走查PendingRequestJournal并实际离线执行单测 | 代码逻辑与独立单测复测通过：12/12，其中4项P1专项；本地22:42:10本轮JUnit证据 | 未做真实HTTP/模拟器故障注入，不能称UI故障链独立通过 |
| 乘客1.1.3-experience，车费展示与P1生产组件 | 反重力生产代码走查及本地离线强制重跑4套测试 | 14/14独立通过：新增2项报价语义测试、4项P1、4项议价、4项基础规则；本地23:26:08 XML | 未执行任何UI/设备/云接口；旧独立无终点UI失败保持，不能外推UI故障链通过 |

## 后台独立HTTP记录

原始报告：[`qa/antigravity/report.md`](../qa/antigravity/report.md)；最新结果：[`test_results.json`](../qa/antigravity/test_results.json)；Codex复核：[`codex-review.md`](../qa/antigravity/codex-review.md)。

早期headless调用虽返回SUCCESS但response为空，写证据权限被拒，不计通过；后续同一会话交互逐项授权后实际执行。首轮将unknown action的403误期望为400；反重力自行改为有权限的support.reply缺requestId，实际得到400，原轮保留为test_results-round1.json。最新结果验证无会话401、空登录400、正常登录、缺requestId400、VIEWER写入及导出403、登出后401，并读取独立样本PORDdcabe83784eb（DONE、1724分）和已回复投诉PC1f2deda60b4b。

这是v3接口验收，样本不是标准三端UI主单PORD29d6c69f4de0。该后台会话只有截图文件属性检查，没有像素审阅和独立浏览器操作。Codex后续v7的28项真实云读回及UI截图是另一组证据。

## 司机0.3.2独立真实UI与后续后台回读

原始报告：[`driver-032-report.md`](../qa/antigravity/driver-032-report.md)。报告确认真实操作合成历史订单HISTORY02，最终仅单次提交并刷新显示唯一测试标记。初期ElementTree叶子节点布尔误判、Compose外层可选择容器与内部RadioButton断言对象差异由测试方纠正，不能误记为App修复失败。

交付目录保留独立提交证据：

- `qa/paired-app-evidence/run032_16_complaint_submitted_result.png`与同名XML。
- `qa/paired-app-evidence/run032_17_complaint_refreshed.png`与同名XML。

之后Codex在司机0.3.2刷新验证后台回复，两个记录均显示“已回复”及实际回复内容：

- 投诉 `CPbc6ee5b8df7f`：`qa/paired-app-evidence/codex-complaint-admin-reply-readback.png`和同名XML，关联HISTORY02，回复“QA验收：投诉已收到，已核对测试流程”。
- 反馈 `FB1eee7f3b5c44`：`qa/paired-app-evidence/codex-feedback-03-admin-reply-readback.png`和同名XML，内容标记Codex-QA032-feedback-d34f9cd4，回复“QA验收：反馈已收到，交互回测完成”。

两条后台→司机App回读已实际通过，但执行者是Codex，不冒充反重力完成了反馈或回复回读独立测试。证据来源清单为paired-app-evidence/provenance.json；文件实际名称没有`driver-codex`前缀。

## 乘客独立UI失败和静态发现处理

最新原始报告：[`independent-final-report.md`](../qa/antigravity-passenger/independent-final-report.md)。执行版本1.1.2-experience（versionCode4），报告APK SHA256为 `eefc891e6d890b93f20a907385deb47c5f9e0e000e8fbf1c9101fb95b94998be`；这项哈希是该独立轮的版本记录，不自动等于后续最终交付包。

首轮脚本候选按钮文案未包含实际“确认呼叫 · 即时用车”，保存fail_call_btn_not_found.png/XML后中断。第二轮未按实际checked状态正确设置无终点选项，实际误建带默认终点订单PORDcecfc8298776（¥29.26），费用待定断言失败，保存fail_no_dest_pending_label.png/XML与03_order_detail_created.png/XML。测试方随后实际取消该单，约22:37显示乘客已取消，并停止交还设备。结论是测试未通过/不确定，不是已证实App无终点业务缺陷；不能将取消清理当作目标用例通过。

首版未经执行的脚本曾含无条件通过、订单定位和取消断言不足，Codex拒绝执行并要求收窄修正，记录见[`codex-script-review.md`](../qa/antigravity-passenger/codex-script-review.md)。未经执行的“通过”字样不构成证据。

静态发现及分级：[`antigravity-static-review.md`](../qa/antigravity-passenger/antigravity-static-review.md)、[`antigravity-findings-triage.md`](../qa/antigravity-passenger/antigravity-findings-triage.md)。确认的P1链路为“不确定提交后再遇401/403清旧键，重登重试可能生成新键”，不是普通单次400/409即证明重复扣款。1.1.2开发方改为只在明确成功后清键、以稳定passenger.id隔离、身份未取得时禁止操作，并有开发方单元/后端验证。早期UI报告当时记录“未独立复核”；后续p1-retest.md已补生产组件源码走查及12/12离线单测独立通过，更新代码/单测结论。真实HTTP与设备故障链独立注入仍未执行，早期UI失败范围保持不变。

## Codex新轮实测结果与独立边界

司机0.3.3与乘客1.1.2的真实线下议价UI订单`NEG1bafda5d1f03`已由Codex两端主任务实际完成：V1 2800分、乘客异议、V2 2600分、模拟支付FAILED恢复SUCCESS、22:45:46司机DONE；后台真实浏览器有各阶段截图，negotiation-admin-final.json的7项断言及logout通过。这是Codex执行结果，不是反重力独立复测；具体步骤及测试前置见THREE_PARTY_UI_REPORT.md。司机0.3.3 SHA-256 `3ad5f5570fea3a470fc27b638a6ee06b20ede8a324b040166b754f2be4579e7b`；乘客1.1.2 SHA-256 `eefc891e6d890b93f20a907385deb47c5f9e0e000e8fbf1c9101fb95b94998be`。司机账本只读核对50248→52848分恰增加2600分，FAILED未入账；App证据34/36/38—45详见THREE_PARTY_UI_REPORT.md。已有标准单真实三端闭环、司机0.3.2投诉独立UI、后台v3独立HTTP、乘客静态修复和乘客独立UI失败应分别呈现，不能合并为“最新三端反重力验收全通过”。

## 后续P1独立复测补充（本地22:42:10）

新原始报告：[p1-retest.md](../qa/antigravity-passenger/p1-retest.md)；实际Gradle命令输出：[p1-gradle-raw.log](../qa/antigravity-passenger/p1-gradle-raw.log)；由XML解析的摘要：[p1-unit-retest.log](../qa/antigravity-passenger/p1-unit-retest.log)。三份本轮JUnit XML已原样复制至同目录，时间均为UTC `2026-10-23T14:42:10`（本地22:42:10），每套4项，共12项，失败/错误/跳过均0。Gradle输出BUILD SUCCESSFUL、25项任务均executed，不能把摘要log叫原始Gradle输出。

反重力实际复核生产PendingRequestJournal：模拟503→401→403后持久化重建/同账号重登仍复用原键、按稳定passenger.id隔离不同账号/动作、旧格式迁移、写盘失败阻止网络发送、成功确认后清理失败仍保留记录。4项P1专项及其他8项单测全部通过，**P1代码逻辑与单测独立复测通过**。

本次仅本地源码与离线JVM模拟Disk/transport测试，未操作设备/云接口；不证明真实HTTP代理故障注入、Android真实存储、断网、后端会话过期及UI重试链已独立通过。原独立无终点UI失败与竞价/NEG未独立执行结论不变。

[最新Codex勘误](../qa/antigravity-passenger/codex-review.md)与新原报告均保留：原UI报告曾称两设备均空闲，属越界推断；当时Codex已接管司机设备进行NEG测试，原司机快照不能证明交接状态。保存XML只证明复选框当时checked=false，不足以证明“已选→未选”及文本点击的因果，故只记录测试状态设置失败，不归因产品缺陷。

## 最后Codex补测与0.3.4范围说明

Codex随后实际UI完成无终点PORD599ce232e019费用待定→取消，以及BID PORD5b52d7bea9c4报价3600→选择→预付。预付后司机0.3.3取消按钮被!paid隐藏（APP-010），0.3.4/code7最小修复增加8例，旧逻辑2fail、修复后31allpass；Codex执行独立静态审查，同签名覆盖候选后原单UI23:07:20进入WAIT_CONTINUE并退款3600，23:09:37乘客不继续最终CANCELLED，余额52848未变、退款时间不变。候选SHA256 `04c1ada0dafb4072a091003edfce45c71deccd5c5a838fd0d42a2cad1acb391c`，旧哈希仅为业务回归候选；后续退款展示修复候选r2哈希cdaa7147…05a152ae已在旧单两页由Codex实际只读回读通过，见下文；旧04c1哈希只对应取消业务回归。

这轮静态审查/自动化/UI执行归属Codex，不是反重力；不能将AG司机0.3.2投诉通过扩展为0.3.4独立通过，也不改写AG无终点UI当时失败。实际BID轮选择预付取消，行程DONE和多轮refundHistory均未在本轮UI执行。最新云28断言和13198分已付/3600分退款另属于Codex只读对账。

## 司机0.3.4 r2退款展示：Codex真实旧单回读通过

历史候选0.3.4（04c1ada0…acb391c）已通过业务取消退款，但司机详情/费用页曾误显示未支付和收入36元。修复候选r2 SHA256为 `cdaa71477675407d6fc57647cccd48da6e314ac791c3d4d2a145c80b05a152ae`，司机测试40项通过（开发方记录），不能归为反重力测试。

[`qa/final-display-ui/result.json`](../qa/final-display-ui/result.json)记录23:17:55 Codex只读打开既有退款单PORD5b52d7bea9c4：详情和费用两页退款标签均正确、退款3600分、未计入收入显示0，未新增业务动作，PASS。P2旧单显示复测已通过；AG独立司机范围仍为0.3.2投诉UI，不写0.3.4 AG通过。

## 乘客1.1.3最新独立代码与14项离线复测

本轮新证据已原样复制至`qa/antigravity-final-113/`：

- [反重力原报告](../qa/antigravity-final-113/report.md)
- [真实Gradle执行日志](../qa/antigravity-final-113/raw-test-execution.log)
- [版本与证据校验记录](../qa/antigravity-final-113/verification.json)
- [Codex核验和限制说明](../qa/antigravity-final-113/codex-review.md)
- 同目录4份原始JUnit XML及test-summary.txt。

对象为乘客`1.1.3-experience`，候选APK SHA256 `dca3ecee4389a2dba8c59610be2c24e99ef511407c811ecaef924bbde6d92fab`。独立会话实际执行`testDebugUnitTest --offline --rerun-tasks --console=plain`，日志BUILD SUCCESSFUL in 16s、25 actionable tasks全部executed、退出码0。4份XML时间均UTC2026-10-23T15:26:08（本地23:26:08），分别4+2+4+4=14项，失败/错误/跳过全0；复制后再次直接解析XML并核对verification记录的SHA256，原报告和执行日志哈希亦一致。test-summary逐例PASS文本不单独当通过依据。

本次独立走查`OrderFarePresentation`的BID未选司机参考估价、已选司机报价、NEG司机报价语义，以及`PendingRequestJournal`未决键保留、账号隔离和写盘守卫；新增2项车费语义测试与此前4项P1、4项议价、4项规则共14项独立通过。接受范围是所读代码与这些测试，不采纳原报告“彻底消除”“边界完备”为所有真实网络/存储/生命周期均穷尽验证的事实。

本轮没有adb、模拟器、云API或业务状态操作，真实UI仍由Codex执行。原AG无终点UI失败及勘误继续保留，不因14项离线测试而改写为UI通过；真实HTTP/设备故障链独立注入仍未做。

新包乘客1.1.3+司机0.3.4 r2的BID订单PORDfe4ce3005b17已由Codex实际UI完成：未选司机参考估价、选定¥36报价、预付、司机取消退款、乘客结束订单，两端CANCELLED/REFUNDED，退款时间保持23:30:40、余额52848未变。后台23:41:40另8只读断言通过，见qa/final-ui-order-readback.json及qa/final-release-ui/。该新单不是AG独立UI测试。后续乘客1.1.4中文显示修改未包含在本份AG 1.1.3的14项独立执行中。


## 2026-10-24 司机035新版独立UI尝试

反重力实际启动一次后，其headless命令执行权限自动拒绝，返回空response、wrapper exit16；实际UI动作数为0，未产生新版UI截图。本项结果为 **BLOCKED_PERMISSION_NOT_TESTED**，不是通过，也不是产品缺陷。未放宽全局权限，未重复绕过。原始结果已复制到`qa/antigravity-driver-035-blocked.json`，原作业位于`/Users/dongge/Documents/AI-Team-Link/outputs/20261024-002213`。

司机035/乘客115另由Codex独立子agent进行源码差异及实际截图审阅，结论分别位于`qa/driver-035-r3-compat-review.md`、`qa/passenger-115-compat-review.md`及`qa/ui-redesign-visual-review.md`；这些结果不能替代反重力独立UI操作。新版双模拟器与后台联动结果另记实际操作者和时间。乘客115反重力本地单元回归已实际执行结束：00:33:21生成5份JUnit XML，16项通过、0失败/错误/跳过，原始日志退出码0。根任务重新汇总XML与日志哈希，记录在qa/antigravity-final-115/；该结果是离线单元测试通过，仍不等于独立模拟器UI或三端云业务通过。反重力文字report.md随后已生成并原样复制归档，明确声明未独立操作双模拟器；其截图审阅来自其他agent的截图。根任务另实际查看最终键盘图，确认操作区位于键盘上方，不将文字审阅替代点击证据。

## 2026-10-24规则修复与小程序增量

共享后端最终冻结v8由GPT6.1独立执行45/45项通过，35文件测试前后SHA一致。小程序早期独立34+4项、最终受影响11+独立4项通过；最终绑定56文件。各轮重叠，不求和当成独立总量。根会话另用Cua导航8页并检查只读边界和7类页面截图；这是Codex执行，不是反重力。

证据：qa/logic-audit-20261024/final-61-review.md、miniprogram-61-review.md、miniprogram-browser-review.md；均为本地临时库/HTTP或只读浏览器，没有云写。司机开发方55项自动化、乘客开发方41项与可移植解压验证分别归属开发方。

用户要求所有模型6.1，反重力当前模型清单没有GPT6.1，所以本轮未启动反重力测试。旧反重力版本结论不自动覆盖v8、036-r2或新小程序。微信开发工具编译、原生权限和真机未执行；新A/B完整晚付实机联测因用户要求暂停云而保留待验收。
