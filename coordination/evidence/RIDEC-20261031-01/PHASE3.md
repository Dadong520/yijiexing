# RIDEC-20261031-01 Phase3：跨子选址页的第二tap（RIDEB残余P2）
会话 6c0ddf57-d854-4c5c-a8a9-43f7ee7e3063（exact）；source /Users/dongge/.codex/tmp/yijiexing-ride-flow-polish-20261031；基于 HEAD 4a6a2970d81c62dca59f64b9deb92831b75b5841。2026-10-31。
未commit/stage/deploy。无Bash：编译/单测/native 全部 NOT_RUN；frontend 将对最终SHA做真实快速连点复验，不声称PASS。

## 缺口
Phase2 的 leave() 在 stopSlot>0（途经点页返回）时只设 editor="destination"，未 arm；重新打开的目的地页是新的 PlacePickSession，第二tap可直接选中/地图选点/搜索结果，静默改最终目的地；途经点修改/删除、＋途经点同样可被第二tap触发。

## 修复（仅 BookingScreens.kt + PlaceSelectionTest.kt）
- 首页草稿内新增 `openEditor(next)`：先 `returnShield.arm()`（同一 owner 作用域、700ms 有界 ReturnTapGuard）再切 editor。所有选址页切换都经它：首页路线行打开上车点/途经点摘要/目的地页；`leave()` 无论回首页还是回目的地页（点选成功或返回/系统返回）；目的地页“修改途经点”和“＋途经点”。
- `AddressDialog` 新增可选参数 `shield: ReturnTapShield?`（默认 null，Profile/Order 调用不变）；首页传同一 `returnShield`，因此屏障跨子选址 session 生效。
- AddressDialog 内动作入口在消耗选择机会之前检查 `shield.allows()`（时钟判定）：
  - `pickRow`（常用/体验/历史行，含按名称搜索分支）：检查在 `session.commit` 之前，拒绝时不 commit、不改查询；
  - `pickResult`（搜索结果）：检查在 commit 之前；
  - 地图点击 onPick：拒绝时不 beginLookup、不 cancelSearch；异步结果提交处不再检查（避免已消耗后丢回调卡住）；
  - “＋途经点”：enabled=`!shield.active` + 点击时检查。
- RouteStopsEditor 新增 `enabled` 参数；“修改”“删除”按钮 enabled=`!returnShield.active`，onEdit/onRemove 内实际检查 `returnShield.allows()`，拒绝时不改途经点、不切页。
- 首页“确认呼叫/查看当前行程”“交换起终点”保护（phase2）保留，现由更多切换点 arm。Profile 常用地址、OrderDetail 确认/取消/支付保护（phase2）未改动、未回退。

## 受保护入口一览（窗口 700ms，arm 后）
首页：确认呼叫/查看当前行程、交换起终点。选址页（首页三类 picker）：常用/历史/体验行、搜索结果、地图点选、＋途经点；目的地页途经点 修改/删除。常用地址编辑器：保存/设为默认/取消/外点（phase2）。订单：确认并重新估价/暂不修改/外点、取消订单/去支付（phase2）。

## 保持可用
查询输入、城市框、取消/重新搜索、返回与系统返回、地图平移缩放、页面滚动；700ms 后一切正常选点与途经点管理。窗口每次 arm 重新计时但有界，不全局锁 UI；未恢复“使用此地点”、未延迟返回、未加通用确认；业务门禁与 PlacePickSession/gate/owner 校验不变；返回不改草稿。

## 测试（NOT_RUN）
原 14 例保留；新增 `aTapRefusedByTheShieldLeavesThePickerUsable`：子页返回后 49ms 的拒绝不消耗新 session，700ms 后同一行正常提交。未写镜像 GUI 测试；跨页真实连点由 frontend native 复验。

## 残余风险
- 打开选址页也会 arm：从首页点开后 700ms 内选址页的点选被忽略（有意，防“打开”双击落到选址页同位置；如认为过严可只保留返回处 arm）。
- 列表行/搜索结果在窗口内无禁用外观，仅点击无效（≤0.7s）；“＋途经点”和途经点修改/删除有禁用外观。
- Profile/OrderDetail 的选址页未传 shield（本阶段不允许改这两文件；其选址页无子页返回场景，打开时的双击仍可能在其选址页内即时选点，但随后保存/重新估价仍受 phase2 窗口保护）。
- 编译未跑：注意局部 `fun leave() = openEditor(...)`、AddressDialog 内局部 `interactionAllowed()` 被 composable lambda 捕获、RouteStopsEditor 新参数。
