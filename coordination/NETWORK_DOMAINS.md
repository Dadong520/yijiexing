# 宜捷行固定访问域名（2026-10-25）

用户确认Cloudflare托管域名hbzjhs.xyz。demo.hbzjhs.xyz是既有交互原型，本轮未修改。

API：https://yijiexing-demo.hbzjhs.xyz ，已存在的Cloud Run原生映射Ready，系统TLS验证+health200通过。司机0.3.11-cloud/code14、乘客1.1.11-experience/code13已构建并由frontend独立覆盖安装验证，已22:37统一发布，两公开HTTPS完整GET哈希/签名/版本PASS。两包仅版本/默认域名共6行变化，保留原账号，未更改业务和UI。首次改为该域名需更新一次；后续底层IP变化只调整DNS/后端映射，域名保持即可。旧run.app入口继续兼容，不依赖跨域重定向保留令牌。

新后台：https://demo-admin.hbzjhs.xyz 。Cloudflare新建DNS-only CNAME到ghs.googlehosted.com，Google公共DNS已确认；Cloud Run映射指向driver-admin。最后回读仍CertificatePending，后台自有域名尚不能作为可用交付入口。IAP、IAM及所有其他配置保留，仅Origin精确追加新域名，同镜像配置revision driver-admin-domain-7ee63cc已100%流量。等待证书后单独做TLS/IAP/登录验收。
当前可用后台：https://driver-admin-491829048305.us-west1.run.app/ ，使用已授权Google账号dadonggev587@gmail.com和既有后台账号凭据。

后台实际UI/报表产品0d7dfc04、API8a02527d已PR17合并与发布，19个共享订单关键字段发布前后一致。root已实际登录并以订单号筛选、导出一条WAIT_PAY 2926分订单；不同会话的线上登录提交未完成记录保留，不能改写为其PASS。

低费用：Cloud Run automatic/min0/有效max1/按请求计费，不保活、不自动手动暂停，未新增常驻代理或收费负载均衡。模拟支付保持明确标注，不接原测试/生产。

操作：API域名变化需保留旧主机兼容；后台新origin保持精确白名单。暂停/恢复仍按现有Cloud Run独立体验运维文档，平时让服务无请求自动缩0即可；DNS不能当成暂停业务开关。Google证书待签发时不关闭TLS验证或IAP。
官方参考：https://docs.cloud.google.com/run/docs/mapping-custom-domains 、https://docs.cloud.google.com/run/docs/securing/identity-aware-proxy-cloud-run 。

公开下载：司机 https://yijiexing-demo.hbzjhs.xyz/download ，乘客 https://yijiexing-demo.hbzjhs.xyz/download-passenger 。可以覆盖安装保留原账号。源码PR18合并3bc2f3beb7c10f1d4fd51a6b8ae5ad9a304c7efe。
