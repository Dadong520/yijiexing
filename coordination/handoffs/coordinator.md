# coordinator GRAB实现完成，首轮编译/源审中（尚未发25）
协议2026-10-26-v3；root01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6唯一协调发布，context UNKNOWN。最新用户：后台/前台新单浮窗3秒直接抢；首有效抢单固定5秒候选，距上车点最近有效司机唯一获派；用户确认获派司机上车前取消同单新轮，上车后原限制。普通STANDARD/RESERVED，BID选价/议价/私有示例不改，无新处罚金额规则。

## 源与作者
source /Users/dongge/.codex/tmp/yijiexing-order-competition-20261101，branch codex/order-competition-20261101，base main29e83a8da4513859eaed073e0096ab1256a1a576；root owns coordination/README/Gradle版本，已改driver0.3.22-cloud/code25（仅准备，不已发）；Claude只driver src，backend唯一可写UIO树apps/cloud-service。
backend GRABB作者commit3bdb8f71743b30f83d65131bb90ed7485690b62b，treec5df96a35efc38ca50442b029681b7790b688922，已root cherry-pick导入（准确importSha在根task）。backend307作者自测PASS，root冻结该SHA私有backend-review独立307/79秒PASS+2独立异常HTTP/MemoryCAS PASS（CAS不确定首次写重试计数+1与唯一candidate/winner；候选/输家不能advance或取乘客号码）。初次extra测试以为种子todayAccepted为0的harness失败留证，修成baseline+1，无产品改动。root BACKEND_INDEPENDENT_REVIEW.json PASS，非全客户端验收，releaseReady仍false。
backend可见01a182c6-c058-76e1-9584-38abfe813799 IDLE，cursor6032fc7c-173b-461d-8a96-c62e9636d321:68，307作者日志/合同GRABB证据。合同中央contracts/GRAB-20261101-01.md V1，新增competition round/status/deadline微秒/serverNow+serverTime/myOutcome+selfStatus/remainingMs，顶层本人competitionResults；accept{id,competitionRound}+requestId，首轮旧无round兼容/新轮拒旧。固定有效位置快照排行，原120sec/(0,100]m定位，发派重验资格/单执行；请求前到期结算checkpoint独立commit，Cloud GCS CAS成功才回响应，错误动作不会抹到期授予。不代表GPS真实证明。
Claude官方SID f0e957b7-5f75-4d4b-88a7-0043b2ad9253 Opus5.5/high；phase1 PTY55501与phase2 PTY91247均已exit0/no denials，24新/改JUnit与编译作者NOT_RUN。两阶段均未commit，root将冻结首候选。private /Users/dongge/.codex/tmp/yijiexing-grab-author-20261101，后续精确--resume该SID，禁止continue/latest或同SID并发。中央claude短卡/SELFCHECK已更新；工作树同名仍旧NAVIC，不要再误读或覆盖中央。root有一次误读旧工作树短卡已立即改读中央，未写回污染，继续固定绝对中央路径。

## 下一首构建与Phase3
先把phase2司机源+已验backend冻结确切Git SHA，私有R/scripts/build.py运行原/QA+JUnit；源未测试不能发布。phase2已知待修：原前台10s/后台15s基础轮询远长于5s，只有自己候选1s不足，需仅真实上线听单≤2s、OPEN/PENDING约1s、非听单/在途原长间隔及退避，并确保store/service/candidate单owner去重不保活；CompetitionVerdicts当前只凭payload account+stage且允许competition缺失，需检查row.driverId真实本人和同round，历史WON只反馈；INELIGIBLE本轮不可重试到新round。CompetitionWindow.anchor只300ms安全垫，慢网络应扣本次已测RTT或按请求开始锚定，不凭设备墙钟。待首编译错误一并给Claude Phase3短修，不先接受known issues。phase2其他反馈（3s/5s独立、持久seen512、PI唯一scope、防旧cleanup、后台WP/小屏）已实现待独立验。

## 隔离环境/授权
用户已明确长期授权本轮及后续宜捷行专用隔离模拟器使用Android官方工具安装/点击/输入/测试权限/截图，仅隔离不个人手机；GOV08及GRAB/STANDING_ISOLATED_ANDROID_AUTHORIZATION.json，不再逐轮问。
private R=/Users/dongge/.codex/tmp/yijiexing-grab-native-20261101；新AVD GRABR_20261101，ANDROID_AVD_HOME=R/avd，serial emulator-5588，API34 google_apis ARM64/1536MB，**已启动PTY55027且boot/name核对PASS**（BOOT_PROOF），boot核对PTY61174已exit0。当前未安装APK/未fixture/未reverse；旧AVD5586不碰。R/runtime/tls已正确CA+localhost leaf、QA-only debug trust；scripts/offline.init.gradle+qa-driver.init+build.py+ui.py准备（UI helper仅5588），未执行app业务。private backend-review source只读冻结3bdb，307与extra测试进程均已结束。
新native计划GRAB/NATIVE_ACCEPTANCE_PLAN.md全部待跑：真实3s消失不重弹/前后台/进程重启、新单Hall保留、浮窗/通知直抢、1真GUI+合成peerAPI自然5秒近者获派/输赢/取消新轮/旧请求、permission拒绝fallback、小屏font1.3、NAV原门禁；不能称双手机GUI或真实听音/实车/OEM已验。QA localhost/CA绝不发布，原包原签名/128MiB上限检查。

## 云与收尾
线上仍driver24/乘客23/API navi6cdcee/admin107，前一NAVI/receipt PR49/50 main29e83全部完成不重发。fresh只读baseline grab25-before-{api,admin}-{service,iam}.json0600在private/yijiexing，GRAB/runtime-before.json公开sanitize；无云业务POST/配置改变。当前只AVD运行，作者CLI/后端测试全结束。
**本轮路由不同于上轮**：旧driver-demo历史QA tags共享DB可绕过新规则，发布须default100及所有现存API tags统一新已验revision（保tag名/URLs），移临时staging；admin整体/tags、IAM/ENV/SA/automatic/min0max1/请求计费/API900/concurrency1保持。先新revision无流量smoke/备份、原包候选/准确headCI合并，再统一发布完整回读；当前用户订单可能新增，绝不恢复旧46。详GRAB/INTEGRATION_REVIEW_NOTES。root唯一发布，native/root与非作者关键接口审PASS后才releaseReady；实际receipt/README独立窄审准确CI入main。未新chat/子代理/自动化；frontend HANDOFF_RECOMMENDED继续闲，Gemini停/Astra仅堵点。
