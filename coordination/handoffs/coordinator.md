# coordinator GRAB检查点：后端PASS，Claude Phase4完成，C1编译待跑，尚未发25
协议2026-10-26-v3；root01a17cc7-e0a2-7791-bf1d-4611c09bab22/generation6唯一协调/验收/合并发布，context UNKNOWN。中央本卡/STATE/task权威，勿读worktree旧卡再写回（曾误读已纠正，无污染）。更多固定细节evidence/GRAB-20261101-01/COORDINATOR_CHECKPOINT_DETAIL.md，按具体缺口读，不读历史聊天。
S=/Users/dongge/.codex/tmp/yijiexing-order-competition-20261101，branch codex/order-competition-20261101，base main29e83a8da4513859eaed073e0096ab1256a1a576，当前HEAD65695082d541a648a50a161634f99293f8b12ee7(D0)。P=/Users/dongge/.codex/tmp/yijiexing-grab-author-20261101；R=/Users/dongge/.codex/tmp/yijiexing-grab-native-20261101；L=/Users/dongge/.codex/tmp/yijiexing-grab-release-20261101。唯一文件归属按各task；backend仍仅UIO树可写。

## 用户规则/授权
GRAB-20261101-01：前后台新单浮窗3秒可直接抢；首有效抢单固定5秒候选、有效入场位置到上车点直线距离最小唯一获派，同距入场先后/driverId。用户确认获派司机上车前取消同单新轮，上车后原限制。普通STANDARD/RESERVED共享；BID选价/议价/私有示例/金额/处罚不改。
**GOV08长期授权**：用户允许本轮及后续宜捷行专用隔离模拟器使用Android官方工具安装/点击输入/测试权限/截图，仅隔离不个人手机，不再逐轮问。证据GRAB/STANDING_ISOLATED_ANDROID_AUTHORIZATION.json。DECISIONS近8KB勿追加；旧全文在本日archive。

## 后端已独立接受
GRABB commit3bdb8f71743b30f83d65131bb90ed7485690b62b，导入2eafd7d4353d6afa474ed37e678768902e20d1b2，treec5df96a35efc38ca50442b029681b7790b688922。作者307(23专项)PASS；root冻结该SHA独立307/79s及额外2真实HTTP/MemoryCAS异常PASS（首次写响应丢失重试唯一授予/计数；候选/输家无推进或取号权限）。BACKEND_INDEPENDENT_REVIEW.json PASS，仅后端，非客户端releaseReady。extra初次种子计数假设错已留证修为baseline+1。
当前原子备份48单/2联系快照，gen1793466630274601/SHA83a78b1f7c18c2b0a15007ee73b678fbbd1f9adf0e769e376504dc05a5e8efcc，private grab25-predeploy.sqlite0600。离线新schema clone两表全行一致、comp/contender0、quickcheckPASS；backend-current48-migration.json。最初tuple/sqlite.Row比较harness错已留证重试，无数据改。部署前查generation再刷新，绝不恢复旧46。
契约contracts/GRAB-20261101-01.md（字段/轮次/本人结果/错误码）；accept带competitionRound和requestId，重开拒旧轮；位置快照原120秒/精度规则，授予重验资格/冲突；due checkpoint与Cloud CAS保证唯一持久授予。
backend可见01a182c6-c058-76e1-9584-38abfe813799 IDLE，cursor6032fc7c-173b-461d-8a96-c62e9636d321:68。后续可派最终客户端合同非作者窄审，不重跑307。

## 客户端：Phase3在跑
Claude官方SID **f0e957b7-5f75-4d4b-88a7-0043b2ad9253** Opus5.5/high。Phase1 PTY55501/Phase2 PTY91247均exit0/0denials，24新改JUnit作者NOT_RUN；中央claude卡/SELFCHECK。root版本0.3.22-cloud/code25准备中。D0原build PTY1974已FAIL：NewOrderAlerts.kt722 Val cannot be reassigned，JUnit/原/QA/native NOT_RUN；证据D0-build/D0_SOURCE_REVIEW。私有D0 source/FROZEN/log/result移R/archive/D0，新R/source为空可下一SHA构建。
Phase3 PTY10025已exit0/0denials，已改编译/2s与1s单owner轮询/真实归属round/failclosed/计时/receiver，详中央SELFCHECK，未编译。
Phase4 PTY92125已exit0/0denials，P/run-phase4.py+phase4-prompt.txt，同SID/10min/noBash：把3处“3秒内可抢”标题改“新订单/提示3秒收起”，说明首抢才开始服务器5s；deadline仅扣本次RTT一次（=requestStart+remainingMs，非再减RTT），测1500ms剩3500ms；标旧Q1/Q3/risk被Phase3取代。下一root冻结C1新SHA/编译JUnit原+QA/实际隔离AVD，再合同非作者复核。当前releaseReady仍false，source未提交的Phase3/4只Claude src。

## 隔离资源（全部须清理）
AVD **GRABR_20261101/emulator-5588**，ANDROID_AVD_HOME=R/avd，API34 ARM64/1536MB，运行PTY55027，boot/name核对PASS。**fixture PTY75293/localhost TLS64366**运行且healthPASS，R/backend-review/source精确3bdb+MemoryCAS全新synthetic账户，无生产DB；所有业务走标准鉴权HTTP，无隐藏控制端点；STOP触R/runtime/STOP。未安装APK/未reverse！TLS正确CA+localhost叶证/QA-only trust、offline/qa-driver.init/build.py/ui.py/qa_client.py已备；QA绝不发布。私有identities含合成token不可输出；仅原Android SDK public identifier来自private基线，绝不把配置/原始日志入Git。
GUI未跑，计划GRAB/NATIVE_ACCEPTANCE_PLAN；一模拟器+合成peer API，非双手机GUI/实车/OEM/物理听音。

## 云准备与收尾
线上仍driver24/乘客23/API navi6cdcee/admin107，上轮PR49/50全部完成。新backend镜像仅构建：**40550617-3e31-4cb5-94d7-36528c64ee88 SUCCESS**，digest **ce8a90484a3dcc5fdec139ab4c3673b36fcefeaca88d43cf8f99950ff94fb262**，L/api-image-binding及GRAB/release/api-image-build；backend3bdb/treec5df，最终合并源backend tree须相等，不重建。private grab25-before-{api,admin}-{service,iam}.json与runtime-before只读；transfer.py已适配GRAB/grab25，binding={}，只backup做了，无上传/CloudRun动作。
本轮必须**default100+所有旧driver-demo API tags统一已验新revision**（保tag名/URLs），关闭旧writer绕过；不是上轮只切user-test。admin整体/tags/IAM、API其他ENV/SA/automatic/min0max1/request/API900/concurrency1保持。root唯一独立project/us-west1。后续：编译/原生/非作者审→准确CI合并→stage smoke+备份→全API tags与原APK发布/完整回读→receipt独立审CI入main。无真实云业务POST、新chat/代理/自动化；frontend HANDOFF_RECOMMENDED闲，Gemini暂停/Astra仅堵点。
