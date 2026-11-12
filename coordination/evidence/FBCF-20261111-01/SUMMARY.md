# FBCF-20261111-01 双 Android 第二阶段交接

实现完成，**作者自测通过；待 backend 非作者审查及 coordinator 集成验收**。未提交、未启动模拟器、未打包发布。当前分支 `claude/feedback3-20261110`，HEAD `22e60230edebf3a53d5f9ace48c08aa4eb4ced11`，本次改动尚在工作区。严格只改两个 Android 目录；未修改小程序、后端或后台文件。

## 完成的接线

- 乘客版本 26 / `1.1.24-experience`，司机 28 / `0.3.25-cloud`。两端登录页新增选填邀请码，login/loginExperience 非空时 trim + Locale.ROOT 大写传 inviteCode，空时省略。
- 乘客 Booking：按 cityPricing / 当前 pricing 回退默认 tariff；各段直线里程×1.3 与现有服务端预估模型一致。500米进位、9km后单位回空加收、23:00–05:00整分钟含夜间、夜间与回空相加、每计价单位 HALF_UP 分值。预约按与提交相同的时区转换至上海判断。显示运价费、正额区域服务费、可选券（自动最大/不使用/切换）、抵扣和预计实付，明确预估来源、不含实时等候、最终服务端结算。券只抵运价，不抵服务费，提交选中couponId；UNUSED且达到门槛且在有效期才可选。
- 乘客欠费：CANCELLED订单的UNPAID cancelFee也可发现；首页/订单列表横幅、全局提示及底部入口可进该单结清。订单详情醒目模拟支付，先显式确认，再重查账号/订单状态/金额，携id+expectedAmount提交order.payCancelFee，失败保留重试；不自动支付。
- 乘客费用：tripFare/serviceFee/couponAmount+label/fare、空车费状态；展开接口明细展示里程来源、等候费/分钟、夜间、回空标记。
- 卡券：服务端我的卡券（可用/锁定/已用/过期、面额、门槛、有效期）及领券中心，coupon.claim；熔断/领取报错显式提示“当前营销余额不足，无法领取”，并保留错误原因。profile可用券数实时读取。
- 邀请：referral code复制、人数/达标/积分、注册或首单/固定或阶梯/累计/100人上限、记录；profile摘要。司机同样按referral.rule展示，去除旧合成活动占位说明。
- 回执：列表直接显示冲红/重开待审、通过、驳回；详情显示reissuedAs/reissueOf和申请审核记录。符合状态可填写原因、个人/企业抬头、企业税号及邮箱后提交invoice.reissue.apply；确认时再次核对账号与回执状态。
- 司机费用：运价费、支付手续费0.6%、分账费0.15%、地图费、司机分账金额；正额乘客区域服务费明确不计司机收入。保留原预计/入账状态提示。
- 司机服务分：服务端levels等级区间、每条scoreAfter；公司UNSIGNED在首页/个人中心提示开通模拟分账后可上线。

## 实际验证

- 乘客 `testExperienceUnitTest compileExperienceKotlin`：**84 tests，0 failures/errors/skipped**。
- 司机同任务：**127 tests，0 failures/errors/skipped**。
- 新增7项纯函数场景：距离进位/9km边界、夜间含05:00与加法叠加、城市配置与不抵服务费、券门槛/状态/有效期、环线逐段及无效坐标、单位分值HALF_UP、取消欠费检索。
- 小程序 `npm test` **74/74**；`npm run check` PASS。真实微信编译仍 NOT_RUN。
- `git diff --check` 两目录 PASS；中央真实ID memory gate PASS（结构门禁不等业务验收）。

两个命令入口差异已保留在 TEST_RESULTS：用户给定小程序父目录没有package.json，改在其miniprogram子目录运行；司机wrapper原本无执行位，使用bash gradlew，不改权限。最终日志和原JUnit副本见本目录。全部用既有JDK17/SDK34/offline init脚本执行。

## 未验与下一步

原生 UI/小屏/IME/实际交互、与并发后端真实请求联调、APK/签名、独立业务验收、部署均 **NOT_RUN**。此处金额计算只为预估，服务端仍校验券/欠费/金额/状态及幂等；没有扩展真实资金、税务、银行能力。请root安排backend重点复核计价/券/空车费请求及共享契约，后续由root负责集成、合并与发布。本角色未启动任何AVD/服务，无待清理运行资源。

## 修改文件（相对源码根）

- `apps/passenger-android/app/build.gradle.kts`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/BookingScreens.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/ExtraScreens.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/Feedback3Rules.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/Feedback3Screens.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/MainActivity.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/OrderDetail.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/ParityScreens.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/PassengerConfirmPrompts.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/PassengerStore.kt`
- `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/ProfileScreens.kt`
- `apps/passenger-android/app/src/test/java/cn/hbzjhs/passenger/Feedback3PricingTest.kt`
- `candidates/driver-0.3.6-r2/android/app/build.gradle.kts`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/BusinessCommunity.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/BusinessSupport.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/ConnectedStore.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/CoreScreens.kt`
- `candidates/driver-0.3.6-r2/android/app/src/main/java/cn/hbzjhs/driver/preview/Feedback3Presentation.kt`

源文件SHA256及精确JUnit路径见 TEST_RESULTS.json。无待合并提交SHA（按指令不commit）。
