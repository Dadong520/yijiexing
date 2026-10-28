# PMVO 首批运营后端实现与作者自测

任务PMVO-20261028-01；作者backend `01a168e2-22f7-70a2-af7f-4fb6be0ea695`；2026-10-28。唯一产品树 `/Users/dongge/.codex/tmp/yijiexing-pmv-backend-20261028`，分支 `codex/operations-v2-backend-20261028`，基线 `c3c876ef3359f8bc4bb306f515637e1fc72ca621`。产品提交与源码哈希见results.json/中央任务/短卡，不修改旧PMPOST或原环境。

本批四项已实现：公司客服与显式归属、有效公告发布撤回与每用户每版本回执、地区乘客功能草稿发布及服务端门禁、三证60天提醒与有效材料/待审替换分离。接口契约中央 `coordination/contracts/PMV-OPERATIONS-API.md` 已在首阶段写出，总管审核并通知前端；v1.1只澄清“未批准/驳回材料非待审即可纠正重提”，v1.2按总管集成检查统一附件响应source为REVIEW/ACTIVE（不再返回PENDING），并有回归。其余字段未隐式改变。前端按pending字段接入，不是另一个pendingReplacement字段。

## 实现

`live_operations.py` 使用原Service连接/事务及SQLite快照CAS。新增独立live_operations与live_announcement_acks表；不把旧32类自由文本草稿转成正式配置，不新增连接/提交/定时线程。AdminConsole仍以实际管理会话角色鉴权、console_actions指纹幂等及审计处理，新增配置操作只授config.write。原外层鉴权、Origin、限流、账户、金额及银行逻辑保留。

- 公司是服务端生成的稳定ID；名称、客服电话、启用状态有版本，司机归属必须管理员显式绑定，不能由司机写入或按演示organization猜测。公司停用保留关联并返回DISABLED、电话null。乘客只保存自己的选择，公开公司列表没有司机名册。新单首次指派记录公司，司机后来换公司不会重写历史；旧单不回填虚构公司。公司ID未接开户签约，不是扫呗账户。
- 公告保存只改draft，旧published在显式发布成功前继续生效；发布生成新版本，撤回立即清除可展示快照。有效期start包含/end不包含，按地区及MINIPROGRAM/PASSENGER_ANDROID受众筛选，默认仅小程序，不自动广播司机。读取state/login不插回执。客户端显式ack才竞争展示资格，同乘客+公告+发布版本唯一，两个客户端也共用次数。**同requestId重放特判返回firstDisplay=false，不重放缓存中的true**。旧版/撤回/过期拒绝，跨地区拒绝。这个回执不是物理显示或离线送达证明；CAS确认前不响应成功，丢失响应后重试不弹第二次，可能遗漏一次展示是明确限制。
- 地区功能为全国→大区→城市逐字段继承，默认instant/reservation/bidding/invoice均true。save不生效，publish才更新版本；省略字段继承、空对象恢复继承。已验证城市以外拒绝，无区县伪配置。新order.create按订单city校验，调用者无法用UI绕过；新invoice.apply按本人订单city校验，已存在模拟回执仍可幂等读取。关闭不影响旧单查询、取消、行程、付款或已创建竞价选择；继续呼叫视新操作，拒绝续叫不阻“结束用车”的选择。
- `qualifications.py` 在司机state/管理只读投影中提供version/pending/reviewStatus/daysRemaining/validity/canUpdate/reminder；展示字段按读取时计算，不随钱包审核保存。UTC+8有效期当天仍有效，60天起提醒和更新，长期/61天限制仅针对已批准材料；驳回/未批准材料非待审即可纠正。未知日期标UNKNOWN、允许补正，不假称已验证；旧APPROVED未知日期的新单策略保留原兼容行为，新提交日期严格验证。
- 新证只进pending，旧active日期/附件/APPROVED状态不被上传覆盖。审核中的旧证仍实时按有效期校验；到期阻新增接单/报价选择/预约出发/议价认领/上线，已接驾与行程可结束、晚支付可继续。新pending审核必须expectedVersion和submissionId，通过同事务替换，驳回保留旧材料；legacy顶层PENDING保留原审核入口。审核历史保留候选快照与actor。管理附件REVIEW优先待审，ACTIVE取旧有效附件，原所有权校验保留。

## Gemini / 反重力实际参与

本轮按GOV04和任务授权，backend独占内置CLI `/Applications/Gemini.app/Contents/Helpers/agy`，默认新上下文、未指定模型/修改设置/跳权限；短输入请求主要运营模块的无工具代码候选，明确90s。

精确conversation_id `4528dabc-8ed2-4cc7-97c5-1ad0e531506e`。CLI约102.55s返回print timeout，response为空且turn仍在进展，**未获得可集成代码，不能把SUCCESS包装字段算完成**。随后只向该ID发送停止请求，已读回STOPPED；总管亦独立核对已停止并回收登记。本角色没有杀用户hub或启动其他代理。证据gemini-prompt.txt、gemini-stream.ndjson、gemini-run.json、gemini-stop.ndjson。生成超时后由backend继续实现；本批代码不虚称Gemini作者成果或独立验收。

## 测试与修正记录

