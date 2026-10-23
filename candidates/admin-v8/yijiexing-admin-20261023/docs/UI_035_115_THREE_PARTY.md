# 司机 035 r3、乘客 115 新界面三端联合报告

日期：2026-10-24（UTC+8）。截至本次记录，**新标准单已完成双 App 实际操作及后台同单核对**；五星评价、双方投诉后台处理及各自 App 回读均已完成；BID 预付取消退款亦已完成。00:51:32 已统一放行两端公开发布，乘客00:52:09、司机00:54:08公开GET/哈希/版本/签名均通过。本报告仅记录下列实际证据，不将标准单通过扩展为全部验收项通过。

## 版本、环境和执行归属

| 项目 | 本轮记录 |
| --- | --- |
| 司机候选 | 0.3.5-cloud r3，code8；SHA256 `6b9620a237286002662a966a31ba110f50d3a13c2ca4c113961792b32ee0b4ea` |
| 乘客候选 | 1.1.5-experience，code7；SHA256 `4f9f2b40d435a905690a4675efaa52c721783e6eb69017eb14e704405bb217fd` |
| 云环境 | 独立体验环境，后端 v7 冻结，后台界面不因本轮 App UI 改造而变化 |
| 真实双端操作者 | 乘客主任务 Codex，独占 `emulator-5584` 与 `emulator-5586`；5586 于 00:26:46 明确交接，不操作 5582 |
| 本轮实际身份 | 司机尾号 **0003**，乘客尾号 **0102**；不是此前计划中的 0101 |
| 后台操作者 | 后台主任务 Codex，在真实浏览器只读查看同单状态，随后实际回复两条投诉；订单业务状态由双端推进 |
| 本文编制 | source_audit 只读检查已保存 JSON 与文件索引，未操作设备、浏览器或云业务；实际 UI 结论依据执行主任务记录及原证据 |

两端候选哈希已由主任务独立核对。这里的“独立核对哈希”不等于反重力独立 UI 测试。AG 司机 035 本次因权限阻断未执行；乘客 115 已独立离线强制重跑 16 项单元测试全部通过。其未操作模拟器或云业务，本报告的双端实际操作不得归为 AG 实操通过。

## 标准单实际流程

订单 **`PORD97596bf0cc68`**，STANDARD，宜昌（420500），起点“宜昌东站 · 出租车上客区”，终点“夷陵广场 · 东侧入口”，费用 **2926 分 / ¥29.26**，`fareSource=DEMO_ESTIMATE`，付款方式 `SIMULATED`。这是体验估价及模拟资金流程，不是实际道路计费或真实支付。

| 时刻 | 实际动作与结果 | 证据 |
| --- | --- | --- |
| 00:28:14 | 联调基线：0102 与 0003 无活跃订单，司机余额 52848 分、下线；保存两端候选哈希 | `baseline.json` |
| 00:28:42 | 乘客 App 创建标准单，后台真实浏览器可见同 ID/金额 | App 01–02；后台 `01-standard-created.png` |
| 00:31:55 | 司机 App 接单，双方显示接驾，后台同单同步 | App 03–05；后台 `02-standard-pickup.png` |
| 00:32:53 | 司机到达，双方 ARRIVED | App 06–07；订单事件 `DRIVER_ADVANCE` |
| 00:34:12 快照 | 错误尾号 9999 被 UI 拒绝，双方仍 ARRIVED，未进入行程，余额仍 52848 分 | App 08；`standard-wrong-tail-state.json` |
| 00:34:55 | 正确核验后司机进入行程，双方显示 TRIP | App 09–10；订单事件 `DRIVER_ADVANCE` |
| 00:35:32 | 司机结束行程，进入 WAIT_PAY | App 11–12；订单事件 `DRIVER_ADVANCE` |
| 00:36:32；00:36:38 快照 | 乘客实际选择模拟付款失败；双方 WAIT_PAY、FAILED，`paid=false`、`credited=false`；司机余额 52848 分，未入账 | App 13；`standard-payment-failed-state.json` |
| 00:36:47；00:36:52 快照 | 乘客模拟付款 SUCCESS；双方 DONE、`paid=true`、`credited=true`；司机余额 55774 分 | App 14–15；`standard-done-state.json` |
| 00:37:40 | 后台主任务真实浏览器确认同订单 DONE、¥29.26；只读核对 | 后台 `03-standard-done.png`，主任务操作记录 |

余额核对：`55774 − 52848 = 2926` 分，恰为本单金额。付款失败时余额未变化，付款成功后本轮快照增加一次订单金额。此次实际覆盖失败后成功恢复，不等于已执行所有并发重复点击、断网或 HTTP 故障注入用例。

