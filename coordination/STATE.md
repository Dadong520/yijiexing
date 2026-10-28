# 宜捷行当前检查点 2026-10-28T21:16:47.987400+08:00
协议2026-10-26-v3，中央/Users/dongge/Documents/New project 2/coordination唯一权威。coordinator01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6独占派单合并发布，旧总管退休。context UNKNOWN/not_exposed；PUI恢复检查点明确。

## 当前线上已更新（PUI发布回读完成）
产品PR32已合并main a542f6640681956f330e91bc67ecf2f62cfcbda8，产品目录与已审7d6相同。乘客1.1.15-experience/code17，司机保持0.3.14-cloud/code17，原签名可覆盖安装。API driver-demo-pui-7d6dc60-test1/admin driver-admin-pui-7d6dc60-test1均100%，user-test同新revision。仅独立项目yijiexing-driver-demo-1023/us-west1，禁止原eztrips测试/生产。
APIhttps://yijiexing-demo.hbzjhs.xyz；后台https://demo-admin.hbzjhs.xyz/原PASSWORD账号，无Google/IAP前置；真实登录页源码同字节/未登录state401。快链https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download（司机）及/download-passenger（乘客）；新版乘客整包直连8.25秒/hash/包名/版本/原证书PASS。旧乘客包已归档再代次保护提升，司机对象未改。原自有域名同路径保留。
CloudRun automatic/min0/max1/请求CPU计费/API900秒，ENV/secret/SA/IAM/安全与伸缩注解逐项相同，无保活/自动暂停。发布前后39旧订单全部存储字段一致，无新增；root云业务POST0。私有备份/Key/APK不进Git。真实回执evidence/PUI-20261028-01/release/FINAL-RELEASE-RECEIPT.json；前版PMV回执保留不改。

## 本轮PUI独立验收PASS，已发布
用户UI设计-乘客端.docx 13图/3文字；首版联系页+电话，不做IM；三类持久默认已确认。Android完成预约状态栏/整页更多设置保草稿/详情路线与首屏金额取消/理由选择/筛选顺序及紧凑卡片/个人中心分组/地址乘车人抬头空态默认/安全联系人/回执紧凑展开/投诉正文回复优先。默认仅新草稿建议，不覆盖GPS/已填/历史，不自动叫车开票；司机掩码不可拨，中继未接，公司客服确认后ACTION_DIAL。
固定产品7d6dc6014906c80be87b39f4433ec494ae067098，根树/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027/branch codex/passenger-ui-intake-20261028。backend作者8c9/59、frontend d02/d06与Q1修正f3已集成；root乘客版本1.1.15-experience/code17，司机不动。PR32已合并发布，见上方实际回执。
非作者root默认HTTP6组、兼容13组、运营7组；后端组件哈希与7d6完全相同，实际执行旧SHA保留。root31Android单测/QA与正式构建PASS；mini74在d02执行、组件等同。真实隔离模拟器13组页面交互/长内容/空态通过，取消理由实际提交、3默认保存、系统拨号页未拨出；Q1金额取消需下滑已f3修正并87截图首屏复验。backend PUIA独立审root版本/包名/原HTTPS/原证书/4配置Key精确解压扫描0通过，未自验本人defaults。evidence/PUI-20261028-01/{NATIVE_REVIEW,SOURCE_BINDING,APK_PROOF,INDEPENDENT_ACCEPTANCE}与packaging-review。PUIR精确SHA门禁PASS。正式APKhash798fbb0f9fc6796190ddaa36eb8f71f7081d196d0785867782da442d38996379；APK/DB/Key外置不进Git。
发布前已从权威GCS原子读备份39单/quick_checkOK，0600外置；初次固定代次404因实时替换，后改同一媒体响应generation+MD5绑定成功。无root云业务POST/新增单；发布后严格比较旧业务字段，定位实时并发单独说明，不假定旧38数。

## 业务与已知限制
BIZ04预约待出发不占即时，明确出发且双角色单执行中/保留欠款门禁，不自动处罚或承诺准时。BIZ05历史32类仅配置草稿/人工记录。BIZ06新单目的地必填/拒旧flag，旧单同ID补填。BIZ07差额仅可抽佣扣一次/余分平台，仅模拟。BIZ08三证60天提醒，新证审核通过解除；旧有效待审可接，过期阻新增在途可完。BIZ09每用户每公告发布版本只弹一次可关。用户均确认勿重问。
后台平台运维→公告管理/公司与客服电话/乘客端功能配置；司机管理→资质到期提醒。原32类在配置与治理→历史草稿与人工记录。PUI无新后台导航模块，只增乘客默认数据接口。
高德1008/10008最后未通过，控制台绑定/双包名/原证书已核一致，不重索Key；Key仅运行时下发，JS安全码不下发。PUI地图本地DNS隔离，未证明真实SDK成功。微信缺AppID，源码74不等于微信编译/真机/发布；支付退款提现发票模拟；未接IM/电话中继/录音警方联动。OEM实机NOT_RUN，文档2.0余项/PMPOST未完成未集成，不能称三端真实全业务完成。

## 协作与资源
GOV04可见独立backend01a168e2-22f7-70a2-af7f-4fb6be0ea695/frontend01a167c8-a5eb-77e3-b77e-214927a029da；本轮backend唯一写树pui-backend/apps/cloud-service，PUIA复核后IDLE；frontend pui-clients作者完成，建议本阶段后换新对话，不开新大任务。旧pmv树dirty控制快照/PMPOST均只读不覆盖。Gemini两短context9b453ce4/9cada792成功，root精确result/hash已核，未改模型全局，lease释放。原内部代理不派单。
用户授权ADB仅本次隔离emulator5582；localhostTLS/合成数据，不碰真机。模拟器调试中断后显式端口恢复，失败中间帧不作为PASS。GUI结束已停止fixture/emulator；没有后台常驻/自动化。当前发布与回读全部完成，仅回执归档PR待合并；无再次发布。等待用户体验反馈后定向处理，不自行常驻。
