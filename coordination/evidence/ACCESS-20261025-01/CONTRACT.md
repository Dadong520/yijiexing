# ACCESS-20261025-01 短接口契约

两端新增公开 `fun loginExperience(phone: String)`（异步返回Unit）。司机白名单0001/0002/0003，默认13800000003；乘客0101/0102/0103，默认13800000102。UI只在用户主动同意体验说明后调用；Store不自动接受说明。

内部单次串行执行 auth.code → auth.login → 直接GET本角色state，使用新token完成刷新，再持久化token并公布登录态。链内不调用有busy门禁的requestCode/login/refresh公开方法，不通过验证码回调嵌套登录；全程busy/loading，失败显示原error，不公布半完成身份；旧请求epoch/generation隔离。UI观察已有sessionAvailable/state.loggedIn或loggedIn/state与loading/busy/error。

configured/accessConfigured保留API兼容但表示固定服务已配置，始终可用；旧访问码偏好不读、不发送，save/clear旧码方法兼容无操作，不清token或订单。旧requestCode/login可保留但新版UI只用loginExperience。

公共客户端角色取消X-Demo-Access gate（请求有无或错误旧码均兼容）；账号白名单、Bearer角色令牌、所有业务幂等/账本/CAS不变。driver服务不接收admin API；admin角色只开管理入口，保留独立管理令牌、应用会话、IAM/IAP。不得调用通用司机action获取管理权限。新接口无新schema、无新secret、无内置共享口令。

后端唯一新开发源apps/cloud-service，冻结v8保持只读。仅本地HTTP/临时数据库正负边界验证，不部署不提交；coordinator统一合并发布及非作者设备验收。
