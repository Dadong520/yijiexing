# GRAB 独立验收计划（未运行，不代表PASS）
根非作者执行；Android官方工具已有本轮及后续专用隔离模拟器长期授权GOV08。只GRABR_20261101/emulator-5588，Android34 ARM64新建未启动；无个人设备。native private yijiexing-grab-native-20261101，冻结实际候选后再构建，QA只localhost+测试CA不得发布。

1. 真实Service后台：普通共享新单、原floating工具关闭仍出现核心浮窗；前后台只一个提示。3秒自动消失/关后不重弹，本轮仅hall保留；单调时钟跨前后台/旋转寿命不重置；process重启同轮不重弹与新轮再提醒需审实现。多新单有序/有界/全部hall可见。
2. 后台浮窗直接抢单：明确一次点击发一次含round请求，不再二次确认；网络/busy/同窗重复点击不重复参与；请求200 PENDING不误称获派/不导航。通知权限/overlay拒绝时正确引导与3秒action fallback，位置服务通知不被误删。
3. 用真实5秒时钟、fixture真业务API、合成peer司机：第1个远司机抢，4秒内近司机抢，5秒后近者唯一获派；UI PENDING→LOST、可抢项截止立即关闭/从hall退出，只有当前司机真归属+PICKUP/RESERVED才接驾/导航；WON结果历史不能单独授权。
4. 唯一候选约5秒后获派；第二位候选在>=deadline被拒；截断微秒边界/CAS/重启/幂等由后端真实HTTP测试，不冒称每个边界均手机双端实跑。只有1模拟器时peer是合成API控制，不能称双手机GUI验证。
5. 获派司机上车前取消：同orderId round+1 READY重新提醒/可抢、旧round及旧requestId迟到不串轮、不自动重新报名；乘客取消/改路线作废旧轮。上车后原司机取消限制仍在。BID报价/议价/私有示例路径保持可用，单执行与待出发预约例外保持。
6. 缺失/陈旧/精度差定位失败不启窗；账户实际定位快照算距/不信任客户端自报distance，资格失效/下线/其他执行单不派，可能无赢家则同单新轮。重验定位不是GPS真实性认证。
7. 账号切换/退出/下线/权限撤销/跨轮/撤单/重连旧回调不执行/显示旧候选；无凭据/联系人/原始坐标日志或系统提示暴露，通知PendingIntent不可改token/round/order。
8. 360dp/font1.3/多途经点/大金额按钮完整、3秒小屏可点；前台Dialog与后台Overlay到期不触发下层按钮；返回/导航原业务尾号/距离/乘客同意/途经操作不被破坏。
9. 原签名原包身份/版本25/HTTPSorigin/QA值0命中、APK<128MiB、相关JUnit与root后端回归、关键业务非作者审查后才能releaseReady。现有音频偏好保持，未物理听音不得称真实声音已验。
10. 所有AVD/fixture/reverse/QA进程清理读回；独立Cloud项目仅root发布、API+driver候选/准确headCI/合并、备份当前新用户订单不回滚旧46、数据与配置/IAM保持范围说明、完整公开APK回读、实际receipt入库。
