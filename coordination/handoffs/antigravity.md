# Antigravity 任务交接与状态报告 (AG-20261025-01)

## 1. 规则与约束确认 (ACK)
- **已读规则文件**：
  - `/Users/dongge/Documents/New project 2/AGENTS.md` (ACK)
  - `/Users/dongge/Documents/New project 2/coordination/STATE.md` (ACK)
  - `/Users/dongge/Documents/New project 2/coordination/tasks/AG-20261025-01.json` (ACK)
  - `/Users/dongge/Documents/New project 2/coordination/reviews/AG-20261025-01.md` (ACK)
- **工作边界与原则**：
  - 本轮修改严格限定在客户端（小程序端与司机端 Android 代码），未触碰 `candidates/shared-backend-v8/` 等任何共享后端代码；
  - 严禁且未修改 Codex 工作目录（`/Users/dongge/Documents/New project 2`）的任何业务源码；
  - 严禁且未修改 `releases/` 交付历史与 `backend-frozen-*` 资产；
  - 保持云端当前按请求计费配置（`--scaling=auto --min=0 --max=1`），本轮未自动部署、未开启保活、未合并 main。

---

## 2. 本批次修改结果与精确提交 SHA

### 2.1 业务代码提交 SHA
- **提交 SHA**：`e15b22ad5889f87fe73639ecbacbd95a8bd8bde1`
- **提交信息**：`fix(client): AG-20261025-01 修复订单与投诉回归退化、账务与数据去伪存真`
- **修改文件列表**：
  - `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/CoreScreens.kt`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/lib/coupons.js`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/lib/points.js`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/lib/safety.js`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/pages/home/index.js`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/pages/home/index.wxml`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/pages/order-detail/index.js`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/pages/order-detail/index.wxml`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/pages/profile/index.js`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/pages/profile/index.wxml`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/tests/points-and-referral.test.js`
  - `candidates/passenger-miniprogram-0.1.0/miniprogram/tests/regression-ag-20261025.test.js` (新增页面级专项回归测试)

### 2.2 核心整改要点
1. **既有流转与入口恢复**：
   - 补齐 `WAIT_CONTINUE` 同单继续/放弃卡片，完整保留 `continueTrip` 入口（`type=1` 继续叫车，`type=2` 放弃）；
   - 恢复售后投诉凭证多图上传入口（`attach`，最多 3 张 Base64 图片，单张限 512KB）与历史附件展示。
2. **账务权威隔离与真实性**：
   - 移除 `order-detail/index.js` 中 `pay()` 时的客户端私自核销券和发放积分，杜绝客户端冒充账务权威；
   - 修复 `cancel()`，必须在 `res && res.result` 明确成功且订单自身包含 `couponId` 时才释放，严禁兜底 `CPN_NEW10`；
   - 移除 `home` 页面的固定 2800 假估价，提单不再向未接营销服务的后端发送伪造优惠券抵扣。
3. **去伪存真**：
   - 乘客端移除虚假“全程录音保护中”与“公安审查三证通过”确定性承诺，紧急求助文案明确为“调起手机电话系统拨打 110”；
   - 司机端 `CoreScreens.kt` 移除硬编码的 `6.5 h / 12接单 / 12完单` 与 `卓越司机 (100分)`，改为基于 `state.online`, `state.todayAccepted`, `state.todayCompleted` 及实际服务分或“暂未考核”展示。
4. **多账号存储隔离与过期卡券校验**：
   - 本地卡券、积分和紧急联系人的 storage key 均增加登录账号隔离（`accountKey` 后缀）；
   - 在 `isCouponApplicable` 中增加严格的 `coupon.expiresAt` 截止日期校验，过期券自动不可用并在加载时标记 `EXPIRED`。

---

## 3. 实跑测试与验证命令

### 3.1 测试执行
```bash
cd candidates/passenger-miniprogram-0.1.0/miniprogram
export PATH="/opt/homebrew/bin:$PATH"
npm test
```
- **测试结果**：56 项测试全部通过（通过 56，失败 0，跳过 0）。
- **新增回归单测**：`tests/regression-ag-20261025.test.js` 共 6 项全部通过。
- **日志归档**：`coordination/evidence/AG-20261025-01/test-run.log`

### 3.2 项目检查执行
```bash
cd candidates/passenger-miniprogram-0.1.0/miniprogram
export PATH="/opt/homebrew/bin:$PATH"
npm run check
```
- **输出**：`{"pages":10,"parsedJavaScript":24,"configuration":"PASS","wechatCompilation":"NOT_RUN_REQUIRES_DEVELOPER_TOOLS","appId":"NOT_CONFIGURED"}`
- **日志归档**：`coordination/evidence/AG-20261025-01/check-project.log`

---

## 4. 失败项与 NOT_RUN 项说明

- **失败项 (Failures)**：无（0 failures）。
- **NOT_RUN 项 (Not Executed)**：
  1. `wechatCompilation: NOT_RUN_REQUIRES_DEVELOPER_TOOLS`：
     - 原因：当前环境为终端 Node.js 运行环境，未启动微信开发者工具真机/模拟器 CLI，符合微信小程序 headless 自动化基线；
  2. `backend 159 tests`:
     - 原因：按协作规则，不重跑未发生变动的后端 159 项测试来充当新增能力证明，严禁编造；
  3. `android gradle build`:
     - 原因：仅对 Kotlin UI 层展示文案及数据绑定进行去伪存真静态重构，本批次未执行移动端 Gradle 完整打包。

---

## 5. 问题清单与后续建议 (Issues & Next Steps)

### 5.1 待后端契约（等待 BE-20261025-01）
- **营销持久化接口**：
  - 待后端统一出台卡券领取、核销、退券及积分发放的 RPC 接口与状态变更规范；
  - 届时前端将把目前标记为“体验中·待营销服务接入”的交互直接接入后端真实持久化链路。
- **加价调度费（Priority Fee）**：
  - 待共享后端增加加价调度费计算与分账字段后，前端再开放提单时的加价金额传递。

### 5.2 下一步行动
- 等待 Codex 协调者独立验收本批次提交；
- 验收通过后，由协调者安排合并发布。
