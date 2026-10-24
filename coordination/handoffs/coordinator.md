# coordinator 交接卡

规则版本：2026-10-25-v1；ACK：ACK（已实际读取）。
当前：建立项目级持久协作入口、任务台账、四角色交接、轻量CI及按提交SHA的发布闸门。
已核对：main=e89f90b0a63b72dc04fca5b3690a8897de1543c7；反重力fd44ec08...领先3提交，29文件、无后端变更。独立6.1审查发现关键回归，已记reviews/AG-20261025-01.md，状态CHANGES_REQUESTED。
分工：AG修前端；backend仅契约；frontend仅司机只读复核；根任务负责非作者验收与统一发布。
已建立：AGENTS/STATE/BOARD/tasks/handoffs；tools/check_coordination.py支持结构检查与精确SHA验收检查。
下一步：同步规则GitHub，通知三会话并收ACK；让AG修复后重新验收。仅明确相关测试通过不代表三端真机通过。现有云不变。
上下文：不重复读历史全文；只读STATE+当前任务+本卡，长证据按任务ID保存。
