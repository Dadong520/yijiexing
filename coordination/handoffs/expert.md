# expert｜EXMAP诊断交付，IDLE待root下一明确派单
协议2026-10-26-v3；真实会话01a1830f-d94b-7933-9c22-2c3898e54186，ACK；GOV06授权Astra/xhigh。更新时间2026-10-28T23:33:00+08:00，context UNKNOWN/not_exposed。唯一coordinator仍01a17cc7-e0a2-7791-bf1d-4611c09bab22；不派代理/合并/发布/自醒。

任务EXMAP-20261028-01，DIAGNOSIS_COMPLETE_EXTERNAL_CHECK_PENDING，业务acceptance BLOCKED。sourceRoot `/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027`；当前branch `codex/astra-escalation-20261028`，实查当前HEAD `713fcfb57117741b056e8197d609e3e20f365937`。本轮检查产品基线713fcfb57117741b056e8197d609e3e20f365937，6相关产品文件与其逐字节相同。共享树root工具/协调改动属root，本角色未改。只写中央自身task/本卡/evidence/EXMAP；外置测试 `/Users/dongge/.codex/tmp/yijiexing-astra-amap-20261028`。

已证实：线上回读乘客1.1.15/code17 APK整包798f…6379、包名、apksigner及公开证书SHA1均PASS。原版SDK11.2.100/sea9.8.1 JAR(ac3eba…e52)在桌面JVM用合成Android Context+实际发布证书，真实执行地图/搜索设置Key、AppInfo提取身份、ClientInfo签名及POI请求Handler。Key/身份/scode/body5项检查均true，实际HTTPS仍乘客10008，司机同条件10000+1POI。独立算法实现也相同；3个有界角色/namespace反向控制均10008。7次有效高德公开只读查询，先前2次默认CA失败单独保留不混为鉴权证据；系统CA验证开启、直连。

故障边界：当前乘客Key+已确认发布身份被服务端拒绝，无须App UI/初始化或缓存即可复现。未证实控制台具体Key记录的当前已保存绑定/旧图对应关系；旧图路径不存在root已核无备份，不重索用户、不推翻旧绑定确认。最小产品补丁范围空，不盲改初始化。真实Android设备SDK成功/地图完整鉴权/坐标归一化仍NOT_RUN，不能称修复PASS。

实际命令：`python3 tools/check_coordination.py --memory-role expert --session-id "$CODEX_THREAD_ID"`；`python3 coordination/evidence/EXMAP-20261028-01/build_sdk_harness.py`编译8源exit0；`probe_sdk_request.py`出上述实际结果。完整报告 `coordination/evidence/EXMAP-20261028-01/DIAGNOSIS.md`；核心证据sdk-generated-request-results.json、published-certificate-check.json、source-binding.json及secret-scan.json。8个Java测试源留evidence/sdk-harness-src，产物外置。Key/安全码/原始鉴权请求仅内存管道；任务证据+外置逐值扫描0，APK不入Git。不要直接转储SdkProbe stdout第二行。无私有keystore读取。

下一步：root只读核控制台具体乘客Key整值哈希对应记录及已保存包名/SHA1；若同记录完全一致仍拒绝，形成高德侧调查材料；未授权不代发。root据证据安排非作者复核与必要配置修正，再验真实设备地图/POI。expert现在IDLE，只有root新线索/明确派单再启动。无共享产品/工具/云/GUI/ADB/真机变更，业务写0/订单0，无后台进程需保留。未提交、待合并SHA无、无发布、releaseReady false；root统一收取本任务证据。
