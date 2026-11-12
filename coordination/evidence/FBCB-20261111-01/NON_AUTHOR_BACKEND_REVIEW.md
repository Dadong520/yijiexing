# FBCB 非作者后端审查：BLOCKING

- 日期：2026-11-12，frontend 会话 `01a182c6-c475-7110-bbae-0deafe6d2385`。
- 作者独立性：本人仅参与双 Android 实现，未编写本次后端；本报告不验收本人 Android。
- 精确候选：`c46479ff3d03831d23ca281046065294fd4f1b0a`。
- 比较基线：`571d939f0f7190e1d602b3b3a61f33ed5b203ffd`。
- sourceRoot：`/Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029`；branch `claude/feedback3-20261110`。
- 范围：该区间 `apps/cloud-service/**` 全部30个变更文件，含新增模块、接线和测试差异；依据中央 BIZ-10、DEMO-01 和第二阶段接口契约。
- 开始/结束复核 HEAD 均为上述候选，工作区 clean；未修改源文件/git/真实数据库，未启动服务或发布。只写本报告及 frontend 短卡。

## 阻断项

### [P1] 人工调价保留原券额，可抵扣服务费并绕过满减门槛

位置：`apps/cloud-service/admin_console.py:176-181`，重点179-181。

`order.adjustFare` 将输入直接设为 fare，再反推 `tripFare = amount - serviceFee + couponAmount`；未重新校验券的 threshold，也未将抵扣限制在新 tripFare 内。此路径绕过 `apply_order_price` 的券门槛/上限校验。

真实 AdminConsole.order_action + SQLite `:memory:` 独立复现：合法待支付订单 tripFare=1200、serviceFee=500、couponAmount=300、couponFaceValue=300、couponThreshold=1000、fare=1400；提交已确认/正确旧金额和版本的 adjustFare(amount=400)。实际成功保存并返回：

```json
{"fare":400,"tripFare":200,"serviceFee":500,"couponAmount":300,"couponThreshold":1000}
```

新运价200低于满减门槛1000，抵扣300还大于运价200，实付400低于区域服务费500。与“券仅抵运价费”直接冲突，后续结算可使用这一非法金额。不能因两条分账恒等式仍成立而放行。

修复要求：统一人工调价与券计价约束，明确调价后的运价和实付含义；拒绝非法组合或重算合法券额/实付，并保持版本确认。补真实 AdminConsole 调价入口回归，覆盖低于服务费、跌破券门槛、抵扣上限及后续支付。现有 `test_full_reduction_threshold_rechecked_after_final_fare_changes` 只调用计价函数，未覆盖此入口。

### [P2] 已接单超过免责时长的 PREPAY 订单取消免空车费

位置：`apps/cloud-service/pricing.py:253-261`（254行阶段白名单）；关联 `passenger.py:384`、`398-402`。

乘客选中报价司机时已写 driverId、acceptedAt 并进入 PREPAY；乘客取消允许 PREPAY。但责任判定在阶段白名单处直接返回 False，完全不读 acceptedAt。BIZ-10“接单超2分钟或已到达取消属乘客有责”未列待预付例外。

独立内存边界复现：固定配置 emptyFee=300、freeCancelMinutes=2；同一 BID 订单已有司机且 acceptedAt 为当前时间前3分钟、paid=False。调用生产责任判定再调用生产 apply_cancellation：

```json
{"stage":"PREPAY","responsible":false,"cancelFee":null}
{"stage":"PICKUP","responsible":true,"cancelFee":{"amount":300,"status":"UNPAID","responsible":"PASSENGER"}}
```

因此延迟预付后取消可绕过空车费及欠费新单门禁。此复现是生产策略函数级验证，未声称完整 HTTP 流程实测。

修复要求：将确已接单的 PREPAY 纳入已确认的免责时长判断；保留 BIZ-04 待出发预约既有豁免。补已选司机且超过/未超过2分钟的 PREPAY 取消、欠费阻新单及结清重试回归。

## 已检查与证据边界

