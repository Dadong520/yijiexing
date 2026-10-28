# PUI 默认项接口 v1

PUIB/PUIU，2026-10-28；基线main `8e2d655bd58d33241d79d6da3ad82f29cdec7894`。用户PUI-01已确认默认常用地址/乘车人/抬头，只影响新草稿或新申请的初次建议，不修改已填行程、历史订单或发票快照。此契约先行，实现/自测尚未完成，不是上线声明。

## State 与所有权

沿用Bearer乘客认证、GET `/api/passenger/state`、POST `/api/passenger/action` 及 `{action,payload,requestId}`。无新匿名入口、无客户端自报passengerId。所有记录在服务端当前登录账号范围内校验与更新。

乘客登录/state及action响应的当前state顶层增量：

```
defaults: {favoriteId: string|null, riderId: string|null, invoiceTitleId: string|null}
favorites: [{...原字段, isDefault: boolean}]
riders: [{...原字段, isDefault: boolean}]
invoiceTitles: [{...原字段, isDefault: boolean}]
```

每类最多一个默认。旧数据没有defaults时三个值均null；首次新增不会自动设默认，不根据既有自由字段猜默认。投影各列表时默认项置顶，其他顺序保留；isDefault由唯一引用计算，不信任客户端传来的多个标记。持久存储复用本人passenger_extras JSON中的defaults，不新建资金表。

## 显式设定、保存和删除

`defaults.set` payload：

```
{kind: "FAVORITE" | "RIDER" | "INVOICE_TITLE", id: string|null}
```

id必须是本人该类已存在记录的非空合法编号。null明确清除该类默认，其余两类保持不变。只允许kind和id两个字段；缺失/空字符串/坏类型/非法kind为400，坏ID/跨账号引用为404且无副作用。返回result `{defaults:{...}}`。

现有 `favorite.save`、`rider.save`、`invoice.title.save` 增加可选 `isDefault:boolean`：

- true：保存成功后在同一事务内把本条设为该类唯一默认，原默认自动解除。
- false：仅当本条恰为当前默认时清除默认；不会清除另一条已设默认。
- 省略：编辑保留既有默认；新增不自动成为默认。非法值（包括null、数字、字符串）400，不能先保存再报错。
- 保存结果原 `favorite`/`rider`/`invoiceTitle` 对象增量含isDefault；同一响应顶层state.defaults和列表为当前权威状态。

现有 `favorite.remove`、`rider.remove`、`invoice.title.remove` 删除当前默认时，同事务清空对应引用，不自动选下一条。删除其他记录不影响默认。已存在的订单乘车人快照、地址坐标、金额及已申请回执titleSnapshot均保持原值；删除记录不能抹去历史快照。

## 幂等、并发、持久化与兼容

沿用必填requestId和passenger_actions指纹：相同rid/相同payload重试不重复执行；相同rid改payload为409。同类竞争采用现有事务与快照CAS，确认的成功提交最多一个有效默认；跨实例冲突503后重试。没有新增乐观版本参数，最终成功提交为准。幂等重放result可能是首次执行的快照，客户端应以响应顶层当前state.defaults/列表刷新UI，不能用旧result覆盖当前状态。

三类引用和对应增删在同一SQLite事务及GCS CAS中完成，存储失败不确认成功，重启后保持。读取旧缺省或已失效的引用安全投影为null；不自动选择或改写历史，不把损坏引用迁移到别人记录。旧客户端不带isDefault、不调用defaults.set时行为不变。

## 客户端使用（防止默认覆盖已填内容）

- 默认地址：用于常用地址列表置顶/选址初始建议；不得覆盖GPS位置、用户已经选择的上车点/目的地或当前订单路线，不触发自动下单。
- 默认乘车人：仅新建叫车草稿首次打开、尚未明确选择乘车人时，客户端可选defaults.riderId并在提交时显式发送riderId。服务端order.create缺失/空riderId继续按本人处理，**不静默改用默认乘车人**；用户之后改默认不改已创建订单。
- 默认抬头：仅新申请初次打开且尚未选抬头时预选defaults.invoiceTitleId。invoice.apply仍必须显式titleId，不静默取默认、不自动开票；既有回执继续按原titleSnapshot显示。
- 默认记录被删或无默认：乘车人显示本人，地址/抬头显示未设置或让用户选择。切账号后完全使用新账号state，不复用前账号缓存。

## 联系页与电话边界（无新电话/聊天API）

已发布接口`order.company`与`operations.company`含 `{status,id,name,supportPhone,...}`；联系当前订单时优先该订单的company视图，非订单页可用本人当前operations.company。只有status=CONFIGURED且supportPhone为真实已维护的非空号码时，显示“公司客服电话”并允许用户手动打开系统拨号界面。DISABLED/UNCONFIGURED/null显示“客服电话未配置”，不制造电话号码；该配置不保证接通或实时人工在线。

现有`passenger_view.driver`只有mobileMasked，**不是可拨司机号**，不能给掩码添加tel链接，不额外暴露完整注册号、历史手机号、乘车人号码或假中继。未接隐私号时“联系司机”页应说明当前直接呼叫未配置，并提供已配置公司客服电话作为明确区分的入口。本轮无文字聊天、短信代发、隐私号服务或离线送达承诺；不把旧support.phone的null覆盖为虚构司机电话。

后端验收：本人引用与同类唯一、保存/编辑/删除、无默认兼容、双账号拒绝零副作用、幂等409、SQLite/CAS失败/重试/跨实例竞争及重启、历史订单/发票快照不变。前端独立接线与总管非作者验收后统一合并发布。
