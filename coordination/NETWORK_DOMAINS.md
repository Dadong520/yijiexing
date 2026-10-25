# 宜捷行稳定访问域名

当前后台 https://driver-admin-491829048305.us-west1.run.app/ ，两原生App接口 https://driver-demo-491829048305.us-west1.run.app 。已发布司机0.3.10/乘客1.1.10构建记录中cloudOrigin均为该HTTPS主机名，不是IP。因此底层IP变化不需要重新打包，只需保持域名/服务路由稳定。

用户要求自有域名时，将独立设置后台域名与API域名，例如在用户确认eztrips.cn控制权和DNS平台后使用demo-admin.eztrips.cn / demo-api.eztrips.cn；不改变原主站/原测试生产，不擅自认定其为已授权域名。需DNS记录、有效TLS、后台IAP Google登录与同源Origin复测。若决定将已发App从run.app主机名切为自有API域名，需进行一次地址切换发布；之后域名不变、只换后端IP或映射目标不用重打包。旧包继续保留原run.app入口兼容，不能假定跨域HTTP重定向会保留身份请求头。

待用户输入：自有域名及DNS托管平台，暂不索要密码/密钥。当前项目未发现可用DNS连接器，不擅自修改原站。继续本轮后台功能发布不依赖新域名。

低费用原则：保持Cloud Run automatic/min0/max1/请求计费；优先核对原生域名映射可行性和IAM/IAP完整性，不为了好记地址新增常驻代理或付费负载均衡。Google官方说明Cloud Run自有域名映射处于受限预览、us-west1可用，Google托管证书签发需等待；正式选型须按实际域名/保护需求复核，不承诺立即生效。
官方参考：
- https://docs.cloud.google.com/run/docs/mapping-custom-domains
- https://docs.cloud.google.com/run/docs/securing/identity-aware-proxy-cloud-run
- https://docs.cloud.google.com/run/docs/custom-urls

当前状态：自有域名尚未绑定，TLS/DNS/自有域名App真机验收NOT_RUN；不能把已有run.app访问和计划子域名混称已完成。