| 主题 | 结论及边界 |
|---|---|
| 精确分账 | 独立运行500组整数分组合，覆盖 D<0、D=0、D>0、券0/半额/全运价、平台比例0/1/50/99/100；两条恒等式均通过。确认示例 driver942/company248/platform299/D3/scanSplit1489（分）。不抵消上述非法金额输入问题。 |
| 计价/空车费 | 读取定位计费、低速等候、夜间/回空、回退标记、取消退款/待付/结清与一次入账；PREPAY 缺口见P2。 |
| 公司账户与旧账号 | 源码检查已绑定公司上线/接单/抢单/报价门禁、未绑定兼容、开户换绑版本与掩码、唯一流水及回退；未接银行/扫呗。 |
| 优惠券资金池 | 源码检查 total=available+frozen+used、领券冻结、使用差额归还、取消退券、过期解冻、预警/熔断一次标记；人工调价绕行见P1。 |
| 积分/拉新/服务分 | 源码检查 AMOUNT 按月完单序号选阶梯后乘整元实付、100名奖励上限、稳定标记避免重复、服务分五级/时段/日评估/半星及仅展示规则；本轮未重跑完整场景。 |
| 发票 | 源码检查冲红、重开、乘客申请归属及审核版本、技术服务费按公司月度汇总与金额确认，均模拟。 |
| SQLite | 新表采用 IF NOT EXISTS、旧订单字段回退/credited 防重复路径已读；候选 Service(':memory:') 初始化成功。真实旧 SQLite 快照升级 NOT_RUN，不把空库初始化当历史迁移验证。 |
| 接口/后台 | 已读新增乘客/司机字段投影、动作、管理查询/权限/导出与附件归属改动；双 Android/后台实际联调、原生交互 NOT_RUN。 |

本轮不重复作者338项全量套件；作者自测见任务记录，不能冒充本轮独立执行。无真实支付、银行、税务、消息推送或外部服务验证。

## 实际执行

1. `python3 tools/check_coordination.py --memory-role frontend --session-id 01a182c6-c475-7110-bbae-0deafe6d2385`：恢复与结束均 PASS（结构门禁不等于业务验收）。
2. sourceRoot 内 `git rev-parse HEAD`、`git status --short`；定向读取差异及相关调用链：候选一致、clean。
3. sourceRoot 内 `PYTHONDONTWRITEBYTECODE=1 python3 -B - <<'PY' ... PY`：退出0；只导入候选生产模块并使用内存 SQLite，无HTTP/文件数据库/业务源码写入。核心复现如下，可直接在同目录执行：

```python
import sys, datetime as dt
sys.path.insert(0, 'apps/cloud-service')
from domain import Service
from admin_console import AdminConsole
from common import dumps, stamp, now
from pricing import DEFAULT_TARIFF, settlement_split
s = Service(':memory:')
o = dict(id='REVIEW-ADJUST', passengerId='LOCAL', driverId=None,
    kind='STANDARD', city='420500', stage='WAIT_PAY', paid=False, credited=False,
    tripFare=1200, serviceFee=500, couponAmount=300, couponFaceValue=300,
    couponThreshold=1000, couponId='LOCAL-CPN', fare=1400, fareVersion=1,
    events=[], createdAt=stamp())
s.db.execute('INSERT INTO passenger_orders VALUES (?,?,?,?,?)',
    (o['id'], 'LOCAL', None, dumps(o), stamp()))
c = AdminConsole.__new__(AdminConsole); c.svc = s; c.db = s.db
r = c.order_action('order.adjustFare', dict(id=o['id'], confirmed=True,
    reason='独立内存复现', expectedStage='WAIT_PAY', expectedFare=1400,
    fareVersion=1, amount=400), 'ADMIN:LOCAL-REVIEW')['order']
assert r['couponAmount'] > r['tripFare'] and r['fare'] < r['serviceFee']
s.tariff = lambda city: {**DEFAULT_TARIFF, 'emptyFee': 300}
for stage in ['PREPAY', 'PICKUP']:
    q = dict(id='CANCEL-'+stage, driverId='LOCAL-DRIVER', stage=stage,
        kind='BID', city='420500', acceptedAt=stamp(now()-dt.timedelta(minutes=3)),
        paid=False, events=[])
    responsible = s.passenger_cancel_responsible(q)
    q['stage'] = 'CANCELLED'; s.apply_cancellation(q, 'PASSENGER', responsible)
    assert responsible == (stage == 'PICKUP')
count = 0; signs = set()
for a in [1,2,99,100,999,1000,10001]:
    for service in [0,1,5,500,10000]:
        for coupon in sorted({0,a//2,a}):
            for rate in [0,1,50,99,100]:
                x = settlement_split(a,service,coupon,0,rate)
                assert x['scanPaySplit'] == x['driverAmount']+x['companyCommission']+x['platformCommission']
                assert a+service == x['driverAmount']+x['companyCommission']+x['platformCommission']+x['platformDifference']+x['tripPayFee']+x['tripSplitFee']
                signs.add((x['platformDifference']>0)-(x['platformDifference']<0)); count += 1
assert count == 500 and signs == {-1,0,1}
x = settlement_split(1000,500,0,50,50)
assert [x[k] for k in ['driverAmount','companyCommission','platformCommission','platformDifference','scanPaySplit']] == [942,248,299,3,1489]
s.close()
```

## 下一步

协调者安排后端作者修复两个阻断项并提供新精确SHA和针对性测试，再由非作者复核。当前候选不满足业务验收 PASS，本角色不安排合并或发布。后端代码、Android代码及其他角色文件均未修改。
