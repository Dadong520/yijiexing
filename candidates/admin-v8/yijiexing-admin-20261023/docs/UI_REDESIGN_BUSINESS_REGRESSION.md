# 两端 UI 改版候选业务回归计划

本文是只读源码和上一轮证据核对形成的待执行计划，**不表示新版候选已经通过**。本次编写未操作模拟器、未登录云端、未提交订单；后台和共享后端 v7 冻结。视觉、字号与键盘遮挡要求见 [UI_REDESIGN_QA.md](UI_REDESIGN_QA.md)，本计划只补充防止页面改版破坏真实业务链路的最小回归。

## 候选与证据边界

执行前由主任务协调两个模拟器的独占时间，记录司机与乘客 APK 版本、SHA-256、源码基线、设备标识、系统字体倍率及司机大字开关。两端均安装同一轮候选且保留原数据后，再开始联合业务；不得混用旧截图证明候选通过。后台保持现有版本，后端以 `qa/deployed-v7.json` 的基线为参照，由唯一发布负责人核实未漂移，本计划不触发发布。

上一轮可复用的只读样本：

| 样本 | 已有证据意义 | 本轮使用方式 |
|---|---|---|
| PORD29d6c69f4de0 | STANDARD 完成、2926分、5星；投诉PCfe37daedf4a7已处理 | 只读确认新布局能展示历史状态、费用、评价和回复。不得重新支付或重复评价，不替代新候选操作闭环 |
| PORDfe4ce3005b17 | BID参考估价2926分、选定3600分、预付后司机取消、退款3600分、乘客最终结束 | 只读回归累计退款/当前退款及司机收入0的展示，作为历史显示对照 |
| NEG1bafda5d1f03 | NEG V1 2800→异议→V2 2600→支付失败恢复→DONE | 只读检查报价版本、历史、异议和最终费用，是否需要新NEG业务由候选差异决定 |

证据来源为 [THREE_PARTY_UI_REPORT.md](THREE_PARTY_UI_REPORT.md)、[ACCEPTANCE.md](ACCEPTANCE.md) 和 `qa/final-release-ui/evidence-review.md`。历史结果不能外推为本轮反重力独立测试；独立执行者只能签署自己实际操作或审阅的范围。

## 本轮最小执行集

默认使用两笔新共享订单：一笔 STANDARD 完整闭环，一笔 BID 预付取消退款。测试账号由现有体验资源协调，不创建新云服务或导入原环境资料。涉及写操作的阶段均在已协调的候选验收窗口由真实 App/后台 UI 提交；协议读回用于核对，不代替业务点击。

