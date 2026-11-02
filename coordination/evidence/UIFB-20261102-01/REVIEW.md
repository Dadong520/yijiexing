# UIFB 非作者限定源码审查

**FAIL，P1=0/P2=1。** 精确冻结候选 `6cd71cc6eab1be65f9f65c129b53ae88502cdb4d`，base `874aad43e742e7719519758df36724f540b3a038`；source /Users/dongge/.codex/tmp/yijiexing-ui-feedback-20261102，branch codex/ui-feedback-20261102。仅不可变git对象只读，23文件diff/hash绑定；本角色不构建、不运行native或全307、不云操作、不改源、不派代理。

## UIFB-P2-01：重新定位底层请求没有按生命周期取消
DriverLocationService.kt **406–412**（同目录prefix见REVIEW.json）：API26–29每次重新定位为GPS/NETWORK各注册一个匿名requestSingleUpdate listener，但没有保存listener引用。20s超时419–424只增加retryToken/写提示；下一次重试同样只失效旧token；成功只采用第一个结果却不清其他provider；onDestroy484–492只removeUpdates(this)，并非这些匿名listener。minSdk26使这不是不可达分支。

无fix时，用户每次重试新增的系统定位请求不会因20s超时或下线销毁结束，可能继续占用GPS并保留旧Service直到有fix或进程退出。token能阻止旧结果更新UI，但不能注销LocationManager订阅。API30+ getCurrentLocation传null CancellationSignal，同样无法在超时/重复或服务退出时主动撤销另一provider请求（该API系统自身有界，不称其无限期）。精确源码及source-driven事件序列见LOCATION_LIFECYCLE_SOURCE.txt/TRACE.json；这是源码定位，未声称测过旧系统耗电。

建议保存每轮的CancellationSignal/API<30 listener集合，在成功、超时、新重试、异常和onDestroy统一cancel/remove；或使用兼容的有取消句柄的current-location实现。应补无fix→超时→重复→destroy时活动订阅归零的有意义验证；同时保留主服务原listener与新请求的区分。Android官方[LocationManager文档](https://developer.android.com/reference/android/location/LocationManager)说明requestSingleUpdate存在严重耗电风险，removeUpdates只清理指定listener。

## 已通过的限定检查（14项静态断言，非运行验收）
- 3秒显示从book.current精确account/order/round读取remainingMs；原GrabAlertBook字节未变，读数不重启、不延长。前台100ms/overlay200ms只是本地重绘，hide清ticker。
- 5秒显示读取既有anchors/elapsed now，精确账号/轮次，原CompetitionRules/submit/observe/poll字节未变，anchor仍requestStart+remaining/只取min/RTT一次。零/负数Closed、unknown不编造，BID/NEG及无competition不显示竞抢倒计时，不新增网络轮询或派单判断。
- 原取消CancelScope整个尾段逐字节相同：account/order/round/stage/intent capture与click最新store recheck保持。扩大的取消/退出按钮仍调用原onCancel/onWithdraw，不绕过确认。DTO/store/manifest不变。
- 重新定位入口检查系统权限及定位开关；离线无行程不启动，正常首次启动携全flags；运行service的独立action分支不增加serviceGeneration、不改voice/listening/floating flags、不结束候选。采用elapsed新fix+原location.report认证路径，权限异常如实显示。其底层清理缺陷见上P2。
- 两端MapPanel仍为原Expanded/Half/Collapsed anchor、nestedScroll/rememberSaveable控制；diff改handle尺寸/手势阈值和可点击图标样式，原animateTo目标一致，非业务推进。需root实机/AVD检查小屏手势、按钮可达性，不以源码审查替代布局结果。
- 乘客订单分类保留PassengerUiRules过滤；联系/投诉/恢复屏蔽等新的图标按钮仍调用原callback，enabled/busy沿用，未启用原不可用司机电话。支付/模型/store/网络/后台均未改。钱包仍读原balance/todayIncome；色彩/空状态/记录行布局无新业务记账。

新增CountdownPresentationTest五项仅阅读，未执行。构建、原生、定位硬件和OEM均NOT_RUN；root正在负责，本文不引用其未交付结果。最终需修复本P2并给新精确SHA后差量复核；不自行派修复、合并或发布。中央任务与backend短卡已保存，门禁通过。
