# 当前检查点 2026-10-26T09:57:19.748903+08:00：后台只用账号密码
协议2026-10-26-v3。中央控制目录 /Users/dongge/Documents/New project 2/coordination；先START_HERE/STATE/BOARD/SESSION/本角色卡，不翻历史。当前三个旧会话仍HANDOFF_RECOMMENDED、上下文UNKNOWN；下一大任务应用户新开总管，显式登记并退休旧ID，不自动创建。

## 最新用户决定与运行状态
用户明确取消后台Google登录，只有后台账号密码（DECISIONS AUTH-01），替代此前driver-admin保留IAP要求；其他服务不改。
AUTH-20261026-02 sourceRoot /Users/dongge/.codex/tmp/yijiexing-admin-password-20261026 branch codex/admin-password-only-20261026，固定产品c46f88bd848346196283b90d92977120962ce00c，PR26 merge25f8c9eb29f55a3182b5253fed463d66f75e4a55。
已发布driver-admin-password-c46f88bd-r3 100%，image e51df78b00868abba60d6a1dae267f6b5db48eab366828850c5f9aee510a8109。服务ADMIN_AUTH_MODE=PASSWORD；IAP=false、Invoker检查关闭；只公开登录页/静态资源，后台数据与操作仍要求session/RBAC/Origin，账户失败限流和审计保留。无其他IAM扩权，SA/secretRefs/其他env不变。CloudRun automatic/min0/max1按请求计费，无保活/手工暂停。
管理https://demo-admin.hbzjhs.xyz，使用原管理账号密码，无需Google；备用https://driver-admin-491829048305.us-west1.run.app。两地址匿名200无跳转，state/query/export匿名401。原admin无Google/cookie真实TLS登录200→state200→退出200→旧会话401。24笔共享单ID/状态/金额/已付退款不变。18作者+5root非作者HTTP测试通过，frontend独立匿名落地页PASS，完整点击登录/退出/刷新NOT_RUN；实际HTTPS账号登录/退出已PASS，范围看AUTH报告。
早两次部署已自动回退，原因是云端停用IAP会撤销其专用Invoker grant并更新etag/version；最终只容许这项撤销，其余策略一致。不要重复执行旧部署脚本。回退需先恢复IAP+Invoker检查再旧revision；详AUTH报告。

## 已发App/业务（无须重发）
唯一业务后端/Users/dongge/.codex/tmp/yijiexing-trip-api-20261025/apps/cloud-service产品8ec8419d30955c216d7e4fe90451584d2a9622cf，API driver-demo-distance-8ec100%不变。APIhttps://yijiexing-demo.hbzjhs.xyz，原demo.hbzjhs.xyz演示不动。
Native源/Users/dongge/.codex/tmp/yijiexing-native-permissions-20261025，产品3e9f6ee943948e1081ec9ab0102eef803d447c8b；driver0312/code15 SHA bce9fcff928b3690525f14a6ae5f3a4e074fc6f9ca9bf491733e4b1609852a85，pass1112/code14 SHA61417866fce94d9176ff74e42718d6ce60fa658064e289bf8e0dccfae10257d8，原签名保留，/download和/download-passenger免访问码。PR23/24原发布证据仍有效，唯独旧报告的“当前后台需Google”已被本轮替代。
同单PORDf4f910a233dc为DONE/29.26模拟已付；接送300m默认/50..5000可配及超距本人同意仍在。原backend208、driver55/pass20/mini58与限定三端证据不重跑、不扩大为真机/永久保活。

## 剩余/资源
WX缺AppID，微信编译/真机发布NOT_RUN；QA模拟器overlay原默认恢复未证实，两AVD与8808保持关闭，当前无ADB/CLI运行。frontend已关闭QA页并交还Chrome，全部模型IDLE。无后台AI/自动续跑。当前只收尾AUTH发布回执归档PR，不扩业务；全部凭据不进Git/消息，证据AUTH目录。
MEM协议已PR25 f565e709cf8bd9e5cd348c5a5bd8a33f5282443c固化，16回归通过；其旧工作树里的“保留后台IAP”已按用户AUTH新决定更新。

## 本轮最终交接
AUTH已完成发布与API安全验收，正在提交最后回执；无需App更新/重新部署。完整独立GUI点击流程明确NOT_RUN，未因超时冒称通过。旧长会话仅保存交接，不再接新大开发；需要用户新开总管按NEW_CHAT_PROMPT接任。无后台AI继续运行。
