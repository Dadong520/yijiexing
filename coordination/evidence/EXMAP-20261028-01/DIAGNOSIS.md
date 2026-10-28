# EXMAP-20261028-01｜当前乘客Key被高德拒绝的可复现诊断

2026-10-28 Asia/Singapore。作者expert `01a1830f-d94b-7933-9c22-2c3898e54186`，Astra/xhigh，协议2026-10-26-v3；本报告为作者诊断，不是独立验收或发布批准。

## 结论与精度

**已证实的故障边界：当前私有配置内的乘客Android Key，配合当前已发布乘客APK的真实证书和实际包名，产生的正确SDK签名请求被高德服务端返回10008。相同环境、同一证书、同版SDK和同一公开POI查询，司机Key配合司机包名返回10000及1条POI。** 该差异在独立实现签名和原版SDK生成完整请求两种实现中一致。

这不是泛泛推测“请检查SHA1”。已将具体乘客凭据、当前APK身份、SDK请求生成和服务器响应建立了可复现绑定。服务器拒绝预期身份这一问题，不依赖Compose、UI、地图生命周期或旧安装缓存才出现，因此仅重排应用初始化不足以修复这组凭据的服务端拒绝。当前**最小产品补丁范围为空**，不建议在缺乏证据时修改产品。

**尚未证实**高德控制台当前到底为该乘客Key保存了哪组绑定，以及此前截图的设置行是否对应当前运行时下发的同一个Key。可能是具体Key记录对应关系、已保存绑定或高德侧记录异常，现有证据不能三选一。旧截图路径已缺失，root确认无备份；这不推翻此前已核对包名/SHA1的结论。不能要求用户重复填写同一绑定来替代核查。

真实Android设备内SDK请求及当前地图完整鉴权链路成功仍 **NOT_RUN**。本轮没有解决/发布鉴权修复，不能宣称地图业务PASS。

## 固定输入

- 产品源基线：`713fcfb57117741b056e8197d609e3e20f365937`，6个相关文件与该基线逐字节相同，见 `source-binding.json`。root共享树的工具/协调变更未触碰。
- 实际线上回读乘客APK：1.1.15-experience/code17，`cn.hbzjhs.dc.passenger.cloud`，107139449 bytes，SHA256 `798fbb0f9fc6796190ddaa36eb8f71f7081d196d0785867782da442d38996379`。aapt与apksigner实际验证通过。
- 从APK提取公开X.509证书，SHA1与已确认D0:6F:0A:9E:AC:9D:FD:DC:08:30:B9:8A:F0:6E:2F:68:B6:D2:02:46一致。未读私有keystore，详 `published-certificate-check.json`。
- 依赖：`3dmap-location-search:11.2.100_loc11.2.100_sea9.8.1`；JAR SHA256 `ac3ebaafa350784474178e9dfe0cc5083d4f616f52d410e223aed792ce9d6e52`。
- Key只在内存读取，证据仅留完整SHA256以供具体记录比对。乘客Key指纹 `5ff8a7e6c840c80e13cd703309cfdc114fa2082b7dc160e06ae9cd6f5ca44aeb`；司机Key指纹 `1ea1a9e5131ab7f3702a1f904b65f9813c3abdf6917e137d9eee1df8e0d518da`。

## 实际测试

| 测试 | 结果 | 证据 |
|---|---|---|
| expert真实ID/协议门禁 | PASS，不代表业务验收 | 本轮检查器输出；任务记录 |
| Python默认CA的首次连接 | 2次TLS信任失败，未获取HTTP鉴权响应 | binding-probe-initial.json |
| 系统CA、TLS验证开启、直连；SDK算法复现；乘客Key+乘客身份 | HTTP200，status0，10008 | binding-probe-system-trust.json |
| 同条件司机Key+司机身份 | HTTP200，status1，10000，1 POI | 同上 |
| 有界反向控制：乘客Key+司机身份、司机Key+乘客身份、乘客Key+源码namespace | 均10008；未进行任意身份枚举 | binding-probe-controls.json |
| 原版SDK在桌面JVM，合成Context/PackageManager、假Key与假证书 | 地图/搜索Key取值、身份、scode独立比对及完整请求生成5项true | sdk-jvm-offline.json |
| 原版SDK完整请求+发布APK真实证书+真实乘客Key/包名 | 5项内部检查及实体Key匹配true，HTTP200/10008 | sdk-generated-request-results.json |
| 原版SDK完整请求+同证书+真实司机Key/包名 | 5项内部检查及实体Key匹配true，HTTP200/10000，1 POI | 同上 |
| 原设置图仅内存OCR | NOT_RUN，引用文件NOT_FOUND；root已确认，不扩大扫描 | settings-ocr-comparison.json |

