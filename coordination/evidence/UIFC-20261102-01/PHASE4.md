# UIFC-20261102-01 Phase4 · 重新定位须等待真实新fix（作者自报，未验收）
session 43d13c15-717c-43bf-80c0-dfba56d72fc1 · claude-opus-5-5/high · 2026-11-02 · 基于C3 6a40ee3a9eb95d3965824aa8e1348d9a0323f71d 工作树，未commit。
只改 candidates/driver-0.3.6-r2/.../DriverLocationService.kt。其他源/测试/fixture/后端未动。

## 根因（root native实测）
Android14 getCurrentLocation(gps) 立即交付系统缓存旧位置后即结束该provider请求；新鲜检查拒绝后GPS已被系统remove，只剩network或等20s超时，显式重定位没有触发新的GPS采样。

## 修改
- retryLocation 对所有Android版本改为每个启用provider注册一个专用、可移除的 LocationListener：manager.requestLocationUpdates(provider, 0L, 0f, listener, mainLooper)。不再使用 getCurrentLocation / requestSingleUpdate / CancellationSignal（相关字段与pending计数删除）。
- 旧/缓存/非有限坐标的fix被忽略，listener继续等待硬件下一次真实采样，直到新鲜fix或20s超时；缓存fix不能结束provider请求，也不能结束整个retry。
- 新鲜规则抽为纯函数 RetryFreshness.isFresh：经纬度有限、fix.elapsedRealtimeNanos>0 且 ≥ 请求时刻。比Phase3（请求时刻−1s容差）更严格，未放宽。
- 首个新鲜fix：先 finishRetry（移除全部专用listener含另一provider + timeout），再走 handleFix(forceReport=true, 请求捕获的generation/credentials) 一次上报。
- 单个provider注册抛 IllegalArgumentException 仅跳过该provider；无任何provider注册成功→状态“定位服务暂不可用，请稍后重试”；SecurityException→释放已注册部分，状态“定位授权已失效”（不报成功）。
- 保持C3：activeRetry身份与finishRetry仅清理自身；回调校验 generation==serviceGeneration 且 LocalApi.isCurrent(credentials)，不符丢弃并释放、不上报；新retry先关闭旧retry；正常onStartCommand与onDestroy调用cancelRetry；常规15秒监听`this`不注册/移除变化；retry分支不改listening/voice/floating、NewOrderAlerts、GrabActions，不stop/restart service。3s/5s计时、计价、竞抢未触及。

## 风险/待root确认
- retry期间GPS/network以0ms请求运行，最长20s或至首个新鲜fix即移除；需dumpsys确认成功/超时/重复点击/服务销毁后专用listener全部remove，仅剩15s常规listener。
- 模拟器geo fix需在请求后触发硬件下一次采样，才会产生elapsedRealtimeNanos≥请求时刻的fix；不能用缓存伪造成功。
- RetryFreshness未新增JUnit（本阶段仅允许改该源文件）；如需可在后续授权路径中补测试。

## NOT_RUN
编译、JUnit、构建、Android14/8–10 AVD重定位实测与dumpsys、backend差量复核 全部 NOT_RUN（无Bash）。
