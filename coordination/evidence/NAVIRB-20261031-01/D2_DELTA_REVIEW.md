# NAVIRB D2 八文件差量非作者源审

**PASS（仅源审），P1=0/P2=0。** 精确候选 `6cdcee1a68d57465e4196243f22478c69733654c`，差量基线 `83c6fde3a4c70bbb5bc2a2362f0e4e2f93937212`，releaseReady=false。原D1 REVIEW不改，hash在JSON。

- NewOrderAlerts:152–214同一RouteStops完整展示原途经点，内容滚动、按钮外置并最小高度；MainActivity:255实际传state.orders。detail已消失时保留起终点fallback，不伪造未知WP。alert触发/去重/打开逻辑不变，实际小屏滚动及按钮可达待root。
- MainActivity接单只替换提示内容为routeSummary；NavigationSessionRules:133–136用原有序stopRows，O/W1/W2/D一站一行。接单请求/回调未改，新测试仅检视未执行。
- CoreScreens:980–1047导航复用原MapPanelLayout，正确tripScope/saveKey/chrome scope；固定map调用位置内嵌SDK，原tripActions作bottomBar，attention含stage/next原WPindex/全部WP/distance/cancel/participation。既有MapPanel扩展时返回先降半、收起chrome归属与恢复沿用；不能从源码断言实际拖动不触发SDK重建，root需原生确认。
- **机械字节核对**：tripActions整块、尾号verify对话框至文件末尾与D1完全相同；ConnectedStore/TripWaypoints/DriverDomain/NativeMap/MapPanel未变。所有stage/PIN/allWP/距离同意/预约/取消与联系原链继承D1，无业务调用新增。
- 私有示例导航内补未关联真实乘客提示；PassengerContact提前返回，不出现真实取号按钮；generation之后完整函数后缀与D1一致，真实共享单临时号码/会话/拨号确认保护保持。
- LinkageError仅在engine open新增可见Unsupported+release+退出，无重试/外跳/订单推进；它不等于所有SDK生命周期的ABI异常都有兜底。DriverTripDistance只改一条文案，不改授权或处理。

backend subtree D1/D2完全一致，继承root284独立验收，不自行再验本人backend。root报告D1 provider/业务原生PASS，本报告不冒充D2已经同样实测。实际仅固定Git对象差量/字节比较/树比较/diff --check/中央门禁PASS；编译/JUnit/AVD/云/SDK/JNI/小屏IME均NOT_RUN，root负责。

root结合准确D2构建、地图三态/恢复/全屏、alert/接单小屏滚动、IME与SDK尺寸生命周期证据后统一验收发布；本角色IDLE。
