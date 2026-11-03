# claude 当前短卡
协议ACK：2026-10-26-v3（已重读中央STATE、tasks/WBC-20261103-01.json、CLAUDE_CODE.md）。角色claude；session 466f8898-78a4-4285-b31f-33738a2a8600；claude-opus-5-5/medium；context UNKNOWN；memoryStatus NORMAL。更新2026-11-03。Codex root唯一协调/验收/合并发布。
任务WBC-20261103-01；source /Users/dongge/.codex/tmp/yijiexing-workbuddy-20261103，branch codex/workbuddy-fixes-20261103，base 22dfb6a。
Phase1：作者实现完成，未commit/stage（evidence/WBC-20261103-01/PHASE1.md逐项成立/不成立/证据不足）。
改：乘客PlaceSelection（同名≤30m去重）、PassengerPickupDefaults（远距新fix替换、重新定位freshAfter、300km直线预判）、BookingScreens（超距卡片：重新定位/手动选上车点；半展开按起终点卡底边设minHalfContent 160–360dp）、MainActivity（登录后顶栏去掉权限，显示"服务城市"）、ProfileScreens（应用与连接加定位权限）、OrderDetail（订单动态加订单编号）、AmapSupport、PassengerPermissions按钮min48dp；两端Manifest加portrait；司机AmapSupport同意页FlowRow。新增JUnit 9例。
未动：Gradle/签名/版本/key、后端、计价/取消/竞抢、3s/5s、scope/round/contact fences、P0同步。
测试：编译/JUnit/构建/AVD全部NOT_RUN（无Bash）。
下一步：root编译+JUnit+AVD；backend核#1/#2/#10同orderId；需要返工时用同一SID续跑。
历史：UIFC-20261102-01（SID 43d13c15-…）已发布；GRABC/NAVIC为历史任务。