保存的 `driverTodayIncome` 从 27048 到 29974，差值同为 2926；但该字段是既有 seed 初始化后累计的演示计数，没有跨日重算，**不能当作当日经营统计**。本轮金额结论使用订单与钱包前后值；后台 canonical 订单统计独立计算。

## 原始证据索引

双端文件位于 [乘客联合回归目录](../../yijiexing-passenger-20261023/qa/ui-redesign-regression/)：

- [基线与候选哈希](../../yijiexing-passenger-20261023/qa/ui-redesign-regression/baseline.json)
- [错误尾号后的双端状态](../../yijiexing-passenger-20261023/qa/ui-redesign-regression/standard-wrong-tail-state.json)
- [模拟付款失败后的双端状态](../../yijiexing-passenger-20261023/qa/ui-redesign-regression/standard-payment-failed-state.json)
- [模拟付款成功及完单状态](../../yijiexing-passenger-20261023/qa/ui-redesign-regression/standard-done-state.json)

| App 阶段 | PNG 原文件（均在上述目录） |
| --- | --- |
| 准备与创建 | `01-passenger-ready.png`、`02-standard-created.png` |
| 司机接单与双端接驾 | `03-driver-standard-offer.png`、`04-driver-pickup.png`、`05-passenger-pickup.png` |
| 到达与错尾号 | `06-driver-arrived.png`、`07-passenger-arrived.png`、`08-wrong-tail-rejected.png` |
| 双端行程 | `09-driver-trip.png`、`10-passenger-trip.png` |
| 待付款与失败 | `11-driver-wait-pay.png`、`12-passenger-simulation-options.png`、`13-passenger-payment-failed.png` |
| 双端完成 | `14-passenger-standard-done.png`、`15-driver-standard-done.png` |

后台截图：[创建](../qa/ui-redesign-three-party/01-standard-created.png)、[接驾](../qa/ui-redesign-three-party/02-standard-pickup.png)、[完成](../qa/ui-redesign-three-party/03-standard-done.png)。截图和 JSON 相互补充；JSON 读回不冒充界面点击，根任务后台只读核对不冒充双端业务执行。

## 五星评价与投诉处理进度

同一标准单于 **00:41:57** 完成真实 UI 五星评价；[乘客投诉状态读回](../../yijiexing-passenger-20261023/qa/ui-redesign-regression/standard-passenger-complaint-state.json) 保存 `rating=5`，其 00:42:52 快照中的投诉尚为 PENDING，仅证明当时提交状态，不作为后来已回复的证据。

| 项目 | 实际确认结果 | 证据与待补 |
| --- | --- | --- |
| 乘客投诉 `PCa6f2b8b98513` | 00:42:45 提交；后台主任务于 **00:44:26** 实际回复，关联本单 | App 16/17；[后台回复截图](../qa/ui-redesign-three-party/04-passenger-complaint-replied.png)。乘客已回到旧单实际刷新回读，见 `30-passenger-complaint-reply.png` 及同名 XML |
| 司机投诉 `CP59d6e38495d3` | 00:43:28 提交；后台主任务于 **00:45:18** 实际回复，关联本单；司机 App 已实际刷新读回 | App 18/19；[后台回复截图](../qa/ui-redesign-three-party/05-driver-complaint-replied.png)；[司机回复图](../../yijiexing-passenger-20261023/qa/ui-redesign-regression/20-driver-complaint-reply.png)及同名 XML |

司机回读 XML 中可见本单 ID、乘客尾号 0102、“已回复”和处理文字“已核对本单29.26元模拟收入仅入账一次，司机费用反馈已登记处理”。刷新操作由联调执行主任务完成；本文编制仅检查保存的 XML/证据，不冒称再次点击或刷新。此处后台阶段包含实际处理投诉的写操作，区别于此前标准单状态的后台只读核对。双方各自回读自己的投诉，不由导出的联合状态结构推断 App 能读取另一方私人投诉。

## 乘客 115 独立离线复测

[反重力原报告](../qa/antigravity-final-115/report.md)、[原始执行日志](../qa/antigravity-final-115/raw-test-execution.log)及 [Codex 结果复核](../qa/antigravity-final-115/codex-review.md)已归档。AG 对冻结 115/code7 实际执行离线强制重跑；5 份 JUnit XML 合计 **16 tests、0 failures、0 errors、0 skipped**，退出码 0，25 个任务实际执行，日志耗时 9m42s。本文只读解析 XML 核对上述统计，没有再跑一轮测试。

