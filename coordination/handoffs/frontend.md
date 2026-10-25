# frontend ACCESS短交接

ACK v2。ACCESS-20261025-01独立原生主流程 **PASS_WITH_ONE_P2 / IDLE**，releaseReady=false待文案修正；唯一设备操作权已交还root，不再ADB/窗口、不自动派单或轮询。只改证据/handoff；没有源码、构建、提交、部署或额外会话唤醒。

活跃worktree `/Users/dongge/.codex/tmp/yijiexing-access-20261025`，产品 **2e5dd4e7784467f1ee417f9c7ff2144d907e1467**；driver0.3.10-cloud/code13 SHA256 `673422e7f4a064423640903b310a1b2d24fc33e05d4315ee853159a13040b0a7`；passenger1.1.10-experience/code12 SHA256 `6ab82b23cff0d689605cfc2b8702f80349c1c051aec7c6c90f9fc81359f2b599`。两install-r、pm path/base.apk独立hash和apksigner签名回读匹配，原03/02会话保留。证据根 `coordination/evidence/ACCESS-20261025-01/frontend/`，22PNG/XML全部逐图查看，最终design-qa.json SHA256 **2ee62e11750e80dbf4bb1f2bd26b97ecc6e07a01cf9b882cb452b44cbb1cb638**。20:07:56开始，设备全部操作20:16:36完成，未超20:17:56十分钟上限。

实际PASS：升级后两端真实首页和资料、passenger刷新订单、driver连接重试；正常App退出→无访问码/验证码输入的新登录页；同意前进入按钮可点击父节点disabled；菜单3体验账号并选回默认driver003/passenger0102；连接对话框和driver设置顶部/底部无重置访问码；两AVD各wifi/data0/0进入失败，明确中文提示留登录页；恢复1/1后driver单击、passenger连续双击进入均到真实地图及本人资料；force-stop重开保留登录。doubletap只证明正确单一UI身份，不伪称逐请求计数或捕获busy帧。源绑定两个Store无X-Demo-Access header，内部role token及身份校验保留；没拦截TLS/读token/prefs/code。

OPEN **ACCESS-PAX-P2-01**：passenger退出确认实图04仍写“下次可重新获取体验验证码登录”，与实际无码流程冲突。源码ProfileScreens.kt约187行；建议改“下次可选择体验账号一键进入”。已两次短报root，由root安排修正文案/重建/最终增量验收。本报告只接受2e5及上述已装hash，不替任何后续包自动放行。

资源完全恢复：driver/passenger-restoration.json逐字段original==final，wifi/data1/1、font1.0、physical1080x2400/420dpi均与原值相同；默认003/0102新登录保留；双App force-stop/HOME；原5586/5584 AVD留用。无订单/钱包/投诉/支付业务操作、在线切换、pm clear、清账户、缓存隔离。司机原网络正常、待收款2笔不影响接单与可用offer共存，557.74/15/10/98保留；29.26细单未在本轮重开，root已另做API保留对比不冒称本人测过。

测试辅助纠正均已如实记报告：初始/driver重启UI dump nullroot为屏幕休眠，wake后fresh截图通过；早期disabled断言看了Text子节点，纠正为按钮祖先，非产品缺陷；主脚本passenger完成，driver末图经单独有限wake补齐后双端恢复，完整login-checks/steps/restoration有证据。55/20构建非本人重复跑，root另拥有已上线API边界证据，不能冒称整三端交易完。

NOT_RUN：其余账号实际登录、busy瞬时图/精确auth请求数、29.26原生细单、新完整交易/三端流程、外部高德/投诉、专门真机/横屏/大字/TalkBack、公开APK发布回读。UI03原已验收范围另有原报告，不本轮重复。下一步root关P2并绑定最终SHA/hash/gate统一合并发布；本人不继续无明确任务的新操作。
