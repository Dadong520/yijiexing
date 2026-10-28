最新HEAD `87aaffcd97d518cbbefec2effa250ea76059073e`（父519123c）：按合同v1.3逐订单invoiceApplicationEnabled控制Android/mini候选与按钮；只在字段缺失时回退旧当前地区规则，字段存在但非布尔不放开。当前地区关闭仍可展示允许的历史订单申请，订单禁用则当前地区开启也不放行，已开回执列表始终可查。Android选择的订单失去资格会清空选择/确认框；mini直接调用申请处理器也检查每单字段。
受影响验证：乘客24单测+compileDebugKotlin PASS，mini72 PASS（含实际页面处理器跨区双向规则测试）；没有重新组装APK、没有重跑司机或后台。新字段实际HTTP及双端GUI由root独立验证，本角色NOT_RUN。日志invoice-order-passenger-build.log / invoice-order-mini-tests.log。首次mini回归发现新增fallback读取store未导入，已补显式依赖，原断言未删改，最终全部通过。

最新补丁HEAD `519123c1b6e2712f5a71602d875bf653189c107b`：经营概览pendingCertsCount按“旧status=PENDING或有pending候选”计数，filter每证件只计一次；清空候选且APPROVED后归零。实际统计源码片段覆盖新候选、legacy、两者同时、审核后0、空数据与未加载；admin测试4 PASS，语法/diff/提交门禁PASS。仅改admin.js与对应测试；Android/mini沿用之前结果，无无关重跑。本角色实际BrowserHandler DOM NOT_RUN，由root复制最新JS独立验证。

回调补丁说明：HEAD `b450524c249009af1c1adc2332538a8b88e1be68`（父提交 `df9f47b96f0e38afd53c88587fc9046a35b14bf5`）。本补丁仅改乘客PassengerStore：繁忙时在run前同步调用onFailure；会话已切换的早退也回调，页面原onFailure会清除claiming。保留run的同步占忙与stateEpoch++。静态路径核对及compileDebugKotlin PASS；本补丁未重复旧单测/组装APK，原63/22/70/3及双端组装结果属于主提交，作为未变范围回归依据。实际同帧GUI复现仍NOT_RUN。证据announcement-busy-fix.json与announcement-busy-compile.log。

# PMVU-20261028-01 首批客户端实现（作者自测）

源树：/Users/dongge/.codex/tmp/yijiexing-pmv-clients-20261028；branch codex/operations-v2-clients-20261028；base c3c876ef3359f8bc4bb306f515637e1fc72ca621。产品SHA `df9f47b96f0e38afd53c88587fc9046a35b14bf5`，30个产品文件独立提交；未合并发布。绑定中央 PMV-OPERATIONS-API.md v1；不修改后端/gradle版本/原环境。此处是作者实现与自测，业务独立验收NOT_RUN。

## 实现范围

- 真正的后台入口 apps/admin-console/web/admin.js：新增平台运维的公告管理、公司与客服电话、乘客端功能配置及司机管理的资质到期提醒。原32类通用草稿保留为“历史草稿与人工记录”，不会自动发布。
- 公告为结构化标题/内容/北京时间起止时间/全国大区城市/小程序和乘客Android受众，保存草稿、发布、撤回均绑当前版本。只读用户可查看公告内容，写入口受config.write约束；409刷新后必须重新打开核对，不静默覆盖。
- 公司名称/空电话/启停、司机明确公司ID归属/解绑及expectedVersion。公司不等于扫呗签约，不推断组织字符串，不造号码。
- 地区四项三态字段（继承/开/关），草稿与已发布分别显示；同目标已有记录的新建会被要求返回列表编辑，避免用刷新后新version覆盖旧表单。
- 资质60天提醒列表与司机资料审核：当前有效材料、待审材料及source=ACTIVE/REVIEW图片分开，审核绑定expectedVersion+submissionId，驳回原因必填，OPERATOR使用原certificate.audit权限。
- 司机原生：服务端canUpdate为准且pending阻重复提交，提交绑定打开页面时的version。当前有效材料/待审新材料/被驳回说明分开；首页临期提醒，上海日期计算和60天兼容窗口；公司客服仅明确CONFIGURED且有电话时出现用户确认后的系统拨号。
- 乘客原生：登录/状态/action声明PASSENGER_ANDROID；可见且RESUMED时显式ack后仅firstDisplay=true打开纯文本可关闭弹窗。静默state读取不ack；账号/会话切换检查和旧GET失效保护保留。网络不确定回执沿原重试日志处理，可能遗漏一次物理展示，不能保证送达或冒称外部推送。
- 乘客原生和mini：按正式地区功能隐藏/切换新叫车类型，全部关闭时阻新叫车；关闭模拟开票只隐藏新申请，旧记录仍可查。服务端仍为权限与订单归属的权威。客服按state当前公司或每单company显示；乘客可选择启用公司，不改派单/历史归属。
- 小程序：MINIPROGRAM声明、全局可见页面展示互斥、防跨账号旧响应、只有服务端firstDisplay=true弹；公司电话先确认再调用系统电话；移除固定承诺20元券的营销文案。业务分账/优惠券/发分/真实支付未启用。

