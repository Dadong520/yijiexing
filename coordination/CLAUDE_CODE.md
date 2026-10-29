# Claude Code任务执行入口
2026-10-29用户明确新增其Claude Code作为主要重难实现者，Gemini暂时下线（保留历史成果/模型设置），Codex root仍唯一协调、合并发布，backend/frontend独立验收，Astra按堵点。
官方桌面内置入口：`/Users/dongge/Library/Application Support/Claude/claude-code/2.1.304/6bbba6adc37f/claude.app/Contents/MacOS/claude`。实际--version为2.1.304；auth status已验证claude.ai/Pro，凭据由官方流程保存，不读取/复制桌面token，不入Git/消息。版本升级后重新核二进制路径，不猜latest。用户自己登录可用该入口 `auth login --claudeai`。
首真实session_id `413b9b2c-f346-4a81-8891-87bc3c962394`，init model `claude-opus-5-5`，任务级effort medium与用户截图选择一致。初次ACK后才登记claude；实际session_id不冒用CodexID。根前置工具验证声明/资料，不是模型内部遥测。
本轮sourceRoot `/Users/dongge/.codex/tmp/yijiexing-claude-ui-20261029`，branch `codex/claude-ui-20261029`，base62942d1。中央控制目录仍New project 2/coordination，工作树仅快照。只改CCUI allow_paths，不改业务/凭据/控制文件，不自派代理/合并/发布。
按任务短输入/明确时限，续跑只 `--resume <精确session_id>`，不用continue/latest；进程与同一Desktop会话不并发。CLI参数列表在提示文本前加 `--`，子进程stdin=DEVNULL，避免多值参数吞prompt/继承启动脚本。stream-json原始工具输出外置任务临时目录0600，仅脱敏回执入证据。
尊重权限检查，不使用skip/bypass参数。首批限Read/Glob/Grep/Edit/Write、acceptEdits，Bash不可用时自测明确NOT_RUN由root实跑，不能伪称通过。独立审查由非作者完成。阶段保存自己短卡/任务/证据，结束停止等待指令，不新建守护任务或后台常驻；需要可见时在CLI结束后用官方--desktop --resume精确ID转入Desktop，成功输出前不声称已显示。

## 当前已实现任务
UIO-20261029-01使用新的独立session `b23ffb33-b639-4295-8369-4925bdab9d87`，实际init `claude-opus-5-5`/medium，4阶段exit0，现IDLE。sourceRoot `/Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029`，branch `codex/ui-optimization-20261029`，最终候选137e41534f823c538fe62ff10820f491e0e55074。旧413b9首轮会话为历史，不使用含糊latest续跑；版本/后续发布仍root统一。作者无Bash，独立Codex原生/业务审查已PASS；实际发布以STATE/回执为准。
