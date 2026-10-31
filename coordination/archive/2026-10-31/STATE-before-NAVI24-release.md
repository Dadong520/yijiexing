# 宜捷行检查点：版本23已测试、合并、发布、完整回读；实际回执已入库
中央 /Users/dongge/Documents/New project 2/coordination 唯一权威；协议2026-10-26-v3。唯一coordinator01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6，context UNKNOWN。root独占调度/验收/合并发布，无常驻自动化。

## 新活动NAVI-20261031-01：D2验收PASS，PR49已合并，正在发布
候选6cdcee1a68d57465e4196243f22478c69733654c，source /Users/dongge/.codex/tmp/yijiexing-in-app-navigation-20261031 branch codex/in-app-navigation-20261031。driver24/0.3.21-cloud，passenger原23不重包。Claude官方SIDa695三阶段exit0，backend非作者D2 delta审PASS，根独立backend284/driver95/真实隔离native19组PASS并清理全部AVD/fixture/reverse。loop/联系快照/backend兼容当前46旧单副本全行保持。实际SDK道路导航/指引出现（模拟GPS，非实车语音/OEM），19组范围见NAVI/D2-native/FINAL_REVIEW.json；原包身份与源码见FINAL_BUILD_PROOF.json。旧无快照电话不推断；新订单受控显式直拨并确认系统电话页。
D1实际弹窗漏WP/固定半屏FAIL已由phase3修，D2真实小屏/IME/三态恢复/电话/途经/终态/首次同意/取消验证PASS，迟到回调隔离由95单测覆盖。API镜像f41b121b已构建但**尚未部署24、未GCS提升、产品PR49已合并main6a52f554（CI41216491002准确H1 PASS）**。发布root唯一负责；API/admin仍107e226-test1，保IAM/ENV/SA/请求计费/min0max1，admin不改。API新版本须default100与user-test稳定tag同步新revision，其他历史QA tag保留。用户新增46单已备份，勿恢复旧45。
下一准确head本地门禁/CI/PR合并→备份刷新→API immutable image/流量和driver原包发布回读→真实receipt/README入库。源/进程/镜像/私有路径见coordinator短卡。Gemini停、Claude/backend/frontend IDLE，无新自动化。

## 已完成RIDE / RIDEREC
用户要求有效地点一击回填返回/去Use、历史点击反馈、途经点入口移目的地页、参考成熟网约车简化两端。Claude官方SID6c0ddf57-d854-4c5c-a8a9-43f7ee7e3063 Opus5.5/high四阶段exit0/0拒绝，自测NOT_RUN；Codex独立源审/原生/产物绑定PASS。最终产品0ef2a8d3982dfca0e85a3b756a6004c1825ba798；PR47准确headb78b7fcedbae1be7cffa3360bb9944a450a43c88/CI41197382855 SUCCESS，expected-head squash mergea1527d2b27aeb2a5dd97e40736fd8e0bb5f4841f，完整repo tree与CI head相同，产品tree与验收源相同。
sourceRoot /Users/dongge/.codex/tmp/yijiexing-ride-flow-polish-20261031；当前工作branch codex/ride-flow-receipt-20261031/HEADa152。产品原branch codex/ride-flow-polish-20261031已合并。RIDEREC root/RIDER backend已中央建档，等待实际完整回执后只改coordination/README并独立复核、准确headCI合并，不再部署。

## 实际发布阶段
原包名/原signer9ffec9…b758f支持覆盖安装。乘客1.1.21-experience/code23，SHA cb77e6397a809027c117e963b2a4faf8b4b7a65d62a961bdaf519f333dec0842/107270521 bytes；司机0.3.20-cloud/code23，SHA237ee0ce4feeb24e29ec4257258fb56227d6ea1c95feb9017f15bd737303d30f/108718725。双候选upload60129 exit0、freshbackup32352 generation未变、promotion82170两fixed已提升23、旧22按generation归档和MD5校验PASS；不要重复upload/promote。仅GCS APK替换，无CloudRun部署。
公开乘客87678 direct200/司机95286现有proxy200均完整下载，大小/SHA256/包名/版本23/原signer全部匹配独立原包PASS。司机直连因325秒29MB估算超过900秒限制而由root手动停止(-15)，转既有代理完整重下；原因与attempts原样留证，不伪称自然网络失败。public4GET63751均direct200，collect/实际receipt/README已生成。最终回执 coordination/evidence/RIDE-20261031-01/release/FINAL-RELEASE-RECEIPT.json。RIDER对实际receipt/README候选56021bab95a9501cb4908de3690bb9148ed42bd9非作者17项审查PASS；PR48准确head d1e4d83fd415a627de5fc3105377653723fa6d88/CI41199613317 SUCCESS，expected-head合并 d5d878e2e56ca11fabc576545c46a277d8e7b985，main全tree同CI head/产品tree不变。实际receipt和README23均已在Git main，全部完成，无需再开PR，不再上传/提升/重下或部署。私有helper yijiexing-ride-release-stage-20261031，所有传输进程均结束。
司机 https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download；乘客同域/download-passenger（对应.apk整包）；后台 https://demo-admin.hbzjhs.xyz/ 原账号密码，无GoogleIAP；API https://yijiexing-demo.hbzjhs.xyz。

