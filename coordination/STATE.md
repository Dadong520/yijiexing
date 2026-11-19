# 宜捷行：11月19日司乘 Android 优化已发布
协议2026-10-26-v3。唯一协调/发布 root 01a17cc7-e0a2-7791-bf1d-4611c09bab22，context UNKNOWN。

UIF-20261118-01（Gemini fa4ae6b718c5fa07704e6c0d89e00c228d294fc9 + 发布差量）最终验收源 413439a9fc9ea396bf0e6da5529b87a7c9e1043a；PR #59 合并 9605bcc2a92127203d9a424c187b2b370633300d。
最新乘客1.1.25-experience（27）、司机0.3.26-cloud（29），原包名原签名。两公开安装包逐字节匹配验收原包；旧包已条件保护归档。双Android构建和89/137单测、小程序74、工具27、两阶段独立复核、隔离覆盖安装及差量联调通过。
乘客选址/地图面板/费用明细/优惠券及个人中心更新；司机接听单、在线重定位、资质、钱包与个人页优化。独立体验后台/API仍为 fb3-9a76fc4，Cloud Run配置/IAM和GCS业务快照generation/MD5发布前后保持，微信小程序产品树未改。仅在隔离模拟器复验；真机/OEM/真实在线地图本轮未验。
源工作树 /Users/dongge/.codex/tmp/yijiexing-uif-20261119-release；唯一共享后端仍使用 /Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029/apps/cloud-service，新任务从最新main按任务指定唯一目录开发，不并行改副本。
发布证据：coordination/evidence/UIF-20261118-01/release/FINAL-RELEASE-RECEIPT.json。

前一轮修改意见3.0，产品PR57及回执PR58已合并：
本批交付：区域运价/服务费、单扣差额精确分账、公司分账账户门禁/流水、有责空车费与结清阻断、资金池/制券预览/新人券、司乘积分阶梯与拉新、服务分等级/时段配置与记录、回执冲红/重开审核和技术服务费模拟开票。规则见DECISIONS BIZ-10及本任务契约。
实际验证：后端344、后台10+18、乘客89、司机133、小程序74、工具27通过；两会话非作者交叉复核V2 PASS；线上旧库副本升级PASS；隔离AVD覆盖升级与本地TLS业务/UI闭环PASS，0崩溃，测试资源已回收。真实手机/OEM/实车本批未验。
支付/分账/提现/充值/发票均模拟，未接扫呗银行税务。小程序按用户确认不改，未微信编译发布。
