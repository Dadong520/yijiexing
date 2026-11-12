# 后端独立审查阻断项修复与字段对齐

2026-11-12，backend session 01a182c6-c058-76e1-9584-38abfe813799。
基线/当前HEAD `c46479ff3d03831d23ca281046065294fd4f1b0a`，source `/Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029`，branch `claude/feedback3-20261110`。接单已有coordinator两处admin-console文件未提交修复，未改动它们；本轮仅六个apps/cloud-service文件变化。未reset/checkout/clean/stash/commit、未改两端Android、未部署或访问云。

**作者修复自测：344/344 PASS，85.138s，exit0。非作者复核仍待新候选冻结后执行，不能将本自测称独立验收PASS。**

## 修复内容

1. `admin_console.py` 的真实 `order.adjustFare` 入口：
   - 新计价订单先拒绝amount < serviceFee（409“调整后实付金额不能低于区域服务费”）。
   - 净实付n=amount-service；有绑定券时优先求券生效的trip=n+face，要求trip≥max(threshold,face)。满减重叠区间优先选合法可用券解；若n<threshold且无法用券，trip=n/coupon=0。
   - n=0、threshold≤face时，trip取min(max(threshold,原trip),face)，保留合法运价并限制抵扣≤trip。无绑定券按trip=n/coupon=0。
   - 最后重新校验coupon=min(face,trip)（仅绑定券且达门槛），以及非负整数trip、0≤coupon≤trip、trip+service-coupon==amount；否则409拒绝。金额确认/原版本检查和调价记录保留。
   - tripFare新计价订单支持合法0实付（allow_zero）；旧无tripFare的历史计价仍沿用原正金额限制，不改变其兼容边界。
2. `coupons.py`：最终抵扣为0时走release_order_coupon，不标USED；未过期退UNUSED且清订单绑定，过期转EXPIRED并UNFREEZE。领券中心补validStartAt，保留validEndAt和RANGE/DAYS。
3. `pricing.py`：PREPAY纳入已接单超免责分钟判定；119/120秒无责，121秒有责；RESERVED待出发豁免保持。
4. `domain.py` / `passenger.py`：司机fees返回权威cancelFee；state投影仅当前获派司机获得clone后的cancelFee，非当前司机为None。费用接口不能借此读取其他司机欠费数据。兼容共享但无tripFare的旧单分支。

## 测试

`tests/test_feedback3_phase2.py`新增6个测试方法（另两个fixture helper），本文件现29方法，总套件344方法：

- 真实AdminConsole.order_action + 同一Service/SQLite/CAS事务入口：原trip1200/service500/full coupon300/threshold1000/fare1400，调为400返回409且原记录不变；调为1000得到trip500/coupon0，支付后券退UNUSED而非USED，资金池used=0。
- 立减券face500、trip200、service500，调为500保持trip200/coupon200；调为1001得到trip1001/coupon500，支付后三方分账恒等；满减重叠净实付700优先trip1000/coupon300。
- 零抵扣且锁定券已过期，支付后EXPIRED并全额解冻，无虚假核销。
- PREPAY已选司机：119/120/121秒，超时取消300分空车费、欠费新单409、结清后钱包+300和新单恢复；司机HTTP fees/state分别验证UNPAID→PAID，非所属司机projection不含欠费；预约豁免单独断言。
- RANGE领券中心真实state包含有效起止时间。
- 0实付/立减门槛/满减门槛/零面额共44组真实AdminConsole逆向调价边界，逐次验证抵扣上限、门槛及实付等式。

实际命令：
- `python3 -m unittest discover -s tests -p test_feedback3_phase2.py`：第一轮针对性28项PASS，8.568s（当时尚未加最后44组边界方法），日志REVIEW_FIX_FOCUSED.log。
- **`python3 -m unittest discover -s tests`：344项PASS，85.138s**，精确最终日志REVIEW_FIX_FULL.log。
- `git diff --check -- .`（cloud-service）：PASS；中央memory gate恢复/结束PASS（context UNKNOWN，不是业务验收）。

仅本地真实HTTP、临时SQLite、MemoryCAS、虚拟时钟/位置，不连接真实资金、银行、税务、云环境；未构建Android或管理后台。六文件SHA256和日志哈希见REVIEW_FIX_SOURCE.json；这些是未提交工作区内容，不能用当前HEAD冒充修复已提交SHA。

## 交接

本轮文件：`apps/cloud-service/admin_console.py`、`coupons.py`、`domain.py`、`passenger.py`、`pricing.py`、`tests/test_feedback3_phase2.py`。
原NON_AUTHOR_BACKEND_REVIEW.md（P1/P2）保留不改；原338自测及非作者客户端/后台5项P2+1项P3报告也保留，各作者修复由coordinator统筹。请coordinator冻结新SHA后，安排frontend非作者复核这六个后端文件并独立验收关键金额，再统一集成/CI/合并/发布。本角色不自行提交/发布，不改STATE/BOARD/REGISTRY或其他作者文件。