该结果覆盖议价、基础规则、费用/编号展示、请求幂等日志的离线测试；不等于真实网络故障注入、AG 双模拟器 UI 或本轮全部业务独立通过。原 AG 无终点 UI 未通过记录仍保留，不因本次离线测试而改写。

## BID 预付取消退款与最终只读核对

新 BID **`PORD2a42ed9559c3`** 已通过冻结两端实际 UI 完成报价、选择司机、模拟预付、司机取消与乘客不继续。金额为 **3600 分 / ¥36**，不是标准单的 2926 分。

| 时刻 | 实际流程与结果 |
| --- | --- |
| 00:45:13 | 乘客创建 BID |
| 00:48:05 | 司机报价 3600 分 |
| 00:48:40 | 乘客选择司机 |
| 00:49:03 | 乘客模拟预付 |
| 00:49:46 | 司机取消，订单进入 WAIT_CONTINUE，模拟退款 3600 分 |
| 00:50:13 | 乘客选择不继续（false），最终 CANCELLED |
| 00:50:36 状态快照 | 双端 CANCELLED/REFUNDED，paid=false、credited=false；退款时间仍 00:49:46，司机余额仍 55774 分 |

[最终双端状态](../../yijiexing-passenger-20261023/qa/ui-redesign-regression/bid-final-state.json)与[后台取消退款截图](../qa/ui-redesign-three-party/08-bid-cancelled-refunded.png)对应同一订单。该轮未计入司机收入，乘客最终取消没有再次退款；DRIVER_CANCEL 与 PASSENGER_CONTINUE 各一条。PASSENGER_CONTINUE 是“乘客决定是否继续叫车”的事件名，本次选择为 false，不能写成乘客继续叫车。

乘客也已回到标准旧单实际刷新读取 `PCa6f2b8b98513` 处理回复，见 [30-passenger-complaint-reply.png](../../yijiexing-passenger-20261023/qa/ui-redesign-regression/30-passenger-complaint-reply.png) 和同名 XML。该 XML 可见 5/5 分、“已回复”及本单 29.26 元模拟支付、行程和五星评价的处理文字。司机自身回复仍由 App 20 证据证明；两条提交端回读均完成。

后台主任务于 **00:51:04** 对 canonical 订单、台账和投诉执行只读核对，[ui-redesign-readback.json](../qa/ui-redesign-readback.json) 的 **61 项断言全部通过，退出会话成功**。该时刻共 15 单、7 完成、8 取消、0 活跃，已支付完成金额 16124 分（¥161.24），累计退款 10800 分（¥108）。这是带时间的全体验环境快照，会随后续测试变化，不是司机首页的“今日”计数，也不是只统计本轮两单。

本次 BID 覆盖一次预付取消退款，不代表 BID 行程 DONE、选择继续后重新匹配或同单多轮 refundHistory 已有新 UI 实测。只读断言核对不能替代 UI 操作；PASSENGER_PAY 事件包含付款失败等尝试，不能用其总条数推断成功扣款次数。

## 已收口结果与剩余待回填

| 项目 | 当前状态 | 待补字段 |
| --- | --- | --- |
| 标准单评价 | 00:41:57 五星 UI 成功，后续状态已读回 | 早期 DONE 快照 evaluation=null 保留为当时事实，后续五星见上文 |
| 乘客投诉 | PCa6f2b8b98513 后台回复及乘客旧单刷新回读完成 | App30 PNG/XML与后台04，见上文 |
| 司机投诉 | CP59d6e38495d3 后台回复及司机 App 刷新回读完成 | 见上文 18–20 和后台 05；与乘客投诉分开归属 |
| BID 流程 | PORD2a42ed9559c3 预付→司机取消退款→乘客不继续，实际 UI 完成 | 双端最终 CANCELLED/REFUNDED3600；余额55774不变，退款时间未变，见上文 |
| 重复提交及其他故障恢复 | 本文仅确认模拟 FAILED→SUCCESS | 实际执行步骤、请求/事件计数、UI 结果；未执行应明确说明 |
| 独立测试 | 035 AG 权限阻断未执行；115 离线 16/16 PASS | 原报告已归档，不能写独立 UI 全通过 |
| 公开发布与源码包 | 00:51:32 已统一放行 035r3/115，公开核验已通过 | 全量GET与同一实测APK哈希一致，最终源码清单见UI_FINAL_DELIVERY.md |
| 最终清理与统计 | 00:51:04 只读61断言通过，15单7完成8取消0活跃 | 00:51:46 cleanup.json确认司机下线、两设备字体1.0且返回桌面 |

本轮报告单独保留，旧 032/033/034 与乘客 113/114 的业务证据及独立测试归属不变。最终公开核验与源码清单见UI_FINAL_DELIVERY.md。
