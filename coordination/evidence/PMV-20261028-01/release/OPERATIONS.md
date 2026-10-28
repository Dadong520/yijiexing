# 首批2.0独立体验发布运维

仅项目 yijiexing-driver-demo-1023 / us-west1；coordinator唯一发布。固定产品817ed22，PR30合并7402b117d55bd4d0f1404a5ae2ff0c1cf2e8e0a5。源目录字节相等证据merge-proof.json；Cloud Build SUCCESS与上下文manifest均固定。API继承原Python运行时；新模块均标准库。后台按prepare_browser_build生成。

新API driver-demo-pmv-817ed22-test1，digest d074a2d4dec29113b767803395de94e0c7953a716c9abb3b38383f0ac1763bbe；新后台 driver-admin-pmv-817ed22-test1，digest cbc43c3f1d64c9dc42c428c5aecdcd99a25eb2963c9e17d0b061a9f45e1d9224。均100%，user-test快链指新revision。所有ENV、secrets、服务账号、timeout、缩放/计费标志及IAM bindings与备份一致；min0/max1、自动请求计费，不保活/不自动暂停。后台PASSWORD、session/RBAC/Origin和审计保留。

私有服务/IAM原始描述、GCS generation元数据和SQLite备份仅在本机private目录600权限，不提交。发布前38单，离线克隆启动新构造器27旧表内容全部一致，仅新增live_operations/live_announcement_acks；实际云回读38旧单均保留。一单仅实时定位/device/received时间与updatedAt并发变化，另31乘客/6司机幂等记录增长，旧单其余字段均一致；root未调用云业务POST或新建云订单。严格初始比对差异保留在data-after-strict-initial.json，不冒称整库字节完全不变。

APK以候选对象先上传，逐对象固定generation复制到archive后才覆盖原固定下载对象，复制使用源generation和目标ifGenerationMatch，记录apk-promotion.json。不修改对象ACL/IAM；APK由原Cloud Run固定路由读取。整包下载证据须以最终APK_HTTPS_READBACK.json为准，下载页面200或HEAD成功不等于完整校验。

回退方案（本轮NOT_RUN）：将100%和user-test标签指回driver-demo-pm-54e6d313-dl900与driver-admin-pm-48d2ef4-test2，保持当前服务配置、账号、全部共享数据；APK按apk-promotion记录的archive generation条件复制回固定对象。禁止用predeploy数据库覆盖当前云快照，否则会丢失发布期间用户动作/订单。回退新代码不会删除新增运营表，但旧版不能消费新运营规则。若需回退，先重新保存当时服务/数据/对象generation，再由唯一coordinator执行并回读。

已知边界：高德1008/10008未解决；小程序无AppID未发布；Android新GUI/厂商实机不在本轮已测范围。真实资金、分账、积分、发票提供方均未启用。不是全项目最终验收。
