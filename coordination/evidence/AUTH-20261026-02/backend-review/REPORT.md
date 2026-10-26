# AUTH-20261026-02 backend

显式服务端ADMIN_AUTH_MODE=PASSWORD仅省去外层IAP JWT检查；未设置默认IAP严格；未知模式请求503且入口启动拒绝。所有模式保留原route白名单、v2服务器secret替换、Origin/Sec-Fetch、应用账号会话、RBAC、审计和登录失败锁定。未改共享业务源或任何云配置。

基线f565e709cf8bd9e5cd348c5a5bd8a33f5282443c；当前未提交，按协调者要求不单独commit。两归属文件SHA256见results.json。共享依赖固定8ec8419d30955c216d7e4fe90451584d2a9622cf，YIJ_BACKEND_SOURCE指向唯一trip-api/apps/cloud-service，git diff逐源确认未变。

实际命令：`env PYTHONDONTWRITEBYTECODE=1 YIJ_BACKEND_SOURCE=/Users/dongge/.codex/tmp/yijiexing-trip-api-20261025/apps/cloud-service '/Users/dongge/Documents/New project 2/yijiexing-admin-20261023/.private/browser-venv/bin/python' -m unittest discover -s apps/admin-console/backend -p test_iap_browser.py -v`。
18项真实HTTP+本地SQLite+MemoryCAS PASS（原9项IAP、新默认严格1项、PASSWORD8项）；两运行库均通过，权威现有库结果1.732s，详见verified-runtime-tests.log。未运行后端208或原生75，不重复无关回归。

当前无作者测试失败；root非作者验收、浏览器账号体验、构建/云端匿名200与未登录401回读NOT_RUN。用户新授权仅取消driver-admin外层Google/IAP，不能扩大到driver-demo或取消应用会话；root负责最终CloudRun变更、保留回退。产品不自动发布，旧部署状态以中央STATE为准。
