# 宜捷行乘客端源码与原功能核对

核对日期：2026-10-23（Asia/Singapore）。本报告仅依据本机文件；未连接原测试环境、未读取原服务密钥。

## 结论与搜索边界

**截至本次核对，未找到独立乘客小程序源码。** 因此可以按原 Java 业务接口和新共享订单契约开发原生乘客 App，但不能声称已逐页还原或已证实覆盖原乘客小程序全部页面、交互和隐藏规则。

- 递归检查 `/Users/dongge/Documents`、`/Users/dongge/Downloads`（含企业微信下载缓存）的普通和隐藏文件名，查找 `app.json`、`pages.json`、`project.config.json` 及 passenger/乘客/小程序/miniapp/mini-program/hsdd-app 相关工程文件。
- 成功检查 80 个 ZIP 的中央目录；其中仅司机 `hsdd-driver-app/src/pages.json` 命中小程序/uni-app 路由清单。回收站内 3 个 `.zip` 实为不可解析文件，不计入成功检查。未递归解开压缩包中的二次压缩包；不代表整个磁盘或远端仓库不存在。
- `Downloads/WXWork Files/Caches/Files/2026-10/69957716e20c3dec710de86c8732e25a/hsdd-driver-app.zip` 是司机 uni-app 工程，1728 条归档项。解压工程位于 `driver-native-assessment-20261023/hsdd-driver-app/`，不是乘客工程。
- `Downloads/WXWork Files/Caches/Files/2026-10/f5ae95c5057b84f12a53b79bb46ba688/hsdd.zip` 是 Java 后端，18552 条归档项，没有上述前端入口清单。
- `宜捷行(1).zip` 是培训/销售资料，两个 `宜捷行推广*.zip` 是商业/推进文档；相关原生体验版交付包是司机工程。其余命中的 `pages.json` 属于公务车文档底稿。
- RAR/7z/其他归档只检查文件名，未发现宜捷行/hsdd/乘客源码相关命名；不把这些文件名排查当作完整内容检查。

## 原 Java 功能证据矩阵

下表“存在”表示在源码里存在入口或数据结构；不表示已在原环境运行通过，也不表示新 App 已测试通过。源码基目录：`yijiexing-native-v2/backend-source/hsdd/`；下文 controller 简写对应 `hsdd-ride/hsdd-ride-biz/src/main/java/cn/hbzjhs/hsdd/ride/controller/`。

| 功能 | 原始依据 | 新 App / 体验后端验收要求 |
|---|---|---|
| 乘客身份 | `app/SysUserAppController.java:26` 微信快捷登录；app-server `AppMobileController.java:48` 短信入口 | 独立乘客会话，与司机 token 隔离；体验验证码明确未发短信 |
| 即刻/预约/代叫/电话/线下议价/竞价 | API 模块 `enums/OrderEnum.java:17-23`；`req/order/BaseAppOrderReq.java` | 主闭环必须真实共享订单；原订单类型需逐项披露已实现、受外部配置限制、未覆盖 |
| 下单/订单历史/活动订单 | `app/HsOrderAppController.java:45-67` | 服务端身份归属、重复提交幂等、重启恢复；不能靠司机沙箱事件生成 |
| 预约时间/途经点/备注/乘车人 | `BaseAppOrderReq.java:48,72-110`（最多 2 途经点）；`HsOrderAppController.java:107-115` | 预约未来校验；途经点状态同步；代叫乘车人姓名、电话与账号区分 |
| 竞价报价/选司机 | `HsOrderAppController.java:71-85` | 多司机报价，同单选择；价格/评分/距离排序；超时以服务器时间校验 |
| 接驾/到达/行程/完单 | `HsOrderController.java:172-194`；API `OrderStatusEnum.java:17-26` | 双模拟器看同订单各阶段；订单行程状态与支付状态分离 |
| 取消/续单 | `HsOrderAppController.java:122-142`；`HsOrderCancelController.java:39-57` | 呼叫、预付、接驾不同取消分支；取消后双方一致；模拟退款明确标注；原续单规则须核对 |
| 费用/支付/退款 | `HsOrderAppController.java:232-234`；`HsOrderPayController.java:29-38` | 整数分与服务端费用权威；模拟支付/退款不可显示为真实收款 |
| 评价/投诉/拉黑 | `HsOrderAppController.java:147-155`；`HsCommentReq.java:109-128` | 本人订单；评分 1～5；防重复；投诉记录持续可见；处理状态不伪装人工已处理 |
| 地图/司机位置/路线/ETA | `HsOrderAppController.java:161-206` | 真定位、地图底图、路线估算、示意轨迹分别标识；缺地图导航配置单列 |
| 常用/历史地址 | `HsCommonAddressController.java:57-69`；`HsOrderAppController.java:213-228` | 按乘客隔离新增/修改/删除；订单可复用地址 |
| 乘车人管理 | `HsPassengerController.java:56-89` | 本人名下乘车人 CRUD；下单可选择；不可操作他人记录 |
| 发票抬头/申请/历史 | `HsInvoiceController.java:41-94`；`HsInvoiceApplyController.java:63-65,91-137` | 可保存抬头/申请/查询；缺开票渠道不能称已开正式发票，体验回执明确模拟 |
| 问题反馈 | `HsProblemFeedbackController.java:49-57,106-109` | 提交/历史/状态持久化；未接人工处理须展示待处理 |
| 公告/客服电话 | `HsNoticeController.java:67-70`；`app/HsCompanyCustomerServicePhoneAppController.java:25-27` | 展示公告与客服配置状态，不捏造真实客服电话 |
| 区域功能控制 | `app/HsPassengerFunctionAppController.java:33-36` | 展示当前体验支持能力和缺配置项；城市作用域隔离 |

