# 乘客外围功能 API

沿用 API_CONTRACT.md 的乘客身份、统一 action、requestId 幂等和完整 state 返回结构。金额均为整数分。以下字段由独立 passenger_extras.py 持久化，不与司机演示账号或发票表混用。

## state 字段

- `riders`: `[{id,name,mobile,createdAt,updatedAt}]`，最多 20 条，本人账号下的乘车人。
- `invoiceTitles`: `[{id,type:"PERSONAL"|"COMPANY",title,taxNumber,email,createdAt,updatedAt}]`。
- `invoices`: `[{id,orderId,titleId,title,titleSnapshot,amount,status:"SIMULATED_ISSUED",simulated:true,notice,createdAt}]`。只是明确模拟的回执，不是税务发票，不能报销。无开票平台或邮件实际发送。
- `support`: `{phone:null,phoneConfigured:false,notice,notices:[{id,title,content}],capabilities:[{key,label,status,notice}]}`。外部未配置能力必须显示说明。

## POST /api/passenger/action

| action | payload | 结果 result |
|---|---|---|
| rider.save | `{id?,name,mobile}` | `{rider}`；id存在则修改，否则新增；手机号11位 |
| rider.remove | `{id}` | `{removed:id}` |
| invoice.title.save | `{id?,type,title,taxNumber?,email?}` | `{invoiceTitle}`；企业税号15/18/20位字母数字，邮箱可空 |
| invoice.title.remove | `{id}` | `{removed:id}` |
| invoice.apply | `{orderId,titleId}` | `{invoice}`；仅本人DONE且paid订单；一单一回执，同抬头重复返回原结果 |

下单 `order.create` 可选 `riderId`。后端调用 `passenger_rider(pid,riderId)` 验证本人乘车人并快照姓名、电话；订单核验手机号尾号应取实际乘车人手机。历史订单不得因修改或删除乘车人而变化。司机仅按原契约显示必要联系信息。

所有删除/修改按当前乘客身份查找，别人的记录视为不存在。发票保留抬头快照，删除抬头不会删掉历史回执。

## 订单补充能力（order_options.py）

- `state.blockedDrivers`: `[{driverId,name,plate,sourceOrderId,reason,createdAt}]`，只有乘客自己可见，最多100位。
- `order.blockDriver`: `{id:订单ID,reason?}`。仅本人已结束（DONE/WAIT_PAY/CANCELLED）且有分配司机的订单可屏蔽。屏蔽阻止其接后续新订单；不影响已分配行程查询。
- `order.unblockDriver`: `{driverId}`。仅解除当前乘客已有屏蔽。
- `order.remark`: `{id,remark}`。AVAILABLE/PREPAY/RESERVED/PICKUP/ARRIVED/WAIT_CONTINUE 阶段可改。
- `order.complaint.attach`: `{id,complaintId,imagesBase64:[...]}`。先有本人订单的投诉记录；每投诉最多3张PNG/JPEG，每张512KB，不接受外部URL。返回 `result.attachments:[{id,mime,size,path}]`；订单投诉中 `attachments` 持久保存。
- `GET /api/passenger/files/{id}`：相同乘客鉴权+演示访问码；只返回该乘客的投诉附件内容。其他乘客、司机 token 无法读取。
- BID 订单 `expiresAt` 到期时服务端自动取消，仅限仍为 AVAILABLE；接单、已选司机、已支付或其他状态不自动取消。10分钟为独立体验策略，不能说成原区域生产配置。没有定时常驻服务时，在读写请求到达时计算到期事件，符合随用随停要求。

单元验证涵盖：本人与他人乘车人/抬头/发票/附件/屏蔽记录隔离、重启持久化、重复开票唯一、非法图片整批拒绝且不留部分文件、图片数量限制、超时状态边界和幂等、已完单备注拒绝。通过单元测试不代替双模拟器UI验收。
