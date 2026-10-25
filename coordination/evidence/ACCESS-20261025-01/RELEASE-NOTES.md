# 宜捷行体验包 2026-10-25

司机版0.3.10-cloud（13），乘客版1.1.10-experience（12）。司机和乘客访问码、手填登录验证码均已移除；选择体验账号，阅读并勾选体验说明，点击进入体验。已登录用户可覆盖安装继续使用，云端订单和资料保留。后台仍使用Google/IAP及管理账号权限。

下载入口：
- 司机：https://driver-demo-491829048305.us-west1.run.app/download
- 乘客：https://driver-demo-491829048305.us-west1.run.app/download-passenger
- 后台：https://driver-admin-491829048305.us-west1.run.app/

两端包含Gemini主导的地图与底部操作面板、统一绿色品牌视觉及原型布局。本轮55司机/20乘客测试通过；后台167回归、8独立HTTP边界及14项已部署HTTPS核验通过；新增分发文案9项测试通过。最终安装与UI独立复测参见frontend-final/design-qa.json。首次连接曾遇瞬时非JSON响应，重新打开自动恢复，原因未确认，未将其抹去。新完整订单、费用、支付及投诉流程本轮NOT_RUN，不能将UI/认证验收称为新三端完整生命周期验收。

仅体验角色公开免访问码，角色令牌、体验账号范围和后台权限仍保留。真实短信/微信登录及支付渠道未接；支付、退款和发票以界面实际模拟标记为准。地图为起终点预览，不等于真实GPS导航或腾讯地图集成。

部署维持独立Google Cloud项目yijiexing-driver-demo-1023/us-west1，Cloud Run按请求计费、自动伸缩、最少0最多1，不保活、不自动暂停。回退APK使用各role-public-release.json记录的rollback GCS对象，覆盖前核对当前generation并校验hash。后台不重新部署。完整源码已通过PR12、13、14、15合并；public-release.json包含发布时间、公开GET和签名hash回读。
