# expert｜EXMAP配置已验证，用户推迟真机测试
协议2026-10-26-v3；真实会话01a1830f-d94b-7933-9c22-2c3898e54186，ACK；GOV06 Astra/xhigh。context UNKNOWN/not_exposed。唯一coordinator 01a17cc7-e0a2-7791-bf1d-4611c09bab22；不派代理/合并/发布/自醒。

任务 EXMAP-20261028-01，CONFIGURATION_VERIFIED_DEVICE_TEST_DEFERRED_BY_USER；acceptance BLOCKED，仅设备与独立验收待办。用户直接授权本会话恢复，root已登记。允许只写本task/本卡/evidence/EXMAP；外置测试 /Users/dongge/.codex/tmp/yijiexing-astra-amap-20261028。

最新已证实：官方高德控制台“测试→测试用 乘客端”Key整值哈希匹配既有运行时Key，包名cn.hbzjhs.dc.passenger.cloud正确；发布/调试SHA1首组均误填DO，O码点79，证书应D0，0码点48。旧截图视觉判断被精确DOM证据纠正。用户亲自将两字段改成正确证书SHA1并提交；expert只读重开设置，确认两项已保存D0:6F:0A:9E:AC:9D:FD:DC:08:30:B9:8A:F0:6E:2F:68:B6:D2:02:46。

复测：原版SDK11.2.100/sea9.8.1桌面JVM、合成Android Context、实际发布公开证书及相同包名/Key，请求检查全部true。乘客HTTP200/status1/10000/1POI（此前10008），司机仍10000/1POI。原失败证据未覆盖。实际Android地图/搜索尚待用户反馈；非作者修正后复核尚未完成。无需产品补丁的证据充分，不能据JVM成功宣称真机地图业务完成。

证据 coordination/evidence/EXMAP-20261028-01/：console-binding-typo.json、console-binding-corrected.json、sdk-after-binding-correction.json、DIAGNOSIS.md。实际执行probe_sdk_request.py的内存副本，仅替换输出文件名；所有原请求/Key只在内存，未输出/保存。禁止直接转储SdkProbe stdout第二行。记忆门禁PASS，仅结构检查非业务验收。时间戳保留本地at及高德HTTP serverDate；两时钟不一致。

下一步：已通过异步问题请用户从最近任务划掉乘客App后重开，核对底图并搜索宜昌东站，收集两项结果。若成功由root安排独立验收/收取记录；若失败按实际错误继续诊断。IAB tab1官方控制台保持登录、markHandoff；不重索Key/旧截图。浏览器新turn如browser.user unavailable可reset后getBrowser初始化，使用agent.browsers.get('iab').tabs.get('1')静默取得tab，仅输出脱敏DOM，避免getTab自动输出明文Key。用户亲自提交，expert未更改控制台配置。

原产品诊断基线713fcfb57117741b056e8197d609e3e20f365937，对应乘客1.1.15/code17证书。恢复检查共享源 /Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027 HEAD f8955de824be0108cf550c35a22a21b549083e92、branch codex/claude-ui-release-receipt-20261029，现发布乘客1.1.16/code18、司机0.3.15/code18。历史EXMAPV独立诊断PASS不等于修正后验收。无产品/云/ADB变更、无订单、无后台进程、无待合并SHA、releaseReady false；root统一合并发布。

用户最新回复：我晚一点再测试，你先配置好就行了。配置已保存且SDK鉴权复测PASS，本轮收尾IDLE；真机底图/地点搜索待用户稍后继续，不催测、不自动轮询、不额外修改配置。
