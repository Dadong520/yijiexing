# 当前检查点：记忆协议v3固化 / 2026-10-26T02:11:38.454082+08:00

## 下一轮从哪里接手
本机中央控制目录 /Users/dongge/Documents/New project 2/coordination。先AGENTS→START_HERE→STATE/BOARD/SESSION→本角色卡/当前任务；长期规则按需读DECISIONS。不遍历旧聊天/release/evidence目录；工作树同名文件是快照，不能覆盖中央。
用户默认只给coordinator指令，SESSION是角色实际会话ID登记。coordinator/backend/frontend均已ACK2026-10-26-v3、HANDOFF_RECOMMENDED，实际上下文占用UNKNOWN/null。三个长会话都建议下一大任务前换新；先由用户新开总协调对话粘贴NEW_CHAT_PROMPT，登记新ID、退休旧ID，然后统一安排角色交接，不能双派/双发布。不自动创建新对话。

## 本轮MEM成果（无业务/云部署）
文档及检查器固定产品SHA 047d82e227824b9038f53f5e73303ae58732fe06，工作树 /Users/dongge/.codex/tmp/yijiexing-memory-20261026，branch codex/project-memory-20261026，PR25 https://github.com/Dadong520/yijiexing/pull/25。Main包含本快照表示已合并；精确merge SHA读PR元数据，中央即时回执见本文件末尾。16单测作者及frontend非作者PASS，正向角色ID/负向旧缺ID通过，backend长期规则复核PASS。证据evidence/MEM-20261026-01，任务已归档。
资料预算/ACK/角色唯一/退休ID/真实调用ID/旧快照/假遥测声明由检查器检查；没有模型内部遥测，不是不可绕过锁，不常驻监控。每阶段、约40分钟有效工作、压缩/等待/结束补短卡；70/85只是有可靠用量时的项目预防门槛。README已修正旧暂停/未发布描述，中央两个角色卡恢复当前源。Gemini仅在获派任务按GEMINI_CLI入口/本协议运行，本轮没有为协议唤醒Gemini。

## 当前已发布业务（不要重复发布）
最近业务PR23 af7ec4ce6c618cf0b984c54a76ba7024751e5e10，发布回执PR24 main55071ffd5b7b214e8d2c81e509cea4fe021385fe。
唯一业务后端源 /Users/dongge/.codex/tmp/yijiexing-trip-api-20261025/apps/cloud-service，产品8ec8419d30955c216d7e4fe90451584d2a9622cf，live driver-demo-distance-8ec8419d100%，后端208+root独立HTTP5。Web2863623ff932afc9adc1f59caa64e2b3069dbbae，live driver-admin-trip-2863623f100%。
管理https://demo-admin.hbzjhs.xyz，Google已指定dadonggev587@gmail.com+原admin，TLS/IAP/实际登录及300/300距离规则/同单DONE回读PASS；备用run.app保留。APIhttps://yijiexing-demo.hbzjhs.xyz；原demo.hbzjhs.xyz演示勿改。
Native源 /Users/dongge/.codex/tmp/yijiexing-native-permissions-20261025 branch codex/permission-alert-native-20261025，产品3e9f6ee943948e1081ec9ab0102eef803d447c8b。driver0312/code15 SHA bce9fcff928b3690525f14a6ae5f3a4e074fc6f9ca9bf491733e4b1609852a85；pass1112/code14 SHA61417866fce94d9176ff74e42718d6ce60fa658064e289bf8e0dccfae10257d8（乘客源d896，最后仅driver变）。两签名9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f。下载API域名/download和/download-passenger，免访问码、覆盖保账号；均公开全GET验证。
同单PORDf4f910a233dc真实模拟器定位11062.102m/300，拒绝仍ARRIVED→本人同意TRIP→旧定位/null送达本人同意WAIT_PAY→模拟29.26 DONE、付款事件1次。55driver/20pass及mini58/静态检查通过，真实原生/后台限定独立验收见REL02与ALERT报告。

## 剩余与资源
BOARD仅WX缺AppID（微信编译/真机发布NOT_RUN）、QA模拟器overlay恢复未证实；不擅自继续，不把未测项标完成。原生OEM/强杀/主观听音/完整新增弱网/无障碍/空userdata等未测；实际再次接第二shared由HTTP验证而非App，不混称。
两AVD与本地8808已关闭，Chrome/ADB/CLI无本轮占用，两角色IDLE；无后台AI/定时自动续跑。当前根只收尾文档合并。仅独立GCP yijiexing-driver-demo-1023/us-west1，数据/IAM/IAP保留、automatic/min0/max1请求计费，不保活、不自动手工暂停；禁止原生产/测试。临时目录若缺失，从Git固定SHA恢复，不猜回未提交修改；卡内归属清单优先。

## 最终收尾 2026-10-26T02:14:45.067795+08:00
MEM协议PR25已合并main，merge SHA f565e709cf8bd9e5cd348c5a5bd8a33f5282443c，project-gate PASS（含16项记忆/发布门禁回归与既有小程序检查）。本轮仅文档/协作工具，无业务部署。coordinator/backend/frontend的真实ID/协议ACK前置均PASS，三者均HANDOFF_RECOMMENDED，实际上下文UNKNOWN；两工作会话已idle，Gemini未唤醒。下一次大开发请先新开总协调对话并粘贴NEW_CHAT_PROMPT，旧会话不再接新大任务；不需复制历史。Git快照是提交时检查点，本中央尾注是合并后即时状态，不能反向用旧副本覆盖。

## 新请求AUTH-20261026-02 2026-10-26T08:57:02.544559+08:00
用户明确取消driver-admin Google登录，改现有后台账号密码。此明确指令取代此前该服务IAP保留规则；其他服务保护和数据不改。限定登录入口与云配置任务，源/Users/dongge/.codex/tmp/yijiexing-admin-password-20261026，branch codex/admin-password-only-20261026，base f565e70；backend只入口/测试，root Web/文档/唯一部署。memory仍HANDOFF_RECOMMENDED，不扩展业务。完成后更新当前授权与交接，下次大开发换新。