## 核心约束与需重点补齐的差异

原 `HsOrderAppServiceImpl.java` 中可直接看到竞价订单所属用户校验（790、949 行附近）、重复下单防护（701）、订单竞价成交费用权威拆分（1004）、重复评价（568）及重复投诉（633）防护。新服务不能只补 UI，应保留这些约束。

验收优先顺序：
1. P0：乘客身份 → 创建持久化订单 → 司机看见 → 抢单或报价 → 乘客选司机 → 接驾/核验 → 行程/途经点 → 完单/费用 → 模拟支付 → 评价投诉；取消与模拟退款各阶段测试。
2. P0：鉴权隔离、他人订单拒绝、同单并发抢/选只能一个成功、幂等支付不重复入账、断线/重登后同单状态恢复、服务暂停/恢复。
3. P1：预约、代叫乘车人、常用/历史地址、备注、反馈、发票抬头/模拟申请、公告/客服/能力清单。
4. 必须逐项披露：无终点补目的地、电话叫车、线下议价扫码/异议、续单、竞价超时/排序、投诉附件/拉黑、发票重开等原入口功能；没有实现或没有实际测试就不能写“全部功能完成”。

## 外部依赖与验证界线

真实支付渠道按任务要求排除；模拟支付/退款必须全程标识。短信、地图路线规划、电话呼叫、厂商推送、人工客服和真实发票依赖外部配置/服务，须单独展示配置状态，不能用模拟数据伪装已连通。新原生工程和审计材料在独立 `yijiexing-passenger-20261023/`，共享体验服务改动由后端负责人协调；司机工程不得被乘客实现覆盖。

本报告不是测试报告。最终实际验证项以双模拟器截图、接口自动化结果和反重力独立报告为准。

## 后续实现对齐补充

已继续核对原 `HsOrderAppServiceImpl.java:437-500`：原“续单”指司机取消后订单进入待续单，乘客选择继续则同一订单清空司机关联重新呼叫，选择不继续则取消并按规则退款；不是将历史完成行程再次下单。原 `create-call-phone-order` 是服务端入口，缺乘客前端路由证据时不能直接断言为乘客 App 必须存在的独立页面；电话渠道、语音坐席需单列外部服务边界。

现已提供独立 `passenger_extras.py`、`order_options.py` 给后端负责人整合，具体契约见 `EXTRAS_API_CONTRACT.md`。功能包括乘车人、开票抬头、模拟回执、公告/客服配置状态、屏蔽司机、订单备注、本人投诉图片附件、竞价期限到期取消。其余订单主状态（无终点、续单、代叫）由共享后端负责人统一实现。此段为实现交接记录，不代替最终运行/截图验收。
