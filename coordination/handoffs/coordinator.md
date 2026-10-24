# coordinator 交接卡

规则版本：2026-10-25-v1；ACK：ACK（已实际读取）。
当前：建立项目级持久协作入口、任务台账、四角色交接、轻量CI及按提交SHA的发布闸门。
已核对：main=e89f90b0a63b72dc04fca5b3690a8897de1543c7；反重力fd44ec08...领先3提交，29文件、无后端变更。独立6.1审查发现关键回归，已记reviews/AG-20261025-01.md，状态CHANGES_REQUESTED。
分工：AG修前端；backend仅契约；frontend仅司机只读复核；根任务负责非作者验收与统一发布。
已建立：AGENTS/STATE/BOARD/tasks/handoffs；tools/check_coordination.py支持结构检查与精确SHA验收检查。
当前：规则已通过PR#2与CI合并；反重力原批次正式草稿PR#3，独立审查FAIL。frontend/backend均已ACK并完成，证据落盘，已idle；AG桌面实际读规则后执行修复。
下一步：验收AG新SHA。仅明确相关测试通过不代表三端真机通过。现有云不变。
上下文：不重复读历史全文；只读STATE+当前任务+本卡，长证据按任务ID保存。

发布闸门另验证6个边界：正确证据、错SHA、自审、失败测试、证据篡改与状态超长；均符合预期。GitHub私库套餐不支持强制分支保护，已明确不升级不公开，不能虚称服务端强制。
