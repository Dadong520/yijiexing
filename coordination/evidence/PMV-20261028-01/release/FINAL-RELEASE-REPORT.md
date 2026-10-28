# 修改意见2.0首批发布回执

时间2026-10-28T14:04:07.949249+08:00。PR30已合并main `7402b117d55bd4d0f1404a5ae2ff0c1cf2e8e0a5`，所有产品组件树与独立验收 `817ed22bce503620d692e097a5f8ea996edf5fb2` 相同。API和后台新版均100%流量，user-test快链已重指。Cloud Build SUCCESS，新revision健康与静态文件字节、未登录401校验通过；真实浏览器已打开自有后台登录页（live-login.png）。

首批发布：公司与客服电话/司机公司绑定和订单公司快照；公告草稿发布撤回，每用户每发布版本一次，可关闭；地区乘客即时/预约/竞价/模拟开票开关与服务端门禁；三证提前60天提醒/更新待审保留有效旧证/过期禁新增但在途可完成/通过才替换。菜单为平台运维→公告管理、公司与客服电话、乘客端功能配置；司机管理→资质到期提醒。原32类为配置与治理→历史草稿与人工记录。

乘客1.1.14-experience/code16，司机0.3.14-cloud/code17。两包已上传候选、按generation归档旧固定包后提升新固定对象，原签名不变。快链实际HTTPS整包下载、长度/hash、aapt版本/包名和apksigner均通过；APK_HTTPS_READBACK.json保存每次尝试，未用页面200替代整包校验。私有地图Key/安全码四模式在包内零匹配，原APK_PROOF与非作者PMVA复核有效。

当前38旧单均保留。离线克隆构造器保持27原表内容不变、仅加2运营表。实际前后GCS比较一单定位与updatedAt被客户端刷新，31乘客/6司机幂等记录增长；其余所有订单字段、ID、状态、金额与备份一致。严格初次整行不相等记录保留，没有改写为整库相同。root未调用云业务POST/新建云订单。

服务ENV/secrets/SA、IAM bindings、Origin/PASSWORD、min0/max1/automatic/请求CPU计费和API900秒与之前相同。不保活/不自动暂停；只使用独立项目，未访问原环境。私有服务原始manifest、账号、Key、SQLite、APK、上传sessionURL和缓存均在仓库外。

验证：根非作者7组新HTTP/13兼容/8真实隔离DOM；乘客24、司机63及两构建；mini72/admin4；frontend对root包身份与回执范围独立复核。PR30 project-gate SUCCESS。late后端差量由最终HTTP817覆盖，设备GUI未测不冒称已测。详细可核验JSON见FINAL-RELEASE-RECEIPT.json。

限制：高德1008/10008尚未成功验收；微信缺AppID未微信编译/真机/发布；新版Android设备GUI/实际拨号、部分OEM未测。真实资金、分账、积分和发票提供方未启用。文档其余需求后续分批，PMPOST未集成。此为已知问题明示的独立体验版，不是全项目最终验收。

本地服务、构建、上传和临时浏览器均结束；Gemini无运行任务，无后台自动化。回退方案和未执行边界见OPERATIONS.md，不恢复旧数据库覆盖用户新数据。
