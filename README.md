# 宜捷行

本仓库保存司机Android、乘客Android、乘客微信小程序、Web管理后台、共享体验后端及对应测试/发布证据。历史冻结目录仅供追溯，不代表当前源码或运行状态。

## 长期项目从这里接手

**先读 [AGENTS.md](AGENTS.md) 与 [START_HERE](coordination/START_HERE.md)。** 项目记忆、预警和换会话方案见[MEMORY_POLICY](coordination/MEMORY_POLICY.md)，新对话可复制[接手指令](coordination/NEW_CHAT_PROMPT.txt)。用户只向coordinator提需求，角色登记见SESSION_REGISTRY.json。禁止把旧副本覆盖中央状态，或每次恢复遍历全部历史。

- 当前发布/唯一工作目录/剩余限制：[STATE](coordination/STATE.md)。本机中央`/Users/dongge/Documents/New project 2/coordination`可能比Git检查点更新，先核对。
- 活跃任务：[BOARD](coordination/BOARD.json)；长期决定：[DECISIONS](coordination/DECISIONS.md)；角色细节：handoffs；契约：contracts。
- 恢复与提交检查：`python3 tools/check_coordination.py --memory-role <role> --session-id <真实会话ID>`；检查器不测量模型内部上下文，不等于业务验收。

## 本轮UI交互优化（已发布）

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
| 司机Android | 0.3.16-cloud / code19，原签名 | [司机快速入口](https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download) |
| 乘客Android | 1.1.17-experience / code19，原签名 | [乘客快速入口](https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download-passenger) |
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
