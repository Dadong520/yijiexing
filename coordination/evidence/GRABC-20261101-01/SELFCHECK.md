# GRABC-20261101-01 作者自检（claude）
SID f0e957b7-5f75-4d4b-88a7-0043b2ad9253；2026-11-01；source /Users/dongge/.codex/tmp/yijiexing-order-competition-20261101 branch codex/order-competition-20261101 base 29e83a8。未commit/stage/部署；无Bash。app/build.gradle(0.3.22/code25)属root，未动。

## Phase1（已交，Phase2内修订）
3s提醒唯一owner（GrabAlertBook）、前台Dialog/后台Overlay/通知共享、新单浮窗只需听单资格+系统悬浮窗授权（付费气泡门禁保留）、通知“立即抢单”receiver(exported=false)、权限说明文案。

## Phase2（合同 contracts/GRAB-20261101-01.md V1）
新 CompetitionRules.kt（纯）：parseCompetition(round≥1/READY|OPEN|ASSIGNED|CLOSED/deadline可null/remainingMs/myOutcome+selfStatus别名)、parseCompetitionResults(顶层)；CompetitionWindow.anchor=elapsedRealtime收到时+remainingMs−300ms，只缩不延（墙钟跳变无关）；CompetitionRules.availability→LEGACY/CAN_GRAB/COMPETING/CLOSED_WAITING/UNAVAILABLE；CompetitionVerdicts.resolve（见root#1）；fromFailure(409 COMPETITION_LOCATION_REQUIRED→说明开定位/不称有赢家；ROUND_STALE→本轮结束)；CompetitionWatch候选尝试（token/generation所有权、requestId保留至权威结果、1s→2s→4s有界退避、25s超时“未收到结果”不臆断）；BoundedSeenLog；GrabActionIdentity(PI data URI/requestCode/notificationId按account+order+round)。
GrabAlertRules.kt：key.round=合同round；GrabAlert含有序途经点；directGrab需STANDARD/RESERVED且有round；book用GrabSeenLog，bind不再清seen；删除旧stage-only fromState与GrabActionGate（由watch token取代）；GrabOutcome增WonFeedback，isFinal。
NewOrderAlerts.kt：NewOrderAlerts.install持久化seen(私有prefs driver_grab_seen_v1，≤512条仅account|order|round)；clear只结束提醒不碰候选；GrabActions统一入口submit/grabAlert→POST order.accept{id,competitionRound}+requestId，200只记PENDING不称成功；observeState(store/service/poller唯一入口：锚窗口、判定、转发给其他listener)；短期poller仅有候选时运行；recentlyRefreshed供store 10s/service 15s轮询跳过避免同刻重复；endSession(登出/换号/下线)结束候选+丢弃迟到结果；后台：加入→“已加入竞抢”通知，终态→同ID替换为成功/未获派/未确认；Receiver从PI data取key（无extras/token）；Dialog/Overlay按钮由alertPrimary（竞抢中/已截止/重试/立即抢单）；Overlay显示起点-全部途经-终点于有界ScrollView，按钮固定44dp等权换行，卡片isClickable不穿透；CompetitionCard（详情：轮次/状态/同一抢单入口/仅服务端PENDING时“退出本轮竞抢”Danger）；competitionLabel（大厅）。
MainActivity：grabOrder唯一入口（大厅、详情、确认框；BID→报价；无competition→原确认+原order.accept）；确认框点击时再校验窗口；结果一次性消费，导航仅当前台点击+App可见+Assigned+store当前行同account/owned stage/同round+NavigationSessionRules.legFor可用；取消/退出对competition单带competitionRound，AVAILABLE竞抢单对话框改“退出本轮竞抢/不取消乘客订单”，原理由流程保留；下线成功endSession。
CoreScreens：大厅按钮文案随窗口（仅有OPEN时500ms本地tick，无网络）；详情AVAILABLE竞抢单加CompetitionCard；ConnectedTrip新增onGrab/onWithdraw。
ConnectedStore：DriverOrder.competition解析；accept后observeState(from=自身)；externalState listener（init顺序已处理）；silent refresh在候选poll/刚刷新时跳过；logout endSession。
DriverSpeech：只对LEGACY/CAN_GRAB行提醒（已加入/截止/获派不提醒）；通知posted按key；PI显式receiver+FLAG_IMMUTABLE+唯一data URI；下线(wasOnline→!online) endSession。DriverLocationService：先observeState再announcer；sharedState listener；离线stopSelf前endSession；overlay.render()。DriverDomain：competition字段。

## root Phase1反馈处理
1 Assigned=payload driver.id==key.account && 行stage∈PICKUP/RESERVED/ARRIVED/TRIP && (行competition缺失||round相等)；历史WON→WonFeedback不导航；MainActivity再按store当前行+legFor复核。
2 提醒(epoch)与候选(watch.generation)分离；active分支clear不再触及候选；赢单转PICKUP时同一state先判定Assigned。
3 seen持久化bounded 512；切号再切回/重启不重弹同轮；新round新key。
4 PI identity=account+order+round URI，无extras，旧action只处理原scope且需isActionable。
5 watch token所有权；end/bind后旧回调sent/finish无效；RETRYABLE复用requestId同body；PENDING仅权威结果结束。
6 Overlay全途经、滚动、按钮固定；到期移除且点击时再验isActionable；通知3s timeout/前后台互斥/声音偏好不变；付费bubble规则保留。

## 测试
新/改纯逻辑JUnit：GrabAlertRulesTest 11、CompetitionRulesTest 13（READY/OPEN/截止/时钟偏差、round、真实归属/历史WON拒导航、5s候选不被3s清、持久去重、PI身份、换号旧cleanup不解新busy、重复点击/重试同requestId、有界轮询）。编译/全部JUnit/androidTest/AVD/真机：NOT_RUN（无Bash）。

