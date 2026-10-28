# 宜捷行

本仓库保存司机Android、乘客Android、乘客微信小程序、Web管理后台、共享体验后端及对应测试/发布证据。历史冻结目录仅供追溯，不代表当前源码或运行状态。

## 长期项目从这里接手

**先读 [AGENTS.md](AGENTS.md) 与 [START_HERE](coordination/START_HERE.md)。** 项目记忆、预警和换会话方案见[MEMORY_POLICY](coordination/MEMORY_POLICY.md)，新对话可复制[接手指令](coordination/NEW_CHAT_PROMPT.txt)。用户只向coordinator提需求，角色登记见SESSION_REGISTRY.json。禁止把旧副本覆盖中央状态，或每次恢复遍历全部历史。

- 当前发布/唯一工作目录/剩余限制：[STATE](coordination/STATE.md)。本机中央`/Users/dongge/Documents/New project 2/coordination`可能比Git检查点更新，先核对。
- 活跃任务：[BOARD](coordination/BOARD.json)；长期决定：[DECISIONS](coordination/DECISIONS.md)；角色细节：handoffs；契约：contracts。
- 恢复与提交检查：`python3 tools/check_coordination.py --memory-role <role> --session-id <真实会话ID>`；检查器不测量模型内部上下文，不等于业务验收。

## 修改意见2.0 首批体验版

| 项目 | 已发布与核验 | 入口 |
|---|---|---|
| 司机Android | 0.3.14-cloud / code17，原签名 | [司机快速入口](https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download) |
| 乘客Android | 1.1.14-experience / code16，原签名 | [乘客快速入口](https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download-passenger) |
| Web后台 | 继续使用原账号密码 | https://demo-admin.hbzjhs.xyz/ |
| 体验API | 同一持久数据与HTTPS域名 | https://yijiexing-demo.hbzjhs.xyz |
| 微信小程序 | 源码72测试通过；缺AppID，未微信编译/真机/发布 | candidates/passenger-miniprogram-0.1.0/miniprogram |

首批完成公司与客服电话、公告草稿发布撤回、地区乘客功能发布开关和三证提前60天提醒/新证审核替换。后台位置：**平台运维→公告管理、公司与客服电话、乘客端功能配置；司机管理→资质到期提醒**。原32类保留在**配置与治理→历史草稿与人工记录**。

固定产品817ed22已独立验收：7组新HTTP/13组兼容、真实隔离后台DOM、Android24/63和构建、mini72/admin4；frontend独立复核root包签名/版本/hash及源码绑定。见[独立验收](coordination/evidence/PMV-20261028-01/INDEPENDENT_ACCEPTANCE.md)。[PR30](https://github.com/Dadong520/yijiexing/pull/30)已合并主分支7402b117d55bd4d0f1404a5ae2ff0c1cf2e8e0a5，后台/API新版均100%流量，两固定APK已更新、旧包已归档；真实HTTPS整包下载/hash/包名/版本/原签名均回读通过。当前38笔旧单保留，一笔实时定位并发刷新，其余订单字段一致。见[实际发布回执](coordination/evidence/PMV-20261028-01/release/FINAL-RELEASE-REPORT.md)，部署细节与限制见[STATE](coordination/STATE.md)。旧版发布记录保留PR28/PR29。

高德乘客搜索1008/地图10008仍FAIL；微信真机、部分设备GUI未测。支付、退款、提现、发票均模拟；新分账仅确认模拟口径，不执行真实资金或积分。文档28组剩余需求后续分批，PMPOST未集成。两APK沿用原签名支持覆盖安装，不把本版称全项目最终验收。

当前源码路径：`candidates/driver-0.3.6-r2/android`（目录名历史保留，实际版本看Gradle）、`apps/passenger-android`、`apps/admin-console/web`、`apps/cloud-service`。本机唯一可写后端工作树按STATE指定，不能同时修改重复快照。releases和冻结v8保存原基线，不直接改。

## 已验证的本轮能力与边界

新版本权限引导、司机前台/后台新单提醒；接客/送达默认300米，超距或定位不可靠须本人乘客同意，后台可配。独立双模拟器与后台同单完成29.26元模拟支付；完整范围、失败/未测项见[验收报告](coordination/evidence/REL-20261025-02/REPORT.md)与[运行/回退说明](coordination/evidence/REL-20261025-02/OPERATIONS.md)。不能把模拟器测试写成所有厂商真机、永久常驻或正式支付已验收。

独立Google Cloud项目`yijiexing-driver-demo-1023/us-west1`采用Cloud Run自动伸缩、按请求计费、min0/max1；无访问自动缩0，下次请求自动启动，不保活、不自动手工暂停。旧“云已暂停，必须用户通知才恢复”状态已过时。通过适用测试后由coordinator统一发布，保留后台账号/会话/RBAC/审计、服务间IAM、数据和接口；driver-admin按用户授权取消外层IAP；禁止连接原测试/生产。

支付、退款、提现均模拟。地图/短信/微信身份/实名认证/发票/厂商推送缺少真实配置时逐项记NOT_RUN，不用H5预览或模拟回执冒充真实接入。密码、密钥、私有账号库、APK、缓存不入Git；大文件优先直连。
