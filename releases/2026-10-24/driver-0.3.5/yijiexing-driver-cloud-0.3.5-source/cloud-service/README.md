# v7后端精确源码快照（035配套）

原22文件从034最终归档原字节保留，与provenance/backend-v7-source-manifest.json逐项一致；本README不在22文件内。035只调整司机界面，后端业务冻结。原manifest、Build来源、部署快照及归档hash保持原文，采集时点不等于当前实时云检查。

<!-- RELEASE_FACTS_START -->
035最终业务与发布核验：主任务已确认联合及公网证据。

联合验证：司机035r3/code8与乘客1.1.5/code7完成本轮联合新单。STANDARD PORD97596bf0cc68：错误尾号被拒、模拟支付失败未入账，成功后DONE仅增加2926分，余额52848→55774分，评价5星；司机投诉CP59d6e38495d3与乘客投诉PCa6f2b8b98513的后台回复已在对应App实际可见。BID PORD2a42ed9559c3：模拟预付3600分，00:49:46司机取消即时模拟退款3600分，乘客结束后最终CANCELLED；余额55774分不增，DRIVER_CANCEL与PASSENGER_CONTINUE各一次。后台权威/台账61断言passed，属于数据读回，不替代APK实际UI。新BID司机费用页未另截，早先只读退款费用页不算本新单证据。乘客owner的cleanup记录确认司机下线、两设备字号1.0并退桌面；组包代理未操作设备。以上均为沙箱模拟，未做真实资金或道路行驶验证。

公网发布：主任务已于2026-10-24 00:54:08.950881+08:00完成公开下载地址全量GET：HTTP200、系统TLS证书验证通过、54,757,323字节，SHA256 6b9620a237286002662a966a31ba110f50d3a13c2ca4c113961792b32ee0b4ea与冻结r3一致；包名cn.hbzjhs.dc.driver.cloud、0.3.5-cloud/code8、签名SHA256 9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f兼容性核对通过。后端未重新部署、原测试环境未改变；本次源码组包代理只读取主任务证据，未下载/上传/操作云端。

Antigravity本轮仍为BLOCKED_PERMISSION_NOT_TESTED、0次UI操作，不计作通过。详细限定和证据hash见provenance/final-release-facts.json。
<!-- RELEASE_FACTS_END -->

r3已有40项JUnit通过；Codex有范围的UI回归详见顶层说明，Antigravity权限阻塞且0次UI动作，不得写通过。Python3.12由cloud_server.py启动，CLOUD_ROLE区分driver/admin，GCS generation条件保护SQLite快照；admin沿用IAM私有。访问码、有效会话、管理密码、云凭据不入包。乘客App和独立管理前端另交付，合同原文保留编写时点参考。

## “今日”经营数的已知演示限制

冻结v7的 `cloud-service/seed.py:22` 将 `todayIncome` 初始化为11000分（¥110.00）、`todayAccepted=2`、`todayCompleted=2`。`domain.py` 的 `data()`没有按自然日期重算或清零；接单及 `credit()`继续累加已有值。因此App首页标为“今日”的收入、接单/完成数量实际是**含合成初始值的演示累计值**，不能作为按日经营报表、真实经营统计或财务依据。本次不修改冻结后端或Android业务代码；余额、支付、退款等同样沿用模拟沙箱边界。

支付、退款、收入、短信、提现、认证等仍为模拟/未接入；不代表真实机构资金或认证。物理扫码及到期/重发/跨账号/断网等完整边界不因源码整理获得验收。
