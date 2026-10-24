# BE-20261025-01：营销与调度加价最小持久化契约建议

2026-10-25，Asia/Singapore。负责人 backend。仅设计，未实现、未部署；业务验收 NOT_RUN。建议由协调者批准后另开实施任务。

基线 main `e89f90b0a63b72dc04fca5b3690a8897de1543c7`；审查目标 `fd44ec08d674900cd0b30d3d3f25cd0fc46f1b36`。以下行号都指独立 Git 目录 `/Users/dongge/Documents/yijiexing-antigravity` 中该目标提交，不依赖之后工作区改动。完整摘录及哈希见 `source-evidence.json`。

## 1. 已核实差距

路径简称：M=`candidates/passenger-miniprogram-0.1.0/miniprogram`；B=`candidates/shared-backend-v8/cloud-service`。

| 源码位置 | 实际行为及影响 |
| --- | --- |
| M/pages/home/index.js:66、183–201 | 用固定 2800 分匹配券；创建传 `priorityFee/couponId/couponDiscount`，成功后只在客户端冻结券。 |
| B/passenger.py:153–189 | 按坐标计算体验估价；既不读取也不保存上述三个字段。AST 核对输入读取与订单字面量均证实，金额还是原始估价。B 子树与 main 基线完全一致。 |
| B/passenger.py:213–224、306–312 | 竞价支付 PREPAY→PICKUP、线下议价 PREPAY→TRIP；并非完单。预付订单到行程结束才 DONE；普通单先 WAIT_PAY 再付款到 DONE。 |
| M/pages/order-detail/index.js:184–194 | 每次支付成功后本地核券、发 50 分及金额积分，错误覆盖 PREPAY。没有服务端事件唯一键。 |
| M/pages/order-detail/index.js:205–211；M/lib/coupons.js:201–225 | 取消后未检查动作失败便退券，并可兜底另一张 CPN_NEW10；释放不校验订单绑定，核销不校验冻结状态或所属订单。 |
| M/lib/coupons.js:3、101–129、240–265 | 设备固定存储键；资格不检查有效期，城市使用名称子串；任意非空兑换码可在本地造券。 |
| M/lib/points.js:3–11、60–87 | 设备固定存储键，默认 380 分和邀请统计；无法证明为当前账户的服务端资产。 |
| B/domain.py:130–136；B/admin_console.py:144–162 | 司机按 `fare` 入账；退款按 `fare` 全退/冲司机余额。加券后若仍照搬，会把优惠当现金退款或少付司机补贴。当前没有营销账本。 |
| B/passenger.py:245–254、320–321 | 司机取消可先模拟退款，进入 WAIT_CONTINUE 后同 ID 继续叫车并清除本轮支付。新增账务必须区分同订单的结算轮次。 |

客户端注释提到《宜捷行优惠券开发V-20260914.xlsx》及“原团队特殊规则”，本轮在当前工作区按优惠券/积分/调度/营销文件名搜索未找到该原始文档，未据注释认定业务已批准。原乘客独立小程序源码未找到的既有边界仍成立。本轮未扩大搜索用户其他目录。

## 2. 不可放到客户端的约束

- 资产所有者由有效乘客会话得到的 `passengerId` 决定，忽略或拒绝 body 中所有 owner/passengerId。券实例查询必须同时过滤当前账户；他人券/订单返回 404，不泄露余额。司机投影只包含自身应收与加价，不包含乘客积分、券资产或其他账户资料。
- 每张发放券使用服务端随机实例 ID，模板 ID 与实例 ID 分开；不能将所有账户共用 CPN_NEW10 当作同一资产。客户端缓存以环境+passengerId 隔离，退出/切账户即时清空视图；服务端始终为权威，不导入旧本地券、380 积分或邀请人数。
- 金额统一整数分，积分为独立整数单位；拒绝布尔、负数、小数、非有限数及越界值。`couponDiscount` 不作为可写金额。城市按服务端城市/区县编码及明确隶属规则匹配，不能使用名称 contains。
- 资格校验使用服务端时间 `validFrom <= now < expiresAt`、模板版本、城市、订单类型、本人、状态和门槛。下单时在同一事务再次校验，过期/已被另一单冻结不能继续提交。
- 未部署能力返回 `features.marketing=false` 或具体 unsupported，不展示已到账/已优惠。即使请求里金额为零，也不能将有非空券 ID 的新功能请求静默当成功忽略。

