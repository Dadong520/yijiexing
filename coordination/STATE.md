最新部署分工：UI03已独立PASS并合并PR12=98e87eb57371abf2e20893d4bcc1485ae4ac989e，039/119不单独发布。用户新增去访问码拆为OPENAPI-20261025-01服务端先行（独占/Users/dongge/.codex/tmp/yijiexing-api-20261025/apps/cloud-service，branchcodex/public-experience-api-20261025，已从Auth目录原样移动37文件并核SHA，无双写；API产品3422f1ccb9263438533eee8e89890d7296b644be，167作者+8独立HTTP/非作者源审PASS，待主服务部署和live回读）；ACCESS-20261025-01只负责两客户端AuthStore+Gemini登录UI，活跃目录/Users/dongge/.codex/tmp/yijiexing-access-20261025，0310/1110，编译预检两端PASS，未最终提交或实机。后台driver-admin镜像/IAP不更新。旧Apps兼容由API测试覆盖，新Apps需先API上线再独立无码登录实测，最终才换APK下载。

最新用户指令：去掉两端演示访问码，下载后即可体验。新增ACCESS-20261025-01，唯一活跃AUTH worktree /Users/dongge/.codex/tmp/yijiexing-access-20261025，branchcodex/remove-demo-code-20261025，base b9bd90a58f01a4e6f09e37366ed8f8b3feaaa51b；backend独占apps/cloud-service（从v8冻结参考复制）、两auth Store，协调者/Gemini只改login/connect UI。此apps/cloud-service是新唯一后端开发源，旧v8只读参考不改。公开客户端可免demo码，role token/allowlist/管理API/IAM/IAP保留；无隐藏内置通行码。UI03仍独立完成最终delta，其包不单独发布，之后ACCESS合并含UI升级的新包再统一发布。

# 宜捷行当前状态
更新2026-10-25 18:16 Asia/Singapore。根协调者唯一维护，恢复先读STATE/BOARD/当前task/role handoff。

当前任务UI-20261025-03 WAITING_DEVICE_UNLOCK，acceptance BLOCKED，releaseReady=false。用户原图/墨刀原型，暂停专门适老大字；Gemini直接读图并主写31个Compose函数，root集成真实乘客MapLibre和原创hero，正常字号/青绿/地图底部面板。产品SHA8499d6e5b6a19fc64c4375c6d70cd6edfccfa80f；PR12 https://github.com/Dadong520/yijiexing/pull/12 草稿未合并/未发布，CI结构+58小程序回归PASS不是设备PASS。

两端同签名候选：driver0.3.9/code12 SHA63764e489cb121a4627d8940f5ba18295747d98ce7f5fa4e7085106efe75dc1f；passenger1.1.9/code11 SHA4a99709011fd1be8be421801dd80f52357a67e153a9616787952cd3afa61bc2c。55/20既有单测+构建PASS；backend非作者13核心文件/下单controller/AddressDialog/routes/动作/金额权益审查PASS_SCOPE。backend发现重试旧MapView问题root用key(mapView)修正、静态复核PASS，实际失败恢复必须验。独立frontend实际UI验收NOT_RUN：Mac锁屏拒绝原生观察、ADB guest shell超时，冷启两原AVD及本地5038连接均未恢复；没有最终新UI截图，不清账号、不造订单，未动网络。已请求用户手动解锁，未回复；不绕锁或凭编译发布。

本轮稳定UI源码clone /Users/dongge/.codex/tmp/yijiexing-ui03-integration，branch codex/prototype-native-ui-20261025，origin真正GitHub。原Documents clone integration-ag-final出现dataless云占位读取卡，task源逐文件cp+15hash一致迁到本地，原clone保留不reset。两端源码candidates/driver-0.3.6-r2/android、apps/passenger-android。build输出 /Users/dongge/.codex/tmp/yijiexing-native-ui-20261025/{driver,passenger}/_app/outputs/apk/debug/app-debug.apk。不能回旧云占位目录重建；共享后端本轮禁止修改；唯一冻结参考源为 /Users/dongge/Documents/New project 2/.coordination/integration-ag-final/candidates/shared-backend-v8/cloud-service。若后续确需改后端，先由backend在任务卡确认唯一源/归属并更新STATE，不改重复快照。

当前公开发行仍driver0.3.8/code11与passenger1.1.8/code10（UI02，source55dce79f...），入口https://driver-demo-491829048305.us-west1.run.app/download 与 /download-passenger。driver-demo revdriver-demo-00005-2w2/v8镜像7d71539...。admin https://driver-admin-491829048305.us-west1.run.app/，revdriver-admin-ui20261025/image35726abba...，Google IAP dadonggev587@gmail.com再admin应用登录。独立项目yijiexing-driver-demo-1023/us-west1，既有automatic/min0/max1/request计费，不保活不自动暂停。仅coordinator可发布，无需反复审批但必须真验收。此次仅GCS服务器端备份旧APK（UI03 rollback.json），没有新APK发布/云服务/IAM/订单数据变更。

AUTH01已核实两端访问码匹配Secret driver-demo-access:1和本机yijiexing-cloud-20261023/.private/driver-access-code.txt，32位私密不可进消息/Git；合成司机0003/乘客0102登录和认证读取PASS，未旋转/清数据。验证码与访问码不同。保护既有订单包括PORD9dac969cecf1 WAIT_PAY2926/PORDaa584245c6e6 AVAILABLE，不凭旧快照声称当前总18，禁止为UI测试清单。

Gemini固定/Applications/Gemini.app/Contents/Helpers/agy（旧.local/bin独立App不是同一CLI）。UI03 scoped --add-dir读图成功，两个独立run见coordination/evidence/UI-20261025-03/gemini-run-record.json；当前Gemini idle，不latest/skip权限/并发同桌面上下文。Codex用6.1，frontend01a167c8-a5eb-77e3-b77e-214927a029da、backend01a168e2-22f7-70a2-af7f-4fb6be0ea695。根只唤醒当前负责人，不自动回复循环。

微信无真实AppID，仅开发版，wx.chooseLocation，未体验上传/真机；营销卡券积分为零资产演示，支付退款发票模拟；Geocoder成功依设备，乘客新地图是已选点预览非GPS/导航/腾讯SDK/95128。大传输直连优先；凭据/APK/cache不进仓库。解锁后恢复frontend独立实操→处理真实缺陷→同SHA报告/精确gate→根唯一合并/发布并完整公开GET签名哈希核验。未收到新输入不常驻工作。

18:18 root收尾：frontend已IDLE，根已正常SIGTERM本轮匹配的两个原AVD和临时5038 adb服务，保留userdata/快照，不再空转CPU，CloudRun未暂停；见after/coordinator-resource-teardown.json。等待用户解锁回复再派独立验收。
