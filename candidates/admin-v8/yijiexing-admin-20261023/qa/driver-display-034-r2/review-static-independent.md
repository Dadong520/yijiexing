# 司机0.3.4 r2独立静态审查

结论：**r2显示修复静态审查通过，未发现新增阻断；r2退款/收入显示仍待真实UI确认。** r1真实共享预付BID取消及模拟退款业务已由owner回测PASS；不能将该业务PASS写成r2新文案已验收。

## 范围与候选区分

- 2026-10-23，只读审查034 staging、r1归档源包及现有测试/构建结果。仅更新本报告，未改源文件，未操作设备/云/API，未重跑测试。
- r1原包、源码、31项测试、日志及原静态报告均留在 [candidate-r1](</Users/dongge/Documents/New project 2/yijiexing-cloud-20261023/qa/driver-034/candidate-r1>)，不覆盖。修复前8项中2失败的回归证据也保留。
- r2当前Gradle仍为0.3.4-cloud/code7，新增支付状态、退款金额与收入显示；APK哈希和实际安装身份由主任务另行归档。本报告不把相同版本号当相同APK。

## r1业务回测与显示缺陷

owner实际执行原单PORD5b52d7bea9c4，司机003、共享BID、模拟预付36元后，正常入口取消。主任务报告取消/退款业务PASS。本复核实际查看既有截图：

- [22司机退出](</Users/dongge/Documents/New project 2/yijiexing-passenger-20261023/qa/final-extra-ui/22-driver-034-exited-waiting.png>)显示已取消/已退出本次接驾，乘客等待继续或取消；旧支付行仍误写“未模拟支付”。
- [24乘客退款](</Users/dongge/Documents/New project 2/yijiexing-passenger-20261023/qa/final-extra-ui/24-passenger-refund-36.png>)显示“已模拟退款”和¥36.00。
- [27旧费用显示](</Users/dongge/Documents/New project 2/yijiexing-passenger-20261023/qa/final-extra-ui/27-driver-034-refund-fee-detail.png>)仍把36元显示为预计/司机收入、支付状态写未支付，正是r2要修的显示缺陷。这张旧图不能当作r2结果。

r1已验证业务与r2待验证显示分开；本复核没有代替owner操作UI，也没有重复提交取消。

## 相对r1的实现差异

只读与candidate-r1归档源码比较，生产代码差异限四个文件：

1. `DriverDomain.kt`：增加nullable refundAmount、paymentStatusLabel、refundAmountText、incomeLabel/incomeForDisplay。
2. `ConnectedStore.kt`：解析服务端refundAmount，缺失/null/非法负值保持null。
3. `CoreScreens.kt`：订单详情使用统一支付标签，并在有有效退款金额时显示模拟退款。
4. `BusinessSupport.kt`：费用页共用标签与收入显示，顶部和明细行一致。

新增 `PaymentStatusTest.kt` 九例；`DriverCancellationTest.kt`字节与r1归档相同，canCancel表达式没有改变。没有新增请求、支付/退款动作、资格或金额计算策略、权限、依赖或导航逻辑变化。

归档源包README/INTEGRATION有打包时的候选说明，当前staging文档仍有0.3.1/code4旧文字；local.properties是未纳入归档的本机配置。它们不是本次生产逻辑差异。已提醒root在r2打源包时更新候选文档，不把旧README交付；本复核不改这些文件。

## 显示规则核对

| 重点 | 静态结论 |
|---|---|
| 已知支付状态优先 | REFUNDED/SUCCESS/FAILED/PROCESSING/CANCELLED/UNPAID分别明确显示模拟退款/已付/失败/处理中/取消/未付。不会因退款后的paid=false又写成未支付。 |
| 旧私有快照回退 | 只有paymentStatus缺失/空字符串才按paid回退；保留旧单兼容。未知非空状态显示“支付状态待确认”，不凭paid冒称支付成功。 |
| 退款金额 | 只用服务端refundAmount、整数分经money格式化；仅REFUNDED且金额非负时显示。缺失/null/无效值不拿fare或0伪造退款金额。已知0分会如实显示0.00。 |
| 退款/取消且未credited | closedWithoutIncome成立，统一标签“本单未计入收入”、incomeForDisplay=0；保留名义订单金额36作为订单金额，不将其当收入。已退出司机投影也覆盖。 |
| 未入账且未关闭 | PICKUP/TRIP/WAIT_PAY以及尚未credited的DONE均标“预计收入（未入账）”，不因stage=DONE或paid=true就写实际收入。 |
| 已入账 | 只有服务端credited=true标“本单已入账收入”。当前精确v7 credit按fare入账、费用接口income也为fare且费率0，因此已入账显示金额与当前后端口径一致；不是另算真实分账。 |
| 无本地order | 费用页面只标“预计收入（未核对入账）”，不宣称已入账；此回退不是已确认退款状态。 |
| 两页一致 | CoreScreens订单详情、BusinessSupport费用页均用同一支付标签/退款格式；费用页顶部和底部收入行共用同一label/value，避免同页矛盾。 |

依据：[统一显示逻辑](</private/tmp/yijiexing-driver-034-staging/android/app/src/main/java/cn/hbzjhs/driver/preview/DriverDomain.kt:72>)、[退款解析](</private/tmp/yijiexing-driver-034-staging/android/app/src/main/java/cn/hbzjhs/driver/preview/ConnectedStore.kt:580>)、[费用页](</private/tmp/yijiexing-driver-034-staging/android/app/src/main/java/cn/hbzjhs/driver/preview/BusinessSupport.kt:264>)、[精确v7入账](</private/tmp/yijiexing-passenger-v7/cloud-service/domain.py:110>)、[精确v7费用](</private/tmp/yijiexing-passenger-v7/cloud-service/domain.py:329>)。

## 测试与构建证据：仅查阅既有结果

- r1：31项0失败；修复前8项取消测试2失败，准确覆盖033全局!paid错误。见 [r1原审查](</Users/dongge/Documents/New project 2/yijiexing-cloud-20261023/qa/driver-034/candidate-r1/review-static-independent.md>)。
- r2当前XML：ConnectedContractTest 7、DriverCancellationTest 8、NavigationContractTest 7、NegotiationContractTest 9、PaymentStatusTest 9，**合计40项，0失败/0错误/0跳过**。本复核直接解析root现有XML，没有重跑。
- [build-r2-final.log](</Users/dongge/Documents/New project 2/yijiexing-cloud-20261023/qa/driver-034/build-r2-final.log>)已有 `BUILD SUCCESSFUL in 15s`，testDebugUnitTest及assembleDebug完成。不能据此宣称r2已安装或显示通过。
- [PaymentStatusTest.kt](</private/tmp/yijiexing-driver-034-staging/android/app/src/test/java/cn/hbzjhs/driver/preview/PaymentStatusTest.kt>)九例覆盖：退款精确金额、已知状态优先、旧私单回退、未知状态、无效退款值、非退款不显金额、退款未入账0收入、未入账标预计、credited才标已入账。原8例取消测试未改。

## r2仍待UI证据

在r2实际安装身份确认后，使用现有已退款单刷新核对：订单详情与费用页均显示“已模拟退款/¥36.00”，收入为“本单未计入收入/¥0.00”；未入账有效单标预计，已credited单才标已入账。无需再制造一笔取消来证明纯显示修复。旧r1截图、现有单测或本报告不替代r2截图。

当前只解除静态/构建方面阻断，不替主任务发布，也不宣称r2新增文案已完成真实双端验收。