全部为本机临时HTTP/SQLite与MemoryCAS对象模拟器，无GCP、私有账户或真实资金。

1. 原证件日期4项回归PASS。早期运行曾因未加PYTHONPATH而导入失败，另一次编辑时缩进错误在执行前修正；保留导入失败日志，不计PASS。
2. 首轮运营专项实际执行22项PASS/4.489s，其中借用测试类导致另外9项旧草稿被发现重复执行；已改为模块别名引用，最终全套不重复统计。
3. 新证件替换9项PASS/1.590s，含61/60/59/0/-1、长期/缺失、UTC+8跨日、旧有效待审/驳回/通过、旧证审核中到期及行程/晚支付完成、预约出发重校验、legacy无active审核、CAS失败/丢响应重试。
4. 首轮全套258项：2失败+2错误。2条旧断言把新上传视为覆盖active并立即PENDING，与已确认BIZ08冲突；已改为检查reviewStatus及旧active仍APPROVED，并传审核候选版本。另2条独立ExtrasService夹具没有新增operations依赖/城市；已接真实LiveOperations默认继承和城市，**生产没有加hasattr绕过门禁**。原失败保留full-suite-initial.log。
5. 补充两实例公告竞争：一个200，一个CAS503，失败者同rid重试firstDisplay=false；待审/有效附件分开读取；地区关闭仍可结束续叫；旧缺失起始日期UNKNOWN；后台读投影不写入资金操作。
6. 修正后完整251项PASS/64.513s（full-suite-final.log）。最后把管理端证件投影明确放回只读admin_state、恢复钱包审核原始profile读取，再对最终冻结源码运行全套；该轮251项PASS/64.380s（full-suite-frozen.log）。之后按集成检查统一附件source=REVIEW/ACTIVE及对应回归，最终251项PASS/63.922s，退出0；见results.json及full-suite-contract-aligned.log。

运营专项还覆盖：每用户/发布版本一次、读取不消耗、同rid/新rid不二次弹、受众/地域/开始结束边界、草稿不改变发布、撤回/旧版本、RBAC403/版本409、公司跨身份隔离/历史归属/重启、地区各层继承及三类新单API拒绝、模拟发票关开/旧回执重读、配置并发版本、双服务CAS竞争、失败写回滚和丢响应恢复，以及公司维护前后模拟余额/流水不变。完整回归保留原订单/金额/退款/钱包/认证/隐私/接送距离/预约等测试。

## 验收边界与交接

作者自测PASS不等于非作者验收。总管早期独立HTTP探针是预备证据，尚须绑定最终提交SHA；本角色不把它算自己的最终验收。root统一收前端、非作者复核后合并/部署，本轮不发布。

GUI、双模拟器、真机、微信编译/发布、线上旧订单指纹/发布回读均NOT_RUN。本批不修高德现有1008/10008，不索取新Key。支付/退款/提现/开票仍模拟，BIZ07只确认后续配置/模拟口径，本批未开发真实分账、公司开户、奖惩、积分发放、资金池或群通知；无后台保活/自动暂停或云配置修改。

不提供物理弹窗送达保证；旧日期缺失只能提示补全，legacy原兼容门禁未自动收紧；旧订单历史公司未知保持未知。以上均在契约明确。所有本地测试服务器由测试tearDown关闭，Gemini精确上下文已停止，无后台续跑。

产品冻结提交：`0d9a32c62d1b3f3cb968ddf8b5863a158df0b434`，49文件源码哈希测试后回读一致；后续证据提交不改该产品树。

## v1.3 增量：跨地区订单开票标记

总管发现当前profile城市入口不能代表历史订单city门禁。本补丁只在passenger_view增加invoiceApplicationEnabled及invoiceApplicationRulesVersion，直接复用订单city的effective_features；新申请仍由原invoice.apply再次校验，不改金额/发票数据。布尔表示地区功能许可，原DONE/paid资格仍由客户端/服务端原逻辑判断；历史回执不受布尔关闭影响。契约已同步v1.3。

实际运营HTTP/SQLite/CAS专项16项PASS/3.420s，包含A/B开关相反、乘客切城市、两城开关反转、API一致、历史模拟回执仍可读、原始passenger_orders读前后相等。证据invoice-city-projection-{result.json,tests.log}。原251项全套仅绑定0d9a32c产品基线，本小补丁未重复全套，未声称252全套通过。补丁源文件哈希见独立JSON，精确提交SHA见中央本任务与短卡；冻结SHA非作者及GUI仍NOT_RUN。

## v1.4 增量：通过审核说明不丢弃

通过时可选reason按严格字符串、去首尾空白后0..500字校验，保存auditRecords.reason和audit日志显式reason/approved；rejectionReason/reply在通过时仍清空。驳回继续必填2..500字。未更改审核权限、日期、材料替换、公司、费用或发票。契约同步v1.4。

11项证件定向HTTP/SQLite/CAS回归PASS/1.930s：非空说明保存并跨重启留存、通过时空/省略兼容、非字符串/超长400、驳回空或一字400及原审核生命周期。日志approval-note-tests.log，文件SHA/基线见approval-note-result.json。未重复无关251项，也未宣称新的冻结SHA已独立验收或上线。
