# 集成缺陷与复测记录

以下为本任务实际发现的缺陷。修复源码、自动化通过、部署和浏览器复测分别记录，不能互相替代。

| 编号 | 发现与影响 | 修复 | 复测状态 |
|---|---|---|---|
| ADM-001 | 旧管理页只读司机账户订单，漏掉尚未接单的乘客订单 | 新后台直接读 passenger_orders 权威记录，统计不重复汇总投影 | 实际三端UI主单PORD29d6c69f4de0至DONE通过；v7云28断言核对唯一订单、费用和CSV一致 |
| ADM-002 | 普通角色若持有旧超级管理令牌可绕过 v2 权限 | 本机入口仅转发 v2；云 IAM 与外层管理令牌留在进程，网页仅持账号会话 | 入口边界5项与HTTP/CAS权限测试通过 |
| ADM-003 | 资质审核只有JSON记录，无受控图片查看 | 新增证件附件读取，校验账号会话、读取权限、司机文件归属，记录审计 | 模块及HTTP测试通过；浏览器待复测 |
| ADM-004 | 新管理state未返回审批/定时/批次，配置关闭流程无法闭环 | 补齐三组既有持久化数据与操作页面 | 页面mock检查通过；云实际操作待复测 |
| ADM-005 | 2026-10-23首次云管理员登录401；Secret保留密码文件末尾换行，旧bootstrap哈希与登录规范化不一致 | 首建先规范化；既有初始admin仅在原Secret精确匹配旧哈希时迁移，保留已改密码和业务数据 | 新增首次带换行、精确安全迁移、不覆盖已改密码3项通过；根任务已确认v3发布后实际云登录成功，qa_operator/qa_viewer后续页面与AG HTTP登录结果可佐证；最终管理revision driver-admin-00005-pwl已读回 |
| ADM-006 | 共享后端新增WAIT_CONTINUE后，后台取消按钮与允许状态不一致 | 加入未出发待继续状态取消规则，中文说明权威状态与原司机投影的差异 | 取消/乘客同步/幂等回归记录通过；司机投影暂CANCELLED而canonical为WAIT_CONTINUE，已在界面详情及验收说明解释；0.3.4已实际UI验证WAIT_CONTINUE后选择不继续→CANCELLED；选择继续后重叫分支仍未测 |
| QA-001 | 反重力headless写证据权限自动拒绝，JSON虽SUCCESS但response为空 | 保留原输出，续接同一会话交互逐项授权 | 独立v3 HTTP最终JSON列出断言全部通过；保留原headless错误，不计独立UI通过 |
| QA-002 | 后台反重力会话没有内置浏览器操作工具 | 主任务实际浏览器操作留截图；反重力独立HTTP另记，它只检查文件属性，未读取像素或独立视觉审阅 | 独立UI实操无法执行，不能以主任务截图替代 |

业务规则及外部配置待确认项见 `SOURCE_FUNCTION_MATRIX.md`。模拟支付、退款、提现不涉及真实资金；未配置的外部服务不可标注已接通。

## 本轮待闭环项（2026-10-23更新）

| 编号 | 发现与影响 | 当前处理与待补证据 | 状态 |
|---|---|---|---|
| QA-003 | 首轮unknown action返回403，反重力误期望参数400 | 反重力自行改用有权限的support.reply缺requestId得到400，保留test_results-round1.json；最终test_results.json各断言通过 | 已独立复测通过；适用于v3 HTTP，不外推v7/UI |
| ADM-007 | 继续叫车将已退款移入refundHistory，旧统计仅取refundAmount会漏历史退款 | 已累计当前+历史各轮金额，台账/CSV保留currentRefundAmount、historicalRefundAmount及refundRecords；后台13项测试含多轮历史退款，修复随v7发布 | 已修复及单元复测通过；最终云单轮BID退款3600已实际UI完成；多轮refundHistory云UI尚未实测 |
| QA-004 | 初期主单UI证据仅到TRIP，支付完成未证实 | 已补WAIT_PAY/DONE、失败/处理中/成功、5星/投诉处理回读；PORD29d6c69f4de0三端2926分一致 | 主标准单实际UI闭环完成；新版司机发起NEG线下议价亦已实际UI完成，见QA-NEG-001；乘客发起BID完整UI不因此通过 |
| GAP-001 | 未找到完整原后台前端，仅Java接口/菜单种子及权益Web原型 | 已输出122个Controller、228菜单/按钮索引；新界面按适用源码和业务流程独立开发 | 已明确来源缺口；不是原界面完整复刻 |
| GAP-002 | 共享订单WAIT_CONTINUE与旧司机退出参与状态CANCELLED字面不同 | 保留兼容以免旧App枚举崩溃，后台说明canonical与参与状态；若验收要求阶段枚举字面相同，需要司机端更新 | 明确canonical与司机参与状态语义；BID取消→WAIT_CONTINUE→乘客结束已实际UI通过，重叫分支未测 |

