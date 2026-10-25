# 宜捷行当前状态 2026-10-25 20:40 Asia/Singapore

用户最新要求两App直接去掉访问码。已实现、独立验收、合并和实际发布。
- 司机0.3.10-cloud/code13，APK SHA95c948f29ba44fdf30904e5e7880ce298d33d67036686be19e0b7da152441042；乘客1.1.10-experience/code12，SHAc2a640c69e2781ca75d2b0754dc14b2d84c9ed8e30c0f3da06f9879bf84842f9。两公开完整HTTPS GET核SHA/版本/签名PASS，direct=true。
- 下载https://driver-demo-491829048305.us-west1.run.app/download 与/download-passenger，可直接.apk；覆盖安装保留账号/云端订单。选择体验账号、勾体验说明、一键进入，无访问码或手输登录验证码/隐藏内置码。后台Google/IAP及role权限保留。
- Native产品eb54e70f27c53aaf569390039349cf3487ff008f，PR15合并8461a4e64113b461ca667851ea1a7fb148d34328；55/20测试PASS。frontend非作者原2e5真实无码登录/同意门禁/断网恢复/快速双击通过；最终四文字delta实装hash/旧账号保留/退出提示通过，报告ACCESS/frontend-final/design-qa.json。首启一度非JSON响应，重新打开自动恢复，原因未证实，未假称点击重试。
- UI03已独立PASS，PR12合并98e87eb57371abf2e20893d4bcc1485ae4ac989e；31函数真实Gemini设计、root集成地图/小修，最终界面包含上述新包。039/119未发布。地图真瓦片/平移回位/署名/离线重试恢复/键盘取消/详情等见UI03 after-final。
- API实际唯一活跃源码/Users/dongge/.codex/tmp/yijiexing-api-20261025/apps/cloud-service，branchcodex/public-experience-api-20261025；产品3422公共无码guard，167作者/8非作者HTTP PASS，14已部署HTTPS边界PASS。下载文案ecab94fd2022d3142eefbedbb63c38641979bece仅distribution文字，9测试+frontend非作者静态PASS；PR14合并526e5994a4d411a83f1e22ccaf8246cd886b0feb，已部署driver-demo-publiccopy-ecab94fd100%，image sha256:ebf71391a5d5abbe21416d6248e5ae1027a35a4595396eec66bb9eca4a062bfa。两下载页面无码说明和admin IAP302回读PASS。
- Native唯一活跃worktree /Users/dongge/.codex/tmp/yijiexing-access-20261025，branchcodex/remove-demo-code-20261025。同树apps/cloud-service仅main副本，禁止与API目录双写/部署；v8和releases历史冻结。
- 独立项目yijiexing-driver-demo-1023/us-west1。Cloud Run自动伸缩/min0/max1/请求计费，不保活、不自动暂停。driver-admin未部署，原IAM/IAP/镜像/数据源/旧订单ID状态金额保留，未清理数据。旧APK server-side备份GCS distribution/backups/UI-20261025-03/；各role-public-release.json记录generation和rollback。
- 最新运行证据与说明coordination/evidence/ACCESS-20261025-01/{RELEASE-NOTES.md,*-public-release.json,frontend-final/design-qa.json}；API copy-deployment-readback.json。源码GitHub Dadong520/yijiexing已合并。包在.codex/artifacts/yijiexing/2026-10-25，禁止APK/secret/cache入Git。
- 本轮没有新增完整三端订单生命周期交易实测，不把UI/Auth验收称三端完结。微信无真实AppID开发版；真实短信/支付等未接，支付退款发票保留模拟标记；地图起终点预览非GPS/腾讯SDK导航。
- frontend/backend/Gemini当前全部idle，模拟器网络字体尺寸还原、App停止/HOME，用户休息本轮不常驻。后续只唤醒任务负责人，Codex6.1，Gemini默认agy CLI短任务；新工作先读STATE/BOARD/task/role短卡。