| 编号 | 必要资源与性质 | 操作与预期 | 最少证据 |
|---|---|---|---|
| R0 历史显示与入口 | 两端App分别只读；后台只读；无需同时占用设备 | 登录后分别打开上述三个历史样本。检查订单ID可查、金额分转元、模拟标记、退款与收入区分；刷新/返回后仍显示同一订单。检查详情中的报价、评价、投诉回复不是仅列表标题 | 两端各至少一张历史费用/退款截图；后台同单详情或脱敏读回 |
| R1 STANDARD 同单主流程 | **两设备同时+后台浏览器**；新建业务数据 | 乘客正常发单，司机在线接单；后台看到同ID与参与账户。司机到达→错误尾号一次（必须留在ARRIVED且提示可读）→正确尾号进入TRIP→结束WAIT_PAY。各状态主要按钮可达，订单身份不变；TRIP不可通过普通取消入口误终止 | AVAILABLE、ARRIVED、TRIP、WAIT_PAY关键截图；后台同ID阶段/事件记录；错误尾号前后状态 |
| R2 支付失败→成功及重复保护 | 复用R1；**两设备+后台**；模拟付款 | 乘客先选择模拟FAILED，保持WAIT_PAY、paid=false、司机余额不增；再SUCCESS进入DONE。提交中按钮禁用/等待明确；快速重复触点不得生成第二次财务效果。刷新和返回重进后仍DONE；不以按钮短暂变灰代替服务端核对 | 失败提示、成功结果、司机完成页；前后订单fare、paid/credited、钱包余额与该单收入条数 |
| R3 评价投诉与后台回复 | 复用R1；乘客+后台必需，司机只读可串行 | 乘客提交5星与一条可识别测试投诉；输入法打开仍能确认，提交后有状态。后台以订单ID找到该投诉并回复；乘客返回/刷新后读回REPLIED和回复。评价不得因页面重组重复提交；后台处理不改变订单费用/完成状态 | 新投诉ID、后台处理截图、乘客回复回显；同单DONE/金额不变 |
| R4 BID 选择与预付款 | **两设备+后台**；第二笔新共享订单 | 乘客发BID，司机报价3600分（参考估价按实际读取，不强行要求2926）。选择前乘客清楚区分参考估价与司机报价；选择后PREPAY显示选定3600分。乘客确认模拟预付SUCCESS→PICKUP。司机界面保留合法出发前取消入口；后台bids和选定fare一致 | 报价列表、选定报价、付款确认、PICKUP截图；canonical id/driverId/fare/paid/paymentStatus |
| R5 BID 预付取消、退款、防重复 | 复用R4；**两设备+后台** | 司机UI填原因取消→canonical WAIT_CONTINUE、REFUNDED3600、paid=false、credited=false。旧司机参与态可为CANCELLED，必须说明是“本司机已退出”，不能宣称整单已结束。乘客选择不再用车→canonical CANCELLED。确认提交期间双击/刷新重进不产生重复取消/退款，退款时间与金额保持不变；司机费用页显示模拟退款36元、本单未入账收入0元 | WAIT_CONTINUE、最终CANCELLED、两端退款页；退款前后钱包基线、refundAmount/refundedAt、事件与账本条数 |
| R6 会话退出与权限保持 | App分别串行；后台独立窗口；身份/会话操作，无订单写入 | 两端各在订单已结束后退出，再返回受保护页不得继续显示可操作的旧身份；重新登录后历史订单仍在且不重放旧提交。后台用VIEWER只读打开新单，管理写入口不可用；如果本轮改动触及请求层，再用一次已知无权操作确认403，不能借超级令牌替代。退出后旧会话GET state应401 | 退出界面与重新登录只读结果；后台role、403/401脱敏结果；不留密码/token/完整session |

R1—R5一轮即可覆盖新的核心状态按钮、付款弹窗、取消弹窗、键盘输入、金额和业务回读，不额外重跑会员、提现、资质、城市配置等与此次两端 UI 变更无关的广测。若新候选只修显示且未改业务层，也不能省略两笔订单闭环；但无需重复已冻结后端的135项回归。

## 有条件追加，避免无效扩测

| 触发条件 | 最小追加内容 | 资源与边界 |
|---|---|---|
| NEG配对、报价、异议或付款弹窗/状态逻辑有修改 | 新增一笔NEG：司机生成配对邀约→乘客认领；乘客对V1提异议→司机V2重报价；旧付款窗口不得以V1金额继续成功，应要求重看V2。按V2金额完成模拟支付和行程；后台读取quoteHistory、priceDisputes、DRIVER_REQUOTE及最终金额 | 两设备+后台。报价示例2800→2600分；邀请码属于短期凭据，不录入截图、日志、报告。NEG司机取消直接CANCELLED，与BID待续叫分支不同 |
| WAIT_CONTINUE页面或“继续叫车”动作被改 | R5退款后先选择继续：同一订单恢复AVAILABLE、清除当前司机与旧bids、fare回到参考估价；refundAmount移入refundHistory。确认后台累计退款前后不变，再以乘客取消结束本单；不额外做第二轮付费，除非金额/重试实现也改变 | 两设备+后台。这是此前没有真实UI通过的分支，应标本轮新增，不能写“旧功能已回归” |
| 无终点下单/地址组件修改 | 新建一笔无终点单，确认各端“费用待定”而非0元免费；若补终点入口也改变，在同单补终点后读取确定费用，再取消结束 | 两设备+后台；可与其他地址视觉用例合并。不得直接修改canonical数据制造状态 |
| PendingRequestJournal、ConnectedStore.perform、busy逻辑、网络弹层改变 | 对一个真实新订单提交做一次候选客户端请求失败/恢复，重试相同操作后只产生一个订单/财务效果。优先用可控客户端测试适配层与真实组件回归；若没有可靠UI故障注入，标“未执行”，保留离线协议结果 | 不停云服务、不改路由/代理、不以全网断连影响其他任务；一般模拟FAILED支付不是传输故障，二者不可混称 |

## 金额与状态统一核对表

所有接口金额使用整数分，界面显示人民币元。记录本轮每笔新订单实际fare，不把示例值写死进验收。以下核对以canonical `passenger_orders` 为依据，不汇总司机投影重复计单。