## Phase3（2026-11-01，root首编译失败+D0_SOURCE_REVIEW退回，基于HEAD 65695082，未commit）
0 编译：NewOrderAlerts overlay中局部`val topMargin`遮蔽LayoutParams.topMargin→改名overlayTop（:722报错根因）。其余仍未编译。
1 轮询：新StateCadence（纯）：真实上线+canAcceptOrders+无执行单(预约待出发除外)→2s；同时有OPEN/本人PENDING/本地候选→1s；在途后台15s、前台非听单10s；后台不听单且无在途→null不轮询（服务自停）。GrabActions.tryBeginBaseFetch/endFetch单飞门：任一GET在途、候选poll活跃、或状态比interval−200ms新则拒绝；candidate poll/refreshOnce同计inFlight。前台：MainActivity STARTED时500ms本地检查→store.refresh(silent)经门（原错误退避保留）；后台：Service仅在!DriverUiPresence.visible时500ms检查经门，错误退避4s→60s；DriverUiPresence互斥。Service applyState统一处理自己poll与共享state（announcer、listening、floating、离线无在途→endSession+stopSelf），不再滞后。删除recentlyRefreshed(5000)。
2 归属：CompetitionVerdicts.ownsRow=payload driver.id==key.account且行driverId==account且stage∈PICKUP/RESERVED/ARRIVED/TRIP且competition可解析且round==key.round；无competition不再兜底。CompetitionRules.required(kind,shared,hasKey)：普通shared STANDARD/RESERVED或有competition键→必须可解析，否则GrabAvailability.INVALID（大厅“请刷新后再试”，grabOrder提示+刷新，不走旧确认/不播成功音，不提醒）；BID/NEG/私有示例无键保持原流程。DriverOrder新增driverId/competitionRequired；MainActivity导航前再核store当前行driverId/round/stage/legFor。
3 截止：CompetitionWindow.anchor(existing, RequestTiming(start,received), remainingMs)=start+remaining−RTT，只缩不延；无测量→不新锚（无旧锚则CLOSED_WAITING+一次refresh，不提交）。所有来源带计时：store refresh/query/perform(POST或补GET)、service poll、candidate poll、accept POST/补GET、refreshOnce；login等无计时不锚。
4 Q3：同轮myOutcome≠NONE（含INELIGIBLE/WITHDRAWN）一律UNAVAILABLE，仅新round可再抢。文案：CANCELLED“本轮已结束或订单已取消”；LOST“本轮未获派，订单已由其他司机获得”。
5 Receiver：goAsync；grabAlert/submit新增onDone，未发送立即、发送则accept往返(含state读)完成后、或9s上限先到者finish一次（AtomicBoolean）；不启动Activity；PI仍显式/不可变/唯一account+order+round，旧scope需isActionable。
测试更新：CompetitionRulesTest新增/改（同轮INELIGIBLE/WITHDRAWN不可、新round可、shared缺/坏competition→INVALID、RTT1500不给满5s、同轮后续不延长、无测量不锚、READY null可抢、cadence 2s/1s/15s/10s/null、driverId/round/无competition拒Assigned、取消/LOST中性文案）。全部NOT_RUN。
剩余风险：前台500ms/后台500ms本地tick仅本地判断（不发网）；listening判定依赖features.canAcceptOrders；receiver 9s后请求可能仍在进行（结果走通知）；仍需root真实编译可能暴露Phase2其它小错。

## Phase4（2026-11-01，极小收尾，HEAD 65695082上未commit）
1 文案：前台Dialog标题“新订单”、Overlay/通知标题“宜捷行 · 新订单”（BID/非直抢原文保留）；COMPETITION_NOTE改“首位司机抢单后开启5秒竞抢，派单结果以服务器为准。提示3秒后收起，仍可在订单大厅查看。”，通知正文复用；详情卡“首位司机抢单后开启5秒竞抢/本轮竞抢已开启，截止前仍可抢单”；权限说明去掉“3秒内可抢”。按钮、TTL、业务未改。
2 截止锚：改为fresh=startedAt+remainingMs（=receivedAt+remainingMs−一次传输耗时），min(existing,fresh)；不再多减一次RTT，迟到由服务器409裁决。无时序不锚、同轮不延长、READY null可抢、纯elapsedRealtime保持。单测改：start10000/receive11500/remaining5000→15000，收到后剩3500；更晚同轮不延长；更少剩余取严(12000/12100/1000→13000)；传输耗尽→禁抢。（Phase3“−RTT”描述已被本条取代。）
测试：编译/JUnit/native仍作者NOT_RUN。

## 给root的合同疑问（Phase2历史；结论已被Phase3取代）
Q1 [已被Phase3取代] 原：缺competition按stage+account认Assigned。现：Assigned须行driverId==account+owned stage+competition可解析且同round，无兜底。
Q2 competitionResults按合同取data顶层，不读state内（仍为当前事实）。
Q3 [已被Phase3取代] 原：INELIGIBLE窗口内可重试。现：同轮INELIGIBLE/WITHDRAWN等任何非NONE均不可重入，仅新round。
Q4 accept result.pending忽略，只以state/results定论（仍为当前事实）。

## 未完/风险（当前）
仍未经root重编译，可能暴露其它小编译错；无countdown数字显示；小屏实际排版需AVD；receiver已goAsync但9s上限后请求可能仍在进行（结果走通知）；listening判定依赖features.canAcceptOrders。[Phase2历史“Receiver未goAsync”“service flags最多延后~20s”已被Phase3修复取代。]
