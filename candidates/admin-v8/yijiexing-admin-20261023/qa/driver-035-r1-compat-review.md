# 司机 0.3.5 r1 协议与状态兼容独立静态审查

结论：在本次读取的稳定候选与0.3.4归档对比中，**未发现由 UI 重排引入的明确业务阻断或权限放宽**。该结论允许进入候选实际UI业务回归，不等于已通过两设备操作、视觉可读性或云端验收；本审查没有操作设备、登录云端、修改源码或运行候选业务。

## 绑定范围

- 候选源码：`/private/tmp/yijiexing-driver-035-staging/android`。
- 对照归档：`outputs/宜捷行谷歌云体验版_20261023/源码与部署工具.zip`，前缀 `yijiexing-driver-cloud-0.3.4-source/android/`。
- 实际读取APK：`yijiexing-cloud-20261023/qa/driver-035/app-035-r1.apk`。
- 独立计算SHA256：`8d63ba3afec0b0e530d0a5731ba52221d2dc843d0c651448ea38a366a1178ef5`，与交付标识一致。本审查没有逆向APK证明其每一源码对应关系；源码与构建绑定仍依据构建方manifest。
- 独立计算16个Kotlin改动文件，与 `source-scope-r1.json` 一致。另 `app/build.gradle.kts` 改动，Manifest未变。
- 证据：`driver-035-compat.diff`、`driver-035-compat-identity.json`。构建方报告40测试通过，本审查未重复运行或记为独立执行。

## 已核对内容

| 范围 | 证据及结论 |
|---|---|
| 网络提交/幂等 | ConnectedStore.kt与归档字节一致；perform仍统一分派/api/action及会员接口，action/payload/requestId、pendingKeys策略、失败回调及accept/refresh未变。当前既有LocalApiException会移除pending key的行为也未改变，不能声称所有错误/重启均保证同一键。全部Kotlin内业务action字面量的多重集合未变化；这只是辅助检查，另人工复核了变化调用点 |
| 取消及退款 | DriverDomain.kt字节一致，canCancel仍保留预付出发前取消、参与态已取消排除；paymentStatusLabel/refundAmountText/incomeLabel/incomeForDisplay未变。CoreScreens.ConnectedTrip把取消入口从页头移至底部，仍受同一order.canCancel条件控制、调用同一onCancel。MainActivity取消原因/其他原因校验、id/reason payload、order.cancel处理器未变 |
| 行程确认 | ConnectedTrip仍由原Stage分支决定order.advance或刷新；TRIP途经点限制、ARRIVED缺目的地提示和尾号服务端校验保留。尾号输入仅数字最多4位、失败提示与成功关闭弹窗处理器未变；弹窗增加滚动，按钮变为同参数转发的CoreTextButton |
| 金额 | 金额仍使用Long整数分与原money格式化；费用页继续调用原incomeForDisplay，取消且未入账显示0收入，退款独立展示，没有把退款数当作收入。新增折叠费用明细只包裹原手续费/政策说明。订单详情继续保留destinationPending费用待定，不改fare。OrderCard对BID仅AVAILABLE显示参考报价，其余状态改为订单金额，未重新计算选定报价 |
| 业务确认/按钮包装 | MainActivity确认仍先关闭confirm再调用原c.action；取消对话框确认仍使用原校验。BusinessTextButton/BusinessOutlinedButton/CoreTextButton/PrimaryButton均透传onClick和enabled，新增高度/字号未强制enable。会员购买Footer将Button换成PrimaryButton时enabled与回调保留 |
| 权限边界 | Manifest字节一致，无新增Android权限。听单/当前活动单/会员不可用条件重排后，附近订单与议价/热力入口仍要求active为空、canAccept、online；具体入口继续检查canNegotiate/canHeatmap，未新设管理员/沙箱代付动作 |
| 配对/导航/定位 | NegotiationContract.kt、NegotiationSupport.kt、NavigationContract.kt、TripWaypoints.kt、DriverLocationService.kt、DriverSpeech.kt、MembershipModels.kt均与对照归档字节一致。配对码、quoteVersion及amount的原约束未因本次UI变化被替换 |

## 候选UI回归仍需确认

1. 实际BID预付PICKUP下，移到固定底部的取消入口在目标字体倍率及导航栏下可见可点，原因确认后退款只发生一次。静态条件存在不能替代可操作验证。
2. 尾号确认、取消原因及支付/会员类通用确认弹窗在键盘与大字下可操作；已有按钮未全部按busy禁用的行为不能通过此次静态结论宣称已改善。实际回归应验证快速触点不产生重复业务效果。
3. 费用页折叠后，用户仍能打开费用和模拟政策说明；退款、未入账收入与订单金额的主次关系由独立截图审阅确认。

以上是本轮UI候选新增回归关注点，不认定为已复现缺陷。未发现需要在冻结后端增加兼容分支的改动。若候选源码或APK变化，应对新hash补充差异审查。
