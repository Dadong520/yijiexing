# 宜捷行乘客端1.1.5源码交付

passenger-android是独立原生Kotlin/Compose工程；包名cn.hbzjhs.dc.passenger.cloud，Android8.0+。Java17/AndroidSDK34，配置本机SDK后运行./gradlew testDebugUnitTest assembleDebug。SDK路径、签名私钥、访问码和用户数据不随包提供；采用本机开发签名重新构建时，与交付APK的签名可能不同，需要自行管理更新签名。

shared-cloud-service-v7来自实际部署冻结目录，不是仍可能并行修改的工作副本。运行镜像digest和两服务revision见deployment/deployed-final.json。补齐了乘客身份、真实共享订单与司机兼容投影、竞价/取消/模拟退款/评价投诉、配对议价/异议改价。tests为本次回归测试源，在该目录可运行python3 -m unittest discover -s tests -v（临时SQLite与本机HTTP，不访问原环境或GoogleCloud）。管理员独立13项测试在另一个后台项目，此包没有把它们与135项共享测试合并计数。

existing-cloud-ops保留原独立GoogleCloud随用随停工具，passenger-ops为本次唯一协调部署工具。仅供明确目标项目的维护，构建App无需执行云初始化/部署脚本。不要将这些脚本改指向原测试环境。云端采用同一私有快照持久化、请求计费、最小实例0、服务上限1；真实高并发生产运营不在本次体验验证范围。

原乘客小程序源码未找到；功能依据原Java后端与旧墨刀原型核对，不能声称逐页完整还原原小程序。实际通过、失败与未测范围见docs。独立测试证据另包交付，源码包不包含数据库、会话、验证码、一次配对码、个人媒体、APK或真实密钥。
