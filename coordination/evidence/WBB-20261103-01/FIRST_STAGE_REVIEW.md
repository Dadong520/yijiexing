# WBB 第一阶段：报告 #1 / #2 / #10 核验

2026-11-03；backend；基线 `22dfb6a636f6dde1a73c2906252df3d5deffdd24`。
源码 `/Users/dongge/.codex/tmp/yijiexing-workbuddy-20261103`，分支 `codex/workbuddy-fixes-20261103`。开始 HEAD 精确/clean；结束 HEAD 未变，其他作者仅两处 build.gradle.kts 修改，本审查未改源码、任务或中央控制文件，无提交/部署/云业务请求。

## 结论

**报告三项所用证据比较了不同订单、不同乘客。不能接受其“系统性不同步/P0”和“同单金额不一致”结论。当前未发现这三项需要修改的后端业务规则。** 这不是全报告验收，也不证明所有原生 UI 同步场景无缺陷。

只读核验 coordinator 指定私有 SQLite 快照（mode=ro；不复制数据库、个人资料或凭据到证据）。以订单号、金额、起点、事件时序交叉对应原报告截图：

| 订单 | 匿名账号 | 创建时间 +08:00 | 金额 | 指定截图起点 | 快照/关键时序 |
|---|---|---|---|---|---|
| PORD054310c965c6 | 体验乘客02；无分配司机 | 11-03 16:38:46 | 2912分（¥29.12） | 长江瑞景，与p14一致 | AVAILABLE后17:12:32 PASSENGER_CANCEL；CANCELLED；无接单事件/无距离确认 |
| PORDdace876c1a09 | 体验乘客03；体验司机03 | 11-03 14:27:36 | 3305分（¥33.05） | 我的位置（手机定位），与d06一致 | 14:28:01分配，14:30:28上车确认后TRIP；17:11:30发起DROPOFF确认；17:14:30 EXPIRED；仍TRIP |

两单均 `shared=true, synthetic=false`。本次实际不是司机私有 seed 被误认共享，而是共享旧行程被当作另一乘客新单。司机私有 seed 确实存在，但不能拿它解释本次¥33.05单。

- **#1**：乘客02取消未分配订单，不会取消乘客03与司机03的另一段TRIP。该旧行程DROPOFF确认未获所属乘客同意，180秒超时后仍TRIP符合现有规则；截图剩67秒不是完整TTL。
- **#2**：乘客02这张AVAILABLE订单没有接单/分配事件；刷新继续“等待司机接单”符合快照。到达确认属于乘客03账号，不能出现在02订单。报告建议“增加轮询”基于不完整源码判断：现有前台STARTED生命周期已有5秒轮询；权限门禁/503可停自动刷新，网络失败可退避，忙碌时跳过。因此“存在轮询”不等于所有失败场景都保证5秒可见；本轮未做原生故障注入。
- **#10**：2912/3305分别为不同订单各自fare和estimatedFare，不能当作同单差价。不能以这组截图修改计费或强制两个账号的金额相同。

## 可复查代码位置（基线行号）

- `apps/cloud-service/passenger.py:55` shared_order读取唯一canonical行；`:59` own_passenger_order以orderId+passenger隔离。
- `passenger.py:173` save_shared写canonical；`:182` passenger_view；`:195` passenger_data只取当前乘客。
- `passenger.py:219` sync_shared_orders重建司机投影，`:234`起仅所属司机/可抢大厅订单可见，canonical fare随行投影；`:385` passenger取消限AVAILABLE/PREPAY/RESERVED/PICKUP/ARRIVED/WAIT_CONTINUE，TRIP取消409。
- `apps/cloud-service/trip_distance.py:122` refresh_distance_consent超时只使确认EXPIRED，不结束/取消行程；`:186`推进门禁，`:211`仅所属乘客能决定确认。
- `apps/cloud-service/domain.py:60`新司机initial_state私有演示单标synthetic=true；`passenger.py:223`私有单与共享投影区分，并给SYNTHETIC_DEMO_EXEMPT提示。两者不共用真实乘客确认。
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/MainActivity.kt:137`前台STARTED轮询，每5000ms refresh(true)。同目录PassengerStore.kt`:349`忙碌/刷新中/未登录/未配置/退避门禁，`:356`GET /api/passenger/state，`:248`成功恢复自动刷新，`:260`门禁/503停止及网络退避。

## 实测

实际执行（localhost临时HTTP/SQLite/MemoryCAS；无云账号、短信、真实GPS或金额）：

`python3 -m unittest -v test_passenger.PassengerIntegration.test_standard_shared_full_flow_payment_once_and_restart test_passenger.PassengerIntegration.test_optional_projection_stages_bid_selection_and_loser_visibility test_passenger.PassengerIntegration.test_roles_ownership_sandbox_and_idempotency test_trip_distance.TripDistanceHttp.test_real_http_ownership_pending_consent_atomic_progress_and_cold_reload`

在 `apps/cloud-service/tests`运行，**4/4 PASS，1.020s，退出0**。覆盖同单STANDARD全链路/进入TRIP后取消409/支付幂等重启、已选司机取消投影、跨角色/跨乘客拒绝、远距离确认归属/批准推进/冷启动恢复。测试自带显式虚拟GPS和竞争时钟推进，不是设备定位性能证据。预期HTTPError触发Python3.14 ResourceWarning，测试通过。

额外独立针对性HTTP检查：`python3 coordination/evidence/WBB-20261103-01/check_same_order.py`，**3/3断言组PASS**；实际输出见 `same_order_result.json`：同orderId两端PICKUP/整数金额一致；已分配STANDARD乘客取消后司机同单CANCELLED；未分配取消后从司机大厅移除。

记忆门禁 `python3 tools/check_coordination.py --memory-role backend --session-id 01a182c6-c058-76e1-9584-38abfe813799` PASS，context UNKNOWN（非业务验收）。

## 范围、后续

待合并SHA：NONE（诊断无源码修改）；发布：NOT_RUN/coordinator唯一负责。原生双端同orderId账号切换重测、网络故障注入、Claude候选差异独立审查、其余11项报告、APK/签名/CI/发布回读本轮均NOT_RUN。建议后续原生用同一乘客账号核对完整orderId，再查同步；可考虑展示短订单号/创建时间以免误认历史行程，属于UX建议，非本轮已证实业务缺陷。此阶段结束，不自行派单/轮询。
