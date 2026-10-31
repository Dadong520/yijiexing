# 宜捷行

本仓库保存司机Android、乘客Android、乘客微信小程序、Web管理后台、共享体验后端及对应测试/发布证据。历史冻结目录仅供追溯，不代表当前源码或运行状态。

## 长期项目从这里接手

**先读 [AGENTS.md](AGENTS.md) 与 [START_HERE](coordination/START_HERE.md)。** 项目记忆、预警和换会话方案见[MEMORY_POLICY](coordination/MEMORY_POLICY.md)，新对话可复制[接手指令](coordination/NEW_CHAT_PROMPT.txt)。用户只向coordinator提需求，角色登记见SESSION_REGISTRY.json。禁止把旧副本覆盖中央状态，或每次恢复遍历全部历史。

- 当前发布/唯一工作目录/剩余限制：[STATE](coordination/STATE.md)。本机中央`/Users/dongge/Documents/New project 2/coordination`可能比Git检查点更新，先核对。
- 活跃任务：[BOARD](coordination/BOARD.json)；长期决定：[DECISIONS](coordination/DECISIONS.md)；角色细节：handoffs；契约：contracts。
- 恢复与提交检查：`python3 tools/check_coordination.py --memory-role <role> --session-id <真实会话ID>`；检查器不测量模型内部上下文，不等于业务验收。

## 本轮地图拖动与乘客选址（版本22已发布）

