2026-10-29用户最新决定：Gemini/反重力暂时下线停派，Claude Code接替主要实现；以下为保留的历史入口，未经用户恢复或新的明确调度不运行。当前无在途CLI任务。

# Gemini任务执行入口（2026-10-25纠正）

本项目今后固定使用 `/Applications/Gemini.app/Contents/Helpers/agy`，即当前Gemini应用内置CLI；不依赖PATH中的agy，也不改全局软链接或卸载任何组件。

此前 `/Users/dongge/.local/bin/agy` 是单独安装的Google签名CLI，和应用内置文件不是同一二进制。实际hub进程在Gemini.app/Contents/Helpers/agy，父进程Gemini.app/Contents/MacOS/Gemini；未发现旧Antigravity.app运行。不能据此断言此前连接了已卸载旧App，也不能把超时归因于版本差异。

长任务注意：PA首次CLI出现12分钟超时、无文件产出，返回SUCCESS但turn仍在进展。协调者向精确conversation_id发停止指令，已收到STOPPED并确认停止；不把SUCCESS字段单独当完成，不再续跑该异常上下文。短、无工具的后台UI代码生成已成功产出并应用。

默认使用stream-json输出，及时取得conversation_id与进度；按任务短输入、少量文件、阶段交接。若长期无产出，向该精确会话停止并验证结束，不仅终止本地等待。不用latest/continue，不同任务不共用会话，不与同桌面会话并发。没有任务不调用模型、不常驻。

命令和文件工具均受权限限制，不使用dangerously-skip-permissions。此前headless RunCommand权限被拒，协调者接手实跑测试；可优先让Gemini用无工具短请求生成具体代码/补丁，由协调者应用与测试。作者结果不能替代非作者验收。

关闭桌面端时CLI曾实测成功，后台启动服务仍运行；不承诺强杀全部后台、睡眠/关机或断网后仍可执行。凭据仅保存在本机，不入代码/Git/聊天；不擅自换账号、模型或全局配置。

历史连通与ACK证据见evidence/CLI-20261025；当前内置入口测试另记bundled-gemini-probe结果。
