# 线下议价配对 API v1（供下一版司机 App）

当前已发布司机 0.3.2 不显示配对码，因此不能宣称该 APK 已完成本流程。新接口复用现有独立 Google Cloud 体验服务、X-Demo-Access、角色登录、POST /api/action 与 /api/passenger/action。所有操作使用独立 requestId，网络重试复用该值。金额为整数分，支付仍为明确标注的模拟支付。

## 司机生成一次配对码

司机先通过已有 `order.negotiate` 创建本人私有 NEGOTIATED/PREPAY 未支付订单（旧行为不变），再调用：

`POST /api/action {action:"order.pairOffer",payload:{id},requestId}`

成功 result：`{orderId,pairCode,pairUri,expiresAt,validitySeconds:300,paymentMode:"SIMULATED"}`。

- pairCode 为至少 192 位随机熵的一次码，pairUri 形式 `yijiexing://negotiation?code=<pairCode>`。二维码直接编码 pairUri；可复制配对码给现场乘客。
- 仅本人未支付、未取消、尚未共享的线下议价订单可生成；有效期 5 分钟。相同 requestId 重试返回同码；同单新 requestId 重新生成将撤销旧码。
- 配对码只展示给该司机；不把访问码、会话令牌或订单完整资料放入二维码。不支持通过猜测 orderId 认领。
- 一旦生成配对码，该私有单进入真实乘客配对流程，旧沙箱支付/选择不再能替代乘客；不调用 pairOffer 的旧演示单行为保持不变。

## 乘客扫码/输入认领

`POST /api/passenger/action {action:"negotiation.claim",payload:{pairCode},requestId}`

成功 result：`{order}`；返回的顶层 state.orders 立即包含相同订单 id。order.id 沿用司机原 NEG... 编号，`shared:true`，`kind:"NEGOTIATED"`，`stage:"PREPAY"`，`negotiationStatus:"OFFERED"`，`quoteVersion:1`，`fare` 为司机明示报价。司机原私有记录升级为同一 canonical 共享订单，不再保留重复私有订单。

- 必须为已授权登录乘客；本人有其他未结束订单时不能认领。
- 码必须未过期、未撤销、未认领；并发认领只有一位乘客成功，另一位返回 409。跨实例继续受 GCS CAS 保护，可出现 503 后重试。
- 同一乘客重试同一有效已认领码返回本人既有订单；其他乘客使用该码始终拒绝。猜测 orderId 或访问其他乘客订单不能认领。
- 司机端和乘客端使用原 state/action 路由同步该单；不用沙箱触发。

## 价格异议与重新报价

乘客可在未付款 PREPAY 阶段提出异议：

`{action:"negotiation.dispute",payload:{id,quoteVersion,content},requestId}`

content 2—500 字；quoteVersion 必须等于当前版本。订单仍为 PREPAY，`negotiationStatus:"DISPUTED"`，`priceDisputes` 留存版本/内容/时间。此时拒绝付款；乘客可取消。

原司机重新报价：

`POST /api/action {action:"order.requote",payload:{id,quoteVersion,amount},requestId}`

仅原司机、NEGOTIATED、PREPAY、未付款可操作。quoteVersion 必须等于当前版本，amount 为正整数分。成功更新 fare，quoteVersion 增加 1，negotiationStatus 回到 OFFERED，quoteHistory 留存。相同 requestId 重试不重复增加版本；旧版本返回 409。

## 乘客确认报价并模拟支付

复用乘客 `order.pay`，但线下议价必须显式提供刚展示的版本和金额：

`{action:"order.pay",payload:{id,quoteVersion,amount,outcome:"SUCCESS"},requestId}`

仅 OFFERED 且版本、金额与服务端一致时允许。FAILED/PENDING/CANCEL 也需要版本金额，均不启动行程；SUCCESS 后原子设置 paid=true、negotiationStatus=ACCEPTED、fareConfirmed=true，直接进入 TRIP（线下双方已见面）。支付成功后的重复 SUCCESS 按同一版本金额幂等，不重复入账；不得用新金额默默覆盖乘客已确认价格。

司机从 TRIP 继续使用原 `order.waypoint/order.advance` 完成订单，已预付则 DONE 并只入账一次；评价、投诉及模拟回执复用共享订单功能。

## 取消与限制

- 认领前司机可按原 `order.cancel` 取消私有订单，配对码立即不可认领。
- 认领后任一端在 PREPAY 可取消，最终 CANCELLED，不进入平台大厅续叫（线下已配对订单不应被其他司机抢走）。
- TRIP 后按当前共享订单状态机不可直接取消；已完成后不可改报价。
- 不上传或保存配对码截图到公开验收资料；配对 URI 和码均应视作短期邀请凭证。截图应在认领后或隐藏码后进行。
