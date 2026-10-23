# 共享后端负责人集成说明

本目录由后台agent编写，不修改 cloud-service。复制 `admin_console.py` 到 cloud-service，与 common/domain 同级即可。不要新建数据库，不启动第二套Service，不改snapshot.py。

## 接线

在 server.py 导入 `from admin_console import AdminConsole, ConsoleLoginError`。
在现有 `/api/local/admin/` 分支中，**外层 X-Admin-Token 校验之后、旧路由之前**加入：

```python
if path.startswith('/api/local/admin/v2/'):
    console = AdminConsole(svc)
    session = self.headers.get('X-Admin-Session', '')
    if path == '/api/local/admin/v2/login' and self.command == 'POST':
        try:
            data = console.login(p)
        except ConsoleLoginError:
            # Only login security counters/audits; no business mutation in this route.
            svc.db.commit()
            raise
    else:
        user = console.authenticate(session)
        if path == '/api/local/admin/v2/state' and self.command == 'GET':
            data = console.state(user)
        elif path == '/api/local/admin/v2/action' and self.command == 'POST':
            result = console.action(user,p.get('action'),p.get('payload',{}),p.get('requestId'))
            data = {'result':result,'state':console.state(user)}
        elif path == '/api/local/admin/v2/logout' and self.command == 'POST':
            data = console.logout(session)
        elif path == '/api/local/admin/v2/export' and self.command == 'GET':
            # parse query with parse_qs(urlparse(self.path).query)
            # Produce raw CSV only AFTER existing SQL+SnapshotCoordinator.finish
            data = console.export(user, query.get('dataset',['orders'])[0])
            # Set existing response mime mechanism to text/csv; charset=utf-8.
            # Do NOT return/send before coordinator.finish succeeds.
        else:
            raise ApiError(404,'管理接口不存在')
elif ... existing routes ...
```

新增路径在现有guard的 `/api/local/admin/` 范围内；driver服务必须仍404，admin服务保持Cloud Run IAM外层保护。`X-Local-Actor` 不用于 v2 身份，身份仅由服务端会话决定。

**旧管理接口是独立管理令牌的超级权限接口。浏览器如持有该令牌可绕过v2角色权限。必须使用本机代理只开放v2与前端静态资源并注入X-Admin-Token，不把该令牌发给普通管理员；或在云端废弃旧写接口并要求超级管理员v2会话。** 不要向普通管理员提供原管理令牌。

初始化密码使用仅管理服务的 `ADMIN_BOOTSTRAP_PASSWORD` secret/env（至少12字符），首次访问创建admin。哈希保存在同一个SQLite快照；账号已存在时忽略该环境变量。需要按原snapshot流程finish并上传成功后才返回登录成功。密码与admin_token均不可写静态文件或APK；本机代理令牌仅存权限0600文件/进程环境，不输出日志。

模块不调用commit（登录失败由handler显式提交安全计数，见上文），不开启事务，所有变更与调用方快照上传一致，上传失败必须使用原coordinator失效/恢复机制。新增console_users/sessions/actions是同库增量表，不改变旧表或已有行。

## 验证

运行 `python3 yijiexing-admin-20261023/backend/test_admin_console.py`：8项测试通过，覆盖真实共享Service完整流程、支付失败恢复、金额修改同步、投诉处理同步、退款余额、重复请求与幂等冲突、权限拒绝、会话撤销、重启持久化、事务回滚、状态限制、CSV公式转义。测试使用临时SQLite，不接原环境或云端。

仍需共享负责人跑HTTP/CAS测试，尤其：公开driver服务拒绝v2；管理令牌错误拒绝；无session拒绝；模拟快照写失败不返回成功；前端真实浏览器集成；双模拟器三端联动。以上未由此单元测试替代。

登录限速：每账号15分钟最多5次失败；第6次429，失败与阻止均审计，不记录输入密码。ConsoleLoginError必须按上文处理，否则原handler rollback会抹掉计数。云端CloudHandler继续先CAS再发送401/429，不能绕过持久化。

乘客端 passenger_data 目前 notices/humanSupport 宣称未接人工处理，应由共享负责人调整为“独立后台可处理反馈；未接外部客服”，避免新增能力与文案冲突。

## 附件增量接线
在v2已authenticate后增加GET分支（复制最新admin_console.py）：
```python
elif path == '/api/local/admin/v2/certificate-file' and self.command == 'GET':
    data = console.certificate_file(user, query.get('driverId',[''])[0], query.get('type',[''])[0], query.get('side',['front'])[0])
```
仍走现有JSON响应和快照finish。仅允许读取对应司机当前证件引用的图片，不允许任意URL、任意文件ID；缺图404。front/back对应fileId/backFileId，校验files.driver归属，PNG/JPEG固定格式。审计记录包含图片ID不含图片内容。

## Bootstrap 密码换行兼容修复
首次创建admin时，将通过校验返回的去首尾空白密码用于哈希，与登录输入规范化一致。对于已有admin，仅当当前服务端bootstrap原始值带首尾空白、且该原始值精确验证旧密码哈希成功时，迁移为规范化密码哈希；不覆盖用户已修改密码，不修改角色、启用状态、其他账户或业务数据。迁移同时清除该admin因历史错误产生的登录失败计数，写console.bootstrap.normalize审计。迁移必须经过原快照CAS成功后才确认。无配置、无首尾空白、原始值不匹配旧哈希均不迁移。

本地回归现为11项通过，新增首次含末尾换行、历史哈希匹配迁移且只执行一次、已改密码不被原bootstrap覆盖三项。
