# backend 交接卡

规则：2026-10-25-v2；角色 backend；ACK v2：已实际读取新版 AGENTS、GEMINI_CLI、STATE、BOARD 和本卡。本次仅同步规则；BOARD 无活动任务，BE-20261025-01 已归档。

执行方式：用户由 coordinator 统一派单，无需转发；Gemini/反重力优先主要实现与常规自测，默认 agy CLI；backend 负责明确分配的接口、金额、状态与幂等审查。只写任务 allow_paths，不争写、不自行发布、不派代理。Codex 保持 GPT-6.1；无任务不调用模型、不常驻。CLI 默认独立任务上下文，续跑指定 conversation_id，不使用 latest/continue，不绕过权限；调用由协调者负责。

基线 main `e89f90b0a63b72dc04fca5b3690a8897de1543c7`；核对目标/读取时 HEAD `fd44ec08d674900cd0b30d3d3f25cd0fc46f1b36`。用 git show 固定提交读取，未切分支或修改反重力工作区。

历史结果：BE-20261025-01 契约交付完成；STATE 记录契约 PR#4 已合并。本角色未实现业务或部署。目标提交中客户端 priorityFee/couponId/couponDiscount 被创建接口忽略，后端子树与当时 main 相同；客户端券/积分只有设备存储，不能当服务端账务。源码行号、最小接口、账户隔离、报价权威、绑定与幂等、PREPAY/完单事件、WAIT_CONTINUE 多轮、退款冲正均写入契约；该历史核对不自动覆盖后续客户端修复。

证据根：`coordination/evidence/BE-20261025-01/`。
- `CONTRACT.md`：完整契约、6 类待确认规则、后续验收清单。
- `source-evidence.json`：目标 SHA、逐文件哈希、带行号摘录、AST 输入与持久化字段。
- `verify_contract_sources.py`：本轮源码断言；最终 4 组 PASS，不代表业务验收。
- `checks.json` / `verification.log`：实际检查结果与范围。

后续唯一可写后端目录建议：反重力仓库 `services/backend/`，另立实施任务后从 v8 验哈希迁入；不改冻结副本。必须保留管理浏览器适配和完整 Web/IAP/RBAC，两服务均支持新账务后再开营销；原单和现有 A/B 数据不回填、不清理。

待确认：券适用/时效与 WAIT_CONTINUE 退券规则、加价性质/收款方与档位、补贴承担、积分及邀请奖励规则、退款券与积分追缴。未找到注释所指优惠券原表，注释不当作已批准依据。

当前状态：ACK v2 完成，idle；等待 coordinator 明确的新任务。本次未调用 Gemini、运行检查/测试、修改产品或部署。

本次待合并 SHA：N/A（仅本角色 ACK 文档，由协调者统一归档；未在他人 Git 目录建分支/提交）。历史契约合并状态以 STATE 与归档记录为准。