| 时点 | 订单与账务断言 |
|---|---|
| STANDARD WAIT_PAY/FAILED | 两端及后台fare相同，stage=WAIT_PAY、paid=false、credited=false；司机余额与该笔支付前相同 |
| STANDARD SUCCESS/DONE | stage=DONE、paid=true、credited=true；本单收入仅一条，司机余额增加实际fare一次；本冻结体验环境费用规则下全额入模拟余额，不能外推真实分账 |
| BID报价未选/已选 | 未选展示参考估价及独立bid.amount；已选fare等于选中bid.amount，原estimatedFare保留，不能误把两者混作同一字段 |
| BID已预付但未完成 | paid=true、credited=false；模拟支付成功不等于司机已入账收入 |
| BID出发前取消与乘客结束 | refundAmount=该轮预付金额、paid=false、credited=false，司机余额不得因本单增加；重复触点及乘客最终结束不增加退款，不改变原refundedAt |
| 累计退款 | 费用台账refundAmount = 当前order.refundAmount + refundHistory各轮amount；currentRefundAmount与historicalRefundAmount分别对应两部分。迁移历史前后合计不变，相同金额的不同轮次不得去重 |
| 状态兼容 | 用canonicalStage（若提供）/canonical记录与司机参与态分别解释WAIT_CONTINUE/CANCELLED。两端界面语义不能矛盾，但不要求冻结协议中两个不同概念的枚举字面相同 |

后台只读核对优先使用已登录浏览器和受保护v2 state。现有 `qa/cloud_admin_readback.py` 固定检查上一轮目标订单，可用于R0历史确认，**不能把它成功当成新订单R1—R5通过**。新单需独立记录ID和对应断言。仅当列表/费用导出发生变化才重新导出；本轮后台冻结时无需反复全量导出。

## 候选协议/状态兼容审查清单

可在APK准备好之前只读候选diff完成，无需云数据写入；发现以下变化先报告负责人，不能在后台自行适配绕过：

- 司机仍通过 `ConnectedStore.perform` 调 `/api/action`，action/payload/requestId未被界面组件另起一套实现；保留失败与成功回调、busy及状态刷新。当前源码会在LocalApiException分支移除pending key，不能宣称所有错误/进程重启均保留原幂等键，需按实际改动验证。
- 乘客仍通过 `PassengerStore.submitJournaledAction` 和 `PendingRequestJournal` 提交 `/api/passenger/action`，账户隔离、请求日志和退出后旧响应隔离没有因页面重排丢失。
- `order.accept/bid/advance/cancel/selectDriver/pay/evaluate/complaint/continue`名称、金额整数分、尾号、取消reason及WAIT_CONTINUE继续type=1/结束type=2不变。司机不能出现代乘客支付或代选司机入口。
- NEG付款仍携带用户实际看到的 `quoteVersion` 与 `amount`，由 `QuoteSnapshot/quotationMatches/negotiatedPaymentPayload`校验；不得在点击后偷偷替换成用户没确认的新金额。
- 新页面仍解析退款状态、refundHistory/当前退款、credited、fareConfirmed/destinationPending、bids与quoteHistory；未知状态应清楚提示并允许刷新，不以默认DONE或0元掩盖。
- 登录凭据、IAM或管理令牌不进入APK、调试UI或截图。退出不能保留可提交的前一身份活动页面。

源码参照：乘客 `android/app/src/main/java/cn/hbzjhs/passenger/{PassengerStore,PendingRequestJournal,OrderDetail,Negotiation}.kt`；司机 `android/app/src/main/java/cn/hbzjhs/driver/preview/{ConnectedStore,CoreScreens,DriverDomain,MainActivity}.kt`；冻结共享后端 `passenger.py`、`negotiation.py`、`domain.py`。源码可能仍在候选开发中，审查结论须绑定最终SHA，不把此计划当作现有候选兼容已通过。

## 记录与收口

每个用例记录：执行者、两APK SHA、设备/字体、订单ID、步骤、界面结果、后台canonical核对、脱敏截图/XML路径、缺陷和复测结果。视觉可用与业务正确分别判定；修复后只复跑受影响用例及其相邻关键阶段，最终再同候选确认一笔完整主流程，避免拿不同候选拼接“全通过”。

开始状态全部为“待候选执行”。因设备、账号、已存在活动订单或独立工具不可用而无法执行的项目，写清阻塞及保留的证据范围，不标通过。两端通过且证据确认后，由主任务统一安排发布；本计划不授权任何单端提前发布。
