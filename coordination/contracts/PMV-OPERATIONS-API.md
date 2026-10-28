# PMV首批运营接口 v1（PMVO / PMVU）

基线main c3c876ef3359f8bc4bb306f515637e1fc72ca621；backend写树yijiexing-pmv-backend-20261028。2026-10-28，契约先行；实现/自测未完成，不是上线声明。BIZ08/09已确认，不重复询问。BIZ07本轮不实现资金执行。旧operationDrafts原样保留，不从其自由文本自动发布。

## 传输、权限、版本

沿用原路径：管理GET `/api/local/admin/v2/state`、POST `/api/local/admin/v2/action`，X-Admin-Session + 原服务端保护；乘客GET `/api/passenger/state`、POST `/api/passenger/action`，原Bearer；司机GET `/api/state`、POST `/api/action`，原Bearer。action体 `{action,payload,requestId}`。新增字段均为增量；旧字段保留。

管理新增操作全部 `config.write`（SUPER_ADMIN/CONFIG_ADMIN），资质审核仍 `certificate.audit`（SUPER_ADMIN/OPERATOR）；只读按config.read/drivers.read；401未登录/403无权限/404对象不存在/409旧版本或状态冲突/400参数错误/503 CAS未确认。version与expectedVersion严格整数，不接收bool。新建expectedVersion省略或0；修改/发布/撤回必须当前version；绑定初始expectedVersion=0。成功更新版本+1，审计/幂等同现有console_actions，发布与存储CAS完成后才响应。

全部新增表在原SQLite事务内CREATE TABLE（不executescript），无新连接或后台调度。日期时间接受带时区ISO8601，统一+08；有效期startAt包含、endAt不包含；证件是UTC+8本地日期、到期日当天有效。文本是纯文本，客户端必须转义呈现。

## 1 公司、客服电话、司机归属

管理state增加 `operations`：
```
{companies:[],driverCompanies:[],announcements:[],passengerFeatureConfigs:[],
 qualificationAlerts:[],serverDate:"YYYY-MM-DD"}
```
公司 `{id,name,supportPhone,enabled,version,updatedAt}`，未配置号码为null，无虚构客服电话；电话为可拨号码（数字、+、-、空格，最长30），展示与拨号分开。明确公司ID，绝不自动映射organization。

- `ops.company.save`：`{id?,expectedVersion?,name,supportPhone:null|string,enabled:boolean}` → `{company}`。id服务端生成；停用保留关联/历史，公开状态为DISABLED，不冒充签约状态。
- `ops.driverCompany.set`：`{driverId,companyId:string|null,expectedVersion}` → `{driverCompany:{driverId,companyId,version}}`；司机和启用公司须存在，null解除。客户端/司机不能自写公司归属，后台不按组织文本猜测。
- 乘客 `ops.company.select`：`{companyId:string|null}` → `{company}`，只可选公开启用公司；持久保存本人选择，不改变司机归属或历史订单。

乘客state `operations:{companies:[启用公司公开字段],company:<view>,announcements:[],passengerFeatures:<effective>}`；司机state `operations:{company:<view>}`。company view固定 `{status:CONFIGURED|UNCONFIGURED|DISABLED,id:null|string,name:null|string,supportPhone:null|string,version:null|int,source:ORDER|SELECTED|DRIVER|NONE}`。当前活动订单已记录的companyId优先，其次乘客本人所选；每个乘客订单另增company视图。只能读自己订单/选择；公开电话可读，不公开其他司机公司名册。

新共享订单首次指派司机时持久记录companyId（可null），后续改司机归属不改该历史归属。旧订单无companyId不可按当前司机归属伪造历史公司；未记录则明确UNCONFIGURED并允许本人选择回退。公司电话修改可按公司ID读取当前维护电话，不能把其签约视为有效。选公司本轮用于客服上下文，不新增跨公司派单限制。

## 2 公告：发布与一次展示回执

