# coordinator AUTH限定变更 / 2026-10-26T09:04:40.649341+08:00
ACK2026-10-26-v3；真实thread01a168e6-d76c-7ef0-8c87-706cc6e32d57；HANDOFF_RECOMMENDED、用量UNKNOWN。只完成用户明确授权的限定后台登录调整，不扩新业务；下次大任务仍应换新。
任务AUTH-20261026-02；sourceRoot /Users/dongge/.codex/tmp/yijiexing-admin-password-20261026，branch codex/admin-password-only-20261026，基线f565e709cf8bd9e5cd348c5a5bd8a33f5282443c。backend唯一编辑admin/backend入口和测试；root仅Web提示/文档/构建/唯一发布；共享业务trip-api apps/cloud-service只读8ec，不更改。当前代码未提交，正在backend作者测试，root待非作者复核。
用户明确取消driver-admin外层Google/IAP，替代此前保留此层要求（DECISIONS AUTH-01）。保留后台账号/会话/角色/审计/Origin及失败限流，driver-demo不动。必须先PASSWORD镜像在IAP下就绪并切100%，再单次关闭IAP及Invoker检查；旧OAuth和IAM绑定留存以便回退。
当前线上仍admin-trip-2863623f/IAP=true；API-distance-8ec100%。0600私有GCP基线在~/.codex/tmp/auth02-*-before-private.json，禁止输出/进Git。原管理员凭据只在浏览器RAM填已验证管理站点，不贴消息。测试运行库 /Users/dongge/Documents/New project 2/yijiexing-admin-20261023/.private/browser-venv/bin/python；不得读取其他私有文件。
下一步：backend专项测试→root安全review/固定SHA→build→合并→保护下新镜像→取消Google层→匿名200及未授权401/跨源拒绝→实际浏览器账号登录/退出与后台数据回读→更新新授权与归档。完成前不称已取消Google。
旧业务APP0312/1112免访问码已发，无须新APK；WX缺AppID、QA overlay遗留继续保留，两AVD不启动。无本轮ADB/Chrome/CLI持有；仅构建/发布安排由root。
