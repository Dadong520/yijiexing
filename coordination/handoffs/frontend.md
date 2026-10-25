# frontend ACCESS最终短交接

ACK v2。ACCESS-20261025-01最后文字delta独立验收 **PASS_SCOPE / IDLE**，唯一设备操作权已交还coordinator。只改证据/handoff，不产品/构建/commit/部署/额外唤醒。20:30:58开始，原4min至20:34:58；所有设备操作 **20:34:13**结束，未用root后来延长到20:38的时间继续操作。

唯一AUTH根 `/Users/dongge/.codex/tmp/yijiexing-access-20261025`；最终产品 **eb54e70f27c53aaf569390039349cf3487ff008f**。driver0.3.10-cloud/code13已装SHA256 `95c948f29ba44fdf30904e5e7880ce298d33d67036686be19e0b7da152441042`；passenger1.1.10-experience/code12已装SHA256 `c2a640c69e2781ca75d2b0754dc14b2d84c9ed8e30c0f3da06f9879bf84842f9`。两install-r/pm-path base.apk实际bytes SHA与local APK一致，apksigner同旧签名。55/20为root再次构建证据，本人不重跑。

最终报告 `coordination/evidence/ACCESS-20261025-01/frontend-final/design-qa.json` SHA256 **10a80989db4952d7669dbe445ba2eb7fc1f8c57bf7ace50b9559236868c02610**。6PNG/XML全实际看图；installed/delta/connection-recovery/source-delta/static-review齐。**ACCESS-PAX-P2-01 CLOSED**：实际新版退出确认03说“下次可选择体验账号一键进入”，点击取消，未登出。Pax资料02保留0102，后续04真实地图首页和call CTA、无连接更新错误；driver恢复后02图/树为03网络正常及可用offer/待收款2笔共存，无登录再触发。

必须保留的观察：初driver01为session仍在的“服务连接提示：云端服务返回了无法识别的响应”，Pax01更新中；root说明当时未部署copy镜像或切流。driver重新打开时已自动刷新恢复成功，**没有实际点击重试按钮**（02文件名含retry仅描述原计划，JSON/报告明确retry未点）。Pax再打开04也clean。driver02有App新单heads-up覆盖头部，身份/网络同时有fresh XML证据，不冒称完整视觉重测。异常原因未确定，不能把冷启动/网络假说当事实；原错误图/JSON保留。已报root，无复现依据不改业务。

源2e5→eb54精确diff只有3文件4段文字：Pax logout和help短信项、driverStore旧DEMO错误提示、LocationService错误通知；auth/Home/数据/业务路径未变，后两异常不人为诱发。2e5全登录矩阵证据限定继承 `frontend/design-qa.json` SHA2562ee62e11750e80dbf4bb1f2bd26b97ecc6e07a01cf9b882cb452b44cbb1cb638：同意门禁/三账号菜单/单击与双击/离线失败恢复/角色资料/重启保留等确实已跑，不假称本包重跑。旧P2仅由本轮实际新版图关闭。

非作者下载源码review **PASS_STATIC_SCOPE**：唯一API `/Users/dongge/.codex/tmp/yijiexing-api-20261025/apps/cloud-service/distribution.py` 实际bytes与 **ecab94fd2022d3142eefbedbb63c38641979bece**一致，仅该文件2行HTML/颜色及乘客replace变化；实际推导两份HTML均正确角色+勾说明进入，无访问码/手填验证码文字，后台独立登录/模拟收费边界保留。API auth原3422f1ccb9263438533eee8e89890d7296b644be没在本delta改；原源审/8HTTP由root限定继承，不冒称我再次HTTP/部署/公开页回读。

恢复：原/最终network1/1、font1.0、physical1080x2400/420dpi逐字段相同，本轮未改这些设置；默认003/0102登录不变，无真实退出、订单/钱包/在线/投诉/支付、无prefs/token/code读取、pm clear或清缓存。双App最后均force-stop/HOME，原5586/5584 AVD仍留root。不要再无任务ADB或轮询。

NOT_RUN：重复全登录矩阵、人工403/DEMO/定位通知触发、帮助整页导航（文字已读源码）、新公开下载HTML/APK发布GET、异常根因/稳定性压力、TLS请求计数、真机大字横屏/TalkBack、新三端交易全流程/高德/投诉。下一步coordinator精确绑定最终SHA/hash/gate，统一合并发布回读；本人IDLE，待明确新任务。
