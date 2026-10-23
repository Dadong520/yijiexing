# 司机0.3.5 r3冻结候选增量兼容审查

结论：r1审查后至r3的增量中，**未发现新增协议、业务状态、金额计算、幂等或权限阻断**。可以进入两设备候选UI回归；本审查不替代实际交互、字体与地图署名可读性检查。

实际独立计算冻结产物：

- APK `yijiexing-cloud-20261023/qa/driver-035/app-035-r3.apk` SHA256：`6b9620a237286002662a966a31ba110f50d3a13c2ca4c113961792b32ee0b4ea`。
- 源码 `android-source-035-r3.zip` SHA256：`3739dcb34791138f2c0e34c707b200175a49fbffe11c7f67195c1cd4291dad13`。
- 两者均与发布负责人提供的冻结标识一致。

对r1/r3源zip的所有Kotlin、XML与Gradle脚本比较，仅3个文件变动，完整增量保存在`driver-035-r3-compat-delta.diff`：

| 文件 | 增量与兼容判断 |
|---|---|
| CoreScreens.kt | 去掉一行共享环境解释文案；订单筛选从ScrollableTabRow改为FlowRow/FilterChip，selected索引、回调赋值及原列表过滤逻辑不变，没有改订单读取/状态转换 |
| UiKit.kt | OrderCard在destinationPending时显示“费用待定/目的地待补充”，替代误导性0元显示；其余fare、BID参考报价条件与onClick保持，未修改权威金额或下单参数 |
| NativeMap.kt | 地图外增加Column，将署名从地图底部叠层移到地图下方Text；原地图定位按钮、坐标转换、地图网络/导航行为未修改。实际高度与署名展示由专属QA截图验证 |

另独立比较r2→r3，只有NativeMap.kt变化，与“r3仅地图署名位置修复”一致。r1后未变的网络/状态模型/NEG/幂等/权限结论继承[driver-035-r1-compat-review.md](driver-035-r1-compat-review.md)，仍不扩大其静态审查范围。

构建方报告40项测试通过，本审查未重复运行，未修改源码、未操作设备或云端。此处结论仅绑定上述r3哈希。
