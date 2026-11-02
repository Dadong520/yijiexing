# UIFB 定位生命周期修复差量复核

**PASS，P1=0/P2=0；UIFB-P2-01 CLOSED。** 精确SHA `6a40ee3a9eb95d3965824aa8e1348d9a0323f71d`；仅比较 `6cd71cc6eab1be65f9f65c129b53ae88502cdb4d`→此SHA的DriverLocationService.kt，Git确认只有此文件改变。原REVIEW FAIL及定位trace保留；未变UI/倒计时/cancelScope检查沿用原限定结论。

RetryRequest在377–398保存每轮legacy listener、现代CancellationSignal和timeout；close先置closed，幂等移除timeout、cancel信号、remove精确retry listener并清集合。注册前先保存句柄。成功/all-empty/timeout/异常统一finishRetry→cancelRetry→close；新retry先取消旧轮，normalstart先cancel再generation++，destroy先cancel后清主listener。主服务this listener不被一次重试close误删。

finishRetry仅处理当前activeRetry；answer先校验active identity/closed，再核对捕获的serviceGeneration与LocalApi.Credentials revision。旧轮迟到回调不会取消新轮、改状态或上报；旧账号/代际结果只结束原请求，无报告。新fix通过共享handleFix强制跳过一次节流并使用捕获凭据，网络失败仍受代际/凭据检查。API30+现在有明确CancellationSignal，API26–29有可注销listener，原P2所指无fix持续定位路径已封堵。

11项源码结构检查和10类控制流追踪通过，单文件hash/diff已保存。这是静态审查，非Android运行测试；未执行build/native/全量测试/云操作，无源码改动或代理。初次审查脚本任意cancelRetry总文本次数阈值失败，已改为检查三个真实生命周期方法；不是产品失败。构建与原生定位仍root负责，不借用其未回传结果。中央任务/短卡已更新，门禁通过，backend转IDLE。
