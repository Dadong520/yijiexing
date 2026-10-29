I've made changes for all 11 items in the worktree, but nothing has been compiled or tested (this session had no Bash). G1 is only partly done. Nothing was committed and no central files were touched.

**Changed files** (all inside allow_paths)
- Driver (`candidates/driver-0.3.6-r2/.../preview/`): `UIOComponents.kt` (new), `CoreScreens.kt`, `UiKit.kt`, `NewOrderAlerts.kt`, `MainActivity.kt`, `BusinessKit.kt`, `BusinessCommunity.kt`
- Passenger (`apps/passenger-android/.../passenger/`): `MainActivity.kt`, `OrderDetail.kt`, `BookingScreens.kt`, `PassengerPages.kt`, `ProfileScreens.kt`

**The 11 items**
- **G1 (partial):** Added an 8/12/16/24 spacing set, an empty-state component with an icon, and a status badge with icon plus text. Primary buttons are now filled and secondary ones outlined in the changed places. I didn't go through all passenger screens.
- **D1 done:**
  - The home page is now an order hall titled "订单大厅". Kind filters appear when more than one order kind is present, and the list scrolls.
  - Stats and the membership card are gone from home.
  - Still on home: the qualification banner, the active-trip entry ("继续当前行程"), the waiting-reservations list alongside instant orders, unpaid-order notice, the go-online/listening/accept gates, and the map.
- **D2 done:** The new-order dialog looks up the already-synced order by id and shows the fare, estimated trip distance, and distance to the passenger. Missing values show "未提供"; the system overlay notice still shows only the two addresses. How alerts are triggered and de-duplicated, and the accept rules, are unchanged.
- **D3 done:** Non-available orders show a badge with icon and text (待进行·待出发 / 进行中 / 待支付 / 已完成 / 已取消). The secondary button is outlined. All filter tabs are kept.
- **D4 done:** The wallet shows income/spending rows with icons, plus an empty state. Bank card and trade password moved to the profile under "账户安全". The wallet keeps a hint row linking to the bank card page.
- **D5 done:** The profile is grouped into 出车与资料, 账户安全, 平台服务 and 服务细则与支持. The 我的订单/我的钱包 rows are removed. The `onOrders`/`onWallet` parameters are now unused, so expect a compiler warning.
- **D6 done:** A new membership card shows level, status, expiry and the first three rights, using existing payload fields only. If no rights are returned it shows the existing feature flags instead. It has links to manage membership and to purchase records; there is no selling or invented discount.
- **D7 done:** Empty lists for orders, transactions, messages and all business pages now have an icon. The service-score, referral and points pages also get a "刷新" button that calls `store.refresh()`.
- **P1 done:**
  - The address picker is now a full-screen page with title-bar back, search, map selection, separate scrollable "favorites" and "history" sections, and a fixed "使用此地点" button at the bottom.
  - Title back and system back both close it without writing the draft.
  - Coordinates, metadata, the old-address guard, defaults and the Amap path are kept.
- **P2 done:**
  - The cancel button is now a large red outlined button.
  - Cancelling goes back to home only through the `store.action` done callback, which runs only after the service accepts. On failure the page stays.
  - Order details opened right after booking or negotiation now go back to home; details opened from the order list still go back to the list.
- **P3 done:** "更多体验" and "应用与连接" in the passenger profile are always expanded, with all entries and labels kept.

**Not done:** a full G1 pass over the remaining passenger screens, and the new passenger `UIOComponents.kt` (I didn't create it). No changes to the membership entry/UI/catalog files.

**For you to verify**
1. Both apps compile: the material3 version needs to support `FilterChip` without an opt-in, the new parameters on `NewOrderDialog` and `PassengerFullPage` need checking, and `money`/`mText`/`mRights`/`mRoute` need to be visible from the new driver file.
2. Driver hall:
   - Gates and the waiting-reservation/instant coexistence work.
   - The single-execution gate holds while a trip is active.
   - The filter works when only one order kind exists.
3. New-order dialog: shows "未提供" when the order isn't in the current list.
4. Cancel:
   - Success returns to home.
   - Failure stays on the page with the error banner.
   - The done callback doesn't fire when the session generation has changed.
5. Address page:
   - System back and title back don't write the draft.
   - Map gestures work inside the scrolling page, now that it sits in a full-screen Dialog.
   - History items with no address field still display.
6. Back from details: decide whether opening from the order list should also return to home. I chose to return to the list.
7. Profile: the bank card and password routes work, and the wallet hint row works.

**NOT_RUN:** compile, unit tests, offline build, AVD acceptance, independent review, and `check_coordination.py`.
