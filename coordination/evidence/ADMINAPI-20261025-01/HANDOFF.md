# backend · ADMINAPI-20261025-01 阶段交接

目标：为原后台截图中适用筛选和报表补齐真实持久数据查询、分页、同条件CSV导出。负责人 backend；基线 e600e6bd15d894afe9fbd85a7b1f57cf3cb4e842，实际起点4b61f3d3cfbe363934e448907cecb8a8b644475a。

唯一API树 `/Users/dongge/.codex/tmp/yijiexing-api-20261025`，分支 `codex/admin-query-api-20261025`。待合并产品SHA：8a02527d025245381ebca517fcd88508c5e9606d。作者实现/自测已完成，停止产品编辑，等待非作者frontend验收；不等同业务独立PASS。

归属：只改 admin_console.py/admin_queries.py/server.py/tests/test_admin_queries.py 与本证据目录。root未提交STATE/BOARD/task保持原状，未暂存或提交。role短卡未在allow_paths内，本证据HANDOFF为本任务短卡，由root同步入口。

契约见CONTRACT.md；9数据集 orders/drivers/passengers/payments/support/audit/statistics/cancellations/refunds；query响应items/total/page/pageSize/options/schema/summary/definition，导出相同筛选全部行。保留老state/action、原CSV前置字段和无筛选全量；新增提现、退款轮次、取消适用投影。日期首尾日包含→UTC+8闭开；缺日期旧记录不伪造、不匹配启用的日期筛选。统计按订单创建日队列，非真实清分；支付/退款/提现明确模拟。

实际测试：`env PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s apps/cloud-service/tests -v` 最终185 PASS/54.587s；其中新增18项投影/真实HTTP+SQLite+memoryCAS，专项日志query-suite.log。组合手机号/司机/车牌/类型/城市/日期、稳定分页、summary先聚合后分页、全筛选导出、CSV公式、UTC8起止/UTC时刻、遗留缺字段、每轮等额同刻退款不去重、当前待续叫不冒充终结取消、历史>200审计、VIEWER/会话/外令牌/driver404、冷重载及503恢复、九集查询无领域写入均实际运行。`python3 tools/check_coordination.py` PASS；`git diff --check` PASS。

原Java已精确只读四文件Req/Entity/LedgerReq/Mapper，行号与SHA见source-evidence.json。13原订单条件中10项提供适用范围查询，type/contactNumber/cancelType口径不完全等同；billingMode/company/assigned返回schema NOT_IMPLEMENTED及非空400。账号无持久登记日期；代叫手机只有掩码。电话叫车专用类型、超时/有责取消、真实分账/清分均未实现，不虚构。

保护：8个guard/domain/passenger/admin/snapshot/negotiation/options/extras源与起点逐字节一致，hash见source-evidence。未部署、未启停云端、未访问原测试/生产或现网数据库，未改IAM/IAP/令牌门禁/金额规则/账号与订单数据。

当前未通过项：无作者测试失败；独立浏览器UI/非作者业务复核NOT_RUN，合并/部署回读NOT_RUN。下一步root交frontend复核该固定SHA，合并UI后由唯一发布人安排；作者不得自己验收自己。用户附图说明此前暂停为节费主动操作，已通知root，不依据旧图自行改变当前云状态。
