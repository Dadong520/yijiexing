# GRABB 后端作者交付（待root独立验收）

精确backend-only提交 **3bdb8f71743b30f83d65131bb90ed7485690b62b**，parent main29e83a8da4513859eaed073e0096ab1256a1a576，branch codex/order-competition-backend-20261101。唯一写树 /Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029；10个允许cloud-service产品/测试文件，提交后clean；保留原NAVI分支，未reset别人改动。中央contract供root同步，未在后端提交夹带root控制文件。

## 实现与契约
competition.py私有两表持久化round/deadline/route binding、候选入时定位/距离/进入时刻/本人结果。普通共享STANDARD/RESERVED首位有效候选开启固定5秒，微秒精度不改globalstamp；候选仍AVAILABLE无driverId，不返回假获派order。按入时报告位置直线距、entered、driverId稳定唯一获派；授予时复核online/三证/会员/偏好范围与现有单执行，多个窗口按截止/order稳定结算，预约待出发例外保持。定位只设备报告，非GPS防作弊证明。

HTTP先捕获本请求竞抢时刻，执行到期结算并SQLitecheckpoint，再执行业务；后续迟到accept409 rollback不丢winner，CloudHandler仍等待原GCS CAS成功才回复，CAS冲突503重读。无需常驻timer/保活，闲置期间只在后续API读写结算。

同requestId重放不续窗/重插/跨轮，投影回当前轮且roundChanged；新requestId重复不刷新快照。offline/logout/候选取消退出不复活；乘客取消/起终途经等binding变化使旧候选失效。获派普通单上车前取消同orderId/round+1/READY，已TRIP原拒绝；已付款异常普通单保守拒自动重开，避免隐改退款。保留旧金额/处罚、距离同意历史、真实联系人快照。BID/议价/私有示例原路径保持。

订单competition有round/status/deadline/serverNow/serverTime/selfStatus/myOutcome/remainingMs，data顶层competitionResults只本人最近20。无其他候选身份或完整位置公开。提醒键orderId+round；WON仅反馈，导航须刷新真实归属PICKUP/RESERVED与账号/order/round一致。旧包无round只首轮兼容候选，重开后拒绝缺round，不能宣称旧包已有竞抢界面。

合同：coordination/contracts/GRAB-20261101-01.md。root已批准发布边界：全部API tag名/URL保留但全部指向新已验版本/default100，退休共享DB旧业务writer；admin/IAM/其他配置保持，不加全局版本锁。同版本两个writer的CAS测试不外推混用旧代码安全。

## 实际作者自测
**全后端307/74.826s PASS，0fail/error**，其中新竞抢23项（定向5.451s PASS，为全套子集不累计）。完整命令、log/hash在IMPLEMENTATION.json，日志full-backend-307.log/competition-23.log。

真实HTTP/SQLite/MemoryCAS验证固定微秒5s/边界、多近者/同距进入及ID、重复快照/重启、late409后冷读assignment仍在、两服务同快照CAS200/503且唯一winner、多窗口资格重验、online/cert/会员/偏好、退出/取消新轮/旧请求、隐私/未获派无联系人、预约单执行及BID/private保持。直接service内存断言不替代HTTP持久化证据。

中间全298/10FAIL原日志保留：旧fixture未GPS/假即时获派/旧STANDARD续叫预期按新规则适配；既有策略fixture明确GPS并仅mock competition时间，实际HTTP结算后继续原金额/尾号/距离测试。竞抢新raw tests绕过helper，直接验证PENDING。原手动续叫投影回归改用BID，保留不变路径，不通过放宽生产规则掩盖失败。fixture部分负响应urllib ResourceWarning原样保留，最终所有断言PASS。

git diff --check与中央true-role门禁PASS。**作者自测不是独立验收**，acceptance NOT_RUN/releaseReady=false；没有云/真实privateDB/原生/真实GPS/资金/部署。root导入本准确提交与中央contract，独立审查运行后统一集成发布。
