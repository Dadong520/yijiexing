# AG-20261025-01 专项整改与回归证据汇总

## 1. 任务背景与边界
- **任务编号**：`AG-20261025-01`
- **处理方**：Antigravity（前端与客户端会话）
- **工作分支**：`antigravity/passenger-driver-enhancement`
- **业务代码提交 SHA**：`e15b22ad5889f87fe73639ecbacbd95a8bd8bde1`
- **边界说明**：
  - 未修改 `candidates/shared-backend-v8/` 等共享后端，等待 backend 会话出台统一营销持久化账务契约（`BE-20261025-01`）；
  - 未修改或覆盖 Codex 工作目录（`/Users/dongge/Documents/New project 2`）的业务源码；
  - 未修改 `releases/` 和 `backend-frozen-*` 历史资产；
  - 未部署云端，保持当前实例 `--scaling=auto --min=0 --max=1`，无保活，不合并 main。

---

## 2. 审查阻塞项整改对照表

| 审查阻塞项 | 原代码缺陷 | 整改措施 | 涉及文件 |
| :--- | :--- | :--- | :--- |
| **home 预估与客户端扣减** | 前端预估硬编码 2800，`submit` 私自携带未接后端的 `priorityFee/couponId/couponDiscount` | 去掉 2800 伪造逻辑；卡券与加价栏明确展示“待营销服务接入”；`submit` 仅向后端发送规范起终点与必要字段，不假冒账务权威 | `pages/home/index.js`<br>`pages/home/index.wxml` |
| **WAIT_CONTINUE 入口丢失** | 司机取消后界面状态流转到 `WAIT_CONTINUE` 但缺少继续叫车/放弃卡片绑定 | 补回司机取消卡片，重新绑定 `continueTrip`，支持 `type=1` 继续叫车与 `type=2` 放弃本次叫车 | `pages/order-detail/index.wxml`<br>`pages/order-detail/index.js` |
| **cancel 失败仍退券且兜底 CPN_NEW10** | `cancel()` 未判 `res.result` 即退券，且无券时 `o.couponId \|\| 'CPN_NEW10'` 导致凭空退券 | 严格校验 `res && res.result`，失败绝不退券；仅当 `o.couponId` 真实存在时才释放对应券，严禁兜底 | `pages/order-detail/index.js`<br>`lib/coupons.js` |
| **PREPAY 阶段私自核销与发积分** | `pay()` 本地直接调用 `consumeCoupon` 与 `awardPoints`，让客户端充当账务权威 | 彻底移除客户端私自核销与发分，仅按契约提交 `order.pay`，账务与积分交由服务端统一裁决 | `pages/order-detail/index.js` |
| **虚假安全/资质保证** | 硬编码“全程录音保护中”、“公安审查三证通过”、“自动报警”等无物理依据背书 | 去除虚假录音与三证合规承诺，明确为开发体验环境；求助明确为“调起手机电话系统拨打 110” | `pages/order-detail/index.wxml`<br>`lib/safety.js` |
| **司机端 6.5h / 12单硬编码** | `CoreScreens.kt` 硬编码 6.5h 在线时长、12接单、12完单及卓越司机100分 | 采用真实 `state.online` 展示“出车中/休息中”，接单/完单使用真实 `state.todayAccepted`/`state.todayCompleted`；服务分按实际分值或“暂未考核”展示 | `CoreScreens.kt` |
| **本地券与积分跨账号串用** | 本地 storage 键固定无账号区分，多账号切换时数据串用 | `getStorageKey` 统一追加 `accountKey` 隔离后缀；过期券（`expiresAt`）在加载与校验时强制标记 `EXPIRED` 并拒绝使用 | `lib/coupons.js`<br>`lib/points.js`<br>`lib/safety.js`<br>`pages/profile/index.js` |
| **投诉附件入口丢失** | 投诉历史记录无法展示附件，无多图上传入口 | 恢复投诉历史附件列表展示与 `attach` 上传凭证图片入口（最多3张，Base64编码，限制512KB） | `pages/order-detail/index.wxml`<br>`pages/order-detail/index.js` |

---

## 3. 测试与验证结果

### 3.1 单元与回归测试 (`npm test`)
- **执行命令**：`cd candidates/passenger-miniprogram-0.1.0/miniprogram && export PATH="/opt/homebrew/bin:$PATH" && npm test`
- **执行时长**：~2.3 秒
- **统计结果**：`tests 56, pass 56, fail 0, cancelled 0, skipped 0, todo 0`
- **新增回归用例** (`tests/regression-ag-20261025.test.js`) 6 项全过：
  1. `order-detail WAIT_CONTINUE 支持同单继续与放弃` (PASS)
  2. `取消订单严格校验服务端结果，无券不兜底退券` (PASS)
  3. `pay() 仅执行契约支付，不私自核销券或发放假积分` (PASS)
  4. `过期卡券严格不可选，本地存储按账号严格隔离` (PASS)
  5. `投诉支持多图凭证上传，无伪造公安查验和录音保证` (PASS)
  6. `司机端移除硬编码6.5h/12单和卓越司机100分` (PASS)

### 3.2 项目静态检查 (`npm run check`)
- **执行命令**：`cd candidates/passenger-miniprogram-0.1.0/miniprogram && export PATH="/opt/homebrew/bin:$PATH" && npm run check`
- **输出结果**：`{"pages":10,"parsedJavaScript":24,"configuration":"PASS","wechatCompilation":"NOT_RUN_REQUIRES_DEVELOPER_TOOLS","appId":"NOT_CONFIGURED"}`
- **NOT_RUN 项**：`wechatCompilation`（需要微信开发者工具真实环境 CLI，当前在 headless node 环境下标识为 NOT_RUN）。
