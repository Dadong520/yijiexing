# 冻结 v8 最终后端独立复核

结论：45/45 通过，0 failure，0 error；在本次只读代码审查和临时库测试范围内未发现阻断。仅对 `/private/tmp/yijiexing-passenger-v8/cloud-service` 的 v8-source-manifest.json 精确哈希有效，不代表云端发布或设备验收。

## 绑定与完整性

执行前后校验 manifest 的全部 35 个文件，均与冻结值一致且未变化。关键导入路径均断言在冻结目录内并记入 hash 报告。复用独立探针原文件未修改；预先加载冻结 policy_helpers，并断言 common/admin_console/domain/cloud_server/test_cloud/test_passenger 的实际文件路径，避免脚本旧硬编码路径污染。

与前一轮 reviewed manifest 对比，已列文件中 domain.py、passenger.py、tests/test_wait_pay_policy.py 哈希变化；新增证件和阶段测试已纳入。旧版正文未被留存到本复核输入，因此比较为哈希差异加最终实现定点阅读，不冒称逐行旧新 diff。

## 测试组成

| 范围 | 通过 |
|---|---:|
| test_certificate_dates | 4 |
| test_advance_stage | 3 |
| test_wait_pay_policy | 9 |
| test_fare_confirmation | 6 |
| test_driver_projection_privacy | 2 |
| test_negotiation | 13 |
| 原独立 backend_policy 探针 | 6 |
| 本轮额外独立探针 | 2 |
| 合计 | 45 |

本轮额外探针是：私有及共享订单 expectedStage 陈旧时，accounts/passengers/passenger_orders/actions/passenger_actions/audits 六表逐行不变；validFrom 未来生效时，已接行程仍能推进并完成旧付。探针仅放临时目录，未修改任何原源码或原测试。

## 结论边界

- 有效日期按 UTC+8 当天判定，validFrom/validUntil 当天包含在有效范围。未来/过期证件拒绝新增私有、共享和议价，竞价选司机和议价认领重新检查；截止当天可接受。
- 已过期证件及未来生效证件均不阻断既有行程推进、旧单付款；过期场景另覆盖下线。缺失或非规范日期按实现保持旧数据兼容，并非强制日期校验。
- expectedStage 陈旧返回 409；私有与共享均验证拒绝后六张业务表不变。原成功 requestId 重放安全，缺字段旧调用继续兼容。
- WAIT_PAY 不占司机运输，新单及议价可承接；欠费乘客不能新叫车/认领。跨单晚付、CAS 竞争、金额版本/ABA、退款后重放、司机隐私投影均通过受影响用例。
- 全部采用临时 SQLite、本机 HTTP、MemoryCAS。未连接或写云，未控制设备，未操作真实 001 私有 YC1001 待付 2850；测试 YC1001 为临时种子数据。

证据：final-61-tests.log、final-61-hash.json、final-61-diff-hash.json。