管理 `announcement={id,version,status:DRAFT|PUBLISHED|WITHDRAWN,draft:<content>,published:null|<content+version>}`。
content `{title,content,scope:NATIONAL|REGION|CITY,target,audiences:[MINIPROGRAM|PASSENGER_ANDROID],startAt,endAt}`；title1..120/content1..2000；默认audiences=[MINIPROGRAM]，禁止司机/离线广播；target必须现有CN/大区/城市字典。无区县假配置。草稿可含已开始日期，发布时endAt必须仍在未来。

- `ops.announcement.save`：`{id?,expectedVersion?,title,content,scope,target,audiences?,startAt,endAt}` → `{announcement}`。编辑已发布公告只更新draft，旧published仍按原期生效。
- `ops.announcement.publish`：`{id,expectedVersion}` → `{announcement}`。复制当前draft至published，published.version=这次成功后的实体version。更新发布即新公告版本。
- `ops.announcement.withdraw`：`{id,expectedVersion}` → `{announcement}`。立即撤回，保留草稿、审计和旧回执。

乘客state与login的客户端受众由 `client=MINIPROGRAM|PASSENGER_ANDROID` 指定：GET query `?client=...`，POST login/action可给顶层client；缺省MINIPROGRAM兼容旧调用。未知client400。state中的operations.announcements仅当前区域/受众、start≤now<end、PUBLISHED、本人未ack的公开published快照（含id,version）。**读取state/login绝不消耗次数**。

乘客 `ops.announcement.ack` payload `{id,version,client}`。只接受当前已发布、有效、当前本人区域/受众版本；旧版/撤回/过期409，无权区域403。返回 `{acknowledged:true,firstDisplay:boolean,id,version}`。回执唯一键=乘客ID+公告ID+published.version（跨设备/两种客户端仍每用户每版本一次）。客户端准备展示时先显式ack，**仅firstDisplay=true打开可关闭弹窗**；并发/重复ack返回false，同requestId重放也不得产生第二次firstDisplay。state读取次数不参与。这是展示领取回执，不证明设备物理上已显示；网络丢失可能使本次弹窗遗漏，不能伪称离线推送送达。无需关闭时再重复ack。

## 3 地区乘客功能

管理 `passengerFeatureConfig={scope:NATIONAL|REGION|CITY,target,version,draft:{instant?,reservation?,bidding?,invoice?},published:null|{features,version}}`。字段仅boolean；省略即继承，空对象恢复该层继承。默认四项true，兼容未配置旧功能；现有systemEnabled另行保留门禁。

- `ops.passengerFeatures.save`：`{scope,target,expectedVersion,features:{...}}` → `{passengerFeatureConfig}`。不存在对象expectedVersion=0。草稿不改变任何执行结果。
- `ops.passengerFeatures.publish`：`{scope,target,expectedVersion}` → `{passengerFeatureConfig}`，仅发布影响新操作，原发布值保留至成功。

乘客state.operations.passengerFeatures `{city,features:{instant,reservation,bidding,invoice},sources:{每项:{scope,target,version}},version:<层级版本串>}`，全国→大区→城市逐字段继承。GET依本人profile.city；乘客切城市后重新读state。新订单按服务端验证后的订单city门禁STANDARD→instant、RESERVED→reservation、BID→bidding，拒绝403带reason=PASSENGER_FEATURE_DISABLED/feature/city。不得用客户端按钮决定权限。新模拟invoice.apply按本人订单city门禁；已存在发票重复读/幂等返回不被新关闭阻断。关闭不阻历史查询、旧单接驾/行程/付款/取消/已创建竞价选择；旧单继续叫车视新叫车需重新校验。非法城市仍拒绝，不能冒用区县。

