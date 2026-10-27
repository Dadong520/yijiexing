# 宜捷行独立体验测试版发布回执

本次交付用于用户测试验收；不是全项目最终验收。代码PR28已合并，产品SHA 1d04ff7ce64779fe9f9122513dd651f305093b79。后端/客户端构建54e6d313、后台修复48d2ef4与合并树的字节绑定见merge-equivalence.json。

- 司机0.3.13-cloud/code16：快速入口 https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download 。
- 乘客1.1.13-experience/code15：快速入口 https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download-passenger 。
- 后台 https://demo-admin.hbzjhs.xyz/ ，原账号密码，无Google前置登录。
- 原自有域名download/download-passenger亦指向同固定对象，但本网络完整下载慢/超时；备用同服务直连两包分别约12秒完成，大小/hash/包名/版本/原签名均独立PASS。详HTTPS-APK-READBACK.md。

API driver-demo-pm-54e6d313-dl900、admin driver-admin-pm-48d2ef4-test2均100%。API镜像946602a0…、admin48e1066b…；具体digest查对应build/readback。API追加将下载请求上限300→900秒，镜像/业务代码/ENV/secret/IAM/scaling不变，旧慢路失败未算PASS。CloudRun automatic/min0/max1/请求计费，未保活或自动暂停。两旧固定APK已归档到同桶distribution/archive/20261027-before-54e6d313/，需要回退由coordinator按旧generation/签名核对后恢复。

本版包含：目的地必填/拒绝旧flag、历史单补填保留；等待预约不阻即时单与出发互斥/欠款门禁；尾号及结算显示；后台32类配置草稿与手工记录、fresh订单详情。草稿不启用扣款/扣分/分账/发奖。PMPOST排行/电话投影未集成。

验证：backend非作者合并兼容13PASS，frontend真实DOM/32模块HTTP生命周期/权限/版本冲突及fresh详情PASS；两Android20/59单测+构建、同签名和四私有值扫描PASS；PR gate通过；两个稳定域名health200、后台静态JS与48已验source hash一致；发布前后35旧单ID/业务字段完全保留，coordinator新增0单。新最终包全GUI/真机OEM仍待用户与后续测试。

已知未通过：乘客高德1008/10008，公共点成功校准NOT_RUN；微信缺AppID未发布；OEM声音/后台等不扩大声明。详PMMAP原独立记录，不把底图显示当鉴权成功。支付/退款等仍模拟。资源已清理：frontend无设备/服务器/下载进程，root上传已结束；APK/SQLite/private manifest均仓库外。
