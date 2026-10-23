# v8 后端冻结契约与本地验证

2026-10-24：本轮仅修改 `cloud-service/domain.py`、`passenger.py`、`negotiation.py`、`admin_console.py` 及测试。运行源码与 v7 本地部署副本比较，仅这四个文件有变化。精确 SHA-256 见同目录 `v8-backend-freeze.json`。没有部署、连接云端、操作设备或修改真实用户记录。

## 行程与欠款

- `WAIT_PAY` 保留原状态、金额、未付款和未入账标记，不占司机运输忙碌。私有合成单及共享订单的欠款均不阻止司机接新单、竞价被选或创建符合权益的线下议价单。
- 既有 ACTIVE 集合仍为 `BID_SENT/PREPAY/RESERVED/PICKUP/ARRIVED/TRIP`；不改变已报价未选时的其他接单规则。
- 同一乘客有未付 `WAIT_PAY` 时，STANDARD、RESERVED、BID 下单及 NEG 配对均返回 409，`details.reason=PASSENGER_OUTSTANDING_PAYMENT`。提示先支付欠款；退出/下线/重启不清欠款。
- 旧单晚付仍只入账旧单。新单状态不被覆盖；每笔订单最多入账一次。事务、幂等记录和云端快照 CAS 保持原机制。

## 金额确认

- 普通和 BID `order.pay` 新客户端发送 `{id,outcome,expectedFare,fareVersion}`，金额为整数分，版本为非负整数。每次请求须同时带两个确认字段；仅带一个或类型错误返回 400。金额或版本不等于当前订单返回 409，`details.reason=FARE_CONFIRMATION_STALE`，不改变支付状态或账本。
- 未曾调价且版本 0 的旧 115 客户端允许省略两个字段。存在 adjustments 或版本大于 0 时，省略返回 409，`details.reason=FARE_CONFIRMATION_REQUIRED`，提示更新应用或刷新重新确认。
- `fareVersion` 未存储时对外视为 0。后台每次成功调价递增一次，包括同额调价及金额 A→B→A；同 requestId 重试不重复递增。
- 后台 `order.adjustFare/order.cancel/order.refund` 同样接受 `expectedFare/fareVersion`，遵循相同校验和旧请求边界，原 `expectedStage` 校验保留。
- NEG 支付继续使用 `amount/quoteVersion` 原契约；模拟支付/退款性质不变。

## 司机动作与旧弹窗

- 业务司机动作须提供非空字符串 requestId，最长 120 字符。重复相同 requestId + 相同 payload 返回原结果；复用为不同 payload 返回 409。内部 sandbox actor 保留原调用方式；旧会员专用路由仍使用其原 idempotencyKey 机制，不在本轮改写。
- 私有及共享 `order.advance` 可选 `expectedStage`。存在时必须严格等于当前阶段，否则 409，`details.reason=ORDER_STAGE_STALE`。支持 RESERVED、PICKUP、ARRIVED、TRIP 的正常推进；旧 035 缺字段继续兼容。
- 已成功请求的相同 requestId 重放先于阶段校验返回原结果，不能再次推进。同单旧 ARRIVED 确认使用新 requestId 到达 TRIP 时必须拒绝，不能被解释为送达；旧订单请求不修改新订单。

## 资质与投诉隐私

- 新接单和竞价被选保持整体 `certificationStatus=APPROVED` 校验，NEG 新建及首次认领同样复核。认领时还复核原司机在线、权益和其他活动订单。
- 已存证件 `validFrom/validUntil` 为严格 `YYYY-MM-DD` 且可解析为日历日期时，按 `common.now()` 已有 UTC+8 当天判断；validFrom 晚于当天或 validUntil 早于当天返回 403。到期日当天包含在有效期内。
- 不推断缺失证件的必需性。缺失日期、LONG_TERM、历史不识别格式或非法日历日期不据此新增拒绝条件；不任意解析这些字符串。既有行程推进、WAIT_PAY 付款和下线不受日期门禁影响。
- 司机共享订单投影和旧幂等缓存响应均移除乘客 complaints（含正文、回复、附件 ID）；司机自己的 `profile.complaints` 与独立 `result.complaint` 保留可读。

## 验证边界

最终本地全量 159 项通过（52.517 秒）；最后受影响专项 16 项通过。测试使用临时 SQLite、localhost HTTP、MemoryCAS，不连接 Google Cloud 或原环境。新增关键类共 24 项：WaitPayPolicyTests 9、FareConfirmationTests 6、DriverProjectionPrivacyTests 2、AdvanceStageTests 3、CertificateDateTests 4。

专项覆盖私有欠款后新共享接单/报价/选司机、共享欠款后新私有/共享/NEG、多人欠款加一个活动单、并发抢单、跨实例 CAS、旧单支付不确定提交后重试与接单交错、付款及退款单次账本、金额 ABA、投诉隔离、旧弹窗、日期边界。Antigravity 独立复测及发布后的体验验证由主任务安排；此文不声称它们已完成。
