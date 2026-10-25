# 宜捷行后台原功能与独立体验能力核对

核对日期：2026-10-23（Asia/Singapore）。本文件记录开发开始时的源码基线，**不是最终实现或验收结论**。后续新增后台的结果以验收记录为准。

## 来源与查找结论

1. 原 Java：`yijiexing-native-v2/backend-source/hsdd/`。重点为 `hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/`、`hsdd-upms/hsdd-upms-biz/src/main/java/cn/hbzjhs/hsdd/admin/controller/`。共抽取 122 个 ride/upms Controller（包含移动端），详见 [接口索引](JAVA_INTERFACE_INVENTORY.md)。源码可证实业务接口存在，不能证明其已在 Google Cloud 部署。
2. 菜单权限：`db/2hsddx.sql` 和 `db/0hsddx_boot.sql` 各有 228 条 `sys_menu` 种子。含权限管理、用户、菜单、角色及 `sys_user_add/edit/del/export` 等按钮标识；Controller 还有 `@HasPermission`、`@PreAuthorize`。详见 [菜单索引](ORIGINAL_MENU_INVENTORY.md)。未读取或导入原账号数据，未复制任何密钥。
3. **没有找到完整原后台前端源码。** 对项目根目录递归检索 `package.json`、前端路由/菜单文件，并检查现有三个交付 ZIP 目录：`outputs/宜捷行安卓原生联调版_完整交付_20261023.zip`、`outputs/宜捷行安卓原生体验版_20261023/宜捷行司机端_安卓原生工程_0.1.0.zip`、`outputs/宜捷行谷歌云体验版_20261023/源码与部署工具.zip`。后者含已有演示管理静态页；这些包未发现原系统后台工程。另有 Gradle 工具 ZIP，不是业务源码包。此结论仅覆盖所给工作目录及其中压缩包，不断言其他磁盘位置不存在。
4. 找到两个可参考 Web 来源：`yijiexing-native-v2/reference-benefits/app/src/App.jsx` / `store.js`（司机权益管理原型，不能视作完整原后台），`yijiexing-cloud-20261023/cloud-service/static/`（体验管理页）。`driver-native-assessment-20261023/hsdd-driver-app/` 是原司机 UniApp，不是后台 Web。
5. 当前体验服务：`cloud-service/{domain,admin,benefits,passenger,server,cloud_server,snapshot}.py`。共享订单契约：`yijiexing-passenger-20261023/API_CONTRACT.md`。乘客分支正在修改，源码状态不等于线上部署状态；本核对未访问原测试/生产环境。

## 功能对照

“已有”指本次阅读时已有独立体验源码；“需补齐”是本后台任务的差距；“缺少依据”指不能直接确定正式规则；“缺外部配置”不妨碍先实现模拟或本地流程。

