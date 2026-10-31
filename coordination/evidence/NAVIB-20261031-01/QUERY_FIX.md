# NAVIB 联系人query窄修

最终第二提交 `6e1b1ee02b3cc1a3bd7359e2970d2f3d4c3c59c3`，parent fb0eba38ff2bc598901f9c707a38119f05398fda。只2文件：server.py新contact分支局部parse_qs(keep_blank_values=True)，严格唯一键/单值/非空；不改全局query及其他路由。新增HTTP测试先确认有效assigned订单可取号，再对 `orderId=valid&orderId=`、`orderId=valid&unused=`及`orderId=`各断言400。

实际contact suite **8 PASS**（1.822s），日志contact-query-fix-selftest.log，SHA在QUERY_FIX.json；diff --check/中央角色门禁PASS，提交后clean。此轮仅作者自测，独立验收NOT_RUN；没重跑全后端/云/真实privateDB/原生。

**root导入基线提醒**：root报告导入6f76ae9为6afb636并独立282项通过，45旧单clone全字段未变/contact0且无active旧单样本（以上按root报告，不冒充本角色执行）。但6f76ae9是最初临时提交，最终已交fb0eba38多了首次新negotiation.claim明确booker快照与其回归，不能遗漏。已提供精确 `MISSING_FROM_6F.patch`（6f→fb仅passenger.py与14行test，21增1删），供root把已导入树补齐；仅cherry-pick本strictquery提交不会带入该前置差异。不要在旧导入上把最终128自测说成同头结果。

root独立复核最终组合树后安排集成；backend本轮结束IDLE，不轮询不部署。原IMPLEMENTATION128及初诊保留。
