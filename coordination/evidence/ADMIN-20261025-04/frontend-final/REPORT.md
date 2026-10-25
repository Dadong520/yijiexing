# 后台修复版限定复测

结论：PASS_SCOPE。产品 `0d7dfc045a137ee93b354acd669edebf6724af29`；API `8a02527d025245381ebca517fcd88508c5e9606d`。首轮完整证据见上级frontend目录，本轮只复查4行改动。

1. **OC1 associated account fixed — PASS**：List now乘客P1 · P1/orderD, same as actual detail. Reply payload ownerId preserved by exact source check; no reply posted.

2. **W1 synthetic withdrawal approval succeeds with real business id — PASS**：Only localhost fixture W1 PENDING, amount300 cents. UI reason+confirmation then actual browser POST action HTTP200, payload.id W1 (not WITHDRAWAL:D1:W1). Query refresh and expanded raw record report withdrawalId W1/status SANDBOX_APPROVED. No bank transfer or cloud mutation.

3. **Logout isolates filters between accounts — PASS**：Admin applied searchPAGE- yielding27. Logged out, logged viewer in, orders search empty/applied none/34 records, export disabled. Source clears queryViews, pendingRequests and increments queryTicket. No timing-race stress claim.

4. **Export copy and actual cents unit — PASS**：Actual export toast says金额列单位为分. CSV27 PAGE rows, fare1000+i matches prior UI10.00+i/100 yuan. File bytes equal independently accepted baseline filtered27 export.

两项首轮缺陷已关闭。本轮仅本地合成W1做模拟审核，没有银行打款或云端数据变化；未重跑完整三端交易。

![01-support-account-fixed](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend-final/01-support-account-fixed.jpg)

![04-withdrawal-confirm](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend-final/04-withdrawal-confirm.jpg)

![05-withdrawal-approved](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend-final/05-withdrawal-approved.jpg)

![06-withdrawal-readback](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend-final/06-withdrawal-readback.jpg)

![07-export-unit-cents](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend-final/07-export-unit-cents.jpg)

![08-viewer-new-session](/Users/dongge/.codex/tmp/yijiexing-admin-20261025/coordination/evidence/ADMIN-20261025-04/frontend-final/08-viewer-new-session.jpg)