## 验收与范围
乘客0ef实际69项/原包QA通过；司机完整tree同ff53继承实际77项与原包QA，不伪称重跑。B3单击/历史/2stop CRUD/max2/取消、明确叫车恰一单、重新估价先确认/Profile明确保存、小屏IME、代表页/全屏恢复按精确函数相同继承。原ff53 49ms误叫车P1/4a6a嵌套误选P2 FAIL保留；B3 native实际75ms与159ms通过，原49ms不是最终实测间隔。700ms共享shield在commit前拦截，到期不丢session；打开Booking选址也短窗。root实图发现金额拆行，phase4仅金额独立整行+按钮FlowRow，正常/360dp font1.3的8.00/12345.67/费用待定六组与确认/取消smokePASS。原APK/JUnit/Git全部tracked字节、包名/签名/HTTPS origin及四私有值0命中root再绑定PASS。root与frontend清理回读PASS，AVD5584/fixtures64361,64362/转发关闭，原失败私有数据不抹。
真实provider底图/POI/模糊/逆地理/路线/相机/捏合成功NOT_RUN；QA无provider凭据且离线，不把触摸/布局证明当真实provider。个人手机/OEM/真钱/认证/警方联动未验，微信缺AppID未编译/真机/发布。旧MapPanel小屏离线重载文案裁切P3非阻塞备注保留。后台/后端/Store/网络产品未改。

## 云资源与数据
只独立project yijiexing-driver-demo-1023/us-west1，原测试/生产禁止。runtime42414 exit0/API driver-demo-ccui-107e226-test1、admin driver-admin-ccui-107e226-test1及100%流量不变，完整spec/annotations/IAM/ENV/secret/SA/traffic相同；automatic/min0/max1/请求计费/API900/concurrency1，不保活不自动暂停。data75701 exit0，45→45旧订单0丢失/0字段改/0新增，GCS generation1793441160408859、SHA2aa07b9824f5ac39bc355fc6d76258ee15df863ffb4cd2907e46cd63773cbe45。比较scope passenger_orders，不扩称全部表；SQLite快检PASS，原始DB/凭据/APK/cache私有0600不入Git。

## 连接与长期规则
先前手机连接反馈用户已确认“已经恢复”，根因未证实，不继续盲改云配置。高德SHA1字母O→数字0已由用户修；合成SDK10008→10000不等于手机验证，用户延后实测，Astra闲。
BIZ04待出发预约不占即时，明确开始接驾后单执行，无自动处罚/准时保证。BIZ05历史32类配置草稿/人工记录，非真实扣款/奖励。BIZ06新单终点必填、旧单同ID补齐。BIZ07差额单扣、余分归平台仅模拟。BIZ08三证60天提醒、审核通过替换；有效旧证待审可接、过期禁新单、在途完成。BIZ09每用户每发布公告版本一次可关，既有先领后显跨重启边界未改。默认项仅新草稿建议；本轮新草稿GPS起点/空终点，不覆盖手动/已有草稿。ACTION_DIAL公司电话，无IM/中继。后台公告/客服电话/乘客配置、资质到期、配置与治理历史草稿已在前基线提供，本轮无新增后台模块。
共享backend唯一可写仍/Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029/apps/cloud-service，本轮未改。root/Claude/backend/frontend全部IDLE，等用户体验反馈，frontend HANDOFF_RECOMMENDED，未来大任务先评估接替，不自建chat；Gemini暂停/Astra仅堵点。公开22旧基线已完整完成并mainc261d78，旧详细回执见archive/2026-10-31/STATE-before-RIDE23-final.md及MAPS证据，不重做。下一步以coordinator短卡具体session为准。
