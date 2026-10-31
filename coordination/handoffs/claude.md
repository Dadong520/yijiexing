# claude 当前短卡
协议ACK：2026-10-26-v3（已读中央START_HERE.md、STATE.md、BOARD.json、tasks/MAPSC-20261031-01.json、MAPS-20261031-01.json）。角色claude；session 9bc07c4c-731c-4b1f-840b-ce6a0c5dc85a（取自task，未自行遥测）；claude-opus-5-5；context UNKNOWN；memoryStatus NORMAL。更新2026-10-31。Codex root唯一协调/验收/合并发布。
任务MAPSC-20261031-01（证据evidence/MAPSC-20261031-01/IMPLEMENTATION.md）；source /Users/dongge/.codex/tmp/yijiexing-map-panels-20261031，branch codex/map-panels-20261031，base 3587d5a。
状态：实现完成，未commit/add；未碰Gradle/版本/Store/网络/业务/控制文件。
改动：新增两端MapPanel.kt（allow_paths已列），自研三态可拖面板：展开覆盖地图/半展/收起只留把手；嵌套滚动与地图手势不抢；地图只resize一次；固定底栏；attentionKey自动弹回；a11y按钮+customActions；Expanded时返回键先回半展。用于乘客BookingScreens首页、OrderDetail，司机CoreScreens ConnectedHome、ConnectedTrip（仅RESERVED/PICKUP/ARRIVED/TRIP）。乘客新草稿目的地默认空，GPS起点链路不变，无权限时显示提示。AddressDialog：地图在上、查询为空、结果在下；选中才保存；debounce/gate/stale保护保留。
Phase2（基于root freeze cbe8190，只改BookingScreens.kt）：修非作者审查2项FAIL。1）searchFor和城市框变化都会立即清掉旧selection；“使用此地点”在pending或searching时禁用，点击时再次校验；query与selection仍分离。2）交换起终点只在两端名称和坐标都有效时可用，onClick开头再验，失败则不改任何字段（GPS起点不清、不锁死）。证据见IMPLEMENTATION.md的Phase 2。未commit；自测NOT_RUN；未碰frontend导出目录。
Phase3（基于source freeze ee23c4c）：面板收起时真全屏。MapPanel.kt新增内存MapChromeHost（token→scope）、LocalMapChromeHost和scope函数，scope同时作saveKey；司机home的key改为带账号。面板只在目标为Collapsed时claim，dispose或恢复时按自己的token release。MainActivity只在claim的scope与当前页面scope一致时，才隐藏乘客顶栏标题行/busy/底tab（连接与错误条保留）和司机底tab；CoreScreens同条件隐藏司机首页资质与身份栏、行程页PageHeader。Collapsed不再持久化（存为Half）。MapView不重建，锚点改瞄不循环。未改导航、权限、门禁、事件。8条风险待native验证。自测NOT_RUN，未commit。
联动：ProfileScreens（不在allow_paths，未编辑）常用地址编辑同样换成新选址行为，签名兼容。
测试：编译/单测/GUI全部NOT_RUN。下一步：非作者复核Phase3差量（含Phase2）后再compile/native；退回在同一session修。
历史：UITC-20261030-01（session 93f99706-…）阶段1-4已交root；NAV-20261029-01已由root验收发布version20。