**v1.3 跨地区开票投影补丁**：每个passenger_view返回的订单增加 `invoiceApplicationEnabled:boolean` 与 `invoiceApplicationRulesVersion:string`，严格按该订单已保存的city及当前已发布的区域invoice规则计算，与新invoice.apply门禁同源，不按乘客当前profile.city计算。布尔只表示地区功能许可，客户端仍需检查原有DONE/paid等申请条件；已开回执及历史列表始终可读，不因该布尔false隐藏已存在回执。新申请按钮逐订单使用此布尔，旧后端缺字段可兼容回退当前地区规则；全局新入口继续按operations.passengerFeatures当前地区展示。字段是实时只读投影，不写回订单、费用或发票，规则发布后版本串随订单city生效层变化。

## 4 三证：60天、待审核保留旧证

保留profile.certificates各项的现有type/name/status/validFrom/validUntil/fileId等，顶层为正在使用的有效材料，增加 `version:int`（legacy初始0）、`pending:null|{submissionId,validFrom,validUntil,fileId,backFileId,submittedAt,status:PENDING}`、`reviewStatus:NONE|PENDING|REJECTED|APPROVED`、`daysRemaining:int|null,validity:VALID|EXPIRING|EXPIRED|NOT_EFFECTIVE|LONG_TERM|UNKNOWN,canUpdate:boolean,reminder:boolean`。

已批准有效材料第61天不提醒/不可更新；60、59、0天提醒/可更新；-1过期且可更新；已批准LONG_TERM不提醒/不可更新；未批准或已驳回材料只要不在待审，即便长期/超过60天也允许纠正重提（v1.1澄清，canUpdate与提交规则一致）；缺失/坏日期UNKNOWN提示补全、可更新，不冒充有效。旧APPROVED但不认识日期保留历史兼容门禁，不能据此称真实资质有效；新提交严格日期验证。待审不可重复提交，canUpdate=false但reminder仍按旧证剩余日期计算。

`certificate.submit` 原payload增加可选expectedVersion（新客户端必传），仍 `{type,validFrom,validUntil,imageBase64,backImageBase64?}`。图片校验与原规则不变，保单不支持长期。新证只进pending，版本+1；有效旧证顶层及资质APPROVED不变。无有效旧证仍不能新增接单；旧证审核中到期，则实时阻新增，在途可完成。

管理 `certificate.audit` 保留 `{driverId,type,approved,reason?}`；新pending必须补 `{expectedVersion,submissionId}`，陈旧候选409；旧legacy顶层PENDING兼容旧审核。通过则同事务用pending替换顶层材料、清pending、版本+1，按全部当前证件状态计算资质；驳回清pending、保留旧材料和旧有效状态、reviewStatus=REJECTED，保存完整审核链，不自动通过。

管理state.operations.qualificationAlerts为三证提醒/过期/未知日期的只读列表，每项含driverId,type,version,validUntil,daysRemaining,validity,pending/reviewStatus；司机同字段随certificates返回。GET `/api/local/admin/v2/certificate-file` 增加 `source=REVIEW|ACTIVE`，缺省REVIEW（有pending则读pending，无则原active）；响应source也固定为REVIEW（待审材料）或ACTIVE（当前材料），返回submissionId以便审核绑定（v1.2字段对齐），不暴露他人附件。服务端新增接单/报价选择/预约出发/议价认领均保留实时证件校验；driver.online也检查，过期不能上线新增，但允许已有在途完成/下线。公司签约资金门禁本轮不启用。

## 前端接入次序与验收

后台先读state.operations及版本，写成功采用返回state；409必须刷新再确认，不能覆盖。司机只读维护电话及证件有效/待审两套材料；乘客选择公司、依区域功能渲染新入口，并处理服务端403；小程序/Android声明client，state后逐条claim公告，只打开firstDisplay=true的纯文本可关闭弹窗。历史单入口与在途控制始终保留。

后端需真实HTTP/SQLite/CAS、重启、双身份隔离、409/403、并发回执、日界线及旧资金指纹测试。作者自测不是独立验收，后续仅coordinator合并/部署。
