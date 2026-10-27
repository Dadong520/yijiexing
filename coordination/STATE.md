# 宜捷行当前检查点 2026-10-27T22:55:43.299836+08:00
协议2026-10-26-v3；中央目录 /Users/dongge/Documents/New project 2/coordination。唯一coordinator 01a17cc7-e0a2-7791-bf1d-4611c09bab22，generation6；旧01a168e6-d76c-7ef0-8c87-706cc6e32d57退休，仅转达信息。根本轮一次可见压缩，已按CHECKPOINT_REQUIRED保存并定向恢复，现NORMAL；上下文遥测UNKNOWN。

## 当前源码与归属
父任务PM-20261027-01 ACTIVE_INTEGRATION。集成树/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027，branch codex/pm-amap-integration-20261027，基线main31cee06f415e51b0dcd8c05dd6f76e2fab311a76；HEAD0df8b187ac2f68c52fa6d17db846fbb8854b4eda，产品clean。
唯一可写后端/Users/dongge/.codex/tmp/yijiexing-admin-drafts-20261027/apps/cloud-service，branch codex/pm-trip-presentations-20261027，backend作者HEAD83c7c2a8c3e7cda54f3539e77f11ca6e72cdaab4。主集成树后端只读已验SHA集成，不能并改副本。司机candidates/driver-0.3.6-r2/android，乘客apps/passenger-android，小程序candidates/passenger-miniprogram-0.1.0；root维护前端和统一集成。旧树/release/frozen不改，不覆盖旧admin-password两份dirty报告。
backend隔离会话01a17ccb-1aa7-7773-a300-46743666876d闲置待非作者；frontend隔离会话01a17cd2-bcf7-7931-a94b-62119cefce48正在SDK还原和PMDEST独立差量审。旧长角色会话闲置。Gemini地区限制400/0token，IDLE不绕过，不改模型/账号/全局设置。

## 用户最新决定
BIZ06：取消新增无终点叫车。Android和mini去开关/新建flag，始终要求目的地；服务端拒绝destinationPending=true。保留历史单补填/完成，旧数据不删。PMDEST后端018ca49根4组HTTP/CAS PASS，已集成e01a040；客户端786f2bb mini62/Android20+build PASS，非作者实际shared.base流程及Android/WXML静态入口审PASS_SCOPE，Android/微信GUI未新增运行，未发布。
BIZ04：待出发预约保留、不阻即时单；明确开始接驾时双角色最多一段执行中，保留欠款门禁；不自动处罚/保证准时或擅设窗口。
BIZ05：暂无运价/分账/服务分/积分券规则，先草稿与手工记录，不自动计算/扣款扣分发奖。
高德双Android包名/SHA1用户已确认保存，勿重复索要Key。四值仅私有amap-20261027.json(0600)；认证state运行时分角色下发，JS安全码不下发。

## 有限验收与未完成
PMPA b792d0d：预约时间/补目的地/详情默认地图；20tests/build与旧完成单地图GUI PASS，特殊活动单GUI NOT_RUN。
PMBE5667940：出站尾号/缓存改派/退款优先结算，非作者15组PASS。PMRESbda490a0：非作者8组HTTP/CAS含真实并发/欠款/陈旧投影PASS。PMCL ee8b32f：driver59/passenger20/mini60，非作者实际数据流13组PASS；真实声音/后台提醒/OEM/部分AndroidGUI NOT_RUN。
PMAD e9a0a919+ea41f179：根非作者6组HTTP/CAS PASS，已集成5432693+3febc4d；32类仅可配置DRAFT/记录，不称32项完整业务。root admin.js 7ae3093+786f2bb：范围/参数限制/发生时间和fresh订单详情；真实表单GUI及非作者UI NOT_RUN。
PMPOST83c7c2a：作者68相关测试PASS，排行/直线接驾估算/登记电话投影；根非作者审核、客户端接入均NOT_RUN，未集成。合同/证据trip-presentations/。
PMMAP4a7ca46：官方11.2.100SDK checksum匹配，两端编译PASS，源码/解压APK四Key0命中，backend11组仅准0%预览。乘客地图10008/POI1008在保存绑定后仍复现；服务器内存核对角色Key均匹配。司机首次登录/地图显示且未见fatal，不等于SDK鉴权PASS。真实公共点校准/同单未做；0新云单。frontend还原与清理进行中，待回执。
后台矩阵evidence/PM-20261027-01/ADMIN_MODULE_MATRIX.json保留已有/部分/缺规则/缺服务边界。旧DOC图片NOT_REVIEWED，DOCX7图已查。WX缺AppID，微信编译/真机发布NOT_RUN。

## 线上和预览
只使用独立项目yijiexing-driver-demo-1023/us-west1，禁止原eztrips。API https://yijiexing-demo.hbzjhs.xyz，driver-demo-distance-8ec8419d 100%。0%预览driver-demo-pm-364078b4-qa1/tag pm-map-qa，https://pm-map-qa---driver-demo-phfgquzmua-uw.a.run.app，image sha256:7ea4a0d61b42bccd8b54dee8f13ec63389cecddd3c7c0f2ed017244765942fe8。旧流量/IAM/env/secret/SA保留，仅新增两AndroidENV；公开切流未获业务验收。
后台https://demo-admin.hbzjhs.xyz，driver-admin-password-c46f88bd-r3 100%，AUTH01 PASSWORD，无Google/IAP；session/RBAC/Origin/审计/限流保留，不改其他IAM。CloudRun automatic/min0/max1请求计费，不保活、不自动暂停。
已发司机0.3.12/code15、乘客1.1.12/code14仍旧包；原签名SHA2569ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f。最终版本号/合并SHA/本轮正式发布均待办。只读实际已有35笔共享单已私有备份/业务字段指纹，不能沿旧24误记；仅允许标记QA新增，当前0新增、不改旧35笔。

## 资源与下一步
frontend SDK资源已还原/关闭，ADB无设备/qemu无进程；原乘客包已还原、原无司机QA已卸。旧权限模式保留，新增4条拒绝/默认存储操作记录，无新授权；显示/同意还原后读回NOT_RUN，见PMMAP-resources-final.json。SDK/APK/缓存外部tmp不进Git。root无已知运行HTTP/Gradle；外部浏览器venv已安装阶段结束、尚未启服务。Gemini用户hub不终止。
PMDEST非作者逻辑范围PASS，SDK资源已回执清理，均保存明确未发布边界。后续PMPOST独立审核与客户端、后台GUI、SDK鉴权仍待，不能称三端完成。当前阶段后建议换新协调上下文再扩任务；交接入口START_HERE/短卡/NEW_CHAT_PROMPT，用户未请求新建则不擅建。
