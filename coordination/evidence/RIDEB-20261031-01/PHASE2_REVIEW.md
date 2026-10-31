# RIDEB Phase2 差量独立源审

**FAIL：P1新增0，P2阻塞1。** 候选 `4a6a2970d81c62dca59f64b9deb92831b75b5841`，差量基线 `ff53f570296d0bd97d8dc7ee4e16c915987a8542`。仅源审；releaseReady=false。

## P2 RIDEB-P2-NESTED-RETURN
BookingScreens.kt **433–434** 仅返回首页时arm；途经点返回直接 `editor="destination"`，427的key切换会使733处目的地AddressDialog得到新的未提交session。748–769的行/结果、780处地图选点不检查返回保护。第一tap选途经点后，第二tap若落到新页的有效行，目标/owner检查仍通过，493–497写最终目的地，随后才为首页arm。这样同一连点序列能误改终点。作者PHASE2.md也明确列为残余。本轮源码已确认无防护；尚未实测新页具体命中坐标，不声称已在4a6a复现自动叫车/支付。

建议：返回目的地选址页之前启用跨页保护，在新页消耗session或开始可提交map lookup之前检查窗口；不要仅拒绝save回调造成session已消耗。700ms后正常选点；仍一击回填，不增加第二确认按钮。frontend需要补途经点→目的地的原生重合行连点、无错误终点/无请求、期满正常选择。

## 其余差量
- ReturnTapGuard PlaceSelection.kt:118–141用单调nanoTime差值，700ms自动开放；rearm重计，倒退开放，不持久化。Compose包装BookingScreens.kt:512–543以同一guard控制点击时检查与enabled，generation重启等待并settle，源码未见无限锁。
- Booking首页确认呼叫/当前行程与交换均有enabled及点击时检查；返回首页先arm再editor清空。owner-keyed生命周期和原owner/target/GPS/业务门禁未变。**原RIDEF-R1主路径修复PASS_SOURCE，但不能替代原生P1关闭证据。**
- Profile在选择/返回前arm，保存/默认/取消/外点内部均检查，保存/取消外观禁用；仍需独立保存。OrderDetail先arm再打开重新估价确认，确认/暂不修改/返回及底部取消/支付入口同样检查，服务端规则不变。取消选址不保存；窗口内忽略退出为刻意短保护，窗口后按正常动作恢复。
- Profile shield随编辑器销毁；Booking随owner销毁；OrderDetail未按id重建shield，只可能给切换订单遗留至多700ms的短禁用，无身份/业务数据携带，未列P2。

## 实际检查与边界
中央入口门禁PASS；HEAD准确且clean；Git差量仅5个获准乘客产品/测试文件；diff --check exit0。新增5guard单测只读检视，未编译/执行；未跑GUI/API/DB/云。原SOURCE_REVIEW.json/md与FINDINGS_ff53原封保留，前者只说明当时源审，不能覆盖后续真实P1。

下一步：root安排上述P2有界修复，frontend对新准确SHA复验原P1、嵌套返回和700ms后正常操作。root唯一整体验收、合并与发布。
