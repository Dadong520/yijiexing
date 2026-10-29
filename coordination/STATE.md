# 宜捷行当前检查点 2026-10-29T11:11:36.150520+00:00
中央 /Users/dongge/Documents/New project 2/coordination 唯一权威，协议2026-10-26-v3。唯一coordinator01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6统一派单、验收、合并和发布；context UNKNOWN/not_exposed。

## 目前线上基线
Git main最新a720d76e9f822dcfc860c4f324993c18f3c7b6e0（PR39新UI已合并发布；回执文档待UIOREC同步）；当前原生产品源137e41534f823c538fe62ff10820f491e0e55074；后端/后台保留107e226d1d23fc6d7ef5dab75229962bd38306d8/PR37基线。当前已发布乘客1.1.17-experience/code19、司机0.3.16-cloud/code19；原CCUI版18为历史。
API https://yijiexing-demo.hbzjhs.xyz，revision driver-demo-ccui-107e226-test1；后台 https://demo-admin.hbzjhs.xyz/，revision driver-admin-ccui-107e226-test1，均100%，user-test同版。后台原账号密码，无Google/IAP前置。
安装快链 https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download（司机）与/download-passenger（乘客），对应.apk整包，实际版本以回读为准。CCUI发布时HTTPS/hash/原签名和39旧单全部字段一致，证据CCUI-20261029-01/release/FINAL-RELEASE-RECEIPT.json。后来用户新增单，本轮fresh42单备份和回读全部字段一致，不复用旧39结论。
仅独立项目yijiexing-driver-demo-1023/us-west1；禁止原测试/生产。CloudRun automatic/min0/max1/请求CPU计费、API900秒，保留ENV/secret/SA/IAM/RBAC/Origin，不保活、不自动暂停。私有Key/账户/DB/APK/cache/rawCLI外置0600。

## 当前UIO：已发布并完整回读通过
用户新UI设计.docx要求继续优化，尽量Claude实现、Codex审查；用户已离开，要求自主完成、明天体验，不因常规确认停。当前UIO-20261029-01，11项详见任务与evidence/UIO-20261029-01/REQUIREMENTS.json。
产品树 /Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029，branch codex/ui-optimization-20261029，base5302803，最终已验收137e41534f823c538fe62ff10820f491e0e55074。乘客1.1.17-experience/code19、司机0.3.16-cloud/code19已发布，公开HTTPS整包/原ID/原签名/hash匹配验收包。PR39已附聊天，准确head b7f39271e360afc9036af38534f6485840faad98的CI40863647646 SUCCESS，合并a720d76e9f822dcfc860c4f324993c18f3c7b6e0；五产品子树与已验收137完全相同。
Claude官方bundled CLI2.1.304已claude.ai/Pro；本次新独立session b23ffb33-b639-4295-8369-4925bdab9d87/init claude-opus-5-5/medium，4阶段exit0现IDLE。旧首轮413b9历史Desktop上下文不混用。原始输出外置yijiexing-ui-optimization-20261029，脱敏AUTHOR_PHASE1..4入证据。无Bash作者测试NOT_RUN；不绕权限或改全局设置。
实现：司机首页订单大厅及类型筛选/失效筛选复原、新单金额和已知未知距离/服务端沙箱来源、醒目订单状态、钱包个人中心分组迁移、会员身份渐变卡、语义空态；乘客整页地址选择与可滚动历史/坐标与草稿保护、取消成功才回首页/失败留详情、详情返回统一home、功能直接展开、共用图标/按钮层级/间距。无Store/domain/backend/网络/权限/包名/签名/Key改动；root仅版本与治理。
Codex frontend UIOV最终137独立PASS：乘客31（完整子树同f828继承）、司机70最终构建/测试，原ID/签名/版本19/HTTPS/4私有值零命中；本轮获准新隔离AVD emulator5584/本地合成数据实际57截图，取消成功失败、详情与子页返回、地址/历史/草稿、类型过滤与在途预约门禁、会员/账户安全迁移/空态、大字体代表场景实查。原生QA覆盖与真实发包分别证明，真实地图provider及OEM/真实资金未验。AVD/fixture/端口已清理，reviewer IDLE。
Codex backend UIOB最终137 PASS：未知距离误加km、共享沙箱距离来源两项P2均已由Claude修正复核；原FAIL保留。取消成功回调/跨账户、资质与单执行、预约即时并存、金额单位/通知去重不变。初版CI40860616334失败仅累计标题旧回归，Claude真正恢复“运营明细（累计） · 含演示数据”后root在137跑mini74与10页25JS检查PASS，微信编译未跑。证据UIOV/REVIEW、FINAL_BUILD_PROOF、SOURCE_BINDING、UIOB/FINAL_REVIEW、UIO/CI_REPAIR_LOCAL_CHECKS。
UIO精确SHA门禁已PASS，releaseReady true表示验收/发布前提已齐，不表示已上线。源码与验收证据已入库。两固定包已generation保护归档/提升，HTTPS整包回读PASS；fresh42旧单全部字段一致，前后快照同代次/hash，root云业务POST0；完整spec/注解/IAM/revision/traffic均不变。实际回执UIO/release/FINAL-RELEASE-RECEIPT.json。计划仅GCS固定APK更新，无需再部署API/admin。外置release helpers /Users/dongge/.codex/tmp/yijiexing-uio-release-stage-20261029；runtime-before已保存，两服务仍CCUI。本轮这些发布步骤已全部完成；当前仅UIOREC回执/README入库，完成后IDLE，不重建或重复发布。