## 后续发现与已验证修复

| 编号 | 问题 | 修复与实际复测 | 状态 |
|---|---|---|---|
| ADM-008 | 本机访问入口不可达时页面只显示英文网络失败，用户不清楚如何恢复 | 改中文提示“无法连接后台，请检查本机入口是否运行及网络连接后点击刷新重试；若刚提交过操作，请先核对结果”；实际失败截图cloud-gateway-failure-fixed.png，恢复截图cloud-gateway-recovered.png | 已修复并浏览器失败/恢复复测通过 |
| QA-005 | 司机自身投诉/反馈后台回复后，需要司机App实际回读确认 | CPbc6ee5b8df7f、FB1eee7f3b5c44后台回复及司机0.3.2实际刷新回读通过；本工程paired-app-evidence/codex-complaint-admin-reply-readback和codex-feedback-03-admin-reply-readback均有PNG/XML | 已通过 |

最终部署读取：driver-demo-00004-pnn、driver-admin-00005-pwl，同镜像b3cb3e2f61e4a724dd95b6dfac0d357d2bbd793b9e225402965b8fedb5ff0d3d。服务级automatic/max1，revision级max3保留、CPU throttling=true。管理IAM仍私有。共享135项及后台13项自动化通过，不代表所有矩阵行均完成UI验收。

## 外部服务与模拟功能边界

- 支付、全额退款、司机提现均为独立体验模拟：仅持久化状态与沙箱余额，不发生收款、银行打款或真实退钱。真实支付渠道本轮明确不接。
- 短信：白名单和体验验证码登录已实现，不发送短信。需短信服务账户、签名模板、授权与预算才可真实接通。
- 地图：坐标、司机位置及示意路线已有；地图底图和具体App表现需以对应端实测报告为准，坐标连线/体验估价不等于真实路径导航、计程计价。需地图服务授权Key、应用签名配置及真实路径接口。
- 推送/电话中继/实时人工客服：当前主要靠状态轮询，后台可处理已保存投诉反馈；无厂商推送、真实电话中继及即时客服通道。需供应商配置、授权与设备验证。
- 实名与证照：资料上传和人工审核记录可实现，不等于接通真实实名核验。需核验供应商账户与授权。
- 发票：模拟申请/记录以两端实际实现为准，未接真实税务开票；需开票主体、税务或供应商接口配置。
- 原组织权限范围、处罚扣分、行程中强制终止、会员退款转级折算等正式规则仍需确认。本轮采用明确受限的体验操作，不自动执行无依据的资金/处罚规则。

## 独立乘客测试与最新版本边界

| 编号 | 发现或执行事实 | 已实现/自动化 | 实际UI与独立复测结论 |
|---|---|---|---|
| PAX-IDEM-001 | 反重力静态发现不确定提交后遇401/403清未决键，会破坏重登重试身份；普通单次400/409不能直接证明重复扣款 | 1.1.2开发方已修为明确成功才清键、稳定passenger.id隔离，并有开发方单元/后端验证 | 后续p1-retest.md已完成生产组件源码+12/12独立单测（4项P1专项），逻辑/单测独立通过；未做真实HTTP/设备故障链注入，不能称UI故障链独立通过 |
| QA-PAX-001 | 乘客独立无终点UI首轮找不到实际按钮文案，次轮复选框状态未正确处理，误建带终点单PORDcecfc8298776 | 测试方法失败，不能直接当App功能缺陷；首版未执行的无条件通过脚本已拒绝 | FAIL/INCONCLUSIVE，误建单已实际取消清理；竞价、线下议价及历史评价投诉未独立执行 |
| QA-NEG-001 | 司机0.3.3+乘客1.1.2线下议价单NEG1bafda5d1f03需要真实配对闭环 | Codex实际UI完成V1 2800→乘客异议→V2 2600→FAILED恢复SUCCESS/TRIP→DONE；后台截图与7项云读回断言及logout通过 | 已实际UI通过；账本50248→52848恰+2600，FAILED未入账；App证据34/36/38—45见三端报告；不是BID竞价UI，也不宣称反重力v7/0.3.3独立通过 |

上述独立报告路径及证据来源见[INDEPENDENT_TEST_REPORT.md](INDEPENDENT_TEST_REPORT.md)。司机0.3.2独立投诉提交已通过，与Codex后续后台回复回读分开归属；后台无独立UI工具不代表司机那一会话也没有做真实UI。

## ADM-009 无终点订单误显示零元（已修复并云UI复测）

真实订单PORD599ce232e019的destinationPending=true，后台原列表/详情显示¥0.00，易被理解为免费。前端改为按明确标记显示“费用待定（目的地待补充）”，覆盖订单列表、详情、概览、乘客关联订单及模拟费用台账；合法0元无待定标记仍显示¥0.00。后端/导出金额未改变，详情补充口径。语法与7项定向检查通过；根已在真实云浏览器复测已取消的同单，详情与费用台账均正确，证据qa/three-party-no-destination-fixed.png及-ledger-fixed.png，原失败图保留为three-party-no-destination.png。没有为此重部署云端。

