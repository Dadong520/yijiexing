# 当前状态 2026-10-26：项目记忆协议固化中
上一轮业务工作23:50起、01:48已结束（当轮01:50硬截止，不是本轮新请求的计时器）。三项用户需求已完成适用范围验收并发布：首开权限引导、新单声音/前台/后台提醒、超300m接送本人乘客同意及后台配置。

## 固定产品与发布
集成工作树 /Users/dongge/.codex/tmp/yijiexing-permission-trip-20261025，branch codex/permission-trip-integration-20261025，固定产品edb532549506abd962e0a2000015857b6ec6d6ce，PR23 merge af7ec4ce6c618cf0b984c54a76ba7024751e5e10。后续只提交发布回执，勿重复部署。
后端唯一源 /Users/dongge/.codex/tmp/yijiexing-trip-api-20261025 apps/cloud-service：8ec8419d30955c216d7e4fe90451584d2a9622cf，PR22 merged275ea3e，live driver-demo-distance-8ec8419d100%。旧21单ID保留；测试新增3笔(a5be/00d684取消未付、f4完整DONE29.26)。无原环境接入。
Web2863623ff932afc9adc1f59caa64e2b3069dbbae，live driver-admin-trip-2863623f100%，镜像264abd84..。https://demo-admin.hbzjhs.xyz 已实际dadonggev587 Google+原admin登录，新规则默认300/300/50..5000与f4 DONE确认卡回读PASS。backend非作者另确认TLS/302/IAP/DomainMapping三True，NET旧证书等待已关单。
Native作者树 /Users/dongge/.codex/tmp/yijiexing-native-permissions-20261025 branch codex/permission-alert-native-20261025，固定3e9f6ee943948e1081ec9ab0102eef803d447c8b已push。Gemini agy生成5helper约1750行，root集成/修复，frontend独立真AVD。driver0.3.12/code15 SHA bce9fcff928b3690525f14a6ae5f3a4e074fc6f9ca9bf491733e4b1609852a85；pass1.1.12/code14 SHA61417866fce94d9176ff74e42718d6ce60fa658064e289bf8e0dccfae10257d8（源码d896，最后仅改driver，字节证明）。两签名9ffec9b342d12909ff288bd73f23ba104893b39120cb24ea83c9734a0d2b758f。
两APK已直连上传/公开全GET验证TLS/版本/签名/SHA。下载https://yijiexing-demo.hbzjhs.xyz/download 与 /download-passenger；API同域名，免访问码，覆盖安装保留账号。GCS当前driver gen1792949858881200/pass1792949900790511，前版备份及回退见ALERT public-release/rollback JSON。APK/缓存/私有账户和密钥不入Git。

## 证据与边界
REL-20261025-02/REPORT.md、OPERATIONS.md、acceptance-summary.json、integration-source-equivalence.json；ALERT-20261025-01/frontend/{design-qa.json,REPORT.md}。208API作者+root5独立HTTP；55driver/20pass构建；58mini+语法；backend非作者Web草稿/发布/继承/权限矩阵及中文delta；frontend两AVD同一真实共享单与最终队列/中文/GPS off→on不重启恢复采样差量PASS。
同单PORDf4f910a233dc，driver03/pass02，2926分：GPS11062.102m/300，拒绝仍ARRIVED→本人同意TRIP→司机定位过旧/null需本人同意才WAIT_PAY→模拟支付DONE。root浏览器跨三端同单回读paid/credited真、PASSENGER_PAY1次；新后台历史3张确认卡。全部模拟款。
实测App只回听单/打开private确认，并未实际再接第二shared；实际欠款司机接他人单/乘客阻叫由rootHTTP5覆盖。声音只观察MediaPlayer/系统通道，未音频采集或主观听感；OEM/强杀常驻、完整新增弱网/无障碍/新装空userdata未测。mini无AppID，微信编译/真机发布NOT_RUN。不能称厂商离线推送、永久常驻或GPS防伪。

## 资源/剩余
frontend和backend已idle，Gemini CLI全部结束。两原AVD正常关闭，原网络/定位/普通权限/Appfloatingfalse恢复；唯一收尾异常：系统overlay包级get仍allow，default/ignore/UIDdefault均未能证实恢复，QA-20261026-01保留原始失败读回，不伪称成功。当前设备关闭，不影响已发布产品；本轮不再重启。
WX-20261025-01仍缺AppID；QA-20261026-01下次测试先核实系统有效授权。不得启动原测试/生产，不更改demo.hbzjhs.xyz原演示。root统一部署；CloudRun automatic/min0/max1请求计费，无访问自动缩0，不手工暂停。
本轮已于01:48收尾。发布回执PR24已合并55071ffd5b7b214e8d2c81e509cea4fe021385fe，CI project-gate PASS；PR22/23/24均已attach。当前无待发布项，产品与发布回执均在GitHub main。本地8808预览已停止，ADB两设备均无连接，frontend/backend/Gemini均idle或结束；无后台AI或定时自动续跑。仅WX缺AppID与QA模拟器overlay恢复作为后续，不自动启动。中央STATE/本卡是比Git发布时检查点更晚的收尾状态；用户下一轮从这里恢复，不读历史全文。

## 当前请求MEM-20261026-01
只完善并固化记忆/预警/换会话方案、更新过时README/角色卡/检查器，不开发业务或重新部署。协议2026-10-26-v3，sourceRoot /Users/dongge/.codex/tmp/yijiexing-memory-20261026，branch codex/project-memory-20261026，基线main55071ffd5b7b214e8d2c81e509cea4fe021385fe。文件归属与独立复核见任务。两个角色已获通知只做各自ACK/短卡/协议审查。当前coordinator HANDOFF_RECOMMENDED：固化后下一大任务应让用户新开协调对话，接手指令NEW_CHAT_PROMPT.txt；没有可靠上下文百分比，留UNKNOWN。不得自动新建对话或启动任何旧业务待办。