| 模块 | 原功能依据 | 体验后端已有 | 需补齐/缺少依据/缺外部配置 |
|---|---|---|---|
| 管理账号、角色和权限 | upms `SysUserController /user`、`SysRoleController /role`、`SysMenuController /menu`；用户角色菜单种子及按钮权限 | 管理独立令牌、Cloud Run IAM 外围保护；演练 actor | 需独立管理员账号、服务端权限校验、启停/角色分配、会话；不能把浏览器 actor 字段当认证。缺原组织/岗位授权映射，不导入原用户 |
| 司机档案 | `HsDriverController /hsDriver/page,add,enable,getDriverInfo,editById,export` | accounts.profile.driver、账号与司机状态 | 需列表筛选详情、受限编辑、审计；禁用是否影响在途订单应先保留在途并限制新业务，正式规则待确认 |
| 车辆与绑定 | `HsVehicleController /hsVehicle/page,add,edit,enable,export`，司机 bundleCar/unbundleCar | 司机 profile 中车牌与车型 | 需展示/编辑真实档案及审计；原多车多司机绑定、公司范围数据权限缺完整规则，不能假装已复刻 |
| 资料资质审核 | `HsDriverCertificateApplicationController`、司机 certificateExpire/page/update、DriverProfileAdminController | 附件文件表、certificate.audit，只允许 PENDING，退回原因、审核记录，司机状态回写 | 后台查看附件、通过/退回、日期校验；外部证照/实名验证未接通，人工体验审核不等于真实核验 |
| 乘客账户与乘车人 | `HsPassengerUserController /hsPassengerUser/page,enable,export`、`HsPassengerController` | passengers、乘客独立 sessions、profile；乘车人扩展由乘客任务负责 | 需后台查询、状态管理、审计，与乘客任务统一兼容；缺真实实名/短信配置 |
| 订单列表和详情 | `HsOrderController /hsOrder/page,orderDetail,nodeList,bidDetail,export` | passenger_orders 为 canonical，司机端投影；标准/预约/竞价状态流 | 需直接查 canonical，展示乘客/司机/原始ID/状态/金额/事件。旧 accounts.state 中私有演示单必须单独标识，不能充当共享三端验收 |
| 报价、选择司机及行程 | `HsOrderController bidDetail,nodeList`、`HsDriverGrabOrderController`、`HsOfflineNegotiationController` | BID报价、选司机锁定金额、尾号核验、途经点、到达、完成 | 后台报价和事件明细；不能以后台造数据替代双 App 操作。真实地图导航缺配置 |
| 取消与异常订单 | `HsOrderController cancelOrder,cancelExport`、`HsOrderCancelController`、`HsOrderEarlyCompleteRecordController` | passenger/driver 出发前取消；预付取消模拟退款；TRIP/结束态禁止普通取消 | 后台受限取消、确认原因、审计与幂等；行程中强制结束、爽约扣款、取消次数惩罚缺适用正式规则，先明确不自动执行 |
| 费用和支付 | `HsOrderFeeController`、`HsOrderPayController`、`HsPaymentRecordController`、`HsOrderTransactionRecordController` | 整数分估价/报价、模拟SUCCESS/FAILED/PENDING/CANCEL、付款后完成及司机入账 | 支付记录/失败尝试/费用明细、调整前后值与受限状态；模拟标识。动态计价、附加费、优惠/分账完整正式算法缺确认；真实支付不在本轮范围 |
| 退款和提现 | `HsRefundRecordController /hsRefundRecord/page,export`、`HsWithdrawalRecordController`、对账Controller | 取消预付单模拟退款；司机提现PENDING审核、驳回返沙箱余额，重复处理409 | 后台可追溯查询/受限模拟退款审核；要核对司机钱包已结算和余额防双返。会员退款/转级折算只生成待规则确认任务，禁止误标退款已完成 |
| 评价投诉与反馈 | `HsCommentController /hsComment/page,handle,admin/detail,admin/score-preview,export`、`HsProblemFeedbackController page,handle,export` | 共享订单评价/投诉、司机投诉反馈、乘客反馈持久化；旧后台仅司机 feedback.reply | 补共享订单投诉及乘客反馈处理与双端可见结果。原扣分预览、处罚等级/申诉机制规则缺确认，不能自行扣分或封号 |
| 城市区域 | `HsRegionController`、`HsAreaConfigController driver-config,passenger-config,order-config`、HsAreaAppFunctionController | CITIES静态字典，全国/区域/城市配置继承、草稿、版本、批量、定时 | 后台统一列表与有效配置；需区分当前城市字典与原完整行政区。CITIES.drivers/paidDrivers为演示常量，禁止用作实际经营统计 |
| 司机会员权益 | reference-benefits需求与原型、现 benefits/admin | 试用/普通/VIP、全国/地区继承、价格、开关、模拟购买、订阅、停售二级审批、定时及版本冲突 | 后台适配这些能力；正式折扣/升级差额/权益退款未确认，现有策略保留权益并建待办。二级审批必须不同真实管理身份 |
| 经营统计 | HsDailyOrderStatsController、HsBusinessDataScreenController、DriverOperationStatController | 可由持久化共享订单/账户/支付派生；旧城市常量不能用 | 定义日期时区、创建/完成口径、净实收/退款、取消率、模拟收入；取消和私有演示数据不能误记营收 |
| 导出 | 多Controller export及原按钮权限 | 已有城市配置CSV | 补订单、账户、费用投诉日志等适用导出，按筛选/权限执行；金额注明分或元；防CSV公式注入、手机号隐私 |
| 操作日志 | SysLogController `/log`、SysAuditLogController `/audit`、配置变更记录 | audits(actor,action,before_value,after_value,created) | 管理员、目标、原因、前后值、时间、结果可查；不得记录明文密码/token；业务事件与管理审计需关联 |
| 短信 | 原短信相关类/订单催收任务 | 白名单、回显体验验证码、失效/尝试限制 | 缺供应商账户、模板签名、授权与费用配置；当前无真实短信 |
| 地图/定位/导航 | 原地图与轨迹接口、司机定位 | 双端坐标、司机位置、示意路线 | 缺地图授权Key/包签名/真实路径计费配置；坐标连线不是导航，统计距离是体验估算 |
| 推送/语音/外呼 | HsPushController、NlsController、GuoJiaoCall*Controller | 状态轮询/端内展示，已有司机本地提示能力需App实测 | 缺外部推送证书/厂商服务/外呼供应商，不能声称送达 |
| 发票 | HsInvoiceApplyController/HsInvoiceController/FptController、重开审核 | 司机会员模拟发票；乘客extras由乘客任务开发 | 缺税控/开票供应商授权及税务配置；模拟申请/记录可验证，真实发票未接 |

