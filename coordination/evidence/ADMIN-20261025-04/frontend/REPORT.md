# 后台独立验收记录

结论：需要修复后复测。产品 `fe201d921eb9a5eca5be3b51d404dfc49fa4c71a`，API `8a02527d025245381ebca517fcd88508c5e9606d`。只使用本地合成数据，没有上线或修改业务记录。

两个待修问题：提现审核使用了投影编号；投诉列表账号显示与实际账号不一致。

1. **登录页和错误提示 — PASS**：1366级左右布局清楚；390x844无横溢出，输入label可读；合成错误密码有明确提示。

2. **订单时间和多条件筛选 — PASS**：UTC8的10月24日只D/B/C，退款2700分；kind+账号手机+车牌+DONE组合只D，chips正确。

3. **分页、详情和返回 — PASS**：20条与15条/页，翻页后PAGE-06详情金额10.06正确；重置回第1页；空查询0条；从日志返回订单保留PAGE过滤且旧审计按钮不可见。

4. **实际CSV导出 — PASS**：日期匹配3条、无筛选第2页导出34条、PAGE过滤第2页导出27条；非当前页15条或全库34条。未提交草稿不生效；=CMD(A1)加单引号保护。

5. **失败和重试 — PASS**：实际fail-next造成query503，页面明确持久化不可用，导出禁用、PAGE草稿保留；实际点重试恢复27条。

6. **取消、退款和报表 — PASS**：C是WAIT_CONTINUE待乘客续叫而非终结；退款3轮同额同刻各900分共2700不去重；日统计3单1完1取消1活跃，模拟实付20/退款27元；详情可读。

7. **司机、乘客和模拟账务 — PASS_READ_ONLY**：三个列表均加载真实query并实际查询/打开对应详情；缺登记日期明确禁用且说明，不伪造。提现审核变更本轮未执行。

8. **投诉和历史审计 — REQUIRES_FIX**：投诉筛选/详情可用，但OC1列表显示D/D，详情账号P1/订单D，API accountId=P1；另按ADMIN:old能查到id1，超旧state最近200限制。

9. **只读角色和边界 — PASS**：VIEWER导出和调价按钮disabled；自己的HTTP会话直接export/action403、无session401、driver路径404；结束所有本地会话logout并重置viewport。

截图只能支持对应页面和交互状态；未做完整无障碍或真实资金、三端交易验收。以下为本轮实际保存并查看的截图。

![01-login-desktop](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/01-login-desktop.jpg)

![02-login-narrow](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/02-login-narrow.jpg)

![03-login-error](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/03-login-error.jpg)

![04-overview](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/04-overview.jpg)

![05-orders-date](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/05-orders-date.jpg)

![06-orders-combined-detail](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/06-orders-combined-detail.jpg)

![07-orders-page2-detail](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/07-orders-page2-detail.jpg)

![08-orders-empty](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/08-orders-empty.jpg)

![09-query-failed](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/09-query-failed.jpg)

![10-cancellations](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/10-cancellations.jpg)

![11-refund-detail](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/11-refund-detail.jpg)

![12-driver-detail](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/12-driver-detail.jpg)

![13-passenger-detail](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/13-passenger-detail.jpg)

![14-payment-detail](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/14-payment-detail.jpg)

![15-support-detail](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/15-support-detail.jpg)

![16-statistics-detail](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/16-statistics-detail.jpg)

![17-audit-old-detail](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/17-audit-old-detail.jpg)

![18-viewer-detail](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/18-viewer-detail.jpg)

![19-filtered-page2-export](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend/19-filtered-page2-export.jpg)
