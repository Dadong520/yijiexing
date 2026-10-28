# backend 当前短卡
ACK2026-10-26-v3；角色backend真实threadId01a168e2-22f7-70a2-af7f-4fb6be0ea695；updatedAt 2026-10-28T13:11:42+08:00。memory NORMAL，contextUsedPercent null/measurementSource not_exposed，实际UNKNOWN。唯一coordinator01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6；其独占STATE/BOARD/SESSION/DECISIONS与合并发布。用户GOV04已恢复本侧栏独立会话，按中央入口+真实ID门禁，不冒用旧backend_review ID。

当前PMVO-20261028-01作者完成，等待冻结SHA非作者验收，非产品已上线。唯一产品树/Users/dongge/.codex/tmp/yijiexing-pmv-backend-20261028，branch codex/operations-v2-backend-20261028，base c3c876ef3359f8bc4bb306f515637e1fc72ca621，产品提交 0d9a32c62d1b3f3cb968ddf8b5863a158df0b434。后续证据提交只改allow_paths协调证据、不改产品；pendingMergeSha以中央本任务最终值为准。旧PMPOST83和原集成树只读，未碰原测试/生产/云。

四闭环实现：live_operations独立持久表，保留旧32类DRAFT；公司维护电话/启用、司机显式companyId、乘客本人选择，新单公司历史固定/旧单未知不回填。公告draft/save→publish→withdraw，范围/受众/日期过滤，state不耗次数，ACK唯一用户+公告+发布版本；同rid缓存重放特判false，双实例CAS失败重试不再firstDisplay。地区功能全国/大区/城市逐字段继承，未配默认旧功能true，发布才门禁新下单和新模拟开票，历史查询/取消/行程/已开回执继续；拒续叫不阻结束用车。
证件60天提醒/更新，pending保留active有效材料；通过原子替换，驳回保留旧证，现代审核强制expectedVersion+submissionId，legacy PENDING仍可审核。旧证过期阻新接单/预约出发/上线，在途/晚支付可完成。UNKNOWN日期明确补正，保留既有legacy兼容（不伪称真实验证）。root发现驳回长期/61天canUpdate不一致已修并加回归；管理员只读投影不写入钱包流程。
契约中央contracts/PMV-OPERATIONS-API.md v1.2已写入产品提交：pending字段不是pendingReplacement；附件查询/响应source都是REVIEW|ACTIVE，避免待审图误标当前图。root已通知frontend接线，本人无跨会话主动发送。

实测251项全套PASS/63.922s，最终log full-suite-contract-aligned.log；49文件hash测试后不变，source-binding.json绑定产品SHA和Git树。13个原改动/新文件+契约共14路径；资金/会员/认证等旧测试保留。首轮258有2旧BIZ08断言+2extras夹具错误已修，旧FAIL日志保留；曾重复发现9个旧TestCase已去除，不计入最终251。git diffcheck、中央真实ID门禁PASS（结构非业务验收）。root早期独立HTTP探针通过仅预备，尚需绑定最终SHA，本角色不自验收。
Gemini本轮独占已结束：内置agy默认新context/90s，无模型覆盖/跳权限；精确ID4528dabc-8ed2-4cc7-97c5-1ad0e531506e超时无候选代码，随后已读回STOPPED，root独立核对/登记idle。本批主要代码由backend补实现，不伪称Gemini作者；证据backend-implementation/gemini-*.ndjson及run.json。无其他代理。

证据中央evidence/PMV-20261028-01/backend-implementation/{REPORT.md,results.json,source-binding.json,full-suite-contract-aligned.log}。BIZ07单次扣差额/余分平台已确认，仅配置模拟，本任务没启真实钱/奖励/开票/签约/群通知。BIZ08/09按上述实现。GUI/双模拟器/真机/微信/线上数据/发布/冻结SHA非作者验收NOT_RUN；高德1008/10008仍按STATE，不在本轮修复。
资源：所有作者临时HTTP/SQLite测试已退出，无ADB/Chrome/云发布/Gemini后台占用，不保活/不自动暂停。根预置dirty STATE/BOARD/DECISIONS/SESSION仍原样保留，不一起暂存。下一步总管收代码/前端并独立验收、统一合并发布；backend IDLE等待具体缺陷，勿重跑旧全套或擅自部署。
