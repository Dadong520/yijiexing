# FBCB-20261111-01 后端第二阶段实现与自测

最新：金额阶梯口径修正完成；338/338全量自测通过，独立验收待coordinator。

2026-11-12；backend真实session `01a182c6-c058-76e1-9584-38abfe813799`。
工作树 `/Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029`，分支 `claude/feedback3-20261110`，接单与交付HEAD `22e60230edebf3a53d5f9ace48c08aa4eb4ced11`。中央契约 `coordination/contracts/FBC-20261111-01.md` 与 DECISIONS BIZ-10 为依据。接单时即有Claude五个新模块和其余未提交后端WIP，本次在其上增量补齐，未覆盖或重置。未执行reset/checkout/clean/stash/commit；无提交SHA，未部署。

## 完成内容

- 混入InvoiceOpsMixin，接乘客重开申请与列表。模拟冲红、管理员重开、乘客申请审核（通过/驳回）、公司月度技术服务费汇总和模拟开票均接到既有事务/CAS。冲红后可重开且只重开一次，保留原新关联。
- LIVE_OPS_ACTIONS合并EXTRA_ACTIONS和公司分账动作，OpsExtras统一委托并audit；公司保存支持旧参数及可选地图费/平台比例/抬头/税号，未传保留；新旧积分规则兼容。
- 新增company_split.py：税号前置、版本控制、模拟SBVA开户/换绑/查询/提现；只存银行卡脱敏尾号，audit不含原卡号。绑定公司司机上线、接新单/报价、线下议价、竞抢进入与最终资格复核均签约门禁，未绑公司不受影响。完单按唯一订单流水键入公司佣金，重复调用不重复入账；提现扣可用余额并记录流水；退款冲回公司分账，余额不足明确拒绝人工处理，不造负余额。
- 乘客operations.pricing/cityPricing与司机company.splitAccountStatus接线，公开公司投影不泄漏商户税号/分账银行信息。后台所有约定运营配置/明细字段已暴露，敏感资金集合受payments.read、拉新受passengers.read、服务分明细受drivers.read约束。
- orderSettlements数据集：payments.read权限、settledAt日期、city/companyId/关键字筛选、汇总及CSV；开票查询/导出增加RED_FLUSHED及冲红重开字段。
- 订单timeline优先实际定位接驾/计价里程，回退显式标注；费用分列预估运价/运价/区域服务费/优惠/应付实付/空车费/退款/司机share及平台差额等；钱包待入账按司机分账额。已退款分账记录显式标记REFUNDED，月技术费排除退款。

## 审查中修复的边界

1. 标准司机取消重开、BID司机取消WAIT_CONTINUE、乘客和后台取消均退券；重开/续叫清除旧券绑定和优惠，避免一券解锁后还在另一单产生折扣。
2. 满减门槛在最终运价下降时再次校验；券仅抵运价费，服务费不被抵扣。领券检查领取窗口；资金池三余额非负且守恒；过期解冻/完单差额释放幂等。
3. 重复拉新奖励防护、空白邀请码、最多五档参数/类型、累计奖励重复人数及服务分范围重叠检查；服务分等级必须五个对象，非法嵌套返回业务错误而非500。
4. 投诉服务分把本次尚未保存的投诉纳入周期计数；支持0.5分档评价；在线时长加分和扣分同时执行，每日只评一次。
5. GPS异常速度跳点更新基准持久化；空车费credit重复防护与expectedAmount严格整数；冲红后可重开但不可重复。
6. 技术服务费模拟发票税额改用Decimal四舍五入（避免Python round半分向偶数舍入），增加3分/20%税额半分边界断言。
7. 既有driver投影settlementUpdatedAt每次读取stamp导致跨秒只读比较不稳定；改用订单持久updatedAt/completedAt/createdAt，不因刷新伪造变更。

## 本轮18个修改/新增文件

全部相对源码根 `apps/cloud-service/`：
`admin_console.py`、`admin_extras.py`、`admin_queries.py`、`company_split.py`（新增）、`competition.py`、`coupons.py`、`domain.py`、`growth.py`、`invoice_ops.py`、`live_operations.py`、`ops_extras.py`、`passenger.py`、`passenger_extras.py`、`pricing.py`、`tests/test_feedback3.py`、`tests/test_feedback3_phase2.py`（新增）、`tests/test_live_operations.py`、`tests/test_passenger_extras.py`。