## 3. 金额口径与兼容

建议首期只允许已知目的地的 STANDARD/RESERVED；BID、NEGOTIATED 和待补终点先不启用营销/加价，保留原流程。其他组合需业务批准再开放，不能先套用 2800 分。

服务端报价返回：

```json
{
  "quoteId": "opaque-account-owned-id",
  "expiresAt": "UTC ISO time",
  "pricing": {
    "baseFareCents": 3000,
    "priorityFeeCents": 300,
    "couponDiscountCents": 1000,
    "driverGrossCents": 3300,
    "passengerPayableCents": 2300,
    "platformSubsidyCents": 1000,
    "couponInstanceId": "CPNI_...",
    "policyVersion": 1
  },
  "paymentMode": "SIMULATED"
}
```

这是建议字段/示例，不代表这笔优惠已存在。建议规则：满减基数=运费本金；加价不凑门槛且不参与折扣；`gross=base+priority`，`payable=gross-discount`，`driverGross=payable+subsidy`。一单一券，不叠加；折扣只抵本金，结果夹在 0 与本金之间。百分比用整数万分比和明确半入规则计算，禁止不同端分别浮点四舍五入。折扣细则、封顶与补贴承担方仍待确认。

原 `fare` 保留“约定运费/司机应收毛额”口径，旧单无营销时所有值与原来相同。新单增 `pricing` 快照，不能把 `fare` 静默改成乘客净付导致司机少收。营销订单支付请求必须同时携带原 `expectedFare`、`fareVersion` 与新 `expectedPayable`；旧客户端缺净付确认时返回 `PRICING_CONFIRMATION_REQUIRED`，不按毛额扣款。司机侧展示毛额与加价，乘客侧主要展示净付及抵扣明细。

沿用并扩大 `fareVersion`：调价、改路线、加价、券/补贴变化均递增，包括 A→B→A。不另造平行的费用版本。付款校验金额+版本+轮次；NEG 原 `quoteVersion/amount` 仍保留，当前不接券。退款/发票模拟回执取实际已支付净额减已退款净额，后台毛额、现金、补贴分列，不再以旧 `fare` 充当全部统计口径。

“调度加价”是否获得优先排序、谁收取、最高值及取消费尚未获证实。建议配置城市级整数分白名单/上限，默认不开；真实排序未实现前不能承诺更快派车或接单保障。已启用时该加价必须写进同一共享订单，司机、乘客和后台读取相同数值。

## 4. 最少接口扩展

继续使用现有鉴权、`GET /api/passenger/state` 与 `POST /api/passenger/action`，不增公共资产写入口。以下均为提案，当前后端尚不支持。

| 动作/查询 | 输入 | 服务端结果/限制 |
| --- | --- | --- |
| state 增量 | 无 owner 参数 | 自己的 coupons、points balance/ledger 游标、营销能力和政策版本；已有 state 字段兼容。大列表分页，不把全账户账本混入 state。 |
| `order.quote` | 现有完整行程字段、`priorityFeeCents`、可空 `couponInstanceId` | 持久化本人 quote、行程规范化指纹、政策/金额快照、到期时间；可返回可用券及拒绝原因。报价不冻结券，不产生积分。 |
| `order.create` 扩展 | 现有行程字段 + `quoteId` | 指纹、账户、时效和政策一致才创建；quote 最多绑定一个订单，换 requestId 也不能再造第二单。创建订单+消耗 quote+冻结券+幂等结果同事务。原旧请求只在无营销模式下兼容。旧字段 `priorityFee/couponDiscount` 或有券无 quote 明确拒绝，不能忽略。 |
| `order.pay` 扩展 | `id/expectedFare/expectedPayable/fareVersion/settlementCycle/outcome` | 当前仍模拟；服务端写支付回执，已预付只记支付，不奖励。保留原 requestId 重试。 |
| `coupon.claim` | 服务端已配置活动兑换码 | 校验活动有效、资格、库存和每账户次数；同活动发放唯一实例。任意字符串不造券。没有活动配置则未开放。 |

