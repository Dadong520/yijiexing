# ADMINAPI-20261025-01 查询契约 v1

负责人 backend；API 工作树 `/Users/dongge/.codex/tmp/yijiexing-api-20261025`；起始 HEAD `4b61f3d3cfbe363934e448907cecb8a8b644475a`，任务基线 `e600e6bd15d894afe9fbd85a7b1f57cf3cb4e842`。仅新增只读投影/查询/同条件导出，保留既有 state/action、IAM/IAP、令牌和管理会话。

## 路径与通用规则

`GET /api/local/admin/v2/query?dataset=orders`；沿用 X-Admin-Token + X-Admin-Session。响应 data 内：`{dataset,items,total,page,pageSize,options,schema,summary,definition}`。`total/summary` 均基于全部筛选结果，items 才分页。缺字段输出 null/空，不伪造。所有金额整数分，所有交易为体验模拟。

共同参数：`dataset`（默认 orders）、`search`（忽略大小写包含，最多100字）、`startDate/endDate`（YYYY-MM-DD，用户选择的首尾日均包含；内部转换 UTC+8 首日00:00 <= t < 末日次日00:00）、`page`（默认1，1..1000000）、`pageSize`（默认20，1..100）。单侧日期可用；首日不得晚于末日。拒绝重复、未知参数、无效枚举/日期/分页；空字符串视为未筛选。文本专用字段为忽略大小写包含，枚举/ID/city 精确匹配。缺少可解析时间的旧记录，在启用日期条件时不匹配，并在 summary.missingDateCount 报告。排序为时间倒序、唯一投影ID升序；无登记时间的账号按ID升序。

`GET /api/local/admin/v2/export` 接受相同 dataset 和筛选参数，导出全部匹配记录，忽略 page/pageSize 的切片但仍校验参数。无筛选保持既有全量导出兼容。需要 export + 对应 read 权限；VIEWER 禁止导出。CSV 防公式注入。schema/options 和 join 字段不能绕过当前权限。

## 数据集和准确参数名

| dataset | 读权限 | 专用参数 | 时间字段/行语义 |
|---|---|---|---|
| orders | orders.read | kind, fareSource, passengerMobile, driverId, driverName, driverMobile, stage, cancellationType, plate, orderId, city；billingMode/company/assigned 保留为 NOT_IMPLEMENTED | createdAt；一条共享订单 |
| drivers | drivers.read | driverId,mobile,city,currentLevel,certificateStatus,vehicleType,plate | 无账号登记时间；日期筛选 NOT_IMPLEMENTED；一条实际持久账号 |
| passengers | passengers.read | passengerId,mobile,city | 无账号登记时间；日期筛选 NOT_IMPLEMENTED；一条实际持久账号 |
| payments | payments.read | recordType(ORDER/WITHDRAWAL),paymentStatus,refundStatus(NONE/HAS_REFUND),withdrawalStatus,orderId,accountId | ORDER用 paidAt 或 createdAt；WITHDRAWAL用createdAt；订单支付状态投影与实际持久提现记录，非银行流水 |
| support | support.read | source,status,orderId,accountId | createdAt；已有评价/投诉/反馈，source=ORDER_COMPLAINT/EVALUATION/PASSENGER_FEEDBACK/DRIVER_FEEDBACK/DRIVER_COMPLAINT |
| audit | audit.read | actor,action | created；全部持久审计，不限旧state最近200条 |
| statistics | statistics.read | 与orders相同的专用参数 | createdAt；按UTC+8订单创建日聚合，每日一行，无日期订单一组UNKNOWN；summary为所有匹配订单 |
| cancellations | orders.read | 与orders相同的专用参数 | cancelledAt=最后取消事件at，旧记录无事件不捏造；仅当前CANCELLED或WAIT_CONTINUE，一订单一行，不是历次取消事件流水 |
| refunds | payments.read | orderId,accountId,driverId,refundSource(HISTORY/CURRENT) | refundedAt；历史每轮+当前各一行，不按金额或时间去重 |

枚举以响应 options 为准；固定订单类型 STANDARD/RESERVED/BID/NEGOTIATED，订单状态 AVAILABLE/PREPAY/RESERVED/PICKUP/ARRIVED/TRIP/WAIT_PAY/DONE/CANCELLED/WAIT_CONTINUE；cancellationType=PASSENGER/DRIVER/ADMIN。支付与提现状态保留真实持久值并由 options 提供，不沿用 Java 数字枚举。`certificateStatus` 对应 profile.driver.certificationStatus；证件详情保留 certificates。

items 保留旧 state 同名数据集原字段，额外投影：orders 包含 passengerMobile/passengerName/driverMobile/driverName/plate/vehicleType/cancellationType/cancelledAt；drivers 包含 plate/vehicleType/certificateStatus；payments 包含 id/recordType/accountId/createdAt/amount/status/refundStatus；support 增加唯一 rowId/accountId；refunds 包含 id/orderId/accountId/driverId/amount/refundedAt/refundSource/roundIndex/paymentMode；cancellations 沿用订单字段，另 cancellationStatus=CLOSED/AWAITING_PASSENGER；statistics 含 id/date/orderCount/completedCount/cancelledCount/activeCount/paidFare/refundAmount。

schema 为 `[{name,label,type,availability,reason?}]`，availability=AVAILABLE/NOT_IMPLEMENTED；options 为 `{字段:[{value,label}]}`。company/assigned/billingMode 及账号日期条件返回 NOT_IMPLEMENTED 原因；传非空这些条件返回400，不能静默忽略。原有演示 organization 仅是账号资料，不能当成真实公司维度。跨账号关联使用持久 accounts/passengers，以实际账号手机号为准，不从掩码推测；相关 read 权限不足则隐藏关联手机号/姓名，相关筛选403。

summary 始终有 rowCount/missingDateCount。订单/取消/统计另含 orderCount/completedCount/cancelledCount/activeCount/paidFare/refundAmount。paidFare 为匹配订单中DONE且仍paid的fare之和，退款为各轮真实记录之和；按订单创建日归组，是订单队列口径而非收款清算。refunds 为 refundAmount/refundCount；payments 分开 orderCount/withdrawalCount/orderFare/refundAmount/withdrawalAmount，不把不同交易类型相加当收入。

## 原Java核对与缺口

已读本地 `hsdd-ride/hsdd-ride-api/.../req/HsOrderReq.java`、`entity/HsOrderEntity.java`、`req/HsLedgerQueryRequest.java` 与 `hsdd-ride-biz/src/main/resources/mapper/HsOrderMapper.xml`。原订单13项中10项提供适用查询：创建时间/kind/乘客账户电话/司机/司机电话/stage/取消参与方/plate/id/city，其中订单类型、取消类型、乘客电话存在口径差异，不能称原版完整复刻。原contactNumber是订单联系人，体验只持久保存代叫号码掩码，所以passengerMobile仅筛选账号手机。原 billingMode=线下1线上2、companyId/companyName、dispatched=0/1 在体验共享订单无权威字段，明确 NOT_IMPLEMENTED；原超时/有责取消与电话叫车、代叫出租车专门类型无完整对应，不能伪映射。原清分的driverAmount/taxiCompanyAmount/platformAmount/entryStatus无体验账务实现，不新增虚构分账报表。

尚需：实现、自测、frontend非作者复核、coordinator集成；当前仅契约，不等于业务验收。禁止访问原线上环境；本任务不发布、不启停云服务。
