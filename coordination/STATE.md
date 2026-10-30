# 宜捷行当前检查点 2026-10-30T01:11:43.243962+00:00
中央 /Users/dongge/Documents/New project 2/coordination 唯一权威，协议2026-10-26-v3。唯一coordinator01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6统一派单、验收、合并和发布；context UNKNOWN/not_exposed。

## 目前线上基线
Git main最新830aa80883d27cd6e518e12e249efc8695129284（PR40真实回执/README，准确head CI PASS）；产品PR39合并a720d76e9f822dcfc860c4f324993c18f3c7b6e0已发布；当前原生产品源137e41534f823c538fe62ff10820f491e0e55074；后端/后台保留107e226d1d23fc6d7ef5dab75229962bd38306d8/PR37基线。当前已发布乘客1.1.17-experience/code19、司机0.3.16-cloud/code19；原CCUI版18为历史。
API https://yijiexing-demo.hbzjhs.xyz，revision driver-demo-ccui-107e226-test1；后台 https://demo-admin.hbzjhs.xyz/，revision driver-admin-ccui-107e226-test1，均100%，user-test同版。后台原账号密码，无Google/IAP前置。
安装快链 https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download（司机）与/download-passenger（乘客），对应.apk整包，实际版本以回读为准。CCUI发布时HTTPS/hash/原签名和39旧单全部字段一致，证据CCUI-20261029-01/release/FINAL-RELEASE-RECEIPT.json。后来用户新增单，本轮fresh42单备份和回读全部字段一致，不复用旧39结论。
仅独立项目yijiexing-driver-demo-1023/us-west1；禁止原测试/生产。CloudRun automatic/min0/max1/请求CPU计费、API900秒，保留ENV/secret/SA/IAM/RBAC/Origin，不保活、不自动暂停。私有Key/账户/DB/APK/cache/rawCLI外置0600。

## 已完成UIO：已发布并完整回读通过，勿重做
用户UI设计.docx11项优化由Claude实现、Codex独审；产品源137e41534f823c538fe62ff10820f491e0e55074已验收发布19。PR39准确head b7f39271e360afc9036af38534f6485840faad98/CI40863647646 SUCCESS，合并a720d76e9f822dcfc860c4f324993c18f3c7b6e0；回执PR40准确head1c8a6422c7fe7fcacce3d0774c55a3ae2698c21a/CI40867244200 SUCCESS，合并830aa80883d27cd6e518e12e249efc8695129284。两PR已附聊天。
UIOV最终137独立PASS：乘客31/司机70单测双构建，原ID/签名/version19/公开HTTPS整包/hash一致；专用AVD合成数据57截图验证取消、返回、地址、订单过滤/预约门禁、钱包/会员/空态/代表大字体；真实地图provider/OEM/真实资金未验。AVD/fixture/端口已清理。UIOB最终137业务语义PASS，原FAIL保留；root mini74与10页25JS检查PASS，微信编译未跑。
实际仅GCS固定APK提升，CloudRun完整spec/注解/ENV/secret/SA/IAM/revision/traffic不变；fresh42旧单全存储字段一致，0丢失/0修改/0新增，root云业务POST0。前后generation1793271975039754及DBsha dd31f6f76b19645f4cad062392ee126d61662b7a0c95dd1e6d73534f7913d411一致。实际回执evidence/UIO-20261029-01/release/FINAL-RELEASE-RECEIPT.json及FINAL-RELEASE-REPORT.md。UIO/UIOREC全部完成，无发布进程，不再重复构建或发布19。

