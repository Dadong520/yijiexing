# 独立三角色接口集成测试报告

执行时间：2026-10-23T21:23:41+08:00（UTC+8）。执行者：后台源码核对/独立QA agent。

结果：**6个测试用例全部通过**，用时约3.2秒。测试使用临时SQLite数据库，运行后清除；没有接触原测试/生产环境，也没有修改共享服务源码或使用真实云账号。

本报告仅证明当前读取版本的Service与AdminConsole业务接口集成行为。不是HTTP网关/IAM验证、不是部署验证、不是双模拟器和浏览器实测、不是反重力独立测试。临时生成的订单ID不作为部署环境验收订单。

## 可复现执行

在项目根目录执行：

```sh
python3 yijiexing-admin-20261023/qa/test_three_party.py
```

测试文件中的密码只用于瞬时临时测试数据库，不是部署管理员凭据。

| 用例 | 验证内容 | 结果 |
|---|---|---|
| test_bid_full_flow_evaluation_complaint_reply_and_restart | 乘客竞价创建→司机报价→后台查询报价→选择司机→模拟预付→到达→错误尾号拒绝→正确尾号行程→结束；三个角色视图相同订单ID/状态/金额；重复预付不重复入账；评价/投诉/后台处理结果双端回读；重开SQLite保留；统计排除私有演示订单 | 通过 |
| test_admin_prepaid_cancel_refund_once_and_both_clients | 竞价已预付后后台取消，三视图退款金额和CANCELLED一致；重复管理请求只产生一次审计且不增加司机余额；同requestId变更内容409 | 通过 |
| test_standard_adjust_failed_payment_recovery_and_completed_refund | 标准单到WAIT_PAY，后台调整整数分费用三视图一致；FAILED支付保留待支付，SUCCESS后DONE；后台模拟全退扣回司机余额一次；重复退款和DONE改价拒绝 | 通过 |
| test_permissions_api_enforcement_and_session_revocation | 新建只读账号、操作与导出403，订单未改；敏感账号列表不返回；调整角色后旧会话401；状态无password_hash | 通过 |
| test_duplicate_create_and_failed_admin_retry_without_partial_mutation | 同requestId重复下单仅一单；缺确认管理操作400且订单未改变；补确认同ID重试成功 | 通过 |
| test_unfinished_cancel_denied_and_atomic_rollback | TRIP后台取消409；WAIT_PAY调价后模拟事务回滚，费用和审计均恢复 | 通过 |

## 本轮未覆盖

- 两台模拟器与后台浏览器实际交互、App刷新/视图呈现。
- 已部署Google Cloud快照跨实例争用、HTTP鉴权路由、暂停恢复。
- 反重力实际操作和截图证据。
- 城市/会员配置、资质附件审核、提现UI；已有其它测试不能自动补算本报告覆盖。
- 外部短信、真实支付、地图导航、实名、推送、发票服务。

这些项目继续保留在ACCEPTANCE.md待执行，不据此宣布三端验收通过。

## 测试时源码哈希

共享源文件可能由其负责人继续更新；部署前应对最终版本重跑。

- `yijiexing-admin-20261023/qa/test_three_party.py`：`21ffe7aa852d49bfe5bce24add9f62e12f6da57478547822dd2ddf4eb89ebbf8`
- `yijiexing-admin-20261023/backend/admin_console.py`：`75301e07886ef08036cca53556cf79ca43a5d3c0498a0e129ec2dfb94c230cd5`
- `yijiexing-cloud-20261023/cloud-service/passenger.py`：`8570f74c033dcd47158291112b75343935e0337dc55d1aede951353328c8043f`
- `yijiexing-cloud-20261023/cloud-service/domain.py`：`2808f5eb5159eaa44e6fd15e0c9cd1572be81070480d3ea75d3f5cc7d4d85b6c`