[PR45](https://github.com/Dadong520/yijiexing/pull/45)已合并并实际发布。产品源d6d5206a82f16987fd51371044fb12e76ba9f8d8，产品合并SHA49bc669ec8f412756eb782498bcedeecb4098b52；准确head125699eb8729e43bd3ec83d2db50bd292b479afd/CI41185604204 SUCCESS。乘客1.1.20-experience/code22、司机0.3.19-cloud/code22，原包名/原签名支持覆盖安装。

乘客出行首页、乘客订单、司机听单首页和司机行程页均支持功能面板三态拖动：上拖展开并滚动全部功能，半展同时看地图和功能，下拖至全屏地图，仅保留恢复入口。全屏暂隐藏应用顶栏、身份栏和底部导航，恢复、返回或账号切换后还原；订单阶段变化继续露出关键操作。屏幕配置重建按设计回到半展开，避免遗留隐藏导航，不承诺跨重建保持全屏。

乘客新草稿以可靠当前定位作为起点，目的地名称及坐标默认空，手动起点和已有草稿/订单不被覆盖。起终点选址每次查询为空，地图在上、输入和模糊结果在下；选中并确认才修改草稿，取消保留原值。新查询立即作废旧选择，防抖/搜索期间不能确认旧点；路线不完整时不能交换或提交。

Claude完成三阶段实现与退修，Codex独立业务及原生验收。最终D6实际乘客54/司机77项单测零失败，双原包及隔离测试包重新构建；M1/M2/M3四页面拖动、滚动末项、全屏、点击恢复、阶段、返回、小屏键盘、账号和定位/选址保护通过。少数早期原生场景按明确函数/处理器相同继承，旧图保留原SHA；没有把所有图伪标最终版。模拟器、本地服务和端口转发已清理。root tools27/mini74/check与相关扫描用例1通过，实际执行及继承来源见记录。

两个公开HTTPS整包已完整下载，大小、SHA256、包名、版本和原签名均匹配独立验收原包。发布前后45→45笔订单，旧订单0丢失/0字段改，新增0；比较范围仅passenger_orders。仅替换独立体验项目GCS安装包，旧21受generation保护归档；后端/后台产品树、CloudRun完整配置、IAM、revision和traffic不变，automatic/min0/max1/请求计费及concurrency1保持。

隔离QA无provider凭据且离线；真实底图、正向POI/模糊结果成功、逆地理、路线及相机位移/捏合成功未验证。地图区域触摸仲裁、尺寸和控件验证不等于provider成功。无个人手机/OEM/真实资金或云业务写，合成阶段不代表完整真实行程。见[实际发布回执](coordination/evidence/MAPS-20261031-01/release/FINAL-RELEASE-RECEIPT.json)、[最终独立验收](coordination/evidence/MAPSF-20261031-01/FINAL_REVIEW.json)和[最终构建来源](coordination/evidence/MAPSF-20261031-01/FINAL_BUILD_PROOF.json)。

## 上一轮司乘界面优化（历史版本21）

[PR43](https://github.com/Dadong520/yijiexing/pull/43)已合并并实际发布。产品源ecc9d8d0e63f5ee1c9a8f45df87e92f7b42e0ea5，产品合并SHA8e8da981faa54de0bd12d24e1921d3d20a3d570b，准确PR head089ea8c/CI41063380582 SUCCESS。乘客1.1.19-experience/code21、司机0.3.18-cloud/code21，原包名/原签名支持覆盖安装。

Claude完成主要实现和审查退回修复，Codex独立验收。统一字号、按钮、弹窗及个人中心，乘客订单操作固定底部、地点/途经点通过选点、欠款提示合并；司机听单设置收紧，工具移至个人中心，行程导航与主操作同行，钱包按页签查看。独立验收发现的小屏大字按钮截字、证件待审重复催更新已修复复验，原失败图与记录保留。

实际17d集成单测乘客54/司机77零失败，root tools27/mini74及项目检查PASS；后续只修改弹窗布局与证件显示，按明确来源继承相关测试，不冒称最终SHA重跑。乘客281构建整树等同最终ECC，司机ECC重新构建；独立原生G1/P1–P4/D1–D3及两项差量复验PASS，隔离环境已清理。场景含合成状态与模拟资金，未把每个阶段布局检查称完整真实订单生命周期。

两公开HTTPS整包已重新完整下载，版本、包名、原签名、大小和SHA256与验收原包一致。初次下载未完整和并发429保留；在不改服务单请求配置的情况下单独下载后均PASS。发布前后45笔原订单全部存储字段一致，0丢失/0修改/0新增；范围为passenger_orders，不扩称全部数据库表。仅GCS安装包替换，旧20受generation保护归档；API/admin完整配置、IAM、revision和traffic保持，automatic/min0/max1/请求计费未改。

本轮没有后台或后端功能变更。真实地图服务/逆地理/外部高德接收、OEM、个人手机、真实资金未验；微信缺AppID未微信编译或发布。未提交叫车草稿在返回编辑/设置时保留，跨底栏重建保留不是本轮承诺。见[实际发布回执](coordination/evidence/UIT-20261030-01/release/FINAL-RELEASE-RECEIPT.json)、[最终独立原生验收](coordination/evidence/UI30F-20261030-01/NATIVE_ACCEPTANCE_FINAL.json)与[最终构建来源](coordination/evidence/UI30F-20261030-01/BUILD_PROOF_ecc9d8d.json)。

## 上一轮导航、定位和确认交互（历史版本20）

[PR41](https://github.com/Dadong520/yijiexing/pull/41)已合并并实际发布，产品源6ab66bdcd20b43dfd314917881342dcdcc165a9e，合并SHA3fdd19cb5bcb89ef19999e9fd7c2dac7571323f8。乘客1.1.18-experience/code20、司机0.3.17-cloud/code20，原包名/原签名支持覆盖安装。Claude Code负责实现与修复，Codex独立完成业务、原生及发布数据复核。

司机在真实进入接驾或行程阶段后自动打开现有外部导航，保留手动入口；乘客确认按账号/订单/请求隔离，失败能重试、点击时重验状态与有效期。定位默认起点尊重权限与手动选择，地址400ms搜索可取消并重试；公告/权限引导/内外确认框按优先级显示。司机身份栏位于地图上方，钱包列表默认3条可展开。

独立验收包括791基线7组/65原生截图，以及最终两条说明文字的6ab差量重建/2页面截图；原65图片保留原SHA。54/77项已实际执行的单测按源码对象一致或明确文案差异复用，不冒称重跑。两个公开HTTPS整包的大小、哈希、版本和原签名匹配独立验收包。44笔旧订单全保留：43笔全字段一致，1笔仅正常并发定位及更新时间变化，经独立定位回执/时间/源路径核验；严格全字段相等的初始FAIL保留，未覆盖新数据。API/admin完整配置、IAM、revision、traffic不变，仅固定GCS安装包提升，automatic/min0/max1/请求计费保持。

本地联测使用合成订单、mockGPS和实际Android Intent接收器；真实高德/逆地理成功、真实地图应用/OEM及真实资金未验。公告既有先领取后显示机制仍不能保证跨账号/进程重启后的持久补弹。见[实际完整发布回执](coordination/evidence/NAV-20261029-01/release/FINAL-RELEASE-RECEIPT.json)、[原生验收](coordination/evidence/NAVF-20261029-01/FINAL_REVIEW.json)、[文案差量](coordination/evidence/NAVF-20261029-01/COPY_DELTA_REVIEW.json)与[并发定位复核](coordination/evidence/NAVD-20261030-01/REVIEW.json)。

## 上一轮UI交互优化（历史发布）

[PR39](https://github.com/Dadong520/yijiexing/pull/39)已合并并发布，产品源137e41534f823c538fe62ff10820f491e0e55074，合并SHA a720d76e9f822dcfc860c4f324993c18f3c7b6e0。乘客1.1.17-experience/code19、司机0.3.16-cloud/code19，原包名/原体验签名。Claude Code完成主要实现，Codex分别做原生与业务语义独立审查。

司机首页整理为可筛选订单大厅，突出状态、新单金额与已知/未知距离及沙箱估算来源；钱包与个人中心重新分组，会员卡更醒目，补齐语义图标和空状态。乘客全屏选址可滚动历史，返回不改草稿，取消成功才回首页、失败留详情，详情返回统一首页，个人中心功能直接展开。

非作者原生31/70项单测、双构建和57组截图通过；乘客完整源码子树同最终版继承已验收构建，司机按最终SHA重建。mini74及10页25JS检查、准确PR head CI通过，原包名/版本19/原签名/HTTPS/hash及私有值零匹配通过。两公开HTTPS整包与独立验收包完全一致，42笔旧订单全部字段一致。此次仅更新固定APK对象，API/admin完整配置、IAM、revision与traffic未改；仍automatic/min0/max1/既有请求计费。

实际地图provider/手机OEM与真实资金未在本轮验证；隔离原生QA使用合成数据，未宣称三端真实业务全通过。见[实际发布报告](coordination/evidence/UIO-20261029-01/release/FINAL-RELEASE-REPORT.md)、[完整回执](coordination/evidence/UIO-20261029-01/release/FINAL-RELEASE-RECEIPT.json)和[原生独立验收](coordination/evidence/UIOV-20261029-01/REVIEW.json)。

## 首轮配色升级与Claude Code接入（历史发布）

产品107e226d1d23fc6d7ef5dab75229962bd38306d8已通过[PR37](https://github.com/Dadong520/yijiexing/pull/37)合并并发布。乘客1.1.16-experience/code18、司机0.3.15-cloud/code18，原包名/原体验签名。保留青绿品牌，加入蓝/暖橙/紫模块层次，统一危险操作与状态色，增强文字和交互轮廓对比度。后台与双Android已更新；小程序共享样式已改，仍缺AppID未微信发布。

非作者原生验收/双端构建与31/63项测试、mini74、admin4、协作门禁27通过；公开HTTPS两个整包/hash/版本/原签名匹配，原有39订单全字段一致。两服务ccui-107e226-test1为100%，配置/IAM/automatic/min0max1/请求计费保持。高德真实鉴权、OEM及全部页面穷举未验，模拟账务边界未改变。见[实际发布回执](coordination/evidence/CCUI-20261029-01/release/FINAL-RELEASE-REPORT.md)与[独立原生结果](coordination/evidence/CCUIV-20261029-01/REVIEW.json)。

Claude Code已真实接入并完成主力实现，Gemini按用户决定暂时停派；唯一协调者仍负责派单、独立验收、合并发布，Astra只按疑难派单。

## 上一批乘客端UI体验版（历史发布）

固定产品7d6dc6014906c80be87b39f4433ec494ae067098已完成13图涉及页面改造与三类持久默认项，乘客1.1.15-experience/code17，司机不变。详情首屏金额取消、整页行程设置保草稿、紧凑列表/空态、联系帮助页、回执和投诉层级均经隔离模拟器复核；本地数据为合成场景。默认HTTP6组、兼容13组、运营7组、Android31与构建、mini74组件等同、独立版本/签名/Key0检查通过，见[本轮独立验收](coordination/evidence/PUI-20261028-01/INDEPENDENT_ACCEPTANCE.md)。[PR32](https://github.com/Dadong520/yijiexing/pull/32)已合并主分支a542f6640681956f330e91bc67ecf2f62cfcbda8；API/后台新版100%，乘客固定APK已更新并完成HTTPS整包、原签名回读。发布前后39旧单全部存储字段一致，实际部署与边界见[本轮发布回执](coordination/evidence/PUI-20261028-01/release/FINAL-RELEASE-REPORT.md)。

## 当前入口与修改意见2.0首批能力

| 项目 | 已发布与核验 | 入口 |
|---|---|---|
| 司机Android | 0.3.19-cloud / code22，原签名 | [司机快速入口](https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download) |
| 乘客Android | 1.1.20-experience / code22，原签名 | [乘客快速入口](https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download-passenger) |
| Web后台 | 继续使用原账号密码 | https://demo-admin.hbzjhs.xyz/ |
| 体验API | 同一持久数据与HTTPS域名 | https://yijiexing-demo.hbzjhs.xyz |
| 微信小程序 | 源码74测试通过；缺AppID，未微信编译/真机/发布 | candidates/passenger-miniprogram-0.1.0/miniprogram |

首批完成公司与客服电话、公告草稿发布撤回、地区乘客功能发布开关和三证提前60天提醒/新证审核替换。后台位置：**平台运维→公告管理、公司与客服电话、乘客端功能配置；司机管理→资质到期提醒**。原32类保留在**配置与治理→历史草稿与人工记录**。

固定产品817ed22已独立验收：7组新HTTP/13组兼容、真实隔离后台DOM、Android24/63和构建、mini72/admin4；frontend独立复核root包签名/版本/hash及源码绑定。见[独立验收](coordination/evidence/PMV-20261028-01/INDEPENDENT_ACCEPTANCE.md)。[PR30](https://github.com/Dadong520/yijiexing/pull/30)已合并主分支7402b117d55bd4d0f1404a5ae2ff0c1cf2e8e0a5，后台/API新版均100%流量，两固定APK已更新、旧包已归档；真实HTTPS整包下载/hash/包名/版本/原签名均回读通过。该批发布时38笔旧单保留，一笔实时定位并发刷新，其余订单字段一致。见[实际发布回执](coordination/evidence/PMV-20261028-01/release/FINAL-RELEASE-REPORT.md)，部署细节与限制见[STATE](coordination/STATE.md)。旧版发布记录保留PR28/PR29。

高德控制台乘客SHA1字母O/数字0笔误已由用户修正，原SDK生成请求从10008恢复10000/1POI；实际手机底图与地点搜索仍待用户验证，本轮无凭据隔离QA不作真实地图成功证据。微信真机、部分设备GUI未测。支付、退款、提现、发票均模拟；新分账仅确认模拟口径，不执行真实资金或积分。文档28组剩余需求后续分批，PMPOST未集成。两APK沿用原签名支持覆盖安装，不把本版称全项目最终验收。

当前源码路径：`candidates/driver-0.3.6-r2/android`（目录名历史保留，实际版本看Gradle）、`apps/passenger-android`、`apps/admin-console/web`、`apps/cloud-service`。本机唯一可写后端工作树按STATE指定，不能同时修改重复快照。releases和冻结v8保存原基线，不直接改。

## 已验证的本轮能力与边界

新版本权限引导、司机前台/后台新单提醒；接客/送达默认300米，超距或定位不可靠须本人乘客同意，后台可配。独立双模拟器与后台同单完成29.26元模拟支付；完整范围、失败/未测项见[验收报告](coordination/evidence/REL-20261025-02/REPORT.md)与[运行/回退说明](coordination/evidence/REL-20261025-02/OPERATIONS.md)。不能把模拟器测试写成所有厂商真机、永久常驻或正式支付已验收。

独立Google Cloud项目`yijiexing-driver-demo-1023/us-west1`采用Cloud Run自动伸缩、按请求计费、min0/max1；无访问自动缩0，下次请求自动启动，不保活、不自动手工暂停。旧“云已暂停，必须用户通知才恢复”状态已过时。通过适用测试后由coordinator统一发布，保留后台账号/会话/RBAC/审计、服务间IAM、数据和接口；driver-admin按用户授权取消外层IAP；禁止连接原测试/生产。

支付、退款、提现均模拟。地图/短信/微信身份/实名认证/发票/厂商推送缺少真实配置时逐项记NOT_RUN，不用H5预览或模拟回执冒充真实接入。密码、密钥、私有账号库、APK、缓存不入Git；大文件优先直连。
