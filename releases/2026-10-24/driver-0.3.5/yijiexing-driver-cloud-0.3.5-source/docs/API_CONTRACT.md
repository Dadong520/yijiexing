# 宜捷行乘客/司机共享订单 API v1

同一独立云体验服务；沿用司机 HTTPS origin 和 X-Demo-Access 访问码。所有响应 `{code:0,data:...}`；失败 HTTP 400/401/403/404/409/503 + `{code,message,details}`。金额整数分，ISO8601 含时区。所有 passenger 路由只接受 passenger token，司机 token 不互通。

## 登录与状态

- POST `/api/passenger/auth/code` `{mobile:"13800000101"}` → `{verificationCode,expirySeconds:300,sandbox:true,message}`，未发送真实短信。
- POST `/api/passenger/auth/login` `{mobile,code}` → 下述完整 state 响应 + `token`。
- GET `/api/passenger/state`（Bearer） → `{passenger:{id,mobile,name,city,cityName},orders:[Order],favorites:[],feedback:[],notices:[],features:{...},environment:{...},serverTime}`。orders 降序包含活动和历史订单。轮询建议 5 秒，后台停止。
- POST `/api/passenger/auth/logout`（Bearer） `{}` → `{loggedOut:true}`。
- GET `/api/passenger/orders/{id}/route` → `{orderId,coordinates:[[lng,lat],...],source:"synthetic",notice}`。
- GET `/api/passenger/orders/{id}/fees` → `{orderId,fare,paymentFee:0,serviceFee:0,paid,paymentStatus,sandbox:true,policyLabel}`。

## 操作

POST `/api/passenger/action`（Bearer）统一 `{action,payload,requestId}`，requestId 每次新操作随机 UUID，失败重试复用同一值。响应完整 state + `result`（主要返回 `{order}`）。

- `order.create`: `{kind:"STANDARD"|"RESERVED"|"BID",origin,destination,originLat,originLng,destinationLat,destinationLng,city:"420500",remark?,scheduledAt?,waypoints?:[{name,latitude,longitude}]}`。RESERVED 必须 scheduledAt，未来 15 分钟至 30 天；waypoints 最多 2 个。坐标有效且起终点不同。费用由服务端生成并标注体验估价；BID 由司机报价，选定后锁定。每乘客同时最多一个未结束订单（包括 WAIT_PAY）。返回 `{order}`。
- `order.selectDriver`: `{id,driverId}`，只适用 BID 且有此司机报价；进入 PREPAY。
- `order.pay`: `{id,outcome:"SUCCESS"|"FAILED"|"PENDING"|"CANCEL"}`，仅 PREPAY/WAIT_PAY，模拟支付须在 UI 明确标注。已支付成功重复请求不会重复入账。
- `order.cancel`: `{id,reason}`，出发前可取消；已模拟预付订单会记录模拟退款、`paymentStatus:"REFUNDED"`，行程中/已结束不可取消。
- `order.evaluate`: `{id,rating:1..5,content?}`，DONE 后每单仅一次；同内容重复幂等。
- `order.complaint`: `{id,reason,content}`，DONE/CANCELLED/WAIT_PAY 后允许，重复同内容返回原记录，状态 PENDING（后台可查看和回复；实时人工客服未配置）。
- `profile.update`: `{name?,city?}`，名字 2..30 字，活动订单期间不能切换城市。
- `favorite.save`: `{id?,label,address,latitude,longitude}`，最多 20 条。`favorite.remove`: `{id}`。
- `feedback.submit`: `{content}`，5..2000 字，持久化 PENDING。

## Order 字段

`{id,kind,origin,destination,originLat,originLng,destinationLat,destinationLng,coordinateSystem:"WGS84",stage,city,fare,estimatedFare,distance,mileage,time,remark,phoneTail,paid,paymentStatus,createdAt,updatedAt,scheduledAt?,waypoints:[],arrivedWaypoints:[],bids:[{driverId,name,plate,vehicleModel,amount,createdAt}],driver:null|{id,name,plate,vehicleModel,mobileMasked,location:null|{latitude,longitude,receivedAt}},evaluation:null|{rating,content,createdAt},complaints:[],events:[{type,at}],shared:true,paymentMode:"SIMULATED",fareSource:"DEMO_ESTIMATE"}`。

状态：STANDARD AVAILABLE→司机接单 PICKUP→ARRIVED→尾号核验 TRIP→WAIT_PAY→乘客模拟支付 DONE。
RESERVED AVAILABLE→司机接单 RESERVED→PICKUP→ARRIVED→TRIP→WAIT_PAY→DONE。
BID AVAILABLE（多个司机报价）→乘客选司机 PREPAY→乘客模拟支付 PICKUP→ARRIVED→TRIP→DONE。
取消到 CANCELLED；报价阶段乘客仍显示 AVAILABLE + bids，司机按自身报价显示 BID_SENT。所有司机继续用原 `/api/action` 的 order.accept/bid/advance/waypoint/cancel；报价 amount 整数分；advance 开始行程用 phoneTail=乘客手机号末 4 位。

## 外部依赖

乘客手机白名单：13800000101、13800000102、13800000103。短信验证码回显；支付及退款模拟；地图路线为坐标连线示意，不是真实导航；后台可查看和回复已保存投诉；实时人工客服、真实呼叫/电话中继、推送和真实发票开具未配置。新订单为用户实际创建并由双端真实共享持久化，不经沙箱事件触发。

## 补充：订单信息修改

`order.update` `{id,remark?,destination?,destinationLat?,destinationLng?}`。备注在 AVAILABLE/PREPAY/RESERVED/PICKUP/ARRIVED 可改；目的地仅 AVAILABLE 且尚无司机报价时可改，名称与坐标必须齐全，服务端重新计算体验估价。已有报价/接单后改变目的地必须取消重新下单，以免静默改变双方约定费用。可“再次叫车”由 App 复用历史订单地点并发送新的 order.create，必须新 requestId。

## 无终点即时订单与司机取消续叫

- `order.create` 额外支持 `destinationPending:true`，仅 STANDARD，省略 destination/destinationLat/destinationLng，不支持同时添加途经点。此时可真实下单并由司机接驾，`fare=0`/`estimatedFare=0` **表示费用未确定，不是免费**，`fareSource:PENDING_DESTINATION`。App 必须显示“目的地待补充·费用待定”。
- 乘客在 AVAILABLE/PICKUP/ARRIVED 使用 `order.update` 补充目的地名称及坐标；服务器计算并同步体验估价。司机必须在开始行程前等待补齐；司机输入尾号开始行程时确认此体验报价 (`fareConfirmed:true`)。BID/RESERVED 不支持无终点。
- 司机在接驾前取消后，订单进入 `WAIT_CONTINUE`，若预付则先模拟退款。乘客 `order.continue {id,type:1}` 清空原司机和报价，同一单重新呼叫；`type:2` 放弃继续并取消。`order.cancel` 也允许取消 WAIT_CONTINUE。
- BID 订单 `expiresAt` 默认创建后10分钟（独立体验策略）；超时未选择司机则取消并同步两端。
- `historicalLocations:[{address,latitude,longitude}]` 与 `regions:[{id,name,enabled}]` 随 state 返回。
