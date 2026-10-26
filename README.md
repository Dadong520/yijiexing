# 宜捷行

本仓库保存司机Android、乘客Android、乘客微信小程序、Web管理后台、共享体验后端及对应测试/发布证据。历史冻结目录仅供追溯，不代表当前源码或运行状态。

## 长期项目从这里接手

**先读 [AGENTS.md](AGENTS.md) 与 [START_HERE](coordination/START_HERE.md)。** 项目记忆、预警和换会话方案见[MEMORY_POLICY](coordination/MEMORY_POLICY.md)，新对话可复制[接手指令](coordination/NEW_CHAT_PROMPT.txt)。用户只向coordinator提需求，角色登记见SESSION_REGISTRY.json。禁止把旧副本覆盖中央状态，或每次恢复遍历全部历史。

- 当前发布/唯一工作目录/剩余限制：[STATE](coordination/STATE.md)。本机中央`/Users/dongge/Documents/New project 2/coordination`可能比Git检查点更新，先核对。
- 活跃任务：[BOARD](coordination/BOARD.json)；长期决定：[DECISIONS](coordination/DECISIONS.md)；角色细节：handoffs；契约：contracts。
- 恢复与提交检查：`python3 tools/check_coordination.py --memory-role <role> --session-id <真实会话ID>`；检查器不测量模型内部上下文，不等于业务验收。

## 上次发布核验：2026-10-26

| 项目 | 已核验结果 | 入口 |
|---|---|---|
| 司机Android | 0.3.12-cloud / code15 | https://yijiexing-demo.hbzjhs.xyz/download |
| 乘客Android | 1.1.12-experience / code14 | https://yijiexing-demo.hbzjhs.xyz/download-passenger |
| Web后台 | 后台账号密码登录；已取消Google前置登录 | https://demo-admin.hbzjhs.xyz/ |
| 体验API | 共享持久数据、自有HTTPS域名 | https://yijiexing-demo.hbzjhs.xyz |
| 微信小程序 | 开发源58测试及静态检查通过；缺AppID，未微信编译/真机/发布 | candidates/passenger-miniprogram-0.1.0/miniprogram |

两APK免演示访问码，保留原签名，可覆盖安装。Android乘客开发已恢复，旧“暂停原生乘客”的描述已被用户后续要求替代。详细版本/哈希与后续发布以STATE和实际回执为准，不把此日期表当实时监控。

当前源码路径：`candidates/driver-0.3.6-r2/android`（目录名历史保留，实际版本看Gradle）、`apps/passenger-android`、`apps/admin-console/web`、`apps/cloud-service`。本机唯一可写后端工作树按STATE指定，不能同时修改重复快照。releases和冻结v8保存原基线，不直接改。

## 已验证的本轮能力与边界

新版本权限引导、司机前台/后台新单提醒；接客/送达默认300米，超距或定位不可靠须本人乘客同意，后台可配。独立双模拟器与后台同单完成29.26元模拟支付；完整范围、失败/未测项见[验收报告](coordination/evidence/REL-20261025-02/REPORT.md)与[运行/回退说明](coordination/evidence/REL-20261025-02/OPERATIONS.md)。不能把模拟器测试写成所有厂商真机、永久常驻或正式支付已验收。

独立Google Cloud项目`yijiexing-driver-demo-1023/us-west1`采用Cloud Run自动伸缩、按请求计费、min0/max1；无访问自动缩0，下次请求自动启动，不保活、不自动手工暂停。旧“云已暂停，必须用户通知才恢复”状态已过时。通过适用测试后由coordinator统一发布，保留后台账号/会话/RBAC/审计、服务间IAM、数据和接口；driver-admin按用户授权取消外层IAP；禁止连接原测试/生产。

支付、退款、提现均模拟。地图/短信/微信身份/实名认证/发票/厂商推送缺少真实配置时逐项记NOT_RUN，不用H5预览或模拟回执冒充真实接入。密码、密钥、私有账号库、APK、缓存不入Git；大文件优先直连。
