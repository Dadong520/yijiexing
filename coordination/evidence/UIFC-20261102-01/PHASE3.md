# UIFC-20261102-01 Phase3 · P2 修复：重新定位的系统请求生命周期（作者自报，未验收）
session 43d13c15-717c-43bf-80c0-dfba56d72fc1 · claude-opus-5-5/high · 2026-11-02 · 基于冻结候选 6cd71cc6eab1be65f9f65c129b53ae88502cdb4d 工作树，未commit。
只改 candidates/driver-0.3.6-r2/.../DriverLocationService.kt。其他源未动。

## 修改
- 新增 inner class RetryRequest(generation, credentials, requestedAtNanos)：持有本次重定位专用 listeners（Android 8–10 requestSingleUpdate，注册前先入表）、CancellationSignal 列表（Android 11+ getCurrentLocation 每个 provider 独立 signal，不再传 null）、pending 计数和 timeout Runnable。close() 幂等：移除 timeout、cancel 所有 signal、manager.removeUpdates(每个专用 listener)；从不移除常规15秒监听 `this`。
- 单一 activeRetry。cancelRetry() 关闭并清空当前请求；finishRetry(request, status) 仅当 request 仍是 activeRetry 时才清理/改状态，旧回调不能清理或覆盖新请求。
- 清理触发：新 retry 开始前（retryLocation 首行）；收到新鲜 fix（立即关闭，另一 provider 的剩余请求被取消）；全部 provider 都返回 null/陈旧 fix（提前结束，状态“暂未获取到新位置…”）；20s timeout；SecurityException（已注册部分被释放，状态“定位授权已失效”，不报成功）；IllegalArgumentException（provider异常，状态“定位服务暂不可用”）；正常 onStartCommand（serviceGeneration++ 前）；onDestroy。
- 捕获凭据/generation：创建时记录 serviceGeneration 与 LocalApi.credentials()（空token直接提示登录，不发请求）。回调先校验仍为 activeRetry，再校验 generation==serviceGeneration 且 LocalApi.isCurrent(credentials)，不符则丢弃 fix、释放请求、不上报。
- 移除 forceReport/retryToken 字段；新增 handleFix(location, forceReport, generation, credentials) 供常规 onLocationChanged 与 retry 共用：上报前再次校验 generation/isCurrent，并使用调用方捕获的 credentials 上报（retry 用请求自己的凭据）。常规路径行为与原先等价（credentials 当场捕获、14.5s 节流不变）。
- 保留：retry 分支仍在 serviceGeneration++/flag 读取前 return，listening/voice/floating、NewOrderAlerts、GrabActions 不变，不 stop/restart service。3s/5s 计时、计价、竞抢均未触及。

## 风险/待root确认
- 正常 start command（如 MainActivity 因 voice/floating 变化重发）会取消进行中的 retry，按要求视为新 session；用户可再次点按。
- getCurrentLocation 被 cancel 后系统可能不回调或回调 null，已被 activeRetry 检查忽略。
- 编译点：inner class 中 `::manager.isInitialized`（外部 lateinit）、Runnable lambda 内 `this` 指 RetryRequest。

## NOT_RUN
编译、JUnit、构建、AVD（Android 8–10 与 11+ 的 retry 成功/超时/重复点击/权限撤销/服务销毁/账号切换下的监听释放与耗电）全部 NOT_RUN（无Bash）。
