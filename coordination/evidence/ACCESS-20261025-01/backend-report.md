# ACCESS-20261025-01 后端/认证实现交接

已完成，待非作者验收与协调者合并。源码唯一目录为本工作树 apps/cloud-service；35个v8清单文件复制后，只有cloud_server.py与test_cloud.py变化，另新增HTTP边界测试及说明。原冻结目录未写；账本、订单、金额、schema与GCS CAS模块逐文件哈希一致。

两端公开 loginExperience(phone:String) 已实现。单次busy/loading里串行code→login→角色state→验证身份/解析→持久化token并公布身份；不嵌套公开login或refresh。乘客busy在launch前同步获取防双点，登录失败不清已有会话；司机使用原请求串行/epoch及原子发布。旧token启动仍可刷新，旧访问码偏好不读不清，新API不发送X-Demo-Access。旧save/clear访问码入口仅兼容无操作，不清令牌/订单。实际体验说明同意由UI明确点击后调用，Store不自动代同意。

服务端公共角色只移除演示访问码gate；白名单、Bearer角色、幂等、Origin保护、admin路由隔离保持。local.reset原有CloudService拒绝保留。管理服务内部令牌/应用会话/RBAC和生产部署IAM/IAP/Secret配置未修改。

真实loopback HTTP + 临时SQLite + MemoryCAS：新增8项涵盖无码两角色登录/state，旧合法/错误访问码兼容，未登录state/action拒绝，错角色token拒绝，非白名单/跨角色手机号拒绝，携内部头也不能从公共端访问admin，公开token reset403且原订单余额未变，司机通用action不能执行config.publish，内部admin令牌合法/非法和v2会话边界、已登录token跨冷启动有效、普通动作幂等重放。全部后端回归167项通过，54.088秒，0失败。没有访问云、真实账户、原环境或付款。

新Android Store代码尚未由本角色构建/设备执行；这些不应借后端167项冒充通过。root已获知Store冻结可构建，需非作者实际检查：未勾选不能登录、默认003/0102一键、切体验号、失败/重试、无访问码header、同签名保留旧token与数据、后台IAP保护。没有部署或提交，最终SHA待root绑定。

旧App带旧码/错误码可被新服务端兼容；未升级的旧二进制自身仍可能保留旧输入门槛，免填体验来自本次新包。新apps/cloud-service内static是冻结参考，若重建后台镜像务必叠加当前浏览器适配与现有管理Web，不能拿旧静态原型覆盖。

文件哈希/实际命令/未测项详见backend-result.json；copy baseline、CONTRACT与backend-tests.log在本目录。阶段完成后idle，不派代理、不改他人UI、不提交、不自行部署。