成功到达高德的公开地点只读请求共7次；此外2次本机CA失败。未访问项目后台/原测试或生产，云业务写入0、订单0、GUI0、ADB0。没有重复无变化轮询。

## SDK层已实查内容与限制

原应用 `AmapRuntime.prepare` 在创建MapView和PoiSearch之前设置动态Key，地图/搜索均调用prepare。字节码及原类运行证明：

1. MapsInitializer.setApiKey → AuthConfigManager → AppInfo静态Key；ServiceSettings.setApiKey → AuthManager → 相同AppInfo存储。未发现两套Key存储错配。
2. AppInfo从PackageManager的PackageInfo.signatures计算SHA1，并取实际包名；源码namespace本身不替代发布applicationId。原版SDK在合成环境使用真实证书所生成身份与预期一致。
3. ClientInfo与BasicLBSRestHandler从身份、时间戳和规范化参数生成scode；原版PoiSearch.Query及PoiSearchKeywordsHandler产生请求实体。真实Key不进入命令行/Java源码，仅stdin内存管道。
4. 测试直接运行原JAR字节码；仅Android平台Context/PackageManager等被小型桩替代。网络传输由Python发出，X-INFO在两角色中同样省略。司机成功是正控制；仍不能将此标为Android设备内完整SDK成功，亦未实测地图GL或坐标归一化。
5. SDK日志输出在测试进程中丢弃；原请求实体仅经父子进程管道留内存，落盘结果采用白名单字段。没有原始鉴权文本、scode、Key或安全码写入证据。

官方说明将INVALID_USER_SCODE及1008对应到Android Key的签名/包名验证，见[高德Android鉴权错误说明](https://lbs.amap.com/faq/android/map-sdk/create-project/46723)；[错误码表](https://lbs.amap.com/api/webservice/guide/tools/info/)将10008定义为MD5安全码验证失败。动态setApiKey是公开支持的接口，见[ServiceSettings官方参考](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/Search/com/amap/api/services/core/ServiceSettings.html)。实际本地SDK字节码比新版本在线API文档更直接对应当前产物。

## 可复现入口与下一步

从中央目录执行：

```sh
python3 coordination/evidence/EXMAP-20261028-01/build_sdk_harness.py
python3 coordination/evidence/EXMAP-20261028-01/probe_sdk_request.py
```

依赖原有私有Key文件和外置 `published-passenger-public-cert.der`；后者仅是已回读APK的公开证书。8个Java测试源在 `sdk-harness-src/`，产物只在任务externalTestRoot。请勿直接运行SdkProbe或转储它的stdout第二行，该行是只供父进程内存消费的请求载荷。运行入口只输出白名单检查结果。

下一步由唯一coordinator核对高德控制台**具体Key记录**：在私有内存把当前乘客Key整值哈希与记录对应，再只读确认该记录的已保存包名/签名。若记录完全一致仍拒绝，应保留当前最小复现交高德支持调查；未经明确发送授权不代发工单。若定位为Key记录错配，应由root决定凭据配置修正，再复验同一POI对照及获准的实际Android地图/POI成功，不盲目调整包名/签名或绕过鉴权。

本角色未提交、无待合并产品SHA、无部署；作者诊断须由其他会话复核。expert阶段完成后IDLE，仅接收root下一明确堵点，不自醒或扩任务。
