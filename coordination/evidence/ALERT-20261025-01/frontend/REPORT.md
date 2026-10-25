# ALERT01 原生限定独立验收

**PASS_SCOPE**。最终源 `3e9f6ee943948e1081ec9ab0102eef803d447c8b`，driver bce9fcff…/乘客61417866…，实际安装身份和签名匹配，原账号保留。

1. **安装及原账号：PASS。** 实际base.apk hash/签名/版本/DEX自有origin匹配，driver03/pass02保留；无清data/prefs/token。
2. **权限指引：PASS_SCOPE。** 两端原userdata升级首引导；乘客系统拒绝→再申请→使用期间授权，返回状态更新；稍后重开不重复，两端入口重进；司机系统浮窗及App显式开关实测。
3. **提醒与声音：PASS_SCOPE。** 真实新单前台Dialog、后台大卡与原小泡；点卡只打开AVAILABLE未抢。d896新00d单卡shown1次，31秒观察后自动收起。UID10192 MediaPlayer started/stopped及new-orders-v2 sound URI/high/no bypassDnd；无音频采集，不称听见或中文播报完成。
4. **同单接客超距：PASS。** f4手工接单PICKUP→ARRIVED→正确0102；真实provider accuracy5m两GPS相差11062m/阈值300。pending保持ARRIVED；本人拒绝不推进；再申请本人同意才TRIP。
5. **同单旧GPS送达：PASS。** 系统UI关司机GPS>132s；双方TRIP pending显示距离未知/定位过旧>120s，非0。本人送达确认快速2点→WAIT_PAY29.26未付。
6. **待付与模拟付款：PASS_SCOPE。** 欠款乘客home只有查看当前行程，无新叫CTA；driver回听单待收款3且能开私有单确认框，已返回未确认。模拟29.26成功按钮快速2点→DONE；root另会话后台2926/paid/credited/SIMULATED，PASSENGER_PAY仅1次。
7. **最终差量：PASS。** d896实际覆盖两包，历史APPROVED已中文乘客已同意，提醒最新队列/超时退休实测。3e9仅Service provider监听delta，off启动→系统on→真实GPS请求/新采样恢复，on后无App重启。其余距离代码按diff继承。

收尾：原网络、字号、定位模式、普通定位/通知权限、App浮窗偏好及GPS已核对，两AVD正常关闭。**系统浮窗权限须协调者确认**：截图46开关OFF，但最终重装后AppOps仍allow；报告未把这一项伪称恢复通过，已移交。

未测真机/OEM长期后台、音频采集/主观听音、网络失败/过期、原生议价全链、第二真实共享单接入、完整无障碍、正式支付及公开发布。实际命令、阶段源码差量、截图与边界见design-qa.json。报告SHA256 `ffba9278e65ddddac402f1a2bb3702454c0e5541da24aedb3078859044ac05f1`。

![超距确认](/Users/dongge/.codex/tmp/yijiexing-native-permissions-20261025/coordination/evidence/ALERT-20261025-01/frontend/passenger-22-pickup-consent-pending.png)

![最终中文与模拟付款](/Users/dongge/.codex/tmp/yijiexing-native-permissions-20261025/coordination/evidence/ALERT-20261025-01/frontend/passenger-42-history-chinese.png)