state 中每张券必须带 instanceId/status/version/validFrom/expiresAt/适用规则及绑定状态；积分 history 来自账本，初始 0，未配置规则时不发。邀请页只回服务端已核实事件；不接受客户端提交“好友已完成首单”。首期不开放积分兑换/抵扣，直到规则批准。

沿用乘客动作信封 `{action,payload,requestId}` 与当前 JSON 错误包。建议明确 `details.reason`：`PROMOTION_NOT_SUPPORTED`、`COUPON_NOT_ELIGIBLE`、`COUPON_EXPIRED`、`COUPON_RESERVED`、`QUOTE_EXPIRED`、`QUOTE_STALE`、`PRICING_CONFIRMATION_REQUIRED`、`FARE_CONFIRMATION_STALE`、`IDEMPOTENCY_CONFLICT`。鉴权 401/403，未知本人资产 404，参数 400，状态冲突 409；网络/CAS 503 继续精确重试原请求。

## 5. 事务、持久化与事件

新增最少实体建议：版本化 promotion_policies；passenger_coupons（owner、实例、来源发放键、状态/版本/绑定单）；pricing_quotes；order_pricing（唯一 orderId、冻结快照、fareVersion、settlementCycle）；payment/refund/driver/subsidy 的不可变资金事件；单独 points_ledger。积分余额可汇总 ledger，不依赖设备初始值。发券唯一键 `(passengerId,campaignId,grantOrdinal)`，单券仅一个有效绑定；所有表加 owner/订单索引。

同一事务必须覆盖订单、券、回执、司机收入、补贴、积分与 `passenger_actions` 幂等结果。保持现有 Service.lock + SQLite rollback + GCS generation CAS；只有 CAS 持久化成功才返回成功。冲突/响应丢失重启后凭同一 `(passengerId,requestId)` 恢复；同键不同 action/payload 返回冲突。不能先回成功再异步上传内存资产。

requestId 只能去重一次调用，还需业务唯一键处理换新 requestId 的重放：成功收款 `(orderId,settlementCycle,CAPTURE)` 只能一次，另记唯一 paymentId，不能靠换 paymentId 重收；结算 `(orderId,settlementCycle,SETTLE)`；奖励 `(passengerId,orderId,ruleVersion,rewardType)`；退款以 refundId 唯一且引用原 paymentId；每个奖励撤销引用原 rewardEventId，最多一次。资金事件必须保存实际收取/退款净额、司机毛额和补贴，累计退款不能超过该轮已收净额。请求幂等结果应先于 quote 时效重验返回，已成功请求的精确重试不能因 quote 后来过期而变成失败。

| 权威事件 | 券与积分动作 |
| --- | --- |
| 创建成功 | AVAILABLE→FROZEN，绑定本人实例与本订单；失败/事务回滚不冻结。 |
| PREPAY 支付成功 | 只生成该轮支付回执；券仍 FROZEN，完成奖励=0。FAILED/PENDING/CANCEL 无到账无奖励。 |
| 行程结束但 WAIT_PAY | 记真实行程结束事件，券仍冻结，无完单结算奖励；乘客欠费门禁与司机可接新单规则不变。 |
| 行程结束且该轮付款成功 | 两个条件无论先后顺序，都进入同一个 settle_once：FROZEN→CONSUMED、司机毛额入账、平台补贴记账、规则化奖励一次。预付单由司机完单触发，晚付单由付款触发。 |
| 最终取消/超时/后台取消 | 各入口共用 cancel_once；仅释放本订单绑定券，无绑定即无动作，过期券回 EXPIRED。已预付则退实际净付，有补贴预留才释放，不冲记尚未入账的补贴；不凭客户端回调退券。 |
| 已结算后全退 | 原支付退款、司机/补贴冲销、原奖励反向账本和券处理同事务，不删原账。若后续允许部分退款，必须先定义按比例/券退还规则，首期明确不支持。 |

司机取消进入 WAIT_CONTINUE 不是最终关闭。推荐券保持与该订单绑定，到“继续/最终取消”决定再处理；每次已退款后继续需递增 `settlementCycle`，重新报价确认，历史回执保留，不能复用旧轮付款/退款/奖励键。若原业务确定“司机取消立即退券”，继续时必须重新选券、重新冻结与确认净额，不能自动取别单券。该分歧需批准后再实现。

