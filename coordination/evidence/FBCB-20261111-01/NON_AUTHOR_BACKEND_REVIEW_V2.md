# FBCB 后端增量非作者复核 V2：PASS

2026-11-12；frontend会话 `01a182c6-c475-7110-bbae-0deafe6d2385`。本角色没有编写后端，仅审查后端；本次未审查本人编写的Android。

- 精确候选：`6548ceca164f9abd74e516a577b2e78c5edb27a5`。
- 本轮差量基线：`c46479ff3d03831d23ca281046065294fd4f1b0a`；总基线 `571d939f0f7190e1d602b3b3a61f33ed5b203ffd`。
- sourceRoot `/Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029`；branch `claude/feedback3-20261110`。
- 六个变更文件：`admin_console.py`、`coupons.py`、`domain.py`、`passenger.py`、`pricing.py`、`tests/test_feedback3_phase2.py`（均位于apps/cloud-service）。完整增量已读，152增7删。
- 依据：中央BIZ-10/DEMO-01、第二阶段契约、首轮后端报告。原首轮报告保留。本轮范围无新增阻断项；首轮P1/P2在本候选已关闭。

## 逐项结论

| 项目 | 独立复核结果 |
|---|---|
| P1服务费下限 | PASS。admin_console.py:178-198先要求实付amount>=serviceFee，合法后才更新订单。原1400调400且服务费500的入口现在409，存储订单未改变。 |
| P1券门槛/抵扣上限 | PASS。按实付逆推运价，重算trip>=threshold时的min(face,trip)，并断言trip>=0、0<=coupon<=trip、trip+service-coupon==amount。满减重叠区间明确优先可用券解；直接券净额0保留合法原运价且上限裁剪；无券走净额。独立480组真实order_action调用满足约束和分账恒等式。 |
| 0抵扣退券 | PASS。coupons.py:163-166在核销前走release_order_coupon。未过期锁定券回UNUSED/orderId=None，仍保留资金池冻结以备下次使用；已过期锁定券变EXPIRED并解冻面额，无USED/used金额虚增。回归实际执行调价→支付→卡券/资金池检查。 |
| P2 PREPAY有责 | PASS。pricing.py:253-261包含PREPAY，仍按acceptedAt超freeCancelMinutes判断。119/120秒无责、121秒有责、未付债务阻新单、结清入账、恢复叫车均在回归通过。RESERVED待出发仍无责，未改变BIZ-04。 |
| validStartAt | PASS。coupons.py:183将RANGE模板起始和截止时间同时投影到couponCenter；新增回归核验validity/start/end精确相等。 |
| cancelFee归属 | PASS。passenger.py:114-115只向当前受派司机返回权威订单空车费；非受派清空。domain.py:366-373费用接口在现有账户订单访问检查后返回同一权威cancelFee。独立真实本地HTTP额外验证UNPAID/PAID两状态：受派费用200可读；另一授权测试司机state不含此订单、fees均404。 |
| 分账守恒 | PASS。分账公式本轮未改；独立480组调价结果逐组检查两条分值恒等式。29项回归还覆盖确认示例、D正/负/零、优惠使用、退款、公司账户与其他第二阶段场景。 |

## 实际命令与结果

1. 中央目录执行：
   `python3 tools/check_coordination.py --memory-role frontend --session-id 01a182c6-c475-7110-bbae-0deafe6d2385`
   接单与结束均PASS；context UNKNOWN/HANDOFF_RECOMMENDED。该门禁仅检查协调资料。
2. sourceRoot：`git rev-parse HEAD`、`git branch --show-current`、`git status --short`；开始和结束候选精确一致，branch正确，工作区clean。
3. sourceRoot：`git diff --check c46479ff3d03831d23ca281046065294fd4f1b0a 6548ceca164f9abd74e516a577b2e78c5edb27a5 -- apps/cloud-service`，exit0。
4. apps/cloud-service目录：
   `PYTHONDONTWRITEBYTECODE=1 python3 -B -m unittest discover -s tests -p test_feedback3_phase2.py`
   exit0，实际输出 `Ran 29 tests in 5.723s` / `OK`。测试使用本地HTTP+临时SQLite+MemoryCAS，tearDown关闭服务并清理临时目录，不接云端/真实账户服务。
5. sourceRoot内联脚本：`PYTHONDONTWRITEBYTECODE=1 python3 -B - <<'PY' ... PY`，生产Service(':memory:') + AdminConsole.__new__（仅省略后台账号bootstrap，order_action/save_shared/audit为真实实现）。独立输入矩阵：
   - serviceFee∈[0,1,500]，threshold∈[0,1,300,1000]，face∈[0,1,300,500]，目标净实付∈[0,1,299,300,499,500,699,700,999,1000]。
   - 每组使用合法旧运价max(1200,threshold,face)和正确旧券额，重置内存行，再以确认版本/金额调用order.adjustFare；断言新券额按门槛重算且不超运价、实付不低于服务费、实付恒等式、版本+1，以及settlement_split两条恒等式。
   - 480组通过；有serviceFee时再调至serviceFee-1，320组均409且存储行不变。
   - 实际输出：`{"independentAdjustCases":480,"rejectedBelowService":320,"splitIdentities":"PASS"}`。
6. 独立本地HTTP附加验证（同样python -B内联，复用Phase2夹具而非修改源码）：创建BID→司机报价→乘客选司机→acceptedAt回退3分钟→乘客取消；另一测试司机登录，检查其state/fees；乘客结清后重复费用隔离检查。实际输出：
   `INDEPENDENT_HTTP_OWNER_ISOLATION: PASS (UNPAID/PAID owner visible, stranger state absent + fees404)`，exit0。

执行说明：第5与第6首次组合调用时，第5的480/320组已全部通过；第6最初使用自造非白名单测试手机号，登录403，组合脚本exit1。这是附加验证的夹具输入错误，未发生业务断言失败，也未绕过白名单。随后改用make_cloud_server默认参数中既有的第二个授权测试账号，仅重跑第6，以上UNPAID/PAID隔离验证exit0。两次均finally清理夹具。没有把首次失败记作整段成功。

## 边界及交付

本结论是指定后端六文件增量的独立复核PASS，结合首轮源码审查关闭两个已知阻断项；不等于三端原生/云发布整体验收。本人Android修复需backend独立审查。真实旧SQLite快照迁移、本轮全量后端套件、Android设备UI、真实银行/支付/税务/推送、部署后回读均NOT_RUN。本轮未改工作树任何源码/跟踪文件/git状态，不提交/推送/发布，只写本报告及frontend短卡。没有常驻资源或待合并新commit，待合并候选由root统一安排。
