# PA-20261025-01 后端角色独立源码复核

时间：2026-10-25T02:07:00.185511+08:00；审查者backend，非本批作者；ACK v2。结论：**PASS_CODE_REVIEW（限定下述冻结源码），未发现需阻止后续构建/设备验收的源码问题**。不等于运行验收或已发布。当前保持idle。

## 基线与哈希绑定

读取 `.coordination/integration-ag-final/apps/passenger-android`；作者冻结记录 `frontend-implementation.json` 基线为 `097e3e47ea05f545705a588a9da7beb23bb9ec4b`。实际7/7变更文件SHA匹配；5/5关键文件SHA匹配且用git show与该基线逐字节核对一致：PassengerStore、PendingRequestJournal、PaymentConfirmation、Negotiation、OrderFarePresentation。未切分支或改产品。

| 冻结变更 | SHA256 |
| --- | --- |
| PassengerLocalExtras.kt | `3e83db4921770685792acad2a53e185140116c8c6791091861ff21811c11fd30` |
| ParityScreens.kt | `7e3a191ac21beff50d5f6aa7642824eddeb6dda95aa480e271a4485f86595f84` |
| AddressSearch.kt | `6b8f574aa7ccb354e5fc5dd90c0a6385329c473660631b128726997a0d65c0c7` |
| MainActivity.kt | `cd8b9ab56cf2d83d92a0ab98361c6a27f4158882d0d224451192942a7deb90ff` |
| ProfileScreens.kt | `7d08176ae60dd90abc3ccb3b0c3ad3714e67c6f8e6b085b446d89f19f0d1e441` |
| OrderDetail.kt | `87ee8974947a9f3befa8af9219496171aa02ede5db74726356af29b2ec248fa0` |
| BookingScreens.kt | `7cca6e6ee90cf712def4b1d188553714d586545e8d8df4db949595342f2fbc4d` |

## 独立审查结论

1. **身份隔离/未登录**：MainActivity仍先以logged门禁显示LoginScreen；新增入口只取共享state.passenger.id，未从手机号尾号生成owner。PassengerExtrasScreen拒绝blank/null owner；PassengerLocalExtras以owner的SHA256生成当前安装私有preferences键，无anonymous/旧全局key回退。SafetyCenter联系人及编辑、删除、错误状态均remember(owner)，切号后重新初始化；不把上个账户列表用于新owner。账号哈希是命名隔离，不是加密保证，未宣称跨设备同步。
2. **营销范围真实**：券/积分/邀请均明确演示、默认0，无订单抵扣或奖励API；剪贴板明确无注册奖励/实际抵扣/拉新绑定，无伪造邀请码。没把小程序历史380分或注册送20元文案带入。
3. **联系人和拨号**：输入长度/电话格式、最多3条、删除确认、同步commit失败提示已存在；仅用户主动ACTION_DIAL，不能自动拨出/发短信。无新增录音、短信、警务定位等能力承诺。
4. **司机卡**：只读当前订单driver；姓名/车型/车牌/掩码电话、企业/服务分/资质字段缺失均为未提供；不硬编码4.9、860单或颜色，不宣称真实警方或身份认证。
5. **名称/坐标绑定**：系统Geocoder在IO线程返回名称和同一结果坐标；选择时一起写入。编辑名称立即清空旧坐标和旧结果、取消旧搜索；未选中/未手填匹配坐标不能保存。常用/体验地点保留完整三元组；手工WGS84入口标明为手动，不冒充定位。经纬度finite/范围检查存在。
6. **异步生命周期**：rememberCoroutineScope随AddressDialog离开组合取消；CancellationException继续抛出，不当普通失败；cancelSearch先取消job再清等待/结果，结果回传还核对查询名，取消job的finally不改新搜索等待状态。provider阻塞请求可能继续在系统执行，但withContext取消可防失效UI回传；未发现把旧搜索结果自动写入新坐标的路径。
7. **核心回归保护**：除新增入口、说明、司机卡弹层与地点dialog外，OrderDetail原支付/取消/WAIT_CONTINUE/投诉图片处理未重写。5个金额/会话/幂等核心文件确为原样；未新增向服务端提交本地营销字段。

## 验证范围与发布条件

本角色实际执行：限定源码与git diff阅读，Python逐文件SHA对照，以及git show基线字节比较；均通过。**Gradle/单测/lint/安装/设备/云接口：NOT_RUN（本角色）**；没有用既有测试数量替代本次运行验收。

可发布功能范围建议：Android 1.1.7体验版的现有真实共享订单业务 + 明确演示的券/积分/邀请 + 按账号保存在本机的联系人 + 系统Geocoder搜索 + 后端司机字段服务卡。没有真实营销账务、地图SDK/自动定位/导航、短信发送或警方联动。系统Geocoder可用性、服务返回坐标/地点准确性需设备验证，不能声称Android已有腾讯地图或与微信地图权限相同。

**发布前门禁仍由coordinator完成并记录**：相关测试/构建；同签名覆盖安装；未登录/切号和联系人CRUD；地址搜索成功/失败/取消、改名清坐标与手动兜底；卡片缺字段；支付确认/继续叫车/投诉附件受影响回归；最终APK版本/签名/哈希与公开回读。现有旧单晚付完整跨端链尚无本轮执行证据，不标通过。完成这些门禁后，本角色无额外源码阻断，不要求重复未变后端159项。

未写STATE/BOARD/产品，未启动代理、模型、云或设备。最终合并/发布SHA由coordinator填写；本角色仅本审查文档，不自行提交或部署。