已消费积分如何退款追回，需定义负余额/待抵扣策略；首期不开放消费，仍使用冲正事件而非删除历史。邀请奖励只由服务端核实注册/首个已结算订单后产生，以 invitee+campaign+event 去重；本轮不承诺完成邀请系统。

## 6. 待确认的业务规则（实施门禁）

1. 找回并确认优惠券原需求版本；一次一券/叠加、BID/NEG/无终点是否禁用，区域适用按哪一地及预约券的时效口径。
2. 券冻结后跨自然到期是否仍有效；取消后过期券是否延长；WAIT_CONTINUE 保持冻结还是立即退券，重新呼叫的优惠是否沿用。
3. 加价是小费、调度费还是运价组成；收款方、城市档位/上限、是否可改、是否保证调度排序及退款承担方。
4. 优惠由平台或司机承担；上述平台补贴公式仅建议，不能未经确认让司机承担或虚构平台实付。
5. 完单固定分与每元积分是否叠加、门槛/舍入/封顶、是否按净实付、邀请首单/自邀限制。50 分、380 初始积分及本地拉新统计都未作为批准规则。
6. 全退券是否可返、退款责任、积分冲正和后续部分退款政策；完成口径必须同时有行程结束与支付结算，不能使用支付按钮点击。

规则未定的能力保持关闭；修复现有订单操作回归和误导展示不依赖这些规则，不应等待本契约落地。

## 7. 唯一后端目录与迁移建议

下一实施任务建议仅写 `/Users/dongge/Documents/yijiexing-antigravity/services/backend/`，独立分支由协调者创建/确认；本轮不创建该目录。先从已核对 v8 `candidates/shared-backend-v8/cloud-service` 复制到该目录并逐文件验哈希，记录来源提交与 schema 版本；冻结副本、releases、旧工作区 cloud-service 不再并行修改。

把线上管理浏览器适配 `candidates/admin-v8/yijiexing-admin-20261023/backend/iap_browser.py` 纳入统一构建，完整管理 Web 仍按其负责人源目录构建叠加。测试 IAP 签名和原 RBAC，不能用旧管理原型覆盖当前浏览器入口。

数据库只做带版本的增量 CREATE/ALTER；不删表、不恢复旧快照、不回填客户端模拟资产。新账务只作用于明确启用后的新单，旧无营销单保持原口径，保留 A/B 和原司机欠费数据。迁移前后核对已有订单数量/关键金额；在临时数据库证明旧单无优惠时兼容、新表保留、重启/CAS 并发重放不丢账。

手机与后台共用快照，但旧 admin 仍可取消/退款绕过营销账本。必须两服务都采用理解新账务的业务版本后才开启营销开关；先以开关关闭部署兼容层，再统一验收开启，保留 IAM/IAP、automatic/min0/max1 和请求计费。只回滚镜像不能撤销已发券/已结算事实，必须保持新 schema 可读及关营销入口，账务错误走可审计冲正。部署仍由 coordinator 唯一安排。

## 8. 下一实施任务的最少验收

账户 A/B 同设备及不同设备资产隔离；同券并发下两单仅一单冻结；过期/跨城/BID/无终点拒绝；客户端伪造折扣或加价边界不改权威金额；取消请求失败不退券；所有取消入口与 WAIT_CONTINUE 多轮不串券；PREPAY 成功积分为零、完单一次结算；WAIT_PAY 晚付不推进司机新单；改价 A→B→A 拒绝旧确认；新 requestId 重放不重复奖励；CAS 503/冷启动后原请求恢复；全退净额/司机/补贴/积分四方核对；原生旧客户端和管理浏览器/RBAC 回归。以上均 **NOT_RUN**，不能用既有 159 项或客户端 50 项替代。

本轮实际运行仅 `verify_contract_sources.py` 的 4 组源码断言和协调文件结构检查。没有访问云、启动模拟器、读取账户数据库、安装依赖或派生代理。独立业务复核/合并 SHA/发布：NOT_RUN，由后续协调者任务安排。