## APP-010 竞价预付后司机取消入口被隐藏（已修复及实际UI复测通过）

PORD5b52d7bea9c4已报价3600、乘客选择并模拟预付，PICKUP/paid=true；司机0.3.3的canCancel包含!paid而隐藏合法出发前取消入口。0.3.4/code7作最小修复，新8规则用例先在旧逻辑2fail，修复后31allpass；Codex独立静态审查及双模拟器实际UI复测完成。验证候选SHA256 `04c1ada0dafb4072a091003edfce45c71deccd5c5a838fd0d42a2cad1acb391c`，同签名覆盖安装保留原单，未清数据或用API替代取消。

23:07:20司机实际取消→WAIT_CONTINUE/REFUNDED3600；23:09:37乘客决定不继续→CANCELLED，paid=false/credited=false，退款时间仍23:07:20，司机余额始终52848分，无重复退款/入账。后台selected、paid-pickup、wait-continue、final-cancelled截图与乘客qa/final-extra-ui 19—27及最终JSON对应。该0.3.4哈希仅为取消退款业务回归历史候选，随后r2退款展示修复已实际UI通过并公开发布cdaa7147…05a152ae；AG司机独立范围仍0.3.2。

后台PASSENGER_CONTINUE标签调整为“乘客决定是否继续叫车”，不改变数据，避免将本单最终取消误述为继续。23:10历史快照28云断言通过：11单6done5cancel，paid13198/refund3600分，CSV同额。历史多轮refundHistory仍仅自动化覆盖。BID行程完成分支未在此UI轮执行。

## 司机0.3.4 r2退款展示P2已修复、实测并发布

历史04c1ada0…acb391c候选曾正确完成取消退款业务，但详情/费用页误显示“未支付”和收入36元；原缺陷及失败证据保留。r2已修正paymentStatus、退款金额与未计入收入显示，司机40项自动化通过。实际已公开发布司机0.3.4/code7 r2 SHA256 `cdaa71477675407d6fc57647cccd48da6e314ac791c3d4d2a145c80b05a152ae`，不是旧04c1候选。

旧单只读两页复测qa/final-display-ui/result.json为PASS；新单PORDfe4ce3005b17在乘客1.1.3+司机r2上再次实际UI完成，详情/费用页正确显示模拟退款36元、本单未计入收入0元。证据在乘客qa/final-release-ui/12-driver-034r2-refunded-detail.png、13-driver-034r2-refunded-fees.png、15-passenger-113-final-cancelled.png、16-passenger-113-final-refund-36.png、17-driver-034r2-final-refunded-detail.png及同名XML和final-cancelled-state.json。两端最终CANCELLED/REFUNDED3600，退款时间23:30:40不变，paid=false/credited=false，余额52848不变，DRIVER_CANCEL与PASSENGER_CONTINUE各1条。此为Codex实际UI验证，不扩展AG司机0.3.2独立范围。

## 乘客1.1.4中文可读性回归

乘客1.1.4/code6中文显示已实际UI回归：列表与详情显示中文订单类型和下单时间，“查看订单编号”可查看/复制完整编号；102账号用宜昌东站→夷陵广场创建PORD6b4bdcb702d8后于23:46:50取消，未付款且未分配司机。后台23:47:58浏览器核对13单6完成7取消0活单、已付¥131.98不变。qa/passenger-readable-114保留PNG/XML、复制核验和清理JSON；16项开发方单测及lint通过。该显示修正不改变后端业务，完整竞价退款证据仍按034r2+113记录，AG14项独立离线范围也仍为113。

## 23:41最新后台只读复核

qa/final-ui-order-readback.json于23:41:40核对最终新单PORDfe4ce3005b17，8项断言及退出全部通过；真实后台截图qa/final-bid-cancelled.png。该时点12单、6完成、6取消、0活动，paidFare=13198分（¥131.98）、累计退款7200分（两笔订单各3600分）。这是两单各一次退款，不是同单多轮refundHistory实测。23:10的11单/3600退款仅为历史快照。qa/final-release-ui已整体复制至本后台，含两端新包截图、XML、状态JSON及执行复核记录。
# UI版本补充限制（2026-10-24）

司机035首页“今日收入/接单/完成”仍读取既有演示累计计数，未按自然日重算。共享v7的seed.py初始化合成计数，domain.py在完单时累加，App直接展示；跨日不能将该首页数字当作按日经营报表。本轮已由司机负责人核对并纳入交付说明，冻结联测期间未修改APK或数据库。后台经营统计按canonical共享订单计算，其全量/筛选口径另行说明，不使用上述司机计数。该限制尚未修复，不记为“全部统计已完成”。

