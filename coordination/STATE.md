# 宜捷行当前检查点 2026-10-28T14:04:07.949249+08:00
协议2026-10-26-v3。中央/Users/dongge/Documents/New project 2/coordination唯一权威。coordinator01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6独占派单/合并/发布，旧总管退休。context UNKNOWN/not_exposed，按START_HERE恢复。

## 当前已发布，可供验收
修改意见2.0首批已由PR30合并main 7402b117d55bd4d0f1404a5ae2ff0c1cf2e8e0a5，产品目录与已审817ed22bce503620d692e097a5f8ea996edf5fb2相等。真实回执evidence/PMV-20261028-01/release/FINAL-RELEASE-RECEIPT.json。用户DLV01授权已知问题明示的独立体验版，不是28组全量/全项目最终验收。
仅yijiexing-driver-demo-1023/us-west1，禁止原eztrips测试/生产。
- API https://yijiexing-demo.hbzjhs.xyz ，driver-demo-pmv-817ed22-test1 100%，imaged074a2d4dec29113b767803395de94e0c7953a716c9abb3b38383f0ac1763bbe。
- 后台 https://demo-admin.hbzjhs.xyz/ ，driver-admin-pmv-817ed22-test1 100%，imagecbc43c3f1d64c9dc42c428c5aecdcd99a25eb2963c9e17d0b061a9f45e1d9224。原PASSWORD账号，无Google/IAP前置；session/RBAC/Origin/限流/审计保留。真实线上登录页已打开，静态代码与构建同字节，未登录state401。
- 司机0.3.14-cloud/code17；乘客1.1.14-experience/code16，原签名可覆盖安装。两固定对象已更新/旧包归档；快链完整HTTPS/hash/包名/版本/apksigner通过。https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download 与 /download-passenger；原自有域名同路径保留，之前本网络自有域名整包慢，优先快链。
CloudRun automatic/min0/max1/请求CPU计费；不保活、不自动暂停。ENV/secret/SA/IAM/安全及缩放注解/API900秒均与发布前保持。当前38旧单ID/除实时定位和updatedAt外全部字段一致；1单客户端位置并发刷新、没有丢单，root云业务POST/新增订单0。私有备份/账号/Key/APK/cache不进Git。

## 本版入口、规则及验收
首批4项：公司客服电话/司机显式公司绑定及订单公司快照；公告草稿发布撤回/每用户每版本一次可关；地区乘客即时预约竞价与模拟开票发布开关/API新请求门禁；三证60天提醒与有效旧证更新待审/过期阻新单在途可完/通过替换。
后台平台运维→公告管理、公司与客服电话、乘客端功能配置；司机管理→资质到期提醒。原32类移名配置与治理→历史草稿与人工记录。
根非作者7新HTTP(SQLite/CAS/重启/并发/权限/409/历史)、13兼容、8真实隔离DOM通过。作者修Q1驳回更新/Q2待审图标签/Q3总览计数/Q4跨城逐单invoice/Q5公告busy释放/Q6审核说明留审计后复核通过。乘客24/司机63+双build、mini72/admin4；root包签名/hash/Key0，frontend独立PMVA包装回执复核PASS。真实数据克隆27原表内容不变，仅加2运营表。PR30 CI通过。
BIZ04待出发预约不占即时，明确出发双角色最多一段执行中，保留欠款门禁，不自动处罚/保证准时。BIZ05旧32类仅草稿/手工记录，不自动扣款/扣分/发奖。BIZ06新单目的地必填/旧flag拒绝，历史单同ID补填完成。BIZ07差额仅在可抽佣扣一次/余分平台，仅模拟配置；BIZ08三证60天可更新，审核通过解除，旧有效待审可接，过期阻新增在途可完；BIZ09每用户每公告发布版本一次可关，登录读取不等于微信离线通知。用户均确认勿重问。

## 已知问题和后续
高德官方11.2.100已接，Key角色运行时下发不进源码/APK，JS安全码不下发。用户控制台绑定与双包名/原证书已独立核对一致；最后POI1008/地图10008未通过，成功公共点校准/真实SDK同单NOT_RUN，本版未宣称解决，勿重复索Key。
微信缺AppID，源码72PASS但微信编译/真机/发布NOT_RUN；新版Android设备GUI、实际拨号/OEM部分验证未做。支付/退款/提现/发票均模拟；资金分账、积分/营销等余项待分批，文档共28组不能称全部已完成。PMPOST83c7仅作者68测试，未非作者复核/客户端，未集成本版。旧DOC图片NOT_REVIEWED。

## 源、协作和资源
根树/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027，branch codex/operations-v2-integration-20261028；产品817，PR30头ec666c5，已合并7402b11，后续仅发布回执docs。唯一backend写树/Users/dongge/.codex/tmp/yijiexing-pmv-backend-20261028/apps/cloud-service，作者f1f9128；frontend pmv-clients树作者87aaffc，均IDLE；旧admin-drafts/PMPOST只读不并改。历史releases/frozen不改。
GOV04沿用户偏好可见独立backend01a168e2-22f7-70a2-af7f-4fb6be0ea695与frontend01a167c8-a5eb-77e3-b77e-214927a029da协作，不默认内部子代理。Gemini PMVG短候选成功/根11校验但未进产品；PMVO大任务超时无可用代码，同context4528dabc已核STOPPED/lease释放，现IDLE，未改用户模型/全局。构建/上传/本地fixture退出，临时IAB页已关，原Chrome未动；无后台自动化/常驻工作。等待用户体验反馈后定向修复或下一批。
