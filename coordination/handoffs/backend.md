# backend 当前短卡
ACK2026-10-26-v3；真实threadId01a168e2-22f7-70a2-af7f-4fb6be0ea695；updatedAt 2026-10-28T19:35:49+08:00。memory NORMAL，context UNKNOWN/null/not_exposed。唯一coordinator01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6独占STATE/BOARD/SESSION/DECISIONS和合并发布；GOV04独立会话，按中央入口恢复，不翻旧聊天。

当前PUIB-20261028-01作者完成，待非作者/前端集成，尚未发布。唯一产品写树/Users/dongge/.codex/tmp/yijiexing-pui-backend-20261028，branch codex/passenger-ui-backend-20261028，base 8e2d655bd58d33241d79d6da3ad82f29cdec7894，产品冻结提交 8c9db0a4deee34db0ba00366251ee059256d9b67。开始clean，旧pmv-backend/PMPOST只读，不在旧树切branch/覆盖。允许仅apps/cloud-service/、本任务、contracts/PUI-DEFAULTS-API.md、evidence/PUI-20261028-01/backend/和此卡；仅选择本人路径提交，不反向覆盖中央控制。
契约v1首阶段已写中央，root可转前端：state.defaults含favoriteId/riderId/invoiceTitleId；defaults.set(kind FAVORITE/RIDER/INVOICE_TITLE,id或null)；三类save可选isDefault严格bool，默认列表置顶且isDefault只投影。显式同类单默认，未带标记编辑保留/新增不自动默认；false仅清本条，删除默认清空不选下一条。本人引用校验，跨账号/坏ID零副作用，重启持久。失效旧默认安全投影null/读取不改库。
服务端仍不隐式采用默认乘车人或抬头，客户端仅新草稿/新申请初次建议；缺riderId仍本人，invoice.apply仍须显式titleId；不覆盖GPS/已填行程、不自动叫车/开票。历史及在途订单、回执快照不变。只复用原passenger_extras/账号事务/CAS；无新增资金表/连接/线程。
联系页建议在契约：优先order.company/当前operations.company有效supportPhone，明确公司客服；未配置或停用不伪造号码。driver.mobileMasked不可拨，不新增完整注册号/假中继/文字聊天。driver_public、passenger_view、passenger_create_order与原invoice.apply分支逐字节未改，protected-behavior-binding.json留SHA。

作者新增默认13项HTTP/SQLite/MemoryCAS PASS/3.209s；全套267项PASS/78.729s，退出0；首次运行无失败。含三类设置编辑删除、无默认旧请求、两账号越权/坏类型零副作用、重复rid不重施、重启、保存删除失败回滚/丢响应一次、双实例不同类别CAS竞争重试不丢项、同类并发单默认、历史/在途快照及显式本人/抬头兼容。50文件测试后hash一致，源码及契约绑定source-binding.json；中央真实ID门禁/diffcheck PASS（结构非业务验收）。
证据中央evidence/PUI-20261028-01/backend/{REPORT.md,results.json,defaults-tests-initial.log,full-suite.log,source-binding.json,protected-behavior-binding.json}。后续证据提交产品树不变；最终待合并HEAD读中央任务，不为自引用反复提交。

Gemini本轮frontend独占，本人零调用/未派代理。Android/mini接入/GUI、真实拨号、真机/微信编译发布、线上数据/云部署、冻结SHA独立验收NOT_RUN；root统一验收合并发布。本轮没有APK或部署，不改云/金额/预约/目的地/电话隐私。
已发布PMV首批/1.1.14与0.3.14已由PR30/31合并发布，当前main8e；旧短卡未发布结论已废弃，具体当前线上看STATE。PUI本次仍未发。所有作者临时服务器测试结束，无ADB/Chrome/云/Gemini后台占用，无保活/自动暂停。backend现IDLE，等具体缺陷；不主动跨会话发消息。