## 数据与状态约束建议

- 持久化链路是 SQLite + Google Cloud Storage 快照，`SnapshotCoordinator` 负责刷新/条件写入和冲突处理。管理员和司机/乘客服务应继续共享同一对象，不能另建互不相通的后台数据库来宣称联动。保留既有快照与旧表。
- 订单统一使用 `passenger_orders.id`，参与账户分别来自 `passengers` 和 `accounts`。driver.orders 是投影，同一共享订单不得按每名司机累计。
- STANDARD：AVAILABLE→PICKUP→ARRIVED→TRIP→WAIT_PAY→DONE；RESERVED 多 RESERVED；BID：AVAILABLE/报价→PREPAY→PICKUP→ARRIVED→TRIP→DONE。司机未中标视图的 BID_SENT 是显示态，不能改成 canonical 订单阶段。
- 所有金额用整数分传输，UI统一格式化为元。确认支付是模拟成功，营业收入不能称“真实资金实收”。退款须保留原支付事件，不覆写成“从未支付”。
- 统计默认仅共享订单；建议创建时间作为订单量口径，DONE作为完成量，净模拟收款=成功模拟收款−成功模拟退款，标注日期范围及北京时间/新加坡时间UTC+8。
- 管理操作应经服务端权限、状态、幂等校验。客户端隐藏按钮不构成权限。人工干预必须有确认和原因；不明规则应记录待确认，不随意修改已结算金额。

## 待业务确认项（不阻塞普通流程）

1. 正式组织、角色和按城市/公司数据范围；本轮最小权限体验角色是否保留。
2. 行程中强制终止、乘客失联、异常付款恢复的收费、司机补偿和责任划分。
3. 已支付/已结算后的费用调整权限、退款比例、司机余额不足处理和二次审核规则。
4. 取消次数、爽约、投诉扣分、封禁、申诉及解除规则。
5. 会员停售后的退款折算、转级差额、首年折扣实际适用条件。
6. 实名、短信、地图、推送、开票供应商配置、授权主体及预算；本轮仅真实支付渠道明确排除，其他缺配置项须如实列阻塞。

本文件仅由源码核对生成，不把缺配置或未测试项目标记已完成。
