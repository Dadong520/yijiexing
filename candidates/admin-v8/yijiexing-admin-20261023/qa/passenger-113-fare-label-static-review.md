# 乘客 1.1.3 费用标签修正只读复核

2026-10-23。审查 agent 只读复核，没有修改 Android/后端、重跑测试或操作设备。本文件为源码复核，不等同于构建及双端界面验收。

结论：当前最小修正未发现新增阻断。OrderFarePresentation 以 driverId 是否存在区分未选与已选司机报价；这与实际选定后 fareSource 仍为 DEMO_ESTIMATE 的后端契约一致。JSONObject.text 会将 JSON null 转为空串。未选 BID 为“体验估价（参考）”并说明应看司机报价列表；费用查询行是“参考车费（体验估价）”；已选司机则为“选定司机报价”。

OrderDetail 仅替换费用和查询行的标签及说明，金额仍读取原 fare，保留无终点费用待定和退款金额展示。原费用查询缓存会随 id/fare/paid 变化重置。未改下单、选司机、支付、退款或后端计价规则。

新增两个测试使用真实缺陷形态：未选时 fare2926 与司机报价3600 并存；已选 driverId 存在但 fareSource 仍为 DEMO_ESTIMATE。覆盖 PREPAY/PICKUP/WAIT_CONTINUE/CANCELLED、续叫清除 driverId 后恢复参考估价，以及 STANDARD/NEGOTIATED 标签。测试结果和最终 APK 哈希待构建方提供；本审查未代跑。

与已归档 1.1.2 源码包的 Kotlin 文件逐字节比较：已有文件仅 OrderDetail.kt 改变；新增 OrderFarePresentation.kt、OrderFarePresentationTest.kt。旧 1.1.2 界面、失败证据和通过范围保留；后续 1.1.3 界面复测应另立新订单记录。

## 构建方结果的只读核验

已核验 1.1.3-experience/code5 的 canonical APK：SHA256 `dca3ecee4389a2dba8c59610be2c24e99ef511407c811ecaef924bbde6d92fab`，8,126,801 字节，与 `android/verification/1.1.3/build-1.1.3.json` 一致。四套新 XML 时间为 UTC 2026-10-23T15:17:40，总 14 项，失败/错误/跳过为 0；包含本次两项标签测试。构建记录为 assemble/test/lint 通过，lint 无错误（2 警告、2 提示），签名记录与 1.1.2 相同。

以上是文件和构建结果复核，尚不计为新 APK 设备流程通过。旧版本的 12 项测试和反重力本轮独立 12 项结果保持原日期与版本，不改写成反重力已测 1.1.3 的 14 项。
