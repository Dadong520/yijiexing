# 宜捷行司机 Android 0.3.5 已发布源码

本目录对应司机 **0.3.5-cloud / versionCode8，冻结候选r3**；APK SHA256为 `6b9620a237286002662a966a31ba110f50d3a13c2ca4c113961792b32ee0b4ea`，54,757,323字节。主任务提供的最终业务及公网发布证据已按下方限定纳入说明；组包仅更新文档和来源记录，原候选ZIP保持不变，不重编代码，不操作设备、云、GCS或原测试环境。

<!-- RELEASE_FACTS_START -->
035最终业务与发布核验：主任务已确认联合及公网证据。

联合验证：司机035r3/code8与乘客1.1.5/code7完成本轮联合新单。STANDARD PORD97596bf0cc68：错误尾号被拒、模拟支付失败未入账，成功后DONE仅增加2926分，余额52848→55774分，评价5星；司机投诉CP59d6e38495d3与乘客投诉PCa6f2b8b98513的后台回复已在对应App实际可见。BID PORD2a42ed9559c3：模拟预付3600分，00:49:46司机取消即时模拟退款3600分，乘客结束后最终CANCELLED；余额55774分不增，DRIVER_CANCEL与PASSENGER_CONTINUE各一次。后台权威/台账61断言passed，属于数据读回，不替代APK实际UI。新BID司机费用页未另截，早先只读退款费用页不算本新单证据。乘客owner的cleanup记录确认司机下线、两设备字号1.0并退桌面；组包代理未操作设备。以上均为沙箱模拟，未做真实资金或道路行驶验证。

公网发布：主任务已于2026-10-24 00:54:08.950881+08:00完成公开下载地址全量GET：HTTP200、系统TLS证书验证通过、54,757,323字节，SHA256 6b9620a237286002662a966a31ba110f50d3a13c2ca4c113961792b32ee0b4ea与冻结r3一致；包名cn.hbzjhs.dc.driver.cloud、0.3.5-cloud/code8、签名SHA256 9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f兼容性核对通过。后端未重新部署、原测试环境未改变；本次源码组包代理只读取主任务证据，未下载/上传/操作云端。

Antigravity本轮仍为BLOCKED_PERMISSION_NOT_TESTED、0次UI操作，不计作通过。详细限定和证据hash见provenance/final-release-facts.json。
<!-- RELEASE_FACTS_END -->

## 已确认的验证范围

- r3已有40项JUnit、0失败、0错误；本次整理查阅既有记录，不重跑测试。
- Codex已经完成有范围限制的035界面回归，覆盖系统字号1.0/1.3/1.5及部分App大字1.12组合、首页/设置/订单筛选、退款展示、投诉键盘及确认取消、会员付款布局、r3放大字体登录按钮滚动可达与空表错误，以及地图署名修复。原报告明确区分r1/r2公共布局与最终r3复验，不把全部用例写成r3逐项重跑。
- 1.5登录需继续滚动；已经实际点击空表并看到必填错误，不等于1.5有效验证码登录全链通过。会员付款只验证在期状态与布局，没有实际购买。
- **Antigravity本轮仅尝试1次，权限自动拒绝、响应为空，实际UI操作0次，状态BLOCKED_PERMISSION_NOT_TESTED**。不计作通过，不用历史032投诉证据代替035独立测试。
- 具体界面已验/未验范围以 `provenance/ui-results-codex-035.md` 为准，结构化采集点见 `ui-acceptance-035.json`。真实支付、物理扫码、所有状态、并发、附件、读屏和所有小屏未因此获得完整验收。

## 改动及业务冻结

035侧重大字、高对比、触控面积、长文换行、可滚动表单/弹窗、键盘提交、退款重点展示和会员付款布局。034取消、模拟退款及收入规则保持冻结：已选共享单接驾前的允许状态可取消；退款认服务端paymentStatus/refundAmount；未入账的取消/退款订单显示本单未计入收入0。历史034新单业务已验只属034，035联合结果必须单列。

后端仍为034最终归档中的**精确v7 22文件**，镜像 `sha256:b3cb3e2f61e4a724dd95b6dfac0d357d2bbd793b9e225402965b8fedb5ff0d3d`，Build `95828fa5-a4b6-469c-b227-99fab315bdbe`；原后端manifest/Build与部署快照、API合同、ops保持原字节。部署快照是历史来源记录，本次不是实时云状态检查。

## “今日”经营数的已知演示限制

冻结v7的 `cloud-service/seed.py:22` 将 `todayIncome` 初始化为11000分（¥110.00）、`todayAccepted=2`、`todayCompleted=2`。`domain.py` 的 `data()`没有按自然日期重算或清零；接单及 `credit()`继续累加已有值。因此App首页标为“今日”的收入、接单/完成数量实际是**含合成初始值的演示累计值**，不能作为按日经营报表、真实经营统计或财务依据。本次不修改冻结后端或Android业务代码；余额、支付、退款等同样沿用模拟沙箱边界。

## 来源与完整性

Android来自冻结 `android-source-035-r3.zip`，SHA256 `3739dcb34791138f2c0e34c707b200175a49fbffe11c7f67195c1cd4291dad13`。原56项中排除5项历史verification日志/旧补丁/辅助记录，交付51项；README与INTEGRATION只更新说明，49项非说明的代码、资源、测试、构建文件字节保持不变。完整原manifest保留，排除清单与文档例外见provenance/SOURCE.json。

后端、合同与ops取自只读 `source-034-final.zip`，SHA256 `fdaef43b3c7051e00c0dd23c723705131b79bc90e032da6b808c646be8e165f1`。保留后端22项manifest及来源原文；previous-034-SOURCE.json仅为历史来源，不作为035结论。最终组包已再次校验49项Android与22项后端原字节，并生成CRC和完整内外hash清单。

本包不含乘客Android工程、独立管理前端、APK、数据库、运行数据、QA原图、日志、build/cache、local.properties、访问码、有效会话、管理密码/令牌、云凭据或签名私钥。仅纳入经筛查的说明性证据，不携带访问凭据。

## 本地复现与工具

Java17、Android SDK34、Gradle8.7，自行创建android/local.properties指定SDK路径。

```sh
cd android
./gradlew -PYIJ_CLOUD_ORIGIN=https://driver-demo-491829048305.us-west1.run.app testDebugUnitTest assembleDebug
```

这是复现说明，本次整理不执行。自行构建签名由本机keystore决定；origin为公开服务地址，访问码另行输入，不得放入构建参数/源码。地图条款由使用者自行确认。

ops固定操作隔离项目yijiexing-driver-demo-1023/us-west1的driver-demo与driver-admin，明确项目，不更改gcloud默认项目。启停工具本次不执行。暂停后数据及镜像仍可能收费；费用提醒或单项服务限制不是所有资源实时硬上限，暂停不延长赠金有效期。
