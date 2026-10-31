# claude 当前短卡
协议ACK：2026-10-26-v3（已读中央START_HERE/STATE/tasks/GRABC-20261101-01.json/本卡/contracts/GRAB-20261101-01.md V1）。角色claude；session f0e957b7-5f75-4d4b-88a7-0043b2ad9253（同SID续Phase2）；claude-opus-5-5/high；context UNKNOWN；memoryStatus NORMAL。更新2026-11-01。Codex root唯一协调/验收/合并发布。
任务GRABC-20261101-01（证据evidence/GRABC-20261101-01/SELFCHECK.md）；source /Users/dongge/.codex/tmp/yijiexing-order-competition-20261101，branch codex/order-competition-20261101，base 29e83a8。
状态：Phase1+Phase2作者实现完成，未commit/stage；只改driver src。新增CompetitionRules.kt/CompetitionRulesTest.kt/GrabAlertRules.kt/GrabAlertRulesTest.kt；改NewOrderAlerts/DriverLocationService/DriverSpeech/MainActivity/ConnectedStore/CoreScreens/DriverDomain/DriverPermissions(文案)/AndroidManifest(receiver)。build.gradle版本属root未动。
要点：5s竞抢按合同round/status/remainingMs/myOutcome/competitionResults；所有抢单入口同一GrabActions.submit→order.accept{id,competitionRound}+保留requestId；200=PENDING非成功；窗口按elapsedRealtime锚定只缩不延，截止本地禁抢等权威结果；候选短轮询1s→有界退避，与store/service共享state避免重复；Assigned仅本人+owned stage+同round，历史WON只反馈；提醒3s与候选分离；seen持久≤512；PI按account+order+round；withdraw/cancel带round。
测试：新/改24项JUnit及编译/AVD/真机全部NOT_RUN（无Bash）。合同疑问Q1-Q4见证据。
下一步：root编译+JUnit+源审+隔离AVD；退回同SID修。
历史：NAVIC-20261031-01（SID a6956536-…）已由root验收发布version24。
