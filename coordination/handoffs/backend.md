# backend 交接卡

规则：2026-10-25-v1；任务 BE-20261025-01；角色 backend；ACK：已实际读取 AGENTS、STATE、BOARD、任务、独立审查及本卡。

基线 main `e89f90b0a63b72dc04fca5b3690a8897de1543c7`；核对目标/读取时 HEAD `fd44ec08d674900cd0b30d3d3f25cd0fc46f1b36`。用 git show 固定提交读取，未切分支或修改反重力工作区。

结果：本轮契约交付完成，等待协调者评审；业务实现/独立验收/部署均 NOT_RUN。确认客户端 priorityFee/couponId/couponDiscount 被创建接口忽略，后端子树与 main 相同；客户端券/积分只有设备存储，不能当服务端账务。源码行号、最小接口、账户隔离、报价权威、绑定与幂等、PREPAY/完单事件、WAIT_CONTINUE 多轮、退款冲正均写入契约。

证据根：`coordination/evidence/BE-20261025-01/`。
- `CONTRACT.md`：完整契约、6 类待确认规则、后续验收清单。
- `source-evidence.json`：目标 SHA、逐文件哈希、带行号摘录、AST 输入与持久化字段。
- `verify_contract_sources.py`：本轮源码断言；最终 4 组 PASS，不代表业务验收。
- `checks.json` / `verification.log`：实际检查结果与范围。

后续唯一可写后端目录建议：反重力仓库 `services/backend/`，另立实施任务后从 v8 验哈希迁入；不改冻结副本。必须保留管理浏览器适配和完整 Web/IAP/RBAC，两服务均支持新账务后再开营销；原单和现有 A/B 数据不回填、不清理。

待确认：券适用/时效与 WAIT_CONTINUE 退券规则、加价性质/收款方与档位、补贴承担、积分及邀请奖励规则、退款券与积分追缴。未找到注释所指优惠券原表，注释不当作已批准依据。

下一步：协调者审契约与业务门禁，分配独立实现/复核任务；当前保持 idle，不修业务、不发布、不派代理、不主动向其他对话回信。

待合并 SHA：N/A（当前协调工作区无统一 Git，本轮仅授权文档，由协调者统一提交；未在他人 Git 目录建分支/提交）。
