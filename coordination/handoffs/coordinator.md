# coordinator 当前短卡
ACK2026-10-26-v3；真实会话01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6；当前PUI-20261028-01/PUIR。context UNKNOWN/not_exposed，NORMAL；中央coordination权威，旧总管退休，root唯一合并/发布。

## 当前已发（不要重复发布旧版）
PR30产品main7402b11、PR31回执main8e2d655bd58d33241d79d6da3ad82f29cdec7894已合并。API/admin pmv-817ed22-test1均100%，user-test快链同revision。司机0.3.14/code17、乘客1.1.14/code16，两固定APK提升/旧包归档，原签名/HTTPS整包hash回读PASS。详细证据PMV/release/FINAL-RELEASE-RECEIPT.json，回退OPERATIONS.md；不重复全套测试。
仅项目yijiexing-driver-demo-1023/us-west1，禁原测试/生产。ENV/secret/SA/IAM/PASSWORD/Origin/RBAC/automatic/min0max1请求CPU计费/API900秒不漂移，不保活/自动暂停。上次38旧单ID/业务字段保留，一单仅定位并发更新；下次备份重新数，不假定仍38。Key/私有账户/DB/APK/缓存不进Git或消息。
高德1008/10008最后未通过，控制台身份/原签名已核一致，不重索Key；真实SDK同单未成功验收。微信缺AppID未微信编译/真机/发布。支付退款提现/发票模拟；积分营销未真实接入。PMPOST83c7只作者测，未非作者审/未集成。

## PUI文档和已确认范围
UI设计-乘客端.docx下载副本已读，891567bytes/hashf95baea0b299883954aa86aa3ebd817d53444a4e384c7ee80d677d230ea3a2cc。3段文字/13png（不是全新视觉稿，是当前Android13页修改意见），源编号缺5/6不编。外部/Users/dongge/.codex/tmp/yijiexing-passenger-ui-intake-20261028/source.docx、manifest/text/word/media/contactsheets；中央PUI证据SOURCE_MANIFEST、SOURCE_TEXT、REQUIREMENTS、ACCEPTANCE_PLAN与frontend-review/REPORT（前端13原图逐张已看）。root已看两contact全图。
目标：首页预约状态明显、更多设置独立子页保草稿、卡片与少量辅助色；详情路线首要/ID费用紧凑、取消固定理由+其他；筛选全部/进行中/完成/取消、订单卡；个人中心分组、资料卡紧凑+空态/默认；回执选择/记录紧凑；投诉正文回复优先。
PUI01用户已确认：联系页和电话入口，不做IM；保存默认地址/乘车人/抬头。默认仅新建初次建议，不覆盖已填/历史/实时定位；司机仅mobileMasked/隐私号未接，不拨掩码不虚构直拨，可用实际配置公司客服确认ACTION_DIAL。已告知此边界。安全联系人仍本机账号隔离不云同步。BIZ04-09全部保持，尤其预约并发/目的地必填/金额版本/逐单invoice/模拟边界。PUIF发现mini假“隐私号正在连接”，本轮限定修正该入口和适用tests，不扩mini整体UI/微信发布。

## 当前源码与验收检查点
根树/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027，branch codex/passenger-ui-intake-20261028，产品HEAD7d6dc6014906c80be87b39f4433ec494ae067098；已集成PUIB8c9/59、PUIUd02/d06/f3、root乘客1.1.15/code17。未push/PR/发布。
root非作者默认HTTP6/兼容13/运营7、Android31+双构建，原生13组UI/真实交互PASS。旧组件测试按SOURCE_BINDING复用实际执行SHA，Q1首屏摘要f3已87图复验。backend独立PUIA包装/原签名/HTTPS/Key0 PASS。中央NATIVE_REVIEW/APK_PROOF/INDEPENDENT_ACCEPTANCE及packaging-review。PUIR精确7d6门禁PASS。APKhash798fbb0f9fc6796190ddaa36eb8f71f7081d196d0785867782da442d38996379，路径外部pui-release-build/passenger/_app/outputs/apk/debug/app-debug.apk。QA包不上传。
## 下一步与资源
当前云镜像仍pmv817/test1，两者100%；正式乘客仍1.1.14。私有puir-before-api/admin与IAM已读备份、GCS原子39单备份成功（data-before），原始数据在private0600，未云写业务。
外部pui-release-stage-20261028/api与admin镜像暂存字节核准，未CloudBuild/部署/上传。gcs_transfer/promote_apks已适配仅乘客和PUI新对象前缀，旧PMV回执不动。generation固定下载404因实时更新，backup_current媒体响应generation/MD5原子成功。下一步证据提交→PR/CI/main组件等同→root唯一CloudBuild/无流量候选/回读/100%→归档旧乘客包及新包提升→完整HTTPS/hash/签名/39单业务字段/ENV/IAM/缩放比对→真实回执PR。
本次隔离UI ADB授权已记QA_ISOLATION_PLAN，显式5582/5583恢复中断后完成62-105图。安全联系人/默认/取消/拨号仅合成数据；未实际拨出/云造单。fixture/emulator已停止，构建完成，无本地常驻/自动化。backend PUIA后IDLE；frontend不接大任务，HANDOFF_RECOMMENDED；Gemini两精确context/hash SUCCESS核准、lease释放，不并发CLI。
