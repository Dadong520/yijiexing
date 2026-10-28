# 宜捷行：新会话先读这里

协议版本：2026-10-26-v3。用户只向coordinator发指令，其他会话由其协调。

## 五分钟恢复顺序
1. 读取项目AGENTS.md，第一次执行本协议时读MEMORY_POLICY.md。
2. 本机以`/Users/dongge/Documents/New project 2/coordination`为中央协调目录，先读其中STATE.md、BOARD.json、SESSION_REGISTRY.json。Git工作树同名文件只是检查点快照，不默认更新。
3. 读自己的handoffs/<role>.md及**当前获派的一个任务**；BOARD没有派给自己的任务就不启动开发，不读取全部旧任务。
4. 用DECISIONS.md定位长期规则；只有与当前任务有关时才读对应契约/证据。
5. 在task.sourceRoot核对目录、git status、branch、HEAD、待合并SHA；只定向核对证据，不扫描历史聊天、整个releases或私有账号文件。
6. `python3 tools/check_coordination.py --memory-role <role> --session-id <真实会话ID>`。工作树是旧协议时先从中央读取当前协议；只能在自己允许的文件范围内同步，不能改他人工作树。
7. 先回三行：已读检查点与基线 / 未完成项及已知限制 / 下一动作与唯一文件归属。不把“已部署”误当“仍待发布”。

## 自主交付入口
用户反馈触发的执行约定见`AUTONOMY_POLICY.md`与`AUTONOMY_CONFIG.json`。唯一coordinator完成拆单、开发协同、非作者验收、准确SHA门禁、合并、独立体验发布回读；发布后等反馈，不新增常驻服务。原始安装回执与后续ACK分阶段保留，不能把PENDING安装状态当运行证明。

## 去哪里找
- 当前事实/已上线版本/唯一后端目录：STATE.md。
- 活跃工作：BOARD.json→tasks/<id>.json；完成任务：archive（按需查一个）。
- 长期决定与理由：DECISIONS.md；接口：contracts。
- 角色与换会话状态：SESSION_REGISTRY.json；当前细节：handoffs/<role>.md。
- 原始测试/截图/发布读回：evidence（只开任务指定文件）。
- 换新协调对话：复制NEW_CHAT_PROMPT.txt；不需要复制历史聊天。

本目录不是密钥仓库。没有明确证据的细节写UNKNOWN/NOT_RUN，不能用聊天印象补出已测试或已部署结论。上下文保护不是常驻服务，仅在实际工作、交接和恢复时执行。
