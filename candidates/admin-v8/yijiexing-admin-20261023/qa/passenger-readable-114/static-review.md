# 1.1.4 订单信息显示修正：独立静态复核

审查 agent 只读源码，没有操作模拟器、调用云接口、重跑构建或编辑 Android 文件。当前静态检查未发现此最小变更新增的 P0/P1；构建与实际显示验收仍需新证据。

- `orderSummary`仅输出已知中文订单类型与解析成功的本地时间；无效时间省略，未知类型显示“出行订单”，不回退为内部元数据。
- `IdentifierButton`按原始值展示并由用户点击复制；弹窗状态按value重置，空值无入口。订单编号仍可供查询和售后核对。
- 详情、订单列表、投诉及模拟回执仅调整标识显示；原ID作为查询/动作键继续使用，起终点仍读原字段，没有把英文测试地址硬编码替换。
- 两项新增测试调用实际展示函数，覆盖有效偏移时间、无效/缺失时间回退，以及不改原ID与origin。测试不等于真实Android剪贴板/弹窗操作验收。
- 与已归档112源码逐字节对比，PendingRequestJournal、PassengerStore、Negotiation均不变；113费用标签函数也保持其已验收hash。BookingScreens、ProfileScreens、ExtraScreens相对112只出现本次展示差异；OrderDetail还含已归档113费用标签修正。
- 旧AG113独立14项保持其版本与执行范围，不自动当作114全套独立复测。

## 审查时源码摘要

- `OrderIdentity.kt`: `8831fd4975384e4226e6d3a200915cde459dcb832381f8e2ef6e4f36f67a6af2`
- `OrderDetail.kt`: `ba49d91c62f312d33b2dc00532706974c93bc1bbc7c4b293a5546651d5dfe8eb`
- `BookingScreens.kt`: `29b9b51a05692fe1171429757dd6f989a5af539aa2485748dab890dd680ab157`
- `ProfileScreens.kt`: `71d6c536a82610c2b94ccf23c683294beb4073f0393ad972d8579432fd2f55f4`
- `ExtraScreens.kt`: `27b1336a7c02752d99e9b446a81a026dafdfb729439d3c04cc5f35ced044be54`
- `PendingRequestJournal.kt`: `1d1b4d1bca498d1c8b53794aeb60cccffa0aab43c31238a43d1dd37a2faf251c`
- `PassengerStore.kt`: `74d0cf010ccfd3388a12018b1a984f787e6668e61afcc00cd41d7c0eee92d269`
- `Negotiation.kt`: `76f3f457e1ebb4787534912beaab0dd7f4d2d173d564894d746f50c2237752b7`
- `OrderFarePresentation.kt`: `807e08ea6813ea1ab53c8ae25df2161d60b605f0018ccff50079427bffb512eb`

## 构建证据补充

已独立计算canonical APK哈希，与1.1.4/code6构建记录一致：87eda5e9a1a507cda91f3fbe7ede1d6a6a23862d528d3bc014bb77edd57f8eb9，8,132,037字节。manifest列出的7个源码/构建文件哈希均匹配；五套XML时间UTC15:39:41，共16项，失败/错误/跳过均0。lint无错误，保留2警告2提示。这是开发方本次构建证据核验，不是反重力执行16项，也不替代实际显示/复制/中文地址验收。