## 已确认业务与边界
BIZ04预约待出发不占即时，明确开始接驾后双角色同一时刻单执行/欠款门禁；无自动处罚或保证预约准时。BIZ05历史32类只可配置草稿与人工记录。BIZ06新单目的地必填，旧单同ID补填。BIZ07差额只扣一次/余分平台，仅模拟测算。BIZ08三证提前60天提醒，审核通过替换；旧证有效待审可接、过期禁新单、在途可完成。BIZ09公告每用户每发布版本弹一次可关。勿重问。
持久默认地址/乘车人/抬头仅新草稿建议，不覆盖GPS/已填/历史，不自动叫车开票；司机mask不拨，公司电话确认ACTION_DIAL，无IM/中继。后台平台运维→公告/公司客服电话/乘客功能；司机管理→资质到期提醒；侧栏靠下“配置与治理”为非按钮分组，点“历史草稿与人工记录”后选结算管理/营销等分类，本轮不新增后台菜单。
地图EXMAP：用户亲自改乘客控制台两SHA1 DO（字母O）→D0（数字0）并保存；具体Key哈希/包名匹配，原SDK请求乘客从10008恢复10000/1POI，司机仍10000。root已核console-binding-corrected.json及sdk-after-binding-correction.json，JVM合成Context/Python传输不等于设备内SDK成功。用户决定稍后实际手机测底图/搜索，expert IDLE，不轮询；UIOV隔离QA故意无地图凭据，不作真实provider证据。
微信缺AppID未微信编译/真机发布；支付退款提现发票模拟，无认证/录音/警方联动。2.0余项及PMPOST83候选未混入本轮，不称三端真实业务全完成。

## 角色和恢复
角色精确ID见SESSION_REGISTRY：backend01a182c6-c058-76e1-9584-38abfe813799、frontend01a182c6-c475-7110-bbae-0deafe6d2385均GPT6.1/当前IDLE PASS；expert01a1830f-d94b-7933-9c22-2c3898e54186 Astra/xhigh按GOV06仅堵点，当前IDLE；Gemini PAUSED_BY_USER，无在途任务，保留设置。Claude按GOV07及CLAUDE_CODE.md，root唯一发布。
AUTO已PR35实装/独审ACK。根会话完成本轮闭环后等用户体验反馈，不新建守护/bridge/保活，不自动回信循环。中央控制文件只能root写；各角色只写自身卡/任务/证据；工作树仅提交快照，不反向覆盖中央。此前CCREC已PR38完成。

当前源码工作树/Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029（UIOREC文档分支）；共享后端下一次获派修改时仅此树apps/cloud-service可写，旧pm-amap树仅历史，不并发改重复快照。当前后端无改动或待发补丁。
