# backend 当前交接卡
协议ACK: 2026-10-26-v3；角色backend；threadId: 01a168e2-22f7-70a2-af7f-4fb6be0ea695。
updatedAt: 2026-10-26T09:06:17+08:00；当前任务AUTH-20261026-02（限定8分钟）。
memoryStatus: HANDOFF_RECOMMENDED；contextUsedPercent: null；measurementSource: not_exposed；占比UNKNOWN。有可见压缩摘要且跨多阶段，下一大后端任务建议新对话；本轮仅已授权限定入口修订。无自动新建/常驻。

中央START_HERE/STATE/BOARD/SESSION优先，工作树快照不反向覆盖中央。coordinator唯一派单/登记/合并/部署；作者不验收自己。MEM v3/ACK已就绪，PR25合并f565e709cf8bd9e5cd348c5a5bd8a33f5282443c，真实会话前置PASS。

唯一共享业务源：/Users/dongge/.codex/tmp/yijiexing-trip-api-20261025/apps/cloud-service；branch codex/trip-distance-api-20261025；HEAD09be29e519bac864799f4ac752fff811f9e1dbf0，产品8ec8419d30955c216d7e4fe90451584d2a9622cf。PR22合并275ea3eb251998a7e54399a3e0b0d280dde1a554，live driver-demo-distance-8ec8419d100%。本轮只读该源、未修改；旧api树/services/backend/release/frozen不是写源。其未提交STATE/BOARD/旧任务快照属root，保留不动。

AUTH唯一入口树：/Users/dongge/.codex/tmp/yijiexing-admin-password-20261026；branch codex/admin-password-only-20261026；HEAD/base f565e709cf8bd9e5cd348c5a5bd8a33f5282443c。backend仅修改apps/admin-console/backend/iap_browser.py、test_iap_browser.py，未提交，固定文件hash见该树coordination/evidence/AUTH-20261026-02/backend-review/results.json。Web/ops/README及协调文件另有root改动，勿覆盖或暂存。

用户最新明确授权：driver-admin仅账号密码，取代该服务旧IAP保留规则；driver-demo/业务数据和其他保护不扩大。显式ADMIN_AUTH_MODE=PASSWORD跳过外层IAP身份校验，默认IAP严格、未知模式503/启动拒绝；不是客户端header选模式。route白名单/v2服务secret/Origin/Sec-Fetch/console session/RBAC/审计/失败限流全部保留。

实际测试：18 HTTP/SQLite/MemoryCAS PASS（原IAP9+默认严格1+PASSWORD8），协调者提供的现有测试库1.732s；无/伪造session、正确登录退出、5次错误后锁定、viewer拒写、跨站、legacy404、未知模式均测。命令/日志/源码hash在AUTH backend-review。shared8ec源git diff未变；中央memory-role+真实session-id前置PASS，diffcheck PASS。208/75未重复。无作者失败；root非作者审核、浏览器、构建/发布回读NOT_RUN，待合并SHA由root统一提交后给出，禁止本角色单独commit/发布。

已发布历史：Web2863623中文P2差量关闭；live driver-admin-trip-2863623f；域名demo-admin.hbzjhs.xyz此前TLS/302/Google入口及DomainMapping三True PASS_SCOPE，root另有Google/admin签入回读。该历史不代表AUTH已上线：当前去中央STATE确认，勿重部署旧镜像。TRIP/REL/NET证据保留原集成树；AUTH本轮未动云配置。

业务边界：欠款阻乘客新叫车、不阻司机他人单；接送各300m/50..5000继承，位置120s/精度100m仅免确认资格、非防伪GPS，确认180s/幂等；共享议价预付走PICKUP。钱仍模拟。仅独立GCP/min0max1请求计费，无保活/自动暂停；禁止原生产/测试。

未完成：微信AppID与QA模拟器overlay读回异常仍等后续明确任务；真实支付/短信/厂商推送/强杀常驻等未接未证实；Web精确1366/大区发布/本人非空历史/a11y仍原NOT_RUN范围。旧券积分任务不擅自恢复。
资源：本角色无ADB/Chrome/部署/其他agent占用；测试进程已结束。下一动作：root按AUTH用户新授权做独立复核、统一提交构建部署和回读；本角色不再扩范围，等待具体缺陷单。下一大任务先按MEM建议换角色会话。
