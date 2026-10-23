# 宜捷行后台独立黑盒测试与审查初稿报告 (修正版)

- **测试执行者**：执行成员 Antigravity（交付本轮执行初稿，不声称总管，不代替 Codex 进行整体验收）
- **测试时间**：2026-10-23 22:03 (UTC+8)
- **目标服务环境**：云 Admin v3 独立体验环境 · 本机代理 `http://127.0.0.1:8790`
- **报告交付路径**：`/Users/dongge/Documents/New project 2/yijiexing-admin-20261023/qa/antigravity/report.md`

---

## 一、 测试角色与工具边界声明

1. **测试角色边界**：Antigravity 仅负责执行本轮指定的独立 HTTP 黑盒测试并记录过程事实，交付初稿结果。不代表主控端 Codex，不替代 Codex 完成最终整体验收。
2. **独立 UI 操作工具状态**：**明确标记为不可用**。当前 Antigravity 会话内未挂载交互式浏览器工具或桌面操作系统扩展。严格遵守边界规则，未采用 `osascript`、AppleScript 或私自启动 Chrome 远程调试端口进行替代。
3. **截图真实性声明（无像素视觉审阅）**：
   - 本会话执行工具记录中仅有 `ls` 与 `file` 查看文件元数据，**本会话未具备亦未调用任何实际图片像素读取工具**。
   - 严禁以文字上下文冒充视觉审阅。本会话**仅确认截图文件存在性与文件格式属性**，实际 UI 渲染与画面细节**由 Codex 浏览器操作并核验，本轮未由 Antigravity 进行独立视觉审阅**。
4. **凭据与私密文件保护红线**：仅读取了用户授权的 `.private/qa-accounts.json`；未读取其他任何 `.private` 文件、云端密钥或原始生产数据。所有测试脚本、测试结果 JSON 及本报告中，**均未输出或打印任何账号密码、云端 Token 或完整有效 Session**。

---

## 二、 独立 HTTP 黑盒测试过程与结果记录

本轮测试包含第一轮用例前置设计修正过程与最终复测通过结果。相关文件均已保留归档：
- 第一轮记录：`test_blackbox-round1.py` 与 `test_results-round1.json`
- 最终复测记录：`test_blackbox.py` 与 `test_results.json`

### 1. 用例设计修正过程记录 (403 与 400 逻辑澄清)
- **第一轮情况 (用例前置错误)**：
  - 测试脚本最初设计“验证非法操作参数 400”，调用了未知操作 `action: "invalid.action.name"`，期望返回 400。
  - **实测表现**：服务端返回 HTTP 403，Body 为 `{"code": 403, "message": "当前账号没有此操作权限", "details": {}}`。
  - **原因复盘**：服务端架构在鉴权中间件阶段，先校验 action 是否在账号权限白名单内。未知 action 命中非白名单，因此直接被 403 权限拒绝，尚未走到业务参数解析校验阶段。该用例前置设计存在瑕疵，已归档为 `test_results-round1.json`。
- **第二轮修正复测 (通过)**：
  - 将操作改为账号具备操作权限的合法动作 `action: "support.reply"`，但故意缺失必填的 `requestId` 与关联业务参数。
  - **实测表现**：服务端通过了权限鉴权，进入参数解析阶段，成功触发参数校验，返回 HTTP 400，Body 为 `{"code": 400, "message": "请求标识须为1至120字", "details": {}}`。
  - 此外，未登录空参数提交 `{"username": "", "password": ""}` 亦实测返回 HTTP 400 `{"code": 400, "message": "账号须为1至64字", "details": {}}`。400 参数校验逻辑确认生效。

### 2. 最终黑盒测试各项指标结果
- **T1: 未携带会话访问受保护接口 (401)**：`GET /api/local/admin/v2/state` -> HTTP 401（`请先登录管理账号`）。**【通过】**
- **T2: 登录空参数校验 (400)**：`POST /api/local/admin/v2/login` -> HTTP 400（`账号须为1至64字`）。**【通过】**
- **T3: Operator 登录认证 (200)**：`POST /api/local/admin/v2/login` -> HTTP 200，成功签发独立会话。**【通过】**
- **T4: 已登录缺少必填请求标识 (400)**：`POST /api/local/admin/v2/action` (缺少 requestId) -> HTTP 400（`请求标识须为1至120字`）。**【通过】**
- **T5: Operator 获取 state 数据 (200)**：读取共享订单、司机、乘客与支持列表正常。**【通过】**
- **T6: Viewer 登录认证 (200)**：`POST /api/local/admin/v2/login` -> HTTP 200，成功签发 Viewer 会话。**【通过】**
- **T7: Viewer 状态与只读权限集确认 (200)**：角色为 `VIEWER`，权限数组收敛为只读：`['audit.read', 'config.read', 'drivers.read', 'orders.read', 'passengers.read', 'payments.read', 'statistics.read', 'support.read']`。**【通过】**
- **T8: Viewer 写入操作拦截 (403)**：携带 Viewer 会话发送 `order.cancel` 请求 -> HTTP 403（`当前账号没有此操作权限`），越权写入被有效拦截（严格未用 operator 变更订单）。**【通过】**
- **T9: Viewer 导出数据拦截 (403)**：携带 Viewer 会话请求 `/api/local/admin/v2/export?dataset=orders` -> HTTP 403（`当前账号没有此操作权限`），越权导出被有效拦截。**【通过】**
- **T10: 登出后会话失效验证 (401)**：分别登出后重试原会话 -> HTTP 401（`管理会话已失效，请重新登录`）。**【通过】**

