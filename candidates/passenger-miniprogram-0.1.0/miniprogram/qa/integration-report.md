# 微信小程序与冻结 v8 本地 HTTP 契约集成验证

日期：2026-10-24。结果：7 项测试全部通过，0 失败。使用当前小程序 lib 与冻结 `/private/tmp/yijiexing-passenger-v8/cloud-service` 源码。

## 可复跑

在项目目录运行 `miniprogram/qa/integration-run.sh`。需要 Node（提供 node:test/fetch）和 Python 3。交付包使用同级 `backend-frozen-v8/`；也可用 `YIJIE_BACKEND_SOURCE` 指定冻结 v8 路径。开发工作区保留临时冻结路径作为后备，交付包不依赖该临时目录。

测试启动 `server.make_server`，端口由系统随机分配，监听地址为 `127.0.0.1`。SQLite 位于自动清理的临时目录。测试没有连接云、微信设备、原测试环境，也没有使用实际访问码、管理员码、AppSecret 或真实短信。`local-fixture-only` 是无服务端权限意义的本地测试值。

乘客请求走实际 `store -> api -> wx.request` 适配器，再由仅存在于测试内的传输桥把固定体验域名改送到 loopback HTTP；真实后端执行验证码、登录、鉴权、幂等操作、SQLite 写入和 state 返回，未用成功响应 mock 替代后端。测试桥严格断言原 URL 的 origin 和乘客路径。

司机通过同一后端 Service 的领域方法在本地生成并登录，删除合成种子订单；真实共享订单和版本校验仍执行冻结后端规则。为覆盖议价流程，只在一次性临时数据库启用宜昌体验司机的线下议价参数。后台调价通过真实 `AdminConsole.order_action`，仅调用本地领域函数，不引入管理接口或真实管理员凭据。

## 验证证据

1. STANDARD：真实创建 AVAILABLE；保存 rider 后以 riderId 代叫；途经点保留 name/latitude/longitude；司机接单进入 PICKUP，经 ARRIVED/TRIP 到 WAIT_PAY，逐一确认途经点。欠费乘客新单被真实 409 拒绝，同一司机可接受另一乘客新单。
2. 费用版本：冻结原确认金额和版本；后台真实调价 A→B→A；原 payload 支付被费用版本冲突拒绝；刷新后 fareVersion=2，adjustments 使用 before/after 字段，再以新确认信息完成模拟支付。
3. RESERVED：ISO 预约时间被接受，司机接单返回 RESERVED，依次推进并完成模拟支付。
4. BID：司机真实报价，乘客收到 bids 金额和 driverId，选择司机后 PREPAY，模拟支付后 PICKUP，再完成订单。
5. NEGOTIATED：真实司机创建、配对码生成与乘客认领；初始 quoteVersion=1；乘客价格异议后 DISPUTED；司机重新报价后 quoteVersion=2；冻结 amount/quoteVersion 支付后 TRIP，最终 DONE。
6. Extras：真实保存评价、PENDING 投诉、常用地址、个人发票抬头、SIMULATED_ISSUED 回执。回执 amount 与完成订单整数分费用一致，titleSnapshot 保存抬头；后续真实 GET 返回已保存内容。
7. 当前页面处理器：在隔离 JS 上下文直接加载 home、settings、order-detail 页面源代码，以页面实际 submit/saveRider/saveTitle/apply/cancel 方法生成 payload，并通过实际 store/HTTP 后端完成请求。验证页面代叫、途经点、取消、个人抬头和回执申请。

本轮未发现上述已执行页面处理器的 payload 与 v8 契约不一致。

## 验证边界

此结果证明本地冻结 v8 与当前小程序数据层及已执行页面处理器的接口兼容。不是云部署验收、微信开发者工具编译验收或手机现场验收。真实短信、微信身份登录、微信支付、税务发票、道路导航均未配置或未在本测试验证。投诉图片上传、所有错误分支、司机证件/权益组合、服务重启恢复和每个页面视觉交互未纳入这套集成测试；并发与存储失败由独立 data/review 单元测试覆盖。本地 storage 不等于硬件加密。

原始本地测试输出见 `integration-results.txt`。
