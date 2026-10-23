# 管理台 v2 契约（独立体验环境）

基础路径 `/api/local/admin/v2/`，所有响应使用既有 `{code:0,data:...}`；失败 `{code,message}` + 400/401/403/409/503。所有请求保留 IAM + `X-Admin-Token`，登录后增加 `X-Admin-Session`。本机代理可服务端注入外层令牌，浏览器不可内置令牌。不得将 v2 加到公开 driver 服务。金额均为整数分。

- POST login `{username,password}` => `{session,user:{username,role,permissions}}`。首次部署服务端环境 `ADMIN_BOOTSTRAP_PASSWORD`（至少12字符）创建 `admin`，无配置时不创建。
- POST logout `{}` => `{loggedOut:true}`。
- GET state => `{user,roles,users,orders,drivers,passengers,payments,support,configs,cities,statistics,audit,policies,serverTime,sandbox:true}`。权限限制字段返回空数组，统计无权限返回null。
- POST action `{action,payload,requestId}` => `{result,state}`。requestId 1–120字符，同一账号同一请求ID复用返回原结果；内容变化409。重试必须使用同一 requestId。
- GET export?dataset=orders|drivers|passengers|payments|support|audit => UTF8 BOM CSV，权限 `export`；同时要求对应数据读取权限。

固定角色 `SUPER_ADMIN` 全部；`OPERATOR` 业务读取、订单取消、资质审核、支持处理、导出；`FINANCE` 订单/司机/乘客/费用/统计/审计读取、模拟退款、费用调整、提现审核、导出；`VIEWER` 业务只读；`CONFIG_ADMIN` 业务读取、会员配置、导出。账号仅 SUPER_ADMIN 可管理。用户条目 `{username,role,enabled,createdAt}` 不返回密码哈希。

订单直接使用 passenger_orders.payload（id,passengerId,driverId,stage,fare,estimatedFare,paid,paymentStatus,bids,events,evaluation,complaints,createdAt,updatedAt）。司机含既有admin_state字段及 `profile` 中司机车辆资料；乘客为真实 passengers.profile。费用每共享订单一行 `{id,orderId,passengerId,driverId,fare,paymentStatus,paid,paidAt,refundAmount,refundedAt,paymentMode}`，是当前状态台账，不冒充支付渠道流水。support 合并订单投诉、评价、乘客反馈、司机反馈/投诉，含 source, ownerId, orderId。

## 动作
- `order.cancel` `{id,expectedStage,reason,confirmed:true}`，仅 AVAILABLE/WAIT_CONTINUE/PREPAY/RESERVED/PICKUP/ARRIVED；已付预付金额自动模拟全退。
- `order.adjustFare` `{id,expectedStage:'WAIT_PAY',amount,reason,confirmed:true}`，仅未支付WAIT_PAY，写adjustments与事件。
- `order.refund` `{id,expectedStage:'DONE',reason,confirmed:true}`，仅完成且已付、未退款；模拟全退，已入司机余额时要求余额足够并同步扣回，保留订单原金额，新增refundAmount。
- `support.reply` `{source:'ORDER_COMPLAINT'|'PASSENGER_FEEDBACK'|'DRIVER_FEEDBACK'|'DRIVER_COMPLAINT',ownerId,id,reply}`；ORDER_COMPLAINT 的 ownerId 是订单ID，其他是账户ID。
- `certificate.audit` `{driverId,type,approved:boolean,reason}` 调既有审核规则。
- `wallet.withdrawal.audit` `{driverId,id,approved:boolean,reason,confirmed:true}` 调既有模拟提现规则。
- `user.save` `{username,role,enabled:boolean,password?}` 新用户password必填至少12字符，已有可省略。禁止停用/降权自己，禁止删除最后超级管理员；修改密码/角色/状态清除该用户旧会话。
- 会员配置透传 `config.draft/config.publish/config.restore/config.copy/batch.preview/batch.execute/approval.decide/schedule.cancel` 使用原接口payload规则，服务端强制权限，actor 从会话获取。

统计口径：共享订单全量，排除原司机私有演示订单；订单数/完成数/取消数/在途数；收入为 DONE 且paid的fare合计；退款额合计；司机/乘客为真实表行数。截止serverTime，不宣称与真实账务一致。当前无原平台资费、税务、仲裁、真实退款规则，模拟人工操作仅使用以上体验限制。

## 资质附件预览
GET `certificate-file?driverId=...&type=license|insurance|qualification&side=front|back`，会话需drivers.read，返回 `{fileId,mimeType,imageBase64}`（普通JSON envelope）。默认正面；缺少附件404。服务端只允许当前证件所引用且归属该司机的PNG/JPEG，禁止任意URL或fileId。浏览器转换data URL预览并给出未上传提示。查看操作写入审计。

state另返回 `approvals`、`scheduled`、`batches` 原生会员管理任务数组，均受config.read保护；审批使用approval.decide，取消未执行定时任务使用schedule.cancel。

## 继续叫车的退款口径修订
共享订单 `refundHistory` 为 `{amount,at,driverId,mode}` 数组。乘客继续叫车时，服务端把当前 `refundAmount` 移入历史并删除当前字段，因此两集合不重叠。`statistics.refundAmount` 和 `payments[].refundAmount` 均为当前与历史合计（分）；原始 `orders[].refundAmount` 仍是当前轮退款，不改订单业务字段。

费用台账新增 `currentRefundAmount`、`historicalRefundAmount`、`refundHistory`、`refundRecords`。refundRecords 合并所有退款，每条提供 `source: CURRENT|HISTORY` 和从零开始的 `roundIndex`，保留金额、时间、司机及模拟标志。相同金额和时间可能属于不同轮次，不按金额或时间去重。CSV同步导出累计、当前、历史及记录，避免历史退款漏计。

## v7线下议价读取兼容

乘客认领后的NEGOTIATED订单进入同一passenger_orders；后台按相同ID读取quoteVersion、negotiationStatus、quoteHistory和priceDisputes，不另造订单。quoteHistory包含version/amount/createdAt/actor；priceDisputes包含quoteVersion/content/createdAt。报价、异议及重新报价事件同属订单events。尚未被乘客认领的司机私有邀约不计入共享订单统计；后台页面不显示配对码。金额仍为整数分，最终支付以共享后端的报价版本及金额校验为准。
