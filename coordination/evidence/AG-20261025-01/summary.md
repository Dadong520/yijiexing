# AG-20261025-01 专项整改与回归证据汇总（第2轮收口）

## 1. 任务背景与边界
- **任务编号**：`AG-20261025-01` (Round 2)
- **处理方**：Antigravity（前端与客户端会话）
- **工作分支**：`antigravity/passenger-driver-enhancement`
- **边界说明**：
  - 未修改 `candidates/shared-backend-v8/` 等共享后端；
  - 未触碰 Codex 目录业务源码；
  - 未修改云环境配置；未合并 main。

---

## 2. 第2轮复核阻塞项整改对照表

| 审查阻塞项 | 整改措施 | 涉及文件 |
| :--- | :--- | :--- |
| **虚假安全守护与报警报备** | 绑定 `config.notice`；禁用虚假开关与开启反馈（toast 提示未接入）；订单页报警描述明确为调起系统拨号盘拨打 110（无专网定位报备） | `pages/safety/index.wxml`<br>`pages/safety/index.js`<br>`pages/order-detail/index.wxml` |
| **页面透传 accountKey 与未登录隔离** | 页面层通过 `shared.getAccountOwner()` 提取稳定 `passengerId`，全路径传递给库函数；未登录时联系人与卡券均返回空，不能读历史；未登录拦截兑换 | `pages/shared.js`<br>`pages/safety/index.js`<br>`pages/coupons/index.js`<br>`pages/order-detail/index.js`<br>`lib/coupons.js`<br>`lib/safety.js` |
| **卡券页前端演示与不可抵扣声明** | 卡券页顶部增加前端交互演示免责条；卡券列表标注“前端演示·不可抵扣”；兑换按钮标为“演示兑换”，文案明确为演示兑换成功不可抵扣 | `pages/coupons/index.wxml`<br>`pages/coupons/index.js`<br>`lib/coupons.js` |
| **司机端与乘客卡数据口径去伪存真** | 司机端采用“运营明细（累计）”和“累计接单/累计完单”口径；组织信息读取司机实际组织或未提供；乘客司机卡标注“演示”，企业和工号使用动态字段或未提供 | `candidates/driver-0.3.6-r2/.../CoreScreens.kt`<br>`pages/order-detail/index.wxml` |
| **测试盲点与 A/B 页面级测试** | 支付测试绑定真实有效 spy，验证 `pay()` 运行时不调用 `consumeCoupon` 或 `awardPoints`；新增页面级 A/B 账号切换隔离与未登录检查专项单测 | `tests/regression-ag-20261025.test.js` |

---

## 3. 实测结果
- `npm test`: `tests 56, pass 56, fail 0`（含 6 项专项回归全过）
- `npm run check`: `configuration: PASS`
- `sources.md`: 已归档参考文档与未核实项
