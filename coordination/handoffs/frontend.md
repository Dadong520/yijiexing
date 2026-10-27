# frontend 当前短交接
ACK 2026-10-26-v3；threadId 01a17cd2-bcf7-7931-a94b-62119cefce48；checkpoint RELTEST-20261027-01；NORMAL；实际context UNKNOWN/not_exposed。coordinator唯一负责人01a17cc7-e0a2-7791-bf1d-4611c09bab22。
sourceRoot /Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027；branch codex/pm-amap-integration-20261027。只读产品；仅中央evidence/PM-20261027-01/frontend-review及本卡可写。不提交/发布/改控制文件。

## 当前HTTPS包回读完成
PR28 main1d04ff7，apk-proof source54e6d313。coordinator逐个明确固定对象更新后执行，产品只读、无云写/不启模拟器。
乘客generation1793117996048546、司机1793118437324382；备用user-test同服务HTTPS直连分别11.731555s/11.823458s完整下载107073909/108620421bytes。两包SHA256、包名、versionName/code、证书与apk-proof逐字段一致，apksigner verify PASS：乘客1.1.13-experience/code15、司机0.3.13-cloud/code16。
证据release-test-20261027/HTTPS-APK-READBACK.md及https-{passenger,driver}-readback.json。乘客自有域名直连25s/1082代理300s均HTTP200但部分包超时；正式自有域名完整下载非PASS。root将同时交付快速备用链接；司机未重复慢路线。
外部包/脚本/Users/dongge/.codex/tmp/yijiexing-https-readback-20261028；下载会话均已exit，无资源常驻。root回执分支切换无产品差量，不重测。高德1008/10008仍已知未通过。

## 当前RELTEST独立验收完成
精确48d2ef409248bec3b6833b77570650a21d7b260d，root作者，本角色非作者PASS_SCOPE。发现旧后台navGroups漏32模块入口和空时间错误说明，root最小修后真实DOM关闭。
外部真实MemoryCAS+BrowserHandler PASSWORD fixture，仅本地合成数据。真实DOM全国CN/大区CENTRAL/城市420500/自定义草稿保存编辑参数回填V1..4/归档V5、记录新增/空时间不伪造/归档历史、viewer新增disabled；32模块各自真实HTTP生命周期+陈旧版本409/非法记录改写403/viewer写403，真实DOM逐模块回读已存草稿记录PASS。订单详情打开/刷新读取本地CAS最新状态和金额PASS。双浏览器陈旧表单GUI未跑，HTTP409和JS expectedVersion绑定已核。
证据evidence/PM-20261027-01/release-test-20261027/ADMIN-UI-REVIEW.md、admin-api-probe.json、admin-dom-modules.json及3截图。无线上写/不启模拟器/无产品改动，iAB已关；fixture停止并做资源读回。高德1008仍已知FAIL，32模块仍仅草稿/人工记录，不称规则执行或真实资金完成。
2026-10-28本轮可见压缩一次后定向恢复，context UNKNOWN；门禁PASS。资源退出后归root统一发布，不代部署。

## 最新PMDEST
精确0df8b187ac2f68c52fa6d17db846fbb8854b4eda，含786f2bb客户端必填目的地及最后过时Note字符串删除，root作者。本角色独立PASS：Android/mini新入口无pending开关，所有新请求需完整终点，旧flag/历史repeat不能绕过；历史sameID补填入口保留。定向mini2测试+真实shared.base独立流（三类型/缺目的地/旧flag/repeat/历史update同ID）PASS，复用Android20及mini62，纯字符串删除未重跑整套。证据PMDEST-REVIEW.md/.json及PMDEST-*-flow/targeted日志。新最终包GUI/微信真机NOT_RUN，0云业务写入。

## SDK独立阶段未通过
候选4a7ca4615120a1f624fa2c18fe5ca008e5891c87的两QA包仅0%preview，不作稳定下载。静态已确认swap metadata/manual cancel两P2关闭、司机首次login配置补齐、控制点防identity门禁；源与两SDK包/两QA包4私有值扫描0命中。
乘客同意前native库未映射、可defer，点击同意后底图可见；初始map10008 INVALID_USER_SCODE及POI1008。用户保存绑定后一次重试被QEMU退出中断UNKNOWN，root允许恢复后一次宜昌东站查询仍1008。司机新安装/首次login/独立同意/底图显示范围PASS，未见auth错误但无成功鉴权回执；两个SDK新PID未含Key日志，内存比对布尔全false仅说明没日志，不能证明角色Key。0新单/不主动改旧35笔/余额。正常成功POI/reverse/公开校准/同存GUI、声音/通知/OEM均NOT_RUN。详PMMAP-GUI-REVIEW.md及MANIFEST.json。
诊断：首次逐行脱敏遇SDK换行拆分，发现后该文本已覆写移除，后续仅结构化code/type/count；raw仅内存，绝不再存原文。

## 资源已交还
AVD初次swiftshader及gpu off（仍回退Vulkan）出现QEMU线程hang；恢复用仅本次参数-no-snapshot/-feature -Vulkan/软件渲染/1536MB/2cores/低分辨率，完成受控查询。一次SystemUI ANR，未见乘客App ANR/CRASH记录。
最终乘客原1.1.12重装保留账号；原无司机包，临时QA已卸载。既有AppOps定位模式保留，SDK新增4条拒绝/默认存储审计，未授权限/未reset旧UID ignore。显示密度/旋转/同意还原命令已执行，关闭后未单独读回。PMMAP-resources-final.json确认ADB无设备/QEMU关闭；无常驻任务。

## 早前可复用检查点
PMPA b792d0d：地图已有DONE单GUI+20test范围PASS，预约/待补/活动点GUI未测。PMCL ee8b32f：司机59/乘客20/mini60+真实shared流13状态PASS，修小程序预约横幅日期；实际新提醒仍NOT_RUN。各短报告在同frontend-review目录，按需读取。
下一步由root统一整体验收/安排发布或继续SDK疑难；本角色无待修产品、未部署。
