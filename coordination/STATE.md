# 宜捷行当前检查点 2026-10-28T00:36:21.805537+08:00
协议2026-10-26-v3。中央/Users/dongge/Documents/New project 2/coordination唯一权威。唯一coordinator01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6，旧总管退休不派单/发布；上下文遥测UNKNOWN，按START_HERE/短卡恢复，不遍历旧聊天。

## 已发布，可供用户验收
用户明确要求合并发布，DLV01允许明示已知问题的独立体验版，不等同全项目最终验收。
PR28已合并main 1d04ff7ce64779fe9f9122513dd651f305093b79，与已审48d2ef4树完全相等；API和Android构建54e6d313对应目录同字节。实际回执evidence/PM-20261027-01/release-test-20261027/FINAL-RELEASE-RECEIPT.json。
仅独立项目yijiexing-driver-demo-1023/us-west1，禁止原eztrips。
- API https://yijiexing-demo.hbzjhs.xyz ：driver-demo-pm-54e6d313-dl900 100%，image946602a0c4809903477ac54ff0bc98aeb7e0b2c375ebecc5d739d75486d8997d。下载请求上限300→900秒，其余镜像/ENV/secret/SA/IAM不变。
- 后台 https://demo-admin.hbzjhs.xyz/ ：driver-admin-pm-48d2ef4-test2 100%，image48e1066b74413b532c6b1001ea5a4c46ba0b837335d7e800678c351e0cdb3043。原PASSWORD账号，无Google/IAP；session/RBAC/Origin/审计/限流保留。
- 司机0.3.13-cloud/code16、乘客1.1.13-experience/code15。两固定下载对象更新、旧包archive；独立备用HTTPS整包/hash/版本/包名/apksigner通过，原证书9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f不变，可覆盖安装。
自有域名/download和/download-passenger仍指同对象，但本网络整包慢/超时；快链https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download 与/download-passenger均约12秒完整下载。失败尝试单列未算PASS。
CloudRun automatic/min0/max1/请求计费，不保活/自动暂停，不扩IAM。35旧单发布前后ID/业务字段完全一致，root新增0；私有600备份/manifest/账号/Key/APK/缓存仓库外。

## 已验证范围和剩余
backend当前合并兼容13PASS；PMAD后端根非作者6、PMDEST后端根4和客户端非作者通过；frontend真实DOM四范围/参数/保存归档/只读、32模块HTTP生命周期/409/403/fresh订单详情PASS。Android20/59+build、Key0、原签名及独立完整HTTPS回读通过，mini62+静态检查/PR gate通过。
BIZ04待预约不阻即时，明确出发双角色最多一段执行中并保留欠款；不自动处罚/保证准时。BIZ05后台32类只草稿/手工记录，不自动运价/分账/扣款/扣分/发奖。BIZ06新单目的地必填、旧flag拒绝，历史单保留原ID补填完成。支付/退款/提现仍模拟。
高德官方11.2.100SDK已接，Key认证角色运行时下发不进源码/APK，JS安全码不下发。用户设置图与已发布APK的双包名/签名SHA1已独立核对一致，但乘客POI1008/地图10008仍FAIL，公共点成功校准/真实同单NOT_RUN；司机底图显不等于鉴权PASS，随版明示，勿重复索Key。
PMPOST83c7c2a8c3e7cda54f3539e77f11ca6e72cdaab4仅作者68测试，根非作者及客户端待做，未集成本版。WX缺AppID未微信编译/真机/发布；真实声音/OEM/部分设备GUI待测；旧DOC图NOT_REVIEWED。不能称三端全业务最终完成。

## 当前首批2.0实现与发布准备
产品817ed22bce503620d692e097a5f8ea996edf5fb2已集成根树/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027，branch codex/operations-v2-integration-20261028，base mainc3c876e。PMVO作者f1/PMVU作者87，根非作者7新HTTP+13兼容+真实后台DOM通过；Q1–Q6已修。APK乘客1.1.14/code16、司机0.3.14/code17构建/签名/Key0通过，frontend对root包身份与回执独立复核PASS。PMVR acceptancePASS；本轮尚未PR/上传/部署，当前上线仍上方旧版。
首批四闭环：公司客服电话/司机公司绑定与订单归属、公告草稿发布撤回/每用户每版本一次、地区即时预约竞价/模拟开票入口及API门禁、三证60天与有效旧证待审核/过期阻新单在途可完。后台新菜单平台运维→公告管理/公司与客服电话/乘客端功能配置；司机管理→资质到期提醒；32类改为历史草稿与人工记录。2.0共28组，余项不能称完成。BIZ07单次扣差额余分平台仅模拟；BIZ08审核通过替换；BIZ09每用户每版本一次，用户均确认。
仅root发布PMVR；fresh私有服务与IAM快照已取，接着备份数据/构建镜像/候选上传/PR合并及发布回读。唯一backend写树/Users/dongge/.codex/tmp/yijiexing-pmv-backend-20261028/apps/cloud-service，当前backend IDLE；frontend pmv-clients树IDLE。旧admin-drafts/PMPOST83c7只读未审未集成。新私有与APK不入Git。
GOV04使用可见独立backend01a168e2、frontend01a167c8会话；原内部子代理idle不再派单。Gemini小候选PMVG成功未进产品，大任务PMVO超时无代码、精确context已STOPPED并释放lease，沿用户模型未改全局，现idle。真实上下文UNKNOWN；根短卡已按可见压缩恢复。fixture/浏览器/构建均已退出，无自动化/后台常驻。