---

## 三、 目标订单与投诉数据只读核对记录

本次测试严格执行只读查询规范，未修改任何订单，未对投诉二次提交：

### 1. 目标共享订单 (`PORDdcabe83784eb`)
- **状态 (stage)**：`DONE`（已完成）
- **行程**：API验收·万达广场 → API验收·宜昌东站（城市代码 `420500`）
- **费用**：`1724` 分（即 **¥17.24**），`estimatedFare: 1724`
- **支付**：`paid: true`，`paymentStatus: "SUCCESS"`，`paymentMode: "SIMULATED"`
- **评价**：5 星满分，内容：“双端共享云接口验收”
- **核对结论**：只读核对一致。

### 2. 目标关联投诉 (`PC1f2deda60b4b`)
- **关联订单**：`orderId: "PORDdcabe83784eb"`
- **状态 (status)**：`REPLIED`（已回复）
- **内容**：“API独立验收投诉记录，请后台核对并回复。”
- **回复**：“QA三端联动核对：后台已核实订单记录，体验投诉已处理。”
- **处理人**：`handledBy: "ADMIN:qa_operator"`，`repliedAt: "2026-10-23T21:45:31+08:00"`
- **操作保护执行**：核实已处于已回复状态，按规未进行二次写入。

### 3. 双模拟器协作订单观察 (`PORD29d6c69f4de0`)
- **只读观察**：订单列表中存在该记录，当前状态为 `DONE`，金额 `2926` 分（¥29.26）。
- **操作保护执行**：严格保持只读，未做任何修改，未破坏正在进行的端到端协作。

---

## 四、 截图文件属性核验声明

本轮测试中，Antigravity 对主对话在 `qa/` 目录下留存的截图文件进行了文件系统层面的属性核查：

| 截图文件名 | 文件类型与分辨率 (基于 `file` 工具输出) | 核验结论 |
| :--- | :--- | :--- |
| `cloud-overview.png` | JPEG 图像数据 (JFIF standard 1.01), 586x647 | 仅确认文件存在与属性有效，画面由 Codex 核验，本轮未独立视觉审阅 |
| `cloud-order-standard.png` | JPEG 图像数据 (JFIF standard 1.01), 586x647 | 仅确认文件存在与属性有效，画面由 Codex 核验，本轮未独立视觉审阅 |
| `cloud-complaint-replied.png` | JPEG 图像数据 (JFIF standard 1.01), 586x647 | 仅确认文件存在与属性有效，画面由 Codex 核验，本轮未独立视觉审阅 |
| `cloud-viewer-users-denied.png` | JPEG 图像数据 (JFIF standard 1.01), 601x664 | 仅确认文件存在与属性有效，画面由 Codex 核验，本轮未独立视觉审阅 |
| `three-party-available.png` | JPEG 图像数据 (JFIF standard 1.01), 586x647 | 仅确认文件存在与属性有效，画面由 Codex 核验，本轮未独立视觉审阅 |

**核验边界说明**：
Antigravity 自身未调用像素读取或计算机视觉接口，不以文字上下文冒充看到图像像素；画面内容的实际 UI 表现由 Codex 使用已授权浏览器工具操作并最终核对。

---

## 五、 归档文件索引

归档目录：`/Users/dongge/Documents/New project 2/yijiexing-admin-20261023/qa/antigravity/`

1. [test_blackbox-round1.py](file:///Users/dongge/Documents/New project 2/yijiexing-admin-20261023/qa/antigravity/test_blackbox-round1.py)：第一轮黑盒测试脚本（记录 403 权限前置设计的原用例）。
2. [test_results-round1.json](file:///Users/dongge/Documents/New project 2/yijiexing-admin-20261023/qa/antigravity/test_results-round1.json)：第一轮测试原始响应结果。
3. [test_blackbox.py](file:///Users/dongge/Documents/New project 2/yijiexing-admin-20261023/qa/antigravity/test_blackbox.py)：修正后的完整黑盒测试脚本（动态读取凭据、已脱敏）。
4. [test_results.json](file:///Users/dongge/Documents/New project 2/yijiexing-admin-20261023/qa/antigravity/test_results.json)：第二轮复测全部通过的测试结果快照。
5. [sanitized_state.json](file:///Users/dongge/Documents/New project 2/yijiexing-admin-20261023/qa/antigravity/sanitized_state.json)：脱敏后的目标订单、关联投诉及系统统计摘要快照。
6. [report.md](file:///Users/dongge/Documents/New project 2/yijiexing-admin-20261023/qa/antigravity/report.md)：本份修正版独立审查报告。

---

## 六、 结论与整体验收说明

- **HTTP 黑盒实测**：10 项接口指标全部完成实测验证，鉴权控制、Viewer 越权防护、参数校验与会话失效均符合安全规范。
- **业务数据保护**：未更改任何真实/体验订单，未重复提交投诉，未泄露测试凭据。
- **验收说明**：本报告为 Antigravity 执行初稿，独立 UI 操作工具不可用；任务最终验收由主控端 Codex 进行。
