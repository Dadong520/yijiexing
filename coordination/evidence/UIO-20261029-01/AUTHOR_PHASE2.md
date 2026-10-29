Second round is done; it's all source edits, nothing compiled or run.

**Your four points**
1. **Back from order details now always goes home.** Both the top-bar back and system back clear the selection and go home, whether the details were opened from booking, negotiation or the order list. The contact page and other sub-pages open as dialogs, so system back closes them first. The "订单正在同步" fallback keeps its "返回订单列表" text and behaviour, now as a secondary button.
2. **Passenger shared components:**
   - New `UIOComponents.kt` holds the 8/12/16/24 spacing set, an icon picked from each empty state's existing title text, and outlined secondary and danger buttons.
   - `EmptyCard` now shows a tinted round icon with its original text, which covers all existing passenger empty states.
   - `PageColumn` and `Panel` padding now use 16 and 12/8.
   - Three buttons became secondary: "搜索高德地点", "返回订单列表" and "复制体验说明". The cancel button stays red outlined.
   - Driver `UIOComponents`: I checked imports, visibility and name clashes and found no issues.
3. **Driver hall filter:** a filter only applies while its order kind is still present and there are at least two kinds. Otherwise it resets to "全部", so a hidden old filter can't empty the list. The chips always include "全部", and the empty state is kept.
4. **Address page:**
   - The content scrolls and the confirm area stays fixed at the bottom. It now shows the chosen place ("已选：…", up to 2 lines) and the error (up to 3 lines). The page title wraps for large fonts.
   - A place with missing, NaN or 0 coordinates is listed as "未提供坐标". Tapping it fills only the name and asks the passenger to search or pick on the map. The "填入路线" (reuse a recent route) button uses the same check.
   - Back still only closes the page; the draft is not written.

**Status of the 11 items**
- G1: done across the shared components. Remaining: a few passenger files outside the allowed paths, such as `PassengerContactPage.kt` and `OperationsSupport.kt`, keep their original buttons.
- D1–D7: done (D1 updated for point 3).
- P1: done (updated for point 4).
- P2: done; back now goes home from every entry.
- P3: done.

**Changed this round:** passenger `UIOComponents.kt` (new), `MainActivity.kt`, `BookingScreens.kt`, `PassengerPages.kt`, `ParityScreens.kt`, and driver `CoreScreens.kt`.

**For your checks:**
- That the trailing lambda passed to the nullable `onAction` parameter compiles.
- That `coordText` is visible across files.
- That map gestures still work inside the scrolling full-screen page.

**NOT_RUN:** compile, unit tests, offline builds, AVD acceptance, independent review.
