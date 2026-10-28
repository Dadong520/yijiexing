# backend 二代接任短卡
协议ACK2026-10-26-v3；角色backend；真实threadId 01a182c6-c058-76e1-9584-38abfe813799；更新时间 2026-10-28T21:53:34+08:00。
当前任务ROLLNB-20261028-01接任检查已完成，状态IDLE，无产品开发任务。唯一coordinator/合并发布负责人01a17cc7-e0a2-7791-bf1d-4611c09bab22。context UNKNOWN/not_exposed，memory NORMAL；不得用额度替代上下文遥测。

## 基线与已发布事实
中央coordination为权威，旧退休卡已归档archive/2026-10-28/role-rollover/backend-final.md。
STATE记录当前main（含PR33回执）5096cb6729e9ef911fac19a281d31db4010ef542；PR32产品合并a542f6640681956f330e91bc67ecf2f62cfcbda8，已审产品7d6dc6014906c80be87b39f4433ec494ae067098。
只读核根源码/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027：branch codex/session-rollover-20261028，HEAD 5096cb6729e9ef911fac19a281d31db4010ef542，git status空。分别独立rev-parse复核：本地main=ff9f5a787fede48bb2ac1035c38d45ff543323b2；origin/main=5096cb6729e9ef911fac19a281d31db4010ef542。初次多引用git show合并显示相同提交，未分别解析便误记origin/main也为ff9；本次已纠正，未fetch/切分支/更新引用。
乘客1.1.15-experience/code17、司机0.3.14-cloud/code17已发布；API/admin pui-7d6dc60-test1均100%。无需重复部署，无待合并产品SHA。仅独立体验项目yijiexing-driver-demo-1023/us-west1；禁止原测试/生产。automatic/min0/max1/请求计费、不保活不自动暂停。

## 已确认规则与遗留
三默认仅建议新草稿，不覆盖GPS/已填/历史，不自动叫车开票。司机掩码不可拨，公司客服确认后系统拨号。BIZ04预约待出发不占即时/双角色单执行中/保留欠款门禁；BIZ05历史32类仅草稿人工记录；BIZ06新单目的地必填旧单同ID补填；BIZ07差额可抽佣仅扣一次余分平台且模拟；BIZ08三证60天提醒与审核替换/过期门禁；BIZ09每用户每发布版本公告一次可关。详细规则后续按DECISIONS/contracts定向读取，不重问已确认项。
高德1008/10008未过，不重索Key，运行时下发且JS安全码不下发；微信缺AppID；支付退款提现发票模拟；IM/中继/录音警方联动未接；OEM真机NOT_RUN；文档2.0余项及PMPOST未完成未集成，不能称三端真实全业务完成。

## 验证、归属与下一步
真实ID memory-role前后检查PASS（结构检查，非业务验收）。已读PUI release/FINAL-RELEASE-RECEIPT.json、INDEPENDENT_ACCEPTANCE.md并核SOURCE_BINDING/APK_PROOF/NATIVE_REVIEW入口及JSON有效。仅复用既有证据，本轮业务测试/云端回读NOT_RUN。
唯一写入：本短卡、tasks/ROLLNB-20261028-01.json、coordination/evidence/ROLL-20261028-01/backend-successor.json；未改产品/控制文件，未提交/发布，无自有运行资源或常驻任务。旧作者树只读；新写树与分支须root后续明确派定。待合并SHA无；接任记录由root验收整理。下一步IDLE，仅收到root具体任务后工作；不自动跨会话回信。
