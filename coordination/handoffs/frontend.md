# frontend交接卡

ACK规则2026-10-25-v2。UI-20261025-03 独立新包验收环境阻塞，产品验收NOT_RUN，不能发布放行。本人只read-only设备/报告；没有改源码/构建/部署/唤醒代理。
新稳定clone /Users/dongge/.codex/tmp/yijiexing-ui03-integration；根明确所有后续写coordination/evidence/UI-20261025-03/after，不再UI03缩写（已移入）。产品8499d6e5b6a19fc64c4375c6d70cd6edfccfa80f；root55/20测试PASS只是构建证据。driver039/code12 SHA63764e489cb121a4627d8940f5ba18295747d98ce7f5fa4e7085106efe75dc1f；passenger119/code11 SHA4a99709011fd1be8be421801dd80f52357a67e153a9616787952cd3afa61bc2c，路径看同目录上级build-result.json。

18:04:48开始，限12min至18:16:48。两原AVD在adb列device但shell getprop超8s；adb emu kill+TERM最终退出后18:07:37以no-snapshot-load/save冷启原userdata，没wipe/删快照。当前AVD进程driver3430/passenger3431，端口5586/5584，日志分别20s/19s完成boot。新boot仍shell超时。重启默认adb server5037 failed ACK，日志Address already in use但lsof无监听；独立5038服务可启动并connect127.0.0.1:5587/5585，但两个shell仍超6s。未走到包读取/安装任何一步。Computer Use native inventory明确Mac锁定自动解锁失败；不能用该兜底，不绕过锁屏。已短报root，原12min不延长。
证据after/design-qa.json完整列NOT_RUN和环境阻塞（P1_ENV_ONLY非产品UI缺陷）、device-recovery.json、adb-recovery.json、adb-alternate-transport.json和两restart.log。参考3图已限时复制到after/reference并实际读取；对比要点已记，不能假称实际新包同屏比较完成。

本轮未改网络（离线/失败/重试均NOT_RUN）、未改font、未清数据/退出/切在线/接单/下单/取消/支付/投诉。无当前新包截图、无安装hash独立回读、无登录/弹窗通过。旧before七图在原DocumentsUI03证据，仅旧038/118，不冒充039/119。
下一步人类手动解锁Mac后，coordinator先恢复ADB稳定响应，明确重新派有限设备验收；本人不延时轮询或自行扩大工作。结束前如清理本机临时5038服务/恢复默认5037有结果将附本卡，不改云/Google配置。

收尾：root已向人类发解锁请求，未回复；root只读验证5038 PID10634正在LISTEN且有连接，要求不再攻5037。本人遵从保留5038服务与两已启动AVD供解锁后恢复，不再调用ADB或窗口；没有本轮网络/font/安装/订单改动。App stop/HOME因guest不可响应无法验证，明确NOT_VERIFIED。design-qa.json已补endedAt/teardown，IDLE等人类解锁及root新派单；不继续轮询，不发重复解锁问题。
