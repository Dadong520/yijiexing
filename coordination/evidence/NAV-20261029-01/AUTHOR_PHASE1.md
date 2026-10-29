I've changed the code for all 8 items, but nothing has been compiled or tested (I have no Bash), and nothing is committed. All build, test and device checks are NOT_RUN and go to Codex. The changes stay inside allow_paths. Store, domain, backend, manifest, NavigationContract, permissions, the key and Gradle are untouched.

**Changes by item**
1. **Confirm popups (NAVB-01/02):** I rewrote `PassengerConfirmPrompts.kt` and moved the rules into a new `PassengerConfirmPromptRules.kt`.
   - Each prompt is tracked separately by account + order + question. Handling one never hides another order's prompt.
   - Prompt memory is per account, so logging out or switching accounts starts clean.
   - A distance agree/reject counts as handled only after the server confirms it. On failure the popup stays open with the error shown and can be retried.
   - "稍后" puts aside only the current prompt and sends no request.
   - Quote, prepay and pay prompts still only open the existing order page.
2. **Check when tapped (NAVB-05):** on tap, the popup re-reads the latest store state and checks the account, order, consentId, expectedStage, PENDING status and expiry. A blank or invalid expiry counts as expired. If anything fails, it doesn't submit, shows the reason and refreshes; the backend still makes the final check. Expiry is also re-checked every second.
3. **No stacked dialogs (NAVF-F1):** the new `PassengerModalCoordinator.kt` keeps track of which dialogs are open. These register with it:
   - full pages, including the address editor
   - `PassengerModal`
   - the order detail block/pay/choose-driver dialogs
   - the dial confirmation
   - settings and the permission guide

   The confirm popup stays hidden while another one is open. The announcement holds off claiming while another dialog is open; its claim and once-per-version rules are unchanged.
4. **GPS pickup point (NAVB-03/NAVF-F3):** the rules are in the new `PassengerPickupDefaults.kt`. A fix is used only if it meets the app's existing location standards: finite and in range, accuracy above 0 and at most 100 m, timestamp no more than 30 s ahead or 120 s old.
   - A stale applied fix can be replaced, and so can a fresh one that is at least 10 m more accurate. That 10 m threshold is new, but it only limits repeated reverse lookups; it isn't a pickup-distance rule.
   - The reverse lookup runs only when a fix is accepted or the map key becomes ready. Its result is checked against the fix it was started for, so it can't overwrite a manual choice. Account isolation relies on the existing per-account draft.
   - Manual edits and swapping origin and destination always win. WGS84 and the AMap metadata handling are unchanged, and nothing auto-picks a destination or calls a car.
5. **Address search (NAVF-F2/F4):** text search, map lookup and every manual change now share one "newest request only" guard, so a stale task can't write results, errors or progress. Closing the editor cancels any search. The 400 ms auto-search stays, and after a failure or cancel a small "重新搜索" text button appears instead of a permanent search button.
6. **Driver auto-navigation (NAVB-04):** the new `NavigationAutoRules.kt` opens navigation only on a real move into a new leg: AVAILABLE/RESERVED → PICKUP or ARRIVED → TRIP.
   - Each account + order + leg opens at most once while the app process is alive, so retries, repeated responses and recomposition don't open it again.
   - `navigateForNewLeg` now takes the stage the request was sent from. A distance request that leaves the order in TRIP never opens navigation.
   - A new `AutoNavigationObserver` covers the passenger agreeing on their phone (ARRIVED → TRIP). The first snapshot after startup or an account switch never triggers it.
   - The manual navigation button is unchanged.
7. **Driver layout:** the identity bar was already above the map and the wallet already showed 3 expandable rows. The map now takes 46% of the height (was 42%) and can shrink to 150 dp (was 200 dp) on small screens. The identity row uses a minimum height instead of a fixed one, so large text doesn't clip.
8. **Tests:** I added `PassengerConfirmPromptRulesTest`, `PassengerPickupDefaultsTest` and `NavigationAutoRulesTest`. They cover expiry parsing, the tap-time checks, per-order handling, account scoping, fix quality, replacing stale fixes, manual choice winning, the search guard, real leg transitions and once-per-leg.

**Known gaps / risks**
- Some dialogs are in files outside allow_paths and aren't registered with the coordinator: `ProfileScreens`, `ExtraScreens`, `ParityScreens`, `OrderIdentity`, and the order-page distance dialog in `PassengerTripDistance`. A confirm popup can still stack on those.
- A failed distance submit is detected when the store's busy flag goes false without a success callback. This assumes `PassengerStore.run` sets busy before returning. If it doesn't, the popup could show "提交未完成" while the request is still in flight.
- The driver's once-per-leg memory is cleared when the app process restarts, so an ARRIVED → TRIP change seen after a restart could open navigation again.

**Needs device/build checks (Codex)**
- Compile both apps and run the three new test files.
- Two distance requests on different orders, with a forced POST failure and an expired request.
- Announcement and confirm popup arriving together, and opening settings or the address editor while a popup is pending.
- GPS: start with an old cached fix, then a coarse one followed by an accurate one, then the map key becoming ready.
- Address search: start a text search then tap the map; retry after a failure.
- Driver: accept, start pickup and confirm boarding each open navigation once; a TRIP distance request doesn't open it; the passenger agreeing on their phone opens it once.
- Driver layout on a small screen with large text.

I updated the central `handoffs/claude.md` card (well under 6 KB) with session c3342ddb-6fe0-44b6-8311-00c329e73178, taken from the task file.
