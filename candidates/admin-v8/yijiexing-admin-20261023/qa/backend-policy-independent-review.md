# WAIT_PAY/费用确认/司机投诉边界：独立后端审查

结论：对`backend-policy-reviewed-manifest.json`绑定的共享源码，**未发现本轮已实现范围的明确阻断；独立复用29项专项回归及新增6项探针全部通过**。最终一轮专项回归前后对关键源码和测试计算SHA256，未发生变化。

这不是最终发布验收：证件日期门禁、进一步expectedStage等后续owner改动尚未包含在本结论；未来变更须单独审查并重跑受影响用例。所有验证均在临时SQLite、本机HTTP及MemoryCAS内运行，未写云端、未操作设备、未触碰真实001账户或私有YC1001数据；用例里的YC1001属于临时fixture。

## 执行证据

- 复用共享负责人专项：`test_wait_pay_policy`、`test_fare_confirmation`、`test_driver_projection_privacy`、`test_negotiation`，独立执行29/29通过；完整日志`backend-policy-owner-tests-independent.log`。
- 新增独立脚本：`test_backend_policy_independent.py`，6/6通过；日志`backend-policy-independent.log`。
- 源码/测试哈希与稳定性：`backend-policy-reviewed-manifest.json`；不能把后续不同哈希算作已测试。
- 没有修改共享源码或原测试。初次新增探针因owner同期给管理操作新增金额确认而需要更新本地探针payload，调整后通过；这是测试适配新的约定，不是修改被测实现使测试通过。

## 已验证边界

| 范围 | 结果及证据 |
|---|---|
| 运输占用与欠费分离 | 共享/私有WAIT_PAY不占司机，可接新标准/预约/竞价或NEG；欠费乘客新叫车和认领NEG均被拒绝。原订单保持WAIT_PAY、未支付、未入账，不靠取消或完成旧单解锁 |
| 跨单晚付款 | 新单已经TRIP时支付旧WAIT_PAY，原新单完整payload保持不变；旧单金额只入司机余额一次。包含不确定上传503后同requestId恢复、不同requestId重复付款和重启读回 |
| 并发接单 | 同实例两个接单请求只能一个成功；跨实例同快照CAS一个200、一个503；保留一个实际运输占用，不覆盖私有欠费记录 |
| 不同司机requestId隔离 | 两司机使用相同requestId操作同一共享订单，不会得到对方缓存结果；无归属司机被404拒绝。拒绝前后accounts/passengers/passenger_orders/actions/passenger_actions/audits各表逐行一致 |
| 投诉投影 | 司机常规state、动作结果及修复前已存幂等响应中的乘客complaints/passengerId/bids过滤；乘客投诉正文、附件、后台回复不会给司机。司机自己投诉记录及自己的后台回复保留；canonical乘客投诉不删除 |
| 旧115兼容 | 未调价订单缺expectedFare/fareVersion的旧付款payload仍可完成；已经调价订单缺确认被409拒绝。金额A→B→A仍增加fareVersion，旧版本0即使金额一致也409；正确版本成功且只入账一次 |
| 管理调价/付款竞争 | 新增真实双实例同快照pay vs adjust探针：一个成功一个CAS503；败方带原金额/版本重试409。付款胜出时禁止晚到调价；调价胜出时要求用户以新版本重新确认，最终收入恰一次 |
| 退款不被旧支付复活 | 完成后模拟退款恰扣回本单金额；再次退款和新requestId付款均拒绝且无业务写入。重放已成功付款的旧requestId允许历史result，但响应最新state/canonical仍REFUNDED，不二次入账。消费方应以最新state为准 |
| NEG资格与有效期 | 新建/认领时重新核APPROVED、在线、权益；新增探针验证邀约生成后体验期到期，认领403、offer仍ACTIVE且相关业务表不变。既有专项含邀约过期/撤销/重放归属。证件validFrom/validUntil日期逐项门禁尚属owner准备补的范围，本轮不宣称通过 |
| 危险动作请求ID | 缺失/空/布尔requestId拒绝，不推进私有行程；同key重复advance只推进一次。原接口兼容测试已按新要求提供合法requestId |

## 冻结后最小追加

1. 如果新增证件validFrom/validUntil门禁：分别测未来生效、已过期、合法当前；缺少日期按owner约定，不推定必需证件。测试旧行程推进和旧单晚付不因新资质门禁被阻断。
2. 如果新增expectedStage：未变化阶段可提交；旧页面阶段过期拒绝且无写入；缺字段的旧115按明确兼容约定测试，不能静默改成另一个阶段/金额。
3. 冻结后重新计算源哈希，只重跑新增门禁及本次金额/晚付受影响测试；不重复无关城市/会员配置/导出广测。

独立审查不替代最终APK同步、双设备实际接单、支付和后台回读，也不授权提前部署。
