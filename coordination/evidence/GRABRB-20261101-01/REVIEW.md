# GRABRB 非作者客户端契约窄审

结论 **FAIL，P1=0 / P2=1**。冻结客户端 `9f9ee9b188d47211872d836cbe73ead88d10135a`；后端合同绑定3bdb8f71743b30f83d65131bb90ed7485690b62b。仅用git show读取精确对象，源码树未修改；无编译/native/307重跑/云访问。编译与真实原生结果归root，本文不是全体验验收。

## P2：取消确认没有固定打开时的账号和轮次
位置：MainActivity.kt:93、447–461、539–585（路径前缀见SOURCE_MANIFEST）。cancelOrder只存id；每次重组从最新state查cancelTarget，确认时使用最新competition.round。旧对话因此能变成新轮操作，而不是由服务端拒绝旧round。

事件序列：设备A打开round1退出/取消弹窗并暂留；同订单round1结束或取消→round2；同账号设备B在round2入候选或获派；A的2s/1s刷新把cancelTarget换成round2；A仍在原弹窗选择原因确认，实际发送round2的order.cancel。后端按当前轮合法处理，会退出新候选或取消新获派，违背旧动作不得串轮的契约。即便标题随重组变化，打开时的操作身份已丢失；同id本来就是本轮明确支持的重开路径。

建议：打开弹窗时保存account/orderId/round及withdraw-vs-assigned意图，确认时比对当前账号、轮次和允许阶段，变化则关闭并提示重新确认；不要把当前轮次补入旧确认。跨账号/登出清除此对话。补充旧弹窗跨轮与withdraw→assigned变化的定向验证。精确源码摘录CANCEL_SOURCE_EXCERPTS.txt；3个源码结构断言+事件追踪CANCEL_ROUND_TRACE.json通过，该追踪是源代码推演，**非Android实跑**。

## 其余限定源码检查
- 普通共享缺competition fail closed；hall/detail/confirm统一grabOrder→GrabActions，提醒/overlay/通知统一grabAlert→submit。POST id+competitionRound+UUID requestId；重复请求沿用同body/id，重轮新key。
- HTTP200只把候选设PENDING；CompetitionVerdicts拥有profile/account+driverId+owned stage+同round门禁；历史WON仅WonFeedback。结果消费再次核对当前store归属/轮次/可导航leg；后台结果不自动导航。
- 首抢服务端5s，客户端elapsedRealtime+请求起点remainingMs取min只缩不延。无测量OPEN禁抢并刷新，READY可显式参与；3秒提醒book与25秒候选watch分离，不因提醒收起撤候选。
- 通知action显式非导出receiver，immutable PI，data URI带account/order/round；非令牌extras。receiver goAsync最多9s收尾；无通知强拉全屏。旧key/旧round/过期提醒禁操作，overlay仅系统授权。
- watch token/generation及Credentials revision阻止候选旧回调消费新attempt；logout清watch并失效credentials，offline清候选。book.dismiss按key不会删新round；seen私有有界512。
- 周期base读取前台STARTED/store与后台service按DriverUiPresence交接，统一single-flight门禁；听单2s/OPEN1s，candidate poll运行时base跳过、状态广播复用。候选短轮询1s→2s→4s并25s超时；非听单沿原闲置/在途规则，不新增后台保活线程。
- BID报价/NEGOTIATED/private示例继续旧入口；共享普通缺字段不退回旧假即时接单。DTO stage枚举不改。

这些是源码一致性检查，不声明OEM/真实定位/网络耗时/Android生命周期验证通过。未把上述复杂并发边界概括成完整运行PASS。作者现有单测只作阅读，未执行。root要求审查自己的后端为NOT_RUN（作者不验自己）；root派单载明后端独立307+2 PASS，未在本窄审重复取证。

## 门禁与文件归属
角色gate PASS；10个精确Git对象hash见SOURCE_MANIFEST.json，source status clean。只写中央GRABRB任务/证据/backend短卡。一次读取误在中央非Git目录执行git show失败，随后在正确sourceRoot读取成功，未发生源码修改或测试误计。等待root派修复及新的精确SHA；不自行派代理或部署。
