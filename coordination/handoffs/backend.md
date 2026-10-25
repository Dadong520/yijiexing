# backend 当前交接卡
协议ACK / ackVersion: 2026-10-26-v3；角色backend；任务MEM-20261026-01。
threadId: 01a168e2-22f7-70a2-af7f-4fb6be0ea695（SESSION_REGISTRY登记）。
updatedAt: 2026-10-26T02:00:48+08:00
memoryStatus: HANDOFF_RECOMMENDED；contextUsedPercent: null；measurementSource: not_exposed；实际上下文占比UNKNOWN。
理由：有可见压缩摘要，本对话又跨查询API、距离规则、Web及域名多阶段；当前能核对基线，但旧卡长期滞后，已按中央状态纠正。下一项大后端任务前建议换新backend对话；不猜百分比、不自动新建，不在此长对话扩新大任务。换会话由用户明确授权，coordinator登记替换ID后派单。

中央入口：coordination/START_HERE.md、STATE/BOARD/SESSION_REGISTRY；长期决定DECISIONS。工作树同名快照可能旧，禁止整份覆盖中央。coordinator唯一维护中央状态/登记/决定、合并与发布；作者不验收自己。

唯一API源：/Users/dongge/.codex/tmp/yijiexing-trip-api-20261025/apps/cloud-service；branch codex/trip-distance-api-20261025；HEAD 09be29e519bac864799f4ac752fff811f9e1dbf0（证据），产品8ec8419d30955c216d7e4fe90451584d2a9622cf。PR22已合并275ea3eb251998a7e54399a3e0b0d280dde1a554并已发布driver-demo-distance-8ec8419d100%；不要重复发布。旧api树、services/backend、releases、backend-frozen均非当前写源。
该API树未提交仅STATE/BOARD及ALERT/REL/TRIP任务快照，属于coordinator，保留不动；无本角色未提交业务产品。MEM基线55071ffd5b7b214e8d2c81e509cea4fe021385fe；本轮只写本卡+MEM证据backend-review.json，不单独提交。

已验：208API作者测试+root5独立HTTP；本人作为Web非作者验草稿/发布/继承/权限，中文P2在Web2863623ff932afc9adc1f59caa64e2b3069dbbae差量复测关闭。Web已发布driver-admin-trip-2863623f，后台自有域名demo-admin.hbzjhs.xyz匿名直连302至Google、TLS正常、DomainMapping三True；签入回读由root完成。域名结论PASS_SCOPE，未冒称本人重登/独立重读完整IAM策略。
证据：集成树coordination/evidence/REL-20261025-02/backend-web-review/{design-qa.json,final-delta.json}；NET-20261025-01/final-domain-independent-review.json；TRIP作者证据在API树coordination/evidence/TRIP-20261025-01。发布总回执PR24已合并55071ffd，当前无待发布项（最新版本去中央STATE）。

规则：BIZ01欠款阻乘客新叫车、不阻司机接他人单；BIZ02接送各300m/50..5000继承可配，不可靠定位须本人同意；BIZ03双时间120s/精度100m为免确认资格，非GPS防伪；确认180s且幂等，议价共享预付也走PICKUP。钱仍模拟。ENV01/02只独立体验、保数据/IAM/IAP、min0max1请求计费，无保活/自动暂停。

未完成/NOT_RUN：精确1366、大区Web发布、本人非空确认历史浏览及完整a11y未跑；root另有真实同单三卡回读，不能混淆范围。微信缺AppID；QA-20261026-01模拟器overlay恢复读回仍有异常，由后续任务处理，不当已解决。真实支付/短信/厂商推送/GPS防伪等未接，不承诺强杀常驻。旧券积分金额待办仅属历史审查，未重新获派不得启动。

资源：本角色无CLI/ADB/Chrome/部署占用；中央STATE记8808已停、AVD已关、无后台AI；本轮不重启验证。下一动作：coordinator收ACK并更新SESSION登记/提交MEM；后续新backend从本卡与START_HERE读取一个明确任务，不翻全历史、不重复208。MEM检查见backend-review.json；中央旧检查器不认识--memory-role，需root同步新版或明确新版脚本--root中央。
