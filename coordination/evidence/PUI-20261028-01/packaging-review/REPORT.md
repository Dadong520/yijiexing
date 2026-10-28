# PUIA 独立源码与正式体验APK复核

任务PUIA-20261028-01；复核backend `01a168e2-22f7-70a2-af7f-4fb6be0ea695`；2026-10-28。结论 **PASS_SOURCE_PACKAGE_SCOPE**，未发现阻断项。此次仅复核frontend首屏展示差量及root版本/包身份，不验收本角色编写的defaults业务，不执行GUI/ADB/云部署。

## 固定范围

- 只读源 `/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027`，分支 `codex/passenger-ui-intake-20261028`，完整SHA `7d6dc6014906c80be87b39f4433ec494ae067098`；首读git status为空。
- frontend f3集成差量为该SHA的OrderDetail.kt；父提交 `a207e4f335662ddb5a2afa191d85948f54031775`。root版本包装提交 `6c52fba88a93750817702a163e55dcc9c82f5d50`。
- 正式新APK仅在root明确通知构建完成后读取：`/Users/dongge/.codex/tmp/yijiexing-pui-release-build-20261028/passenger/_app/outputs/apk/debug/app-debug.apk`。未把旁边QA产物或旧a207包算本轮通过。

## 金额与取消差量

用Python把新源码的新增摘要Row/Alignment import移除、把原取消按钮原样放回费用卡，重构结果与父源码**逐字节完全相同**。这是展示差量校验，不是UI运行或后端业务验收。

1. 金额摘要位于“行程路线”卡之前，直接复用orderFarePresentation(o).label及money(o.fare)，不新增金额计算或报价写入。destinationPending仍显示“费用待定（并非免费）”；摘要同时明示支付和退款均模拟。
2. 取消入口复用原cancellable阶段集合与!busy条件，仍先打开原取消原因弹窗。确认后仍只提交原order.cancel及原因，服务端状态/退款判断不变；没有将按钮移动变成直接取消或新增处罚/退款承诺。
3. OrderFarePresentation、PaymentConfirmation、Negotiation、PassengerUiRules四个业务/展示规则文件与父提交完全相同，SHA在source-review.json。未选竞价仍“体验估价（参考）”，选定后仍“选定司机报价”，原报价/金额版本确认门禁未改。
4. 首屏是否在不同设备高度/字号下实际可见属于GUI范围，本角色未运行，不把源码顺序当像素布局验收。后端全套和自己defaults测试未重复。

## 版本与APK独立验证

root版本提交只改乘客versionCode16→17、versionName1.1.14→1.1.15；原applicationId与HTTPS默认值未改。司机目录与main8e基线无差量，仍0.3.14/code17。

| 项目 | 独立结果 |
|---|---|
| APK大小 | 107139449 bytes |
| SHA256 | `798fbb0f9fc6796190ddaa36eb8f71f7081d196d0785867782da442d38996379`，与root通知值相同 |
| 包名 | `cn.hbzjhs.dc.passenger.cloud` |
| 版本 | `1.1.15-experience` / code17 |
| Android | min26 / target34 |
| 签名 | apksigner退出0，v2验证通过，单签名者 |
| 原证书SHA1 | `d06f0a9eac9dfddc0830b98af06e2f68b6d20246`，与任务指定原指纹相同 |
| 原证书SHA256 | `9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f` |
| 包内BuildConfig | 反汇编确认原cloud包名和 `https://yijiexing-demo.hbzjhs.xyz` |
| manifest | usesCleartextTraffic=false |
| 已知QA标识 | QA包名、QA HTTPS地址及另一QA后缀全部0匹配，计数在package-review.json |
| 本轮新增摘要标记 | 在解压成员中找到2次，进一步排除缺少f3的新摘要旧包 |
| ZIP | 全成员CRC检查通过 |

这是原体验证书，证书DN含Android Debug且产物本身debuggable，未换成另一QA签名；不将其描述为生产release签名审计。`_app`为外置build输出目录，并非另建源码副本；init脚本确有buildDir覆盖、源码绑定采用root固定SHA构建通知+只读源码核对+包内新增摘要/BuildConfig证据，不伪称读取了不存在的_app/src副本。构建日志production-build-02-fee.log含BUILD SUCCESSFUL。

## 私有Key扫描

root给出既有配置文件后，仅在内存取webJsKey、webJsSecurityCode、passengerAndroidKey、driverAndroidKey四项精确值；未读取私有账号库、未访问线上Secret Manager或重新索Key。对APK所有非目录成员解压（包含全部DEX），分别以UTF-8/UTF-16LE/UTF-16BE匹配，**四项各编码计数均0，总匹配0**。

扫描只保存字段名/计数/方法及APKhash，私有值、私有配置原文均未输出、未复制到证据或Git。见private-key-scan.json。这里验证无打包嵌入，不代表高德1008/10008已解决或SDK联网鉴权通过。

## 实际检查与限制

- 中央真实IDmemory-role门禁PASS，结构检查不是业务验收。
- git源差量、司机目录不变、展示差量重构断言PASS；source-review.json保存完整SHA及未变文件hash。
- aapt dump badging/xmltree、apksigner verify --verbose --print-certs、apkanalyzer dex code(BuildConfig)、Python ZIP/hash/精确Key扫描均实际执行通过；日志留本目录。macOS系统Java登记未找到后改用**已存在**的Homebrew OpenJDK17，未安装或改全局配置。
- 没有APK安装、模拟器/真机交互、页面截图、实际拨号、高德鉴权、微信编译、线上下载或云发布；这些均NOT_RUN，不扩大为13页UI或两端全流程通过。
- 本角色自己编写的defaults明确NOT_REVIEWED_BY_AUTHOR，root的6组独立HTTP结果不被纳入本次自验收。最终由coordinator统一收取本范围结论、合并和按需发布。
- 产品与分支未修改，所有APK/构建缓存保留外部原位置，Git只允许本任务/packaging-review证据/本短卡；无ADB/Chrome/Gemini/云资源占用。
