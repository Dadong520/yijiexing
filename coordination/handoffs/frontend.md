# frontend phase4等待金额布局冻结
root新指令：¥8.00拆行改为本轮发布前阻断项；当前整体acceptance FAIL/releaseReady false，phase4尚NOT_RUN。B3功能A/B原生及原构建/清理有效保留，不伪标新源全跑。仅待phase4精确freeze后重建受影响乘客original+QA/相关unit，重启隔离资源测normal/360dp/font1.3金额单行、按钮可达、确认/取消轻量smoke，不重跑全A或driver。地图reload裁字仍非阻断已知备注。
恢复gate实际exit1：中央RIDE-20261031-01 invalid acceptance（非本人文件，交root处理）；未擅改中央root任务。自身仅登记等待；无资源重启、无产品操作。
以下为B3准确历史检查点：
# frontend RIDEF限定A/B完成短卡
ACK2026-10-26-v3；真实ID01a182c6-c475-7110-bbae-0deafe6d2385；context UNKNOWN/not_exposed，HANDOFF_RECOMMENDED。只本任务，不自动新chat/代理。root唯一merge/release，线上22，未发布23。
任务RIDEF-20261031-01限定A/B独立验收PASS，有两项非阻断P3视觉备注由root评估。sourceRoot /Users/dongge/.codex/tmp/yijiexing-ride-flow-polish-20261031，branch codex/ride-flow-polish-20261031；base c261d78c0108067b6057ff655d1396ec135d815e；最终及待合并 b3bc07dbecbd49d3b5213d96cb11aef53e9ad644。产品工作树clean，只读，自己只写本任务/card/evidence，无产品提交或部署。
证据入口 coordination/evidence/RIDEF-20261031-01/FINAL_REVIEW.json、FINAL_BUILD_PROOF.json、CLEANUP.json。最终乘客69单测/原包+QA实际PASS；司机77/原包+QA在ff53实际PASS，全司机Git树同B3明确继承，不冒称B3重跑。原包code23乘客1.1.21-experience SHA ce35050482a7a42d62f97331d356bf93250d15ffb006efd94533d5790df979cc；司机0.3.20-cloud SHA237ee0ce4feeb24e29ec4257258fb56227d6ea1c95feb9017f15bd737303d30f。原包名/原Signer9ffec9…b758f/HTTPS/每包全部解压条目四私有值0命中PASS。
R=/Users/dongge/.codex/tmp/yijiexing-ridef-20261031。乘客原APK R/revisionB3/source/apps/passenger-android/app/build/outputs/apk/debug/app-debug.apk；司机R/source/candidates/driver-0.3.6-r2/android/app/build/outputs/apk/debug/app-debug.apk。JUnit各app/build/test-results/testDebugUnitTest；完整绝对路径在proof。QA仅appId/localhostTLS/debugCA，无产品Kotlin改动，禁止发布QA。
原ff53 P1原生49ms第二tap穿透叫车，FINDINGS_ff53.json/PNG/XML原FAIL保留。4a源审P2 nestedreturn未arm来自RIDEB，4a原生NOT_RUN。B3实际R1相同点连点75ms，正确origin/dest回首页且orders0→0，不称精确49ms重放；打开picker42ms有效点拒绝后同session可用；nestedstop选点+159ms第二tap父目的地仍留父picker，窗口后正常选终点成功。max2 stops增改删取消、未知坐标显式搜索反馈、取消query不改caller、真实360dp/font1.3IME A→B/back PASS。回调重复/过期/newest/有效点规则属69单测stub，不冒充provider成功。
窗口后明确CTA一次创建本地单0→1，精确一stop；已有订单点选后必须显式重新估价，快速第二tap不能确认，稍后明确确认一次才改；Profile点选只改表单，快速外层Save不写，稍后Save一次才持久。final-local-mutations.json；所有只新fixture，无云业务写，无司机接/推进/支付。
B代表两端风格及小屏/危险disabled确认/全屏恢复实图ff53，最终报告精确注明来源和继承依据，B3新A/profile/IME图补充；drivertrip仅read-onlyDTO投影，wallet合成。P3两项：360dp/font1.3地图失败overlay重载标签下缘略裁切f44；乘客旧订单小屏¥8.00末位换行b25，无金额数据改变。root评估，未隐藏。
ui.py曾dump失败留旧XML已在外部harness修，f01及早期f07–f18探测排除（纠正f14–f17及有效f18b独立），b14/b21transient不用，f41配置重建caller后fill失配立即停、未计IME。按实际页面断言，不能按文件名推PASS。
资源CLEANUP PASS：AVD RIDEF_20261031/emulator-5584已kill，无adb设备；fixture PIDs17250/24243(SIGTERM)均结束，64361/64362/5584关闭；reverse443已remove。旧session11046/40406/38566不再运行。原误单PORDf9d1de3a8e38 payload SHA前后完全相同，misorder-preservation.json；私有runtime与原DB备份在R外部0600，不进仓库消息。无个人设备/云环境操作。
NOT_RUN真实provider正向POI/逆地理/路线/相机捏合成功、真机OEM、完整生命周期/支付/真实资金/认证/警方/优惠。完成本限定任务不代表这些业务完成。下一root exacthead复核并决定合并发布；本角色IDLE，后续大任务建议换新frontend对话，因可见压缩/恢复成本增加，未自动新开。STATE/BOARD/REGISTRY/DECISIONS root独占。
