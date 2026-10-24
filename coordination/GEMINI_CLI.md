# Gemini任务执行入口

状态：2026-10-25已实测 /Users/dongge/.local/bin/agy 非交互调用成功，模型gemini-3.8-flash-high，mode=plan，返回CLI_READY；随后CLI实际读取根规则并ACK v2。模型列表由本机agy models读取。无需新增API密钥。/opt/homebrew/bin/gemini也已安装，但本项目默认使用反重力agy，避免切换账号和计费路径。

协调者负责调用，不要求用户运行命令。调用前核对作者目录Git状态、BOARD、唯一文件归属；新任务默认不传conversation，续跑显式传该任务conversation_id；不得同时运行同一桌面/CLI会话。CLI本身会保存会话，但项目短交接是恢复依据。

示例（由协调者按任务选择模式，参数不是全局设置）：

```sh
agy --model gemini-3.8-flash-high --mode plan --print-timeout 120s --output-format json --print '<只读任务的短指令>'
```

实际编辑使用accept-edits模式，只限定任务allow_paths，仍遵守工具权限提示。不得使用dangerously-skip-permissions。工具超时、认证失效或返回非SUCCESS时，不当作完成；核对进程/结果后才续跑，保留失败原因。stdout/stderr存本机.coordination，审计仅归档必要结果，不上传凭据、全量会话或私有配置。

任务产出应含：ACK规则版本、基线/最终完整SHA、改动文件、实跑测试/退出码、证据路径、FAIL与NOT_RUN、下一步。作者自测后交非作者验收；合并和发布仍由coordinator统一安排。没有任务不调用模型，不常驻、不自动重复工作。

时限注意：print-timeout仅作CLI等待设置，本轮观察到含工具的回复耗时超过该设置，不把它视为已验证的强制任务终止。超时后须核对原任务状态并停止/完成后再续跑，禁止重复启动同一任务。

关闭桌面端实测：用户报告关闭后，agy最小问答SUCCESS，5.04秒返回CLI_DESKTOP_CLOSED_OK。进程核对未见主界面进程，但GeminiAppLauncher与Finder扩展仍运行。因此当前不依赖桌面窗口，不声称强杀全部后台/电脑睡眠/关机后仍可用。证据evidence/CLI-20261025/desktop-closed-result.json。
