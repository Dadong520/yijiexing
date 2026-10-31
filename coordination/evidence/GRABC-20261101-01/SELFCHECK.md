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

## 给root的合同疑问（按保守实现）
Q1 获派后订单行是否仍带competition？缺失时按stage+account认Assigned（有则须同round）。
Q2 competitionResults按合同取data顶层，不读state内。
Q3 同轮WITHDRAWN后可否再加入？客户端按不可（UNAVAILABLE）。INELIGIBLE视为窗口内可重试。
Q4 accept result.pending忽略，只以state/results定论。

## 未完/风险
未编译，可能有小编译错（overlay import、Compose smart cast等）待root编译反馈；Receiver未goAsync依赖前台服务保活；service flags在候选poll期间最多延后~20s；无countdown数字显示；小屏实际排版需AVD。
