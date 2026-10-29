I've made the phase-2 fixes; nothing has been compiled or tested (I have no Bash). Build, unit tests and the GUI are all NOT_RUN for Codex. Nothing is committed, and Store, domain, backend, manifest, NavigationContract, permissions, the key, Gradle and the version are untouched.

**1. Dialogs in the newly allowed files**
- Each dialog now registers with the modal coordinator only while it is actually showing. No page is held as a modal permanently.
  - `DeleteDialog` (this covers the favorites, rider and invoice-title deletes)
  - the favorite, rider and invoice-title edit dialogs
  - the logout confirmation
  - the receipt confirmation
  - the safety-contact delete
  - the "view order number" dialog
  - the old in-order distance confirmation in `PassengerTripDistance`
- While that in-order distance dialog is open, the app-wide distance popup stays hidden, so they can't stack.
- The other screens in those files use `PassengerModal` or `PassengerFullPage`, which were already registered in phase 1.
- The announcement claim and the BIZ09 once-per-user-per-version rule are unchanged.

**2. Distance submit, checked against the actual Store code (read only)**
- `run()` sets busy before it starts the request, and clears it only after the success callback.
- If another action is already running, `run()` refuses on the spot with an error. If passenger data isn't synced, `action()` does the same.
- The popup now handles each case as follows:
  - **Already busy before the tap:** nothing is sent, and the popup says so.
  - **Still not busy right after calling `action()`:** the request was refused on the spot; the popup shows the Store error and the question stays open.
  - **Store goes idle without the success callback** (network/server error or session switch): treated as a failure, the error is shown and the user can retry. Only the success callback marks the question as answered.

**3. Driver auto-navigation after restarts**
- Driver state isn't saved on the device; it starts empty, and the store reports "connected" only after a successful sync. The observer now compares only complete, connected snapshots for a known account.
- The first such snapshot is only a baseline and never triggers navigation. Snapshots taken while disconnected or mid account switch are ignored.
- "Already opened" memory is now saved in a small UI-layer SharedPreferences file (`driver_auto_navigation_v1`). It holds only driver id | order id | leg, at most 200 entries, so a restart or re-entering the screen doesn't open the same leg again.
- No token or password is read or stored. Manual navigation is unchanged.

**4. GPS account isolation, now explicit in the source**
- Each reverse lookup is tied to passenger id + a draft counter + the fix. When the lookup finishes, the current passenger id is re-read from the store. A different account, a logout, a newer fix or a bumped counter all discard the result.
- I checked every manual pickup path (swap, history refill, editor save). Each one sets the manual flag, which bumps the counter and cancels any lookup in flight.
- Only passenger ids are kept here, never credentials.

**5. Coordinator safety**
- Whether a dialog counts as open depends only on that dialog's own state, never on the coordinator, so there is no write-in-composition loop.
- Registration is released on dispose, which covers closing, leaving the screen and the Activity being destroyed.
- An announcement claim that never returns is reset when the account changes.
- The driver map is 46% of the height but can shrink to 150 dp; the order list below it scrolls, so large text stays reachable.

**Tests added**
- `submitFailed`, including when a different order succeeded
- each order's "稍后"/failure state staying independent
- coordinator releasing registrations
- the account/counter/fix lookup check
- the driver snapshot gate and first-snapshot baseline
- the saved leg memory, including its 200-entry limit

**Remaining**
- The coordinator test uses Compose snapshot state in a plain JVM test. It should work since the Compose runtime is on the test classpath, but that is unverified.
- The saved leg memory has no expiry; old entries just drop off after 200.
- Device checks are still needed:
  - a distance submit that fails, then a retry
  - the in-order distance dialog together with the global popup
  - the driver app restarted mid-trip
  - the passenger agreeing on their phone (ARRIVED → TRIP)
  - an account switch during the GPS lookup
  - small screen with large text

I updated the central `handoffs/claude.md` card (under 6 KB) for session c3342ddb-6fe0-44b6-8311-00c329e73178.
