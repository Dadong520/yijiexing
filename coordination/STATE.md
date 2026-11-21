# 宜捷行：11月21日司乘 Android 优化已发布
协议2026-10-26-v3。唯一协调/发布 root 01a17cc7-e0a2-7791-bf1d-4611c09bab22，context UNKNOWN。

UIF-20261120-01，Gemini 2e2fc0508d743c99f283e4d04afbfe06907037c9 加验收修复。最终产品源 4cdaa9381564c9bc4fc5d3bd481d1484fecff4a5；PR #61 合并 0ebd2f4a33b6b461a92642eb92e2ebe61a3af502。
乘客1.1.26-experience（28）、司机0.3.27-cloud（30），原包名原签名。双公开安装包完整下载匹配验收原包，旧包已条件保护归档。双端91/141单测、小程序74、工具27、两次非作者差量复核、隔离覆盖升级与实测通过。
更新双端界面；乘客投诉图按需缩略与预览；司机听单同步真实采样，抢单走原5秒服务端竞抢，导航面板保留联系乘客。独立体验后台/API仍为 fb3-9a76fc4，Cloud Run配置/IAM和GCS业务快照发布前后保持，小程序未调整。本批未验个人手机或实车导航。
产品树 /Users/dongge/.codex/tmp/yijiexing-uif-20261121-release；唯一共享后端仍使用 /Users/dongge/.codex/tmp/yijiexing-ui-optimization-worktree-20261029/apps/cloud-service，按新任务指定唯一目录开发。
发布证据 coordination/evidence/UIF-20261120-01/release/FINAL-RELEASE-RECEIPT.json。

历史《修改意见3.0》，产品PR57及回执PR58已合并：
本批交付：区域运价/服务费、单扣差额精确分账、公司分账账户门禁/流水、有责空车费与结清阻断、资金池/制券预览/新人券、司乘积分阶梯与拉新、服务分等级/时段配置与记录、回执冲红/重开审核和技术服务费模拟开票。规则见DECISIONS BIZ-10及本任务契约。
实际验证：后端344、后台10+18、乘客89、司机133、小程序74、工具27通过；两会话非作者交叉复核V2 PASS；线上旧库副本升级PASS；隔离AVD覆盖升级与本地TLS业务/UI闭环PASS，0崩溃，测试资源已回收。真实手机/OEM/实车本批未验。
支付/分账/提现/充值/发票均模拟，未接扫呗银行税务。小程序按用户确认不改，未微信编译发布。
