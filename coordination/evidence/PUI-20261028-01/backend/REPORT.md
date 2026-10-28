# PUIB 默认项后端与联系页边界

任务PUIB-20261028-01；backend `01a168e2-22f7-70a2-af7f-4fb6be0ea695`；2026-10-28，Asia/Singapore。唯一写树 `/Users/dongge/.codex/tmp/yijiexing-pui-backend-20261028`，分支 `codex/passenger-ui-backend-20261028`，基线main `8e2d655bd58d33241d79d6da3ad82f29cdec7894`。开始时clean，旧pmv-backend/PMPOST只读，未改动。

## 已实现

三类默认显式持久化在当前登录乘客的既有passenger_extras JSON，分别为favoriteId、riderId、invoiceTitleId，每类最多一个。通过 `defaults.set` 或三种save操作的可选严格布尔isDefault设置；isDefault缺省不改变默认，新建第一条也不自动默认。编辑默认记录保留引用，false只清除本条默认，不清除别人已选的另一条；删除默认清除引用，不擅自选下一条。

state与登录/action当前状态增量返回defaults，各列表投影isDefault并把默认项置顶；存储中的记录不写入重复默认标志。无默认旧数据为三个null，失效/跨账号的旧引用只安全投影null，读取不写回，不从旧行标记猜默认。严格只验证本人对应集合中的引用，跨账号/不存在ID404，非法kind/ID/附加owner字段400，失败不保存任何业务修改。

默认地址仍存既有profile.favorites，默认引用存在extras；favorite.save/remove在既有外层SQLite事务内同时维护两者，SnapshotCoordinator确认CAS后才响应，失败全回滚。乘车人/抬头与默认引用在同一extras记录内维护。未新增数据库连接、后台线程、认证入口或云服务。

重复requestId不重复设置/删除，改payload重用rid为409；同类竞争最多一个默认，跨实例冲突503后重试保持别的类别已提交修改。幂等result可能是首次执行快照，客户端应使用action响应顶层当前state刷新，不用旧result覆盖已更新默认。

## 保护原流程与历史

- 默认仅供客户端新草稿/新申请初次预选，不覆盖GPS、用户已填写的起终点/乘车人/抬头，不触发自动叫车/开票。
- `passenger_create_order`保持原代码：缺失riderId仍本人，不静默换默认乘车人；选择他人乘车必须显式发送本人记录ID。
- `invoice.apply`保持原代码：仍需显式titleId、本人已完成且已支付订单及订单城市开票门禁。无titleId不自动用默认抬头，已有回执titleSnapshot不受编辑或删除抬头影响。
- `driver_public`与`passenger_view`原方法均未修改；没有增加完整司机注册号或中继号。方法及原invoice.apply分支的基线/新源码逐字节相同，SHA在protected-behavior-binding.json。
- 原金额/费用确认/预约/目的地规则/权限/幂等/CAS未改。历史和进行中订单的乘车人、坐标、路线、金额及回执快照不反向同步默认或资料编辑。

## 联系页建议（没有新电话或聊天API）

契约 `coordination/contracts/PUI-DEFAULTS-API.md` 已在首阶段固定，root负责转前端。联系当前订单优先 `order.company`，非订单页可用当前 `operations.company`。只有CONFIGURED且supportPhone为已维护的非空号码才提供明确标注的“公司客服电话”入口，用户手动打开系统拨号；停用/未配置则说明缺配置。

driver.mobileMasked不是可拨号，不能tel掩码、拿公司电话冒充司机直线或补全注册号。未接隐私号时联系页说明直接联系司机未配置；可另列已配置公司客服，不能承诺接通或实时人工在线。用户本轮确认无文字聊天；没有短信代发、离线推送或假电话中继。既有support.phone仍null，前端按已发布operations公司字段读取实际配置。

## 实测与证据

专项命令：`PYTHONDONTWRITEBYTECODE=1 PYTHONPATH=apps/cloud-service python3 -m unittest discover -s apps/cloud-service/tests -p test_passenger_defaults.py -v`。

13项真实本机HTTP/SQLite/MemoryCAS专项PASS，3.209秒，首次运行无失败，覆盖：

1. 三类显式同类唯一、旧客户端省略isDefault及首次保存不自动默认、默认置顶。
2. 编辑保留，false只清本条；删除默认清除但不选择剩余记录。
3. 双账号set/edit/delete拒绝、坏类型/ID/非法owner字段与标记拒绝，业务表前后完全一致。
4. 相同rid不重施旧默认、不重复删除；改payload重用rid409。
5. 三类SQLite/CAS重启保留、账号切换隔离；失效旧引用安全读取且不改持久原文。
6. 保存默认及记录写入失败503全回滚；丢响应重试只生成一次；删除失败不清默认。
7. 两实例不同类别竞争CAS一个200一个503，失败者重试保留两项；同类并发仍只有一个默认。
8. 默认不隐式替代本人/自动开票；在途默认变更和编辑/删除不改canonical订单原文，已支付后模拟回执保留原抬头快照。

专项日志defaults-tests-initial.log；完整兼容回归267项PASS/78.729s，退出0，见results.json/full-suite.log。所有测试仅本机临时服务器/账号与MemoryCAS，无GCP、供应商或原环境访问。读前后source-binding.json冻结文件哈希一致后才提交。中央真实ID门禁及git diff --check均须通过，结构检查不是业务验收。

## 尚未运行与交接

作者自测不等于独立验收。Android接线/设备GUI、系统实际拨号、微信编译/发布、线上数据与云发布均NOT_RUN，由root协调前端与非作者复核后统一合并发布；本轮不新增APK或自行发布。没有真实支付/发票或文字聊天能力声明。

Gemini CLI本轮归frontend独占，本角色没有调用或再派代理。所有作者临时测试服务由tearDown退出，无ADB/Chrome/云配置、保活或自动暂停操作。最终提交SHA以中央task/短卡及结果回执为准，原PMV已发布事实按STATE，不把旧未发布短卡继续沿用。

产品冻结提交：`8c9db0a4deee34db0ba00366251ee059256d9b67`；50文件测试后哈希一致，后续证据提交不改变此产品树。
