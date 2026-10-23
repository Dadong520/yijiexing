# 乘客1.1.5候选业务兼容独立静态审查

结论：对本次读取的1.1.5工作目录与正式1.1.4源码归档进行对比，**未发现页面重排新增的明确协议、幂等、状态或权限阻断**。候选仍在语义/弹窗收尾，此结论绑定本次记录的源码哈希；最终APK冻结后需核对增量，不能直接宣称任意后续115包通过。

本次仅只读源码、写审查证据；没有修改移动端/共享后端、操作模拟器、登录云端或提交业务。

## 基线和范围

- 基线：`yijiexing-passenger-20261023/delivery/yijiexing-passenger-1.1.4-source.zip`，前缀`passenger-android/`。
- 候选：`yijiexing-passenger-20261023/android`，读取时间与文件SHA在`passenger-115-compat-identity.json`。
- 既有源码变化：BookingScreens.kt、MainActivity.kt、OrderDetail.kt、ProfileScreens.kt；另app/build.gradle.kts版本/构建变化。
- 新增UiComponents.kt已全文审阅；包含MenuRow、ExpandablePanel、AddressRow、PassengerModal，非新增网络层。
- 证据：`passenger-115-compat.diff`和`passenger-115-compat-identity.json`。本审查未运行移动端单测/构建，也未绑定尚未冻结的APK。

## 关键行为核对

| 项目 | 审查结果 |
|---|---|
| API与身份 | PassengerStore.kt与114逐字节一致；API地址、乘客token/体验授权、响应读取、会话代次和退出隔离未变。AndroidManifest.xml逐字节一致，没有新增权限或导出入口 |
| 持久化幂等 | PendingRequestJournal.kt逐字节一致，PassengerStore.submitJournaledAction继续通过账户隔离日志向/api/passenger/action提交action/payload/requestId。UI没有新增直接绕过Store的HTTP提交 |
| 创建订单 | BookingScreen抽取submitOrder lambda，将主按钮移到Scaffold底部；原origin/destination坐标、kind、预约时间、city、remark、riderId、waypoints与destinationPending移除终点字段逻辑保留。活动订单分支仍打开当前行程，未强行创建第二笔；busy/submitting启用条件沿用原有逻辑 |
| 地址选择 | 体验点和收藏地址合并去重，仅调整候选显示；选择仍同时赋name/lat/lng。地址提交仍校验名称和WGS84坐标，再调用原save。高级坐标折叠不自动把地址名当坐标；页面明确名称不会自动换算坐标 |
| 竞价选择/取消/继续 | 选择司机仍order.selectDriver(id,driverId)，确认时!busy；WAIT_CONTINUE继续/结束仍order.continue type=1/2。取消/评价/投诉更换为PassengerModal，原action、id/reason/rating/content及长度条件完整保留，没有把司机行为代作乘客行为 |
| 支付失败恢复 | order.pay仍使用原Store日志；SUCCESS/FAILED/PENDING/CANCEL模拟选择保留。普通单payload仍id+outcome；没有新增本地“支付成功”伪状态或改成真实渠道 |
| NEG确认 | Negotiation.kt逐字节一致；QuoteSnapshot、quotationMatches与negotiatedPaymentPayload保留用户看见的version+amount校验。OrderDetail支付仍!busy && quoteCurrent，旧报价时提示关闭重核，未默默接受新金额。异议改用PassengerModal但仍传shownQuote.version/content并验证quoteCurrent |
| 金额/订单身份 | OrderFarePresentation.kt、OrderIdentity.kt逐字节一致，已选报价/参考估价区分和金额整数分格式未改。详情新增信息层级、展开路线和事件，不改变fare或ID |
| 评价投诉回复 | 评分按钮增加可访问语义，点击及语义点击均只设置同一rating；提交仍1..5控件原值、相同order.evaluate动作。投诉输入/长度/附件和回复读取保留，新增文字解释后台处理并未伪造回复 |
| 退出 | ProfileScreen重新分组设置项，退出仍展示原确认并调用store.logout；Store实现未变。没有新引入保留旧账号可提交页面的身份路径 |
| 新弹窗组件 | PassengerModal只透传onDismiss/onConfirm/confirmEnabled；使用Dialog、可滚动内容和固定按钮，不新增业务状态或自动确认。其尺寸、键盘/大字表现仍需专属UI审查，不能从代码存在推断实际可达 |

全量既有Kotlin中已知业务action字面量的多重集合与114一致，仅作为辅助证据；本结论同时建立在上述调用点、payload和启用条件的人工差异核对上。

## 非阻断边界与待实际回归

- 当前BookingScreen的submitting局部状态仍沿用原实现，没有因为抽取lambda新增真正的锁；重复提交保护主要依赖原busy和Store持久化请求日志。不得把本次重排描述为已新增更强幂等机制。
- 地址、取消、评价、投诉、异议、个人资料改用新弹窗，需要候选实际键盘和大字操作验证；退款/评价/后台回复须以同一订单回读确认。
- 支付弹窗及司机选择确认本次仍使用原AlertDialog。是否有遮挡由专属截图/设备审查判断，本审查不操作设备。
- 旧114已存在的业务限制/外部配置不属于本次UI差异缺陷，本轮不扩大到新的后端功能或重新广测全部外围模块。

冻结后增量审查优先核对本文身份/请求日志/付款版本等关键文件仍保持一致，以及新弹窗的confirmEnabled和提交payload没有再次变化。无需为当前差异修改冻结后台或后端。

## 1.1.5 最终冻结增量复核

已收到冻结候选并独立计算：`android/verification/1.1.5/app-debug-1.1.5-candidate.apk` 为8148421字节，SHA256 `4f9f2b40d435a905690a4675efaa52c721783e6eb69017eb14e704405bb217fd`，与负责人给定code7候选一致。源码本次记录中的所有既有和新增文件与初审SHA再次比较，**无增量变化**，因此上述静态兼容结论可绑定此冻结候选。结果见`passenger-115-frozen-identity.json`。

构建方报告16项测试通过、lint零error和同签名，本审查未重复执行测试、lint或签名验证。仍未进行设备、云端或实际UI操作。