## 已确认业务与边界
BIZ04预约待出发不占即时，明确开始接驾后双角色同一时刻单执行/欠款门禁；无自动处罚或保证预约准时。BIZ05历史32类只可配置草稿与人工记录。BIZ06新单目的地必填，旧单同ID补填。BIZ07差额只扣一次/余分平台，仅模拟测算。BIZ08三证提前60天提醒，审核通过替换；旧证有效待审可接、过期禁新单、在途可完成。BIZ09公告每用户每发布版本弹一次可关。勿重问。
持久默认地址/乘车人/抬头仅新草稿建议，不覆盖GPS/已填/历史，不自动叫车开票；司机mask不拨，公司电话确认ACTION_DIAL，无IM/中继。后台平台运维→公告/公司客服电话/乘客功能；司机管理→资质到期提醒；侧栏靠下“配置与治理”为非按钮分组，点“历史草稿与人工记录”后选结算管理/营销等分类，本轮不新增后台菜单。
地图EXMAP：用户亲自改乘客控制台两SHA1 DO（字母O）→D0（数字0）并保存；具体Key哈希/包名匹配，原SDK请求乘客从10008恢复10000/1POI，司机仍10000。root已核console-binding-corrected.json及sdk-after-binding-correction.json，JVM合成Context/Python传输不等于设备内SDK成功。用户决定稍后实际手机测底图/搜索，expert IDLE，不轮询；UIOV隔离QA故意无地图凭据，不作真实provider证据。
微信缺AppID未微信编译/真机发布；支付退款提现发票模拟，无认证/录音/警方联动。2.0余项及PMPOST83候选未混入本轮，不称三端真实业务全完成。

## 角色和恢复
角色精确ID见SESSION_REGISTRY：backend01a182c6-c058-76e1-9584-38abfe813799、frontend01a182c6-c475-7110-bbae-0deafe6d2385均GPT6.1；backend当前IDLE/PASS，frontend本批原生验收ACTIVE；expert01a1830f-d94b-7933-9c22-2c3898e54186 Astra/xhigh按GOV06仅堵点，当前IDLE；Gemini PAUSED_BY_USER，无在途任务，保留设置。Claude按GOV07及CLAUDE_CODE.md，root唯一发布。
AUTO已PR35实装/独审ACK。根会话完成本轮闭环后等用户体验反馈，不新建守护/bridge/保活，不自动回信循环。中央控制文件只能root写；各角色只写自身卡/任务/证据；工作树仅提交快照，不反向覆盖中央。此前CCREC已PR38完成。

当前源码工作树/Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029（UIOREC文档分支）；共享后端下一次获派修改时仅此树apps/cloud-service可写，旧pm-amap树仅历史，不并发改重复快照。当前后端无改动或待发补丁。

## NAV已通过限定验收，正在合并/发布准备，线上仍19
最终产品6ab66bdcd20b43dfd314917881342dcdcc165a9e，树/Users/dongge/.codex/tmp/yijiexing-cloud-navigation-review-20261029，branch codex/cloud-navigation-review-20261029，PR41。Claude c3342ddb-6fe0-44b6-8311-00c329e73178/Opus5.5 medium四阶段exit0；作者测试NOT_RUN，由Codex执行独立验收。
791版本20基线7组/65PNG+XML原生PASS，6ab仅登录/个人两能力Note修正，独立重编乘客与2页面截图PASS；原65截图不伪改SHA。乘客1.1.18-experience/code20、司机0.3.17-cloud/code20原包名/原签名/HTTPS/私有值0扫描；54/77真实测试结果按字节或明确2string差异继承，不冒称新跑。NAVB f7关键规则对象与最终6ab相同继承PASS，frontend最终PASS，所有AVD/fixture/reverse已清理。证据NAVF/FINAL_REVIEW、COPY_DELTA_REVIEW、FINAL_BUILD_PROOF、CLEANUP和NAV/ROOT_FINAL_ACCEPTANCE。
模拟环境本地真实动作处理+mockGPS+导航Intent stub，不作真实高德鉴权/底图/搜索/逆地理/OEM成功证明；这些仍NOT_RUN。BIZ09原ack早于展示，切号/进程结束仍可能丢待显示副本，不保证持久补弹；无跨账号泄漏/重复领取。资金/微信限制不变。
root已fresh44单原子GCS备份/完整性PASS（gen1793323771057295，私有0600），runtime-before完整service/IAM保存；尚无upload/promote/CloudRun或云业务POST。先精确head CI+merge，再原签名包候选上传/保护归档提升/公开HTTPS整包hash和全部旧单/完整runtime回读。准确releaseReady及发布结果看任务/实际回执，不把验收PASS写成已发布。GCS发布helpers外置nav-release-stage-20261030，禁止运行旧UIO脚本。共享后端只读，不扩新业务。
