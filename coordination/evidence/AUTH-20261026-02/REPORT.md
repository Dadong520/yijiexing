# 后台账号密码登录切换验收

用户2026-10-26明确要求取消Google前置步骤，保留后台账号密码。源c46f88bd848346196283b90d92977120962ce00c，PR26合并25f8c9eb29f55a3182b5253fed463d66f75e4a55。

## 已发布
2026-10-26 09:33:59，driver-admin-password-c46f88bd-r3 100%，image sha256:e51df78b00868abba60d6a1dae267f6b5db48eab366828850c5f9aee510a8109。网址https://demo-admin.hbzjhs.xyz/，备用run.app地址仍可用。使用原后台账号密码，无需Google，App不需更新。

默认代码仍严格IAP，只有服务端ADMIN_AUTH_MODE=PASSWORD才启用账号模式。Cloud Run已关闭本服务IAP及入口Invoker IAM检查；服务间凭据/SA/env除模式外不变，其他服务不变。没有增加allUsers IAM绑定；停用IAP时撤销其专用Invoker授权属预期范围。min0/max1/自动按请求计费保留。

## 验证
- 原IAP+新增PASSWORD共18真实HTTP/CAS作者测试PASS。
- root非作者5组HTTP验证PASS：匿名页面/真实静态资源，state/query/export/action会话保护，伪造header/legacy入口不可绕过，Origin/跨站拒绝，密码登录/VIEWER拒写/退出失效，错误模式拒绝。
- 自有域名及run.app无Google/Cookie匿名GET200；state/query/export未登录均401，TLS验证通过。
- 09:51:41原admin账号在无Google/cookie、禁止跳转的HTTPS客户端登录成功200，state200、logout200、旧会话401。仅输出非敏感角色与统计，未写凭据/会话。
- 24笔共享订单全保留，既有状态、金额、已付款/退款字段无变化；既有f4为DONE/2926分/已付。未执行订单或财务操作。
- 独立浏览器范围与实际截图以frontend报告为准；未运行不冒充。

## 实际失败及修正
独立本地测试初始夹具密钥长度不合规定、静态ROOT绑定未覆盖cloud模块、未配置IAP时应503而非401，均修正夹具后通过，未弱化产品。第一次/第二次部署因过严IAM策略完全相等校验自动回退；最终明确仅允许撤销原IAP服务代理Invoker grant，并忽略非授权性的etag/version元数据，其余有效策略逐项一致。初始“只有etag”判断基于回退后的比较，已标为被后续公开策略快照替代的假设。
本机Python默认CA缺失、备用环境缺certifi导致客户端验证未执行；改用系统/etc/ssl/cert.pem并保持完整证书验证后成功，未跳过TLS。

## 回退
在同一独立项目/us-west1，先重新开启driver-admin IAP与Invoker检查，再将流量指回driver-admin-trip-2863623f（旧image264abd84..），移除ADMIN_AUTH_MODE；原OAuth设置保留可恢复。不要只切回旧IAP-only镜像却继续公开入口，否则登录页会被旧入口拒绝。回退不修改数据库、订单、账号或App。

## 最终浏览器范围与收尾
frontend实际Chrome新页直接落在自有域名账号密码页，无Google提示，截图已保存查看。完整点击登录/概览/退出/刷新没有执行，限时停止，记录NOT_RUN；不将另一个HTTPS客户端成功登录冒充浏览器全链。测试页已关闭、Chrome交还、frontend IDLE。root实际无Google/cookie原账号登录与退出撤销401已验证，功能可以使用。

本轮旧长会话的独立UI复核耗时明显超过约定，后续大任务必须换新会话；没有据此假称上下文百分比。尝试使用Codex自身UI停止超时工作被工具安全限制拒绝，未绕过；最终通过协调消息收尾并已收到IDLE确认，无后台AI继续运行。
