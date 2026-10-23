036-r2本地候选接口与验收边界

宜捷行司机 0.3.6-cloud/code9：待验收本地候选（036-r2）

状态：LOCAL_CANDIDATE_ON_HOLD / NOT RELEASED / NOT ACCEPTED。用户要求为节省云资源停止云联测和新公开发布；等待用户通知后再继续验收。云已按用户要求关闭；本打包任务未复核实时云状态。新APK尚未公开，安装后云恢复前不能联机体验；公开035保持不变。

本轮修复：WAIT_PAY结算不占用司机；待收款入口；private synthetic标签；累计统计；旧回包/401隔离和expectedStage；r2把附近可接订单卡前置到地图/统计之前，真实shared乘客订单优先。r1到r2仅CoreScreens.kt变化，其他53冻结源文件一致。

冻结APK：app-036-r2.apk，54787056字节，SHA256 91aff0d8e97b548ee9c27354e6d4afd97ec3d8cdced392ec4c101b82dbed902b。APK不包含在源码包内。Android55项单元/契约测试PASS为已有构建证据，本任务未构建/运行。Android52非说明文档文件精确匹配冻结r2源；两说明文档更新为本地暂缓边界。后端35文件精确匹配v8-source-manifest；部署镜像历史证据绑定sha256:7d71539b9ad631aa1eaed5af8e34c4648c1fb5953cd78c3737605ea40d5d43ac。

停止时设备/后端owner确认：003私YC1002=WAIT_PAY1860；A PORD9dac969cecf1=WAIT_PAY；B PORDaa584245c6e6=AVAILABLE未接；余额55774。r2已安装并保留会话、两笔待付，新B卡前置可见。完整B接单+A晚付未完成，不能判定联合验收通过。AG本轮NOT_RUN。provenance中的r1/r2检查均仅表示各自记录时点，不能替代完整验收。

ops目录5文件为原交付范围的只读副本，本任务从未执行、import或测试。ops/deployment.json为历史静态部署配置，新镜像以provenance/v8部署证据为准；不代表当前live复核，不应把其中历史配置用于直接恢复服务。当前暂停/保留决定以release-hold.json和本说明为准。部署证据中的health200为历史记录，不代表用户暂停后的可用性。

乘客原生116仅本轮冻结兼容测试，未公开新发；后续转小程序。公开下载、完整联合验收、恢复云联测均等待用户通知。不得使用035/v7历史通过结果替代036验收。源码不含.env、local.properties、.private、签名密钥、访问码或构建缓存。
