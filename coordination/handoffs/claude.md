# claude 当前短卡
协议ACK：2026-10-26-v3（已读中央START_HERE/STATE/tasks/GRABC-20261101-01.json/本卡/contracts/GRAB-20261101-01.md V1）。角色claude；session f0e957b7-5f75-4d4b-88a7-0043b2ad9253（同SID续Phase2）；claude-opus-5-5/high；context UNKNOWN；memoryStatus NORMAL。更新2026-11-01。Codex root唯一协调/验收/合并发布。
任务GRABC-20261101-01（证据evidence/GRABC-20261101-01/SELFCHECK.md）；source /Users/dongge/.codex/tmp/yijiexing-order-competition-20261101，branch codex/order-competition-20261101，base 29e83a8。
状态：Phase1+Phase2作者实现完成，未commit/stage；只改driver src。新增CompetitionRules.kt/CompetitionRulesTest.kt/GrabAlertRules.kt/GrabAlertRulesTest.kt；改NewOrderAlerts/DriverLocationService/DriverSpeech/MainActivity/ConnectedStore/CoreScreens/DriverDomain/DriverPermissions(文案)/AndroidManifest(receiver)。build.gradle版本属root未动。
要点：5s竞抢按合同round/status/remainingMs/myOutcome/competitionResults；所有抢单入口同一GrabActions.submit→order.accept{id,competitionRound}+保留requestId；200=PENDING非成功；窗口按elapsedRealtime锚定只缩不延，截止本地禁抢等权威结果；候选短轮询1s→有界退避，与store/service共享state避免重复；Assigned仅本人+owned stage+同round，历史WON只反馈；提醒3s与候选分离；seen持久≤512；PI按account+order+round；withdraw/cancel带round。
Phase3（2026-11-01，root首编译失败+D0退回，HEAD 65695082上未commit）：修overlay topMargin遮蔽编译错；基础轮询听单2s/热1s/在途15s/前台闲10s/不听单停，store/service/候选poll单飞门+前后台互斥，service统一applyState；Assigned须driverId+owned stage+同round且competition可解析，shared普通缺/坏competition→INVALID不回退旧接单；截止锚=请求开始+remaining−RTT，无测量不锚；同轮INELIGIBLE/WITHDRAWN不可重入；CANCELLED/LOST中性文案；receiver goAsync 9s有界finish一次。
Phase4（2026-11-01，未commit）：标题统一“新订单/宜捷行 · 新订单”，说明“首位司机抢单后开启5秒竞抢，派单结果以服务器为准。提示3秒后收起，仍可在订单大厅查看。”；截止锚改startedAt+remainingMs（只扣一次传输，取min），取代Phase3的−RTT；SELFCHECK旧Q1/Q3/风险标已取代。
测试：JUnit(11+15)、重编译、AVD全部NOT_RUN（无Bash）。合同疑问见证据。
下一步：root重编译+JUnit+源审+隔离AVD；退回同SID修。
历史：NAVIC-20261031-01（SID a6956536-…）已由root验收发布version24。
