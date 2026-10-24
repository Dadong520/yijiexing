# UI-20261025-02 乘客 UI 非作者业务保护复核

2026-10-25（UTC+8）；审查者backend，非作者；ACK v2。结论：**PASS_CODE_REVIEW，当前冻结源码未发现业务阻断**。运行/视觉验收仍NOT_RUN（本角色）；由coordinator/frontend完成后决定发布。本角色只写本报告，未改源码、构建、部署、派代理或唤醒其他模型，已idle。

## 绑定范围

工作目录 `.coordination/integration-ag-final`；任务基线 `f0f0920995dcf89dfc5f6125542e26af86cf110d`。乘客版本只从1.1.7/code9改为 **1.1.8-experience/code10**，applicationId和云源构建字段未变。最终业务合并SHA待coordinator记录。

| 文件（apps/passenger-android内） | SHA256 |
| --- | --- |
| app/src/main/java/cn/hbzjhs/passenger/MainActivity.kt | `8fea85764ace542dc2f8d42d6ade471055259e89920e6c68838a23a822a93010` |
| app/src/main/java/cn/hbzjhs/passenger/UiComponents.kt | `f0c885800c34377d754b9e72ae9d541bb9b72d31353d82db841bcaeba7ffc243` |
| app/src/main/java/cn/hbzjhs/passenger/BookingScreens.kt | `22e64dcc7d098473621fb258f03df46ea3001901d4efb3c0d8a7dde822edb9ad` |
| app/src/main/java/cn/hbzjhs/passenger/ProfileScreens.kt | `866680269b00d638688c57474d0531d60c5633ad5cc56c29f75937deab0551e8` |
| app/build.gradle.kts | `0f61b6c9ed4e7a45fb174d753af66ea86104643e218e3cc5b13f30bffe7e460b` |

## 已审查的业务保护

1. **BookingScreen状态/提交不变**：用本机Python和git show对比，从函数签名到Scaffold前的完整区段逐字节一致，包含remember/rememberSaveable、active、submitOrder与异常提示。active仍为非DONE/CANCELLED，WAIT_PAY不能被首页绕过。payload仍包含kind、起终点名称和坐标、remark、city，预约scheduledAt转换，代叫riderId，两组途经点的name/latitude/longitude；原坐标校验、名称校验和动作回调不变。未新增本地券/折扣/加价入参。
2. **预约/代叫/无终点语义不变**：RESERVED日期时间选择仍有15分钟至30天说明；已选择riderId或本人仍走原字段；最多两途经点按非空名称加入，目的地待补的STANDARD仍移除目的地三个字段并禁止途经点。起终点互换仍同时交换两组坐标，历史路线填入仍带全部坐标，不是只换名字。预约沿用设备时区，未在本轮悄悄改变。
3. **回调与门禁保留**：底部呼叫按钮仍按active/busy使用原submitOrder/openOrder；新增进行中卡片只导航同一个orderId，不代提交。起点/终点仍打开原AddressDialog；用车方式、乘车人、备注和途经点仅重排。AddressDialog完整源码与基线逐字节一致，因此此前改名清旧坐标、查询取消/生命周期及WGS84兜底未被本轮UI重写。
4. **订单筛选和ID保留**：OrdersScreen从签名到PageColumn前的filter和阶段谓词逐字节一致；进行中仍包含WAIT_PAY/WAIT_CONTINUE等未结束单，筛选标题回调不变。卡片仍open(o.id)，仍调用IdentifierButton(o.id)提供编号查看/复制；金额来自服务端fare，无目的地仍标费用待定，不把0当免费。卡片颜色/布局没有改变订单权威状态。
5. **个人资料与退出确认保留**：昵称/城市remember、profile.update payload、昵称长度/忙碌确认条件均未被新布局删除；城市选择仍遵循enabled。编辑按钮仍进入原确认弹窗，退出仍经原AlertDialog调用store.logout，可取消；常用地址、乘车人、抬头/模拟发票、投诉、反馈、屏蔽、公告、设置、安全与演示入口仍使用原navigate回调。
6. **通用控件传参未丢**：MenuRow/AddressRow保持原onClick；Field保留onValue、keyboardType、singleLine/minLines；ActionButton保持enabled和onClick。PassengerModal保持confirmEnabled、onConfirm/onDismiss、safeDrawingPadding+imePadding、680dp最大高度、带weight且可滚动的内容区与固定操作行，键盘避让机制未被删。实际小屏/输入法可达性仍待设备验收。
7. **主题/营销边界**：PassengerVisualTheme只调整MaterialTheme颜色，PassengerApp的账号状态订阅、刷新和路由未改。券/积分/邀请仍明确0资产演示、不抵扣、不发奖；未复制注册送券承诺。未改共享订单、支付、退款、权益、会话或后台协议，未新增真实地图/自动定位/短信/警务能力承诺。

## 实际检查与发布边界

本角色实际运行：git diff读取5份变更；Python比较上述3段源码；对critical-baseline.json的13份文件计算SHA并用git show基线逐字节比较，**3/3区段一致、13/13文件一致**。这是静态保护证据，不是新增16项运行测试。全套后端测试未重跑。

**Gradle构建/单测/lint、设备覆盖安装、系统大字、输入法弹窗、预约/代叫/途经点点击、真实下单或付款、公开APK回读：NOT_RUN（本角色）**。本报告不自动覆盖coordinator正在构建的结果，也不代替frontend非作者设备验收。待这些适用门禁通过后，本角色无额外源码阻断，可由coordinator发布独立体验UI版；真实营销与完整跨端晚付链仍不因UI审查而成为通过。

补记司机对比度调整：已将当前司机UiKit的两处颜色常量反向还原后计算SHA，恰好等于前次审核的 `870800db238e5e12976164c64efb7c1e9080f5077df9f5f8dd6ffb0a164bb687`，证明该跟进仅改059669→007A5E、D97706→A65A0A，未改已审业务函数。当前司机UiKit SHA为 `2963ebece346a7447ef23642858f9fc58db663059a0a52d97d2774808f2e9efe`；对比度/视觉实际效果仍由frontend验证。
