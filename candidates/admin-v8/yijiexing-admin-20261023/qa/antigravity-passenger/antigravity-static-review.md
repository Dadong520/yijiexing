# 宜捷行乘客端独立审查与测试准备（静态审查报告）

- **报告角色**：执行成员 Antigravity
- **审查性质**：静态代码与合约文档走查初稿（Codex 负责开发与最终验收）
- **审查范围**：`/Users/dongge/Documents/New project 2/yijiexing-passenger-20261023/` 下公开源码与文档
- **特别声明**：**本报告所有结论均基于代码与文档的静态推断，尚未在双模拟器上进行真实 UI 操作或网络交互。静态推断 ≠ UI 实测。**

---

## 一、 实际读取文件清单

1. `API_CONTRACT.md`：乘客/司机共享订单 API v1（登录鉴权、状态轮询、统一 action、状态机流转、无终点即时订单与取消续叫契约）。
2. `EXTRAS_API_CONTRACT.md`：外围功能 API 契约（乘车人、发票抬头与模拟回执、客服配置状态、拉黑司机、投诉图片附件、竞价 10 分钟到期）。
3. `SOURCE_FUNCTION_AUDIT.md`：原 Java 业务依据与原生乘客端对齐证据矩阵。
4. `android/app/src/main/AndroidManifest.xml`：应用清单（声明 INTERNET 权限、MainActivity 配置）。
5. `android/app/src/main/res/values/styles.xml`：主题样式。
6. `android/app/src/main/java/cn/hbzjhs/passenger/MainActivity.kt`：应用主入口、Compose 导航路由、5 秒生命周期轮询、登录界面与连接设置弹窗。
7. `android/app/src/main/java/cn/hbzjhs/passenger/PassengerStore.kt`：AndroidKeyStore 加密本地凭据存储、API 请求通道、requestId 幂等键维护与签名、投诉图片压缩上传。

---

## 二、 审查发现与潜在问题（静态推断）

> 以下问题均为静态代码逻辑推断，需在后续双模拟器测试中进行实际复现验证：

### 1. [P1] 4xx 业务错误时立即清除重试幂等键，存在幂等失效风险
- **文件位置**：`android/app/src/main/java/cn/hbzjhs/passenger/PassengerStore.kt`（第 177-179 行）
- **事实证据**：在 `action()` 请求捕获异常时，若 `e.status in 400..499`，代码会无条件执行 `clearPending(signature)` 并抛出异常。
- **静态推断**：若遇到短时 409（如竞价阶段司机报价变动或选司机并发冲突）或 400 校验异常，原本用于同一业务操作失败重试的 `requestId` 会被立即销毁。若用户或界面触发重试，将生成全新 UUID，违背了契约中“失败重试复用同一 requestId”的幂等防护要求。
- **待复现步骤**：在竞价选司机或支付操作时制造 409/400 响应，检查后续重试所携带的 `requestId` 是否被重新轮换。

### 2. [P2] 网络超时（SocketTimeoutException）缺乏自动对齐与状态锁定
- **文件位置**：`android/app/src/main/java/cn/hbzjhs/passenger/PassengerStore.kt`（第 143-145 行）
- **事实证据**：当网络读取超时（readTimeout 为 75s）触发 `SocketTimeoutException` 时，代码仅在界面抛出提示文案：“云端启动或响应超时，请等待约 30 秒再重试。原订单可能已提交，请先刷新订单。”
- **静态推断**：超时往往意味着云端已处理写库但响应未正常回传。客户端未在超时后自动拉取状态对齐，若用户忽略提示直接重复点击操作，可能导致重复业务意图提交。
- **待复现步骤**：模拟网络高延迟触发超时，验证操作按钮是否阻断以及手动刷新前后的状态一致性。

### 3. [P2] 投诉附件上传单张与外围契约支持最多 3 张存在差异
- **文件位置**：`android/app/src/main/java/cn/hbzjhs/passenger/PassengerStore.kt`（第 183-212 行）对比 `EXTRAS_API_CONTRACT.md`（第 32 行）
- **事实证据**：`uploadComplaint` 仅接收单一 `uri: android.net.Uri`，打包为单元素数组 `imagesBase64: [encoded]` 发送。
- **静态推断**：契约规定“每投诉最多3张PNG/JPEG”。单图上传实现需验证服务端是进行追加还是覆盖，若为覆盖则无法达到最多 3 张的业务目标。
- **待复现步骤**：在已完单订单的投诉记录上连续上传两张图片，核验服务端返回的 attachments 列表数量与内容。

---

## 三、 待双模拟器实际执行用例（核心闭环）

1. **TC-01 基础即时用车双端联动**：
   - 乘客登录白名单账号（13800000101）发单（STANDARD）-> 司机端接驾 -> 到达 -> 输入手机尾号核验行程 -> 完单 -> 乘客模拟支付 -> 双端状态同步为 DONE。
2. **TC-02 竞价用车与选司机流转**：
   - 乘客发单（BID）-> 司机端报价 -> 乘客端刷新展示报价列表并确认选择司机 -> 模拟预付 -> 双方状态进入接驾。
3. **TC-03 无终点即时订单与补全目的地**：
   - 乘客创建无终点订单（destinationPending: true，显示费用待定）-> 司机接单 -> 乘客补全目的地 -> 司机开始行程确认估价。
4. **TC-04 司机取消与乘客待续叫（WAIT_CONTINUE）**：
   - 司机接单后取消 -> 乘客进入待续叫状态 -> 测试 type:1（重新呼叫）与 type:2（放弃取消）。

---

## 四、 未验证项与环境约束声明

1. **尚未做 UI 操作**：本次审查仅覆盖代码与文档，尚未在双模拟器上启动 App、点击按钮或抓包。
2. **外部服务真实性**：真实短信、真实支付网关、真实发票税控、电话呼叫与厂商推送均未接入，代码内已实现模拟说明与状态标识，需在实测中核验 UI 是否如实展示。
