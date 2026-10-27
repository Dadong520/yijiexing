# 两端HTTPS安装包独立回读

结果 PASS_SCOPE。测试版合并SHA 1d04ff7ce64779fe9f9122513dd651f305093b79；APK构建源54e6d313，后续admin.js导航/时间说明修复不影响APK字节。

|端|实际成功下载入口|大小|版本|结果|
|---|---|---|---|---|
|乘客|https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download-passenger.apk|107073909|1.1.13-experience / code15|完整下载、SHA256、包名、证书、apksigner均与apk-proof一致|
|司机|https://user-test---driver-demo-phfgquzmua-uw.a.run.app/download.apk|108620421|0.3.13-cloud / code16|完整下载、SHA256、包名、证书、apksigner均与apk-proof一致|

两备用入口均由coordinator明确授权，同服务、同已更新固定对象。乘客generation1793117996048546，司机1793118437324382。分别直连11.731555秒、11.823458秒，HTTP200。下载外部tmp，无Range或续传。

乘客自有域名正式入口先直连25秒（收到3114928bytes）及本机1082HTTP代理300秒（收到20574199bytes），均HTTP200但超时未完成；如实保留两次事实，换已授权备用入口后取得完整包PASS。司机自有域名路线未重复运行。故本次确认成功的是备用HTTPS完整回读，自有域名两文件完整下载不作PASS。

核验命令：外部curl完整下载；/opt/homebrew/share/android-commandlinetools/build-tools/34.0.0/aapt dump badging；同目录apksigner verify --print-certs，JAVA_HOME为/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home；Python SHA256/文件大小逐字段与apk-proof.json比对。精确数值见https-passenger-readback.json、https-driver-readback.json。

本角色未上传、未云写、未启模拟器、未安装App，无常驻下载进程。高德1008/10008仍为既知FAIL，不能以包回读通过替代SDK功能验收。
