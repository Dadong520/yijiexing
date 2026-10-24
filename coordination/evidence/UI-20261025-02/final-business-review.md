# UI-20261025-02 最终业务保护补记

时间：2026-10-25T02:42:38.024166+08:00；backend非作者；ACK v2。结论：**PASS_SCOPE**，适用于冻结提交 **`d63fb82620c77466177b80a96e93c45e2428ac74`** 的两端UI及版本变更，无新增业务阻断。基于此前driver-backend-review.md、passenger-backend-review.md，再核对本次最小跟进。未重复构建/设备/后端测试，未改产品、部署或唤醒他人，完成后idle。

## 最小跟进

- 司机定位与播报改Column；工具展开区域具56dp最小点击高度；企业信息改为独立Column中的16sp Text并允许换行。是布局/可读性跟进，没有改features、状态筛选、回调、待收款不占车或金额字段。
- 乘客主题保留白色surface并透明surfaceTint，正文恢复16sp；NavigationBar tonalElevation=0；Panel间距由12改8；地点说明移动到更多行程设置后。控件/字段与业务回调仍在，无新账务/权益承诺。
- 源码EOF空白整理没有改变请求语义。当前审查以Git提交对象读取，不以仍可变化的工作树代替冻结源码。

## 实际验证

Python对critical-baseline.json的13文件逐一SHA对照，并与基线 `f0f0920995dcf89dfc5f6125542e26af86cf110d` 的git show字节比较：**13/13保持一致**。BookingScreen从函数签名到Scaffold前（包含remember/active、完整submitOrder payload与校验）与同基线仍逐字节一致。主题16sp/透明surfaceTint/tonalElevation及地点说明顺序的限定断言通过。上述是源码保护检查，不计作运行测试数量。

协调者报告最终司机55/乘客20项测试和构建SUCCESS；本角色未重跑，不能作为本角色的独立运行证据。frontend最终包视觉PASS、同签名覆盖、公开APK完整回读仍由协调者按任务门禁归档。本结论允许该冻结源码进入统一体验APK发布流程，不能替代这些门禁，也不批准改后端/admin。

## 最终8文件SHA256

| 提交内路径 | SHA256 |
| --- | --- |
| `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/CoreScreens.kt` | `446a760016c95a16dca2989bb7bb6c87ad34dbe9e96f4e592ebaf37a397ec2ab` |
| `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/UiKit.kt` | `68f87c953644ae52f4b90aa26a9d734cf91ec306d5186c015b47b5814bfd23dd` |
| `candidates/driver-0.3.6-r2/android/app/build.gradle.kts` | `2fad7c48287162816fe3e125356548b0d7d450f0bc5dac794ab5b68a486fdcfa` |
| `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/MainActivity.kt` | `ea688f4dc4710131c708fe6533812c78de1229ab94b1613828109d6c658e3e4c` |
| `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/UiComponents.kt` | `9c24a384e7dd2ecc1dd77e182068d26966469cf25f59220b28edaacafbacaec4` |
| `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/BookingScreens.kt` | `8b8f8de374c9e53fae36b37819ffb2d59593116cc789962b737031957db80fdf` |
| `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/ProfileScreens.kt` | `866680269b00d638688c57474d0531d60c5633ad5cc56c29f75937deab0551e8` |
| `apps/passenger-android/app/build.gradle.kts` | `0f61b6c9ed4e7a45fb174d753af66ea86104643e218e3cc5b13f30bffe7e460b` |

设备/输入法/大字实际操作、完整跨端新旧单晚付、部署与公开下载：**NOT_RUN（本角色）**。真实营销、真实支付、自动定位/道路导航及短信/警方联动未因UI发布而实现。最终发布负责人coordinator；本报告本身无新增源码提交SHA。