## 实际测试（见原始日志和result.json）

- driver：外置buildDir + 本地官方高德仓库，JDK17/SDK34，testDebugUnitTest 63 PASS（原59+4日期/服务端状态规则），assembleDebug PASS。
- passenger：相同外置构建，testDebugUnitTest 22 PASS（原20+2地区功能状态），assembleDebug PASS。末次源码修改后差量重编，必须以passenger-build.log最终成功为准。
- mini：node --test tests/*.test.js，70 PASS，含原62和新公告可见性/互斥/回执/账号隔离/电话确认/地区关闭/传输client声明场景；旧共享订单真实本地HTTP回归仍通过。新ops端到端HTTP不是此旧backend测试的覆盖，留root对PMVO整合后实测。
- admin：node --test apps/admin-console/web/operations.test.js，3 PASS（北京时间、非法日期、待审图片REVIEW/PENDING/缺来源但submissionId兼容），node --check PASS；git diff --check PASS。
- 中央memory-role真实ID门禁PASS，结构门禁不是业务验收。
- 首次离线构建缺高德缓存，随后在线成功并切换root给定本地仓库/外置构建；mini首次7 FAIL为新模块未在VM测试依赖中登记，补真实依赖后出现1子测试legacy缺state的兼容问题，已使用同一默认规则修复；最终70 PASS，无弱化原断言。

## root独立验收建议（本轮NOT_RUN）

1. 用PMVO实际后端及非作者会话：SUPER_ADMIN四页保存/发布/撤回/司机绑定/资质候选；VIEWER只读不可写，OPERATOR资质审核可用；旧32草稿与正式配置互不影响。并发打开旧公告/功能/资质表单，409必须刷新重新核对；授权图片有效/待审来源清楚。
2. 公告：开始前/开始时/结束时/撤回/重新发布、地区/双受众、同用户同版本跨设备/两客户端、重放requestId firstDisplay=false、仅state/login不耗次数。Android恢复/切账号/失败稍后，mini页面切换/后台/双订阅，核物理弹窗可关闭且只有成功claim一次。不可宣称微信离线送达。
3. 客服：A/B公司绑定、空号码、停用、司机换绑后旧订单公司不变、新订单记录公司、本人选择回退。Android双端和mini系统电话需本人确认；他人账户/未记录旧订单不得按当前车队推造归属。
4. 地区：全国/大区/城市继承，各关闭一项/全关，保存未发布不影响，发布后切区/重登/深链/直接API受控；旧单接驾/行程/付款/取消/选择已创建竞价和历史发票继续可用。实际API按订单city校验，不以客户端按钮代替。
5. 三证：61/60/59/0/-1天、LONG_TERM、坏日期/待补全，待审时有效旧证可接、新证不会上传即通过、旧证跨天过期阻新单、通过/驳回和过期在途可结束。新候选替换后旧submissionId/版本审核409，重启刷新仍一致。

## 未覆盖与资源

不启动设备/浏览器，不访问云端，不读私有凭据；实际GUI、微信编译/真机（缺AppID）、PMVO新接口实际HTTP/CAS联调、独立验收、正式签名/版本发布均NOT_RUN。高德既有1008/10008未复测，本轮未改地图/密钥。APKs/缓存外置且不进仓库或消息。构建任务完成后无常驻进程；root已有dirty coordination控制快照原样保留，没有一并提交。源树仅允许产品路径提交，状态/短卡放中央；下一步由coordinator整合PMVO和PMVU并非作者复核，统一升版本/签名/发布。

root集成必修项已关闭（作者侧）：待审图片标签兼容REVIEW与旧PENDING，有submissionId但无source仍标待审核，未知来源不标有效；实际渲染调用该helper，admin对应测试PASS。末次门禁一度因coordinator卡超6KB拒绝，root压短后真实frontend门禁PASS，随后仅提交30个产品文件；未越权改总管卡。

最终绑定：司机63/乘客22与两端assembleDebug PASS，mini70 PASS，admin3 PASS。30个产品文件与提交字节完全一致；版本仍为原司机0.3.13/code16、乘客1.1.13/code15，正式版本/签名由root统一。本角色SOURCE SHA df9f47b96f0e38afd53c88587fc9046a35b14bf5，独立验收与发布NOT_RUN。