pricing/coupons/growth/invoice_ops/ops_extras原为Claude未提交新文件，本人进行了边界修正；不要将它们误认本轮从零重写。其他接单已有WIP（admin.py、engagement.py、trip_distance.py、policy_helpers及若干旧测试）均保留。源码逐文件hash见SOURCE_MANIFEST.json，适用于交付时未提交工作区；不把HEAD当这些未提交内容的版本证明。

## 测试与证据

新增 `tests/test_feedback3_phase2.py` 共23个测试方法，含参数化子场景：
- 1000/500/50分、各50%精确对账：订单/实付1500，运价手续费6/2，实付手续费9/2，D=3，K=497，公司248，平台299，司机942，扫呗入账1491/分账1489；正/零/负D恒等式多组验证。
- 夜间05:00包含/05:01排除，回空和夜间附加按当前实现相加、距离单元向上取整；实际meter、低于12km/h并等候和预估回退。
- 空车费预付扣退款、未付欠款阻断新单、错金额拒绝、结清入司机钱包、重复支付/credit保护。
- 多方取消退券、重开无残留优惠、满减最终门槛、仅抵运价、资金池充值冻结/过期/差额、精确5000/500元阈值、日内警报各一次、熔断和领取时间。
- 新人券仅首次注册、乘客固定与月订单阶梯+金额积分并存、司机日/夜、注册/首单拉新、100人上限、阶梯+累计奖励与重复防护。
- 24h接单时段覆盖、非法等级/缺时段、在线加扣并存、半分档评分/5分、当前周期投诉计数。
- 公司税号门禁、所有新接资格、换绑新SBVA/脱敏、唯一入账、超额提现与版本冲突、退款冲回；发票跨人隔离、重复申请、通过/驳回、冲红重开、月技术费开票；后台权限/日期搜索汇总导出。

实际记录：
- phase2-final.log：21项PASS，3.779s（后又在完整套件中包含新增非法等级子断言）。
- full-second.log：331项PASS，79.531s，当时含16个新增方法。
- full-verified.log：336项PASS，80.187s。
- full-verified-final.log：336项PASS，80.599s，包含非法等级防护子断言；最后补负司机入账保护测试后完整命令 `python3 -m unittest discover -s tests` 另见 full-handoff.log：337项PASS，80.822s；税额半分边界修复后最终精确源码完整测试另记 FINAL_TEST.log，FINAL_TEST.log税额夹具漏id导致1错误，已补齐夹具；最新完整测试见FINAL_VERIFIED_TEST.log：**337/337 PASS，81.001s，退出0**。
- git diff --check -- .：通过；memory gate最后结果单独记录到任务。
- 初始full-initial.log：315方法中6个旧纯夹具缺新mixin、1个门禁断言取错错误字段，已适配修正；不是生产规则失败。phase2-initial/third中测试夹具缺role/fareVersion等定位修复保留。full-final.log两次运行重叠输出已作废，并出现旧投影跨秒不稳定失败；不作为最终验收证据。后续全量使用独立full-verified*.log，未掩盖早期失败。

## 交付限制与独立验收

仅本地真实HTTP+临时SQLite+MemoryCAS/虚拟GPS/测试时钟，未调用云环境、真实扫呗、银行、税务或群推送；无Android/管理后台集成构建、真实设备或发布验收。作者自测不等于独立业务验收；任务应READY_FOR_REVIEW、acceptance NOT_RUN，由coordinator安排非作者复核并统一合并/发布。

2026-11-12追加确认已收到：coordinator核对《未命名.rtf》第6条及用户“6、按默认”，AMOUNT阶梯也按**当月完单序号**选档。growth.py已修正为 `tier_points(rule['tiers'], month_completed + 1) * (fare // 100)`（排除本单ID），FIXED仍为固定每元倍率；ORDER和司机DAY/NIGHT保持既有月序号。此前待确认项已关闭，旧337项日志保留为修正前阶段证据。新增月内第一单/第二单跨档、不同乘客独立计数、跨月归零和不足1元不给分测试；最新全量 `python3 -m unittest discover -s tests` 见 AMOUNT_TIER_FINAL_TEST.log：**338/338 PASS，82.45s，退出0**。

地图服务费可配置至100000分，若公式产生负driverAmount则模拟支付结算明确409 SETTLEMENT_NEGATIVE_DRIVER_AMOUNT并事务回滚，提示修正配置；不擅自clamp以破坏精确恒等式，新增测试确认无负钱包及公司流水写入。
