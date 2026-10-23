# 司机0.3.4取消规则独立静态审查

结论：**静态审查通过，未发现新增发布阻断；候选可继续真实UI验收。** 本报告不代表已在设备上验证共享预付BID取消、退款或乘客续叫。

## 实际范围

2026-10-23，只读比较033与034 staging、审查DriverDomain及新增8个JUnit用例，并查阅root已经运行的日志/XML。未重跑测试、未修改源文件、未操作设备、云资源或API。没有把构建和单元测试写成双端UI实测。

对比排除build、.gradle、verification及.git生成/证据目录后，只有三项差异：

1. `app/build.gradle.kts`：仅版本从code6/0.3.3-cloud升到code7/0.3.4-cloud。
2. `DriverDomain.kt`：仅canCancel表达式及其两行说明注释变化。
3. 新增 `DriverCancellationTest.kt`。

没有其他实现、UI、接口、权限、依赖、邀请码或导航逻辑改动。

## 规则核对

实际表达式为：

```kotlin
!participationCancelled &&
    stage in setOf(BID_SENT, PREPAY, RESERVED, PICKUP, ARRIVED) &&
    (!paid || (shared && stage != BID_SENT))
```

与精确v7后端共享取消规则一致：在已指派本人的PREPAY/RESERVED/PICKUP/ARRIVED允许取消，不因预付而隐藏入口；后端负责退款及状态转换。非NEGOTIATED共享单进入WAIT_CONTINUE，NEGOTIATED直接CANCELLED；司机投影的已退出状态仍禁止再次取消。无需额外按kind收窄。

- 共享已预付BID的PICKUP/ARRIVED现可取消，修复本次缺陷。
- 私有已付款订单仍全部拒绝；私有未付款旧流程保持。
- BID_SENT仅未付款可撤回，未放开不合法的已付待选报价。
- AVAILABLE/TRIP/WAIT_PAY/DONE/CANCELLED仍无取消入口；participationCancelled始终拒绝。
- 客户端没有本地退款、改paid或修改共享订单状态；仍等待服务端响应。不扩大到行程中取消，也不修改后端资金/原子性逻辑。

代码：[DriverDomain.kt](</private/tmp/yijiexing-driver-034-staging/android/app/src/main/java/cn/hbzjhs/driver/preview/DriverDomain.kt:101>)；后端依据：[精确v7共享取消](</private/tmp/yijiexing-passenger-v7/cloud-service/passenger.py:288>)、[乘客取消/续叫](</private/tmp/yijiexing-passenger-v7/cloud-service/passenger.py:196>)。

## 八个用例的边界覆盖

| 用例 | 评估 |
|---|---|
| 解析共享BID预付快照后仍可取消 | 覆盖PICKUP/shared/paid=true与真实JSON解析，准确复现入口缺失。 |
| 共享已选阶段 | 覆盖PREPAY/RESERVED/PICKUP/ARRIVED，已付/未付均允许。 |
| 未指派、行程中和结束阶段 | 覆盖AVAILABLE/TRIP/WAIT_PAY/DONE/CANCELLED，已付/未付均拒绝。 |
| 私有已付 | 遍历所有现有OrderKind及Stage，防止顺手放开旧私有退款。 |
| 私有未付 | 保留原五个可取消阶段。 |
| 报价撤回 | 未付BID_SENT允许、已付BID_SENT拒绝。 |
| 已退出司机 | 遍历Stage与paid两值，driverParticipationStage=CANCELLED均拒绝。 |
| 退款续叫投影解析 | CANCELLED + canonical WAIT_CONTINUE + REFUNDED解析后不活跃且不可再取消。 |

这些是独立业务边界断言和解析契约测试，不只是逐字复述表达式；但未实际发出取消API，也未验证服务端真实退款。

## 查阅既有测试结果

- [修复前XML](</Users/dongge/Documents/New project 2/yijiexing-cloud-20261023/qa/driver-034/regression-before-fix.xml>)：8个用例、2失败、0错误。失败为预付BID解析后的取消入口、共享已付阶段允许取消；对应033全局!paid限制。原始[失败日志](</Users/dongge/Documents/New project 2/yijiexing-cloud-20261023/qa/driver-034/regression-before-fix.log>)保留。
- 034当前XML：ConnectedContractTest 7、DriverCancellationTest 8、NavigationContractTest 7、NegotiationContractTest 9，**合计31项，0失败、0错误、0跳过**。为本次独立读取root现有结果，不是本代理重跑。
- [构建日志](</Users/dongge/Documents/New project 2/yijiexing-cloud-20261023/qa/driver-034/build-final.log>)已出现 `BUILD SUCCESSFUL in 24s`、testDebugUnitTest和assembleDebug完成。构建完成不等于设备/UI验收完成。

测试源码：[DriverCancellationTest.kt](</private/tmp/yijiexing-driver-034-staging/android/app/src/test/java/cn/hbzjhs/driver/preview/DriverCancellationTest.kt>)；结果目录：[JUnit XML](</private/tmp/yijiexing-driver-034-staging/android/app/build/test-results/testDebugUnitTest>)。

## 尚待真实验收

用当前共享预付BID通过正常App入口取消一次；核对司机退出投影、乘客WAIT_CONTINUE、36元模拟退款及两端刷新；乘客选择继续/取消分支应保留其独立证据。不要把本次只读报告当作已经完成这些动作。
