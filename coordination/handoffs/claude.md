# claude 当前短卡
协议ACK：2026-10-26-v3（已读中央START_HERE.md、MEMORY_POLICY.md、tasks/NAV-20261029-01.json）。角色claude；实际session c3342ddb-6fe0-44b6-8311-00c329e73178（取自task），claude-opus-5-5/medium；context UNKNOWN（无可靠遥测）；memoryStatus NORMAL。更新2026-10-29。Codex root唯一协调/验收/合并发布。
任务NAV-20261029-01；source /Users/dongge/.codex/tmp/yijiexing-cloud-navigation-review-20261029，branch codex/cloud-navigation-review-20261029。阶段1/2已由root提交为2ba5b79f1113b0de9ec105a2733fc8a4a9e9ffe0（frontend构建+单测47/77 PASS，后台5问题PASS）。
当前结论：阶段3（NAVF-R1/R2）作者修复完成；2ba5之上工作树未提交；作者编译/测试/GUI全部NOT_RUN；等Codex编译与非作者复核。未合并、未发布。未改driver/Store/domain/backend/NavigationContract/manifest/key/Gradle/版本。
阶段3改动（仅乘客）：
- R1 PassengerModalCoordinator：slot加rank（页面0<公告1<确认2），outranked()与mayClaimAnnouncement()。OperationsSupport公告：claim前须无任何他模态；已领取的captured保存在showing（按owner remember）直到关闭，仅在无页面级模态时绘制（showing/failed对话框都复核），不丢弃不重复claim；自身slot不参与判断，无循环。确认弹窗rank2，被公告/页面遮挡时等待。PassengerPermissions按实际shouldShow=forceOpen||!seenGuide注册slot（仅UI）；MainActivity删除只按forceOpen的注册。BIZ09与服务端claim未变。
- R2 PassengerPickupDefaults新增PassengerAddressLookup.debounced：等待前取gate token，等待后/结果/错误/finally均校验；BookingScreens文字搜索effect改用它。手动坐标、同名常用选择、地图、取消、关闭均invalidate→等待中旧查询不发请求。同query重试保留。
- 测试：PassengerPickupDefaultsTest用runBlocking跑真实debounced控制流（等待中手动/改字/新查询、在途作废、失败后重试）；PassengerConfirmPromptRulesTest加rank互斥与claim一次规则。
残余：账号切换时已领取未显示的公告随旧owner丢弃（不串号，但该版本对旧账号不再弹）；GUI NOT_RUN。
下一步：root编译+单测+非作者复核；退回在同一session修。
