I've fixed both NAVF findings on the passenger side; nothing has been compiled or tested (no Bash), so build, unit tests and the GUI are all NOT_RUN. Nothing is committed or deployed, and no agents were dispatched. Driver, Store, domain, backend, NavigationContract, manifest, key, Gradle and the version are untouched.

**NAVF-R1: announcement vs. other modals**
- **Ranking** (`PassengerModalCoordinator.kt`): each registered modal now has a rank. Dialogs and pages the passenger opens outrank an announcement, which outranks an order confirmation. A popup is hidden only by something ranked above it, so its own registration can never hide it and the two popups can't block each other.
- **Announcement** (`OperationsSupport.kt`):
  - A new claim still waits until nothing else is open.
  - Once claimed, the announcement is held per account until the passenger closes it. It no longer jumps straight onto the screen from the claim callback.
  - Both the announcement dialog and the "暂未读取完成" failure dialog are drawn only while no passenger-opened dialog or page is open. So a page opened while the claim was in flight hides the announcement until it closes.
  - The held announcement isn't dropped and isn't claimed again. BIZ09's once-per-user-per-version rule and the server claim are unchanged, and a switched account never sees another account's announcement.
- **Confirm popup** (`PassengerConfirmPrompts.kt`): it ranks lowest and waits while an announcement or any page is open.
- **Permission guide** (`PassengerPermissions.kt`): it now registers using its real show condition (`forceOpen || !seenGuide`), which covers the first automatic guide. I added only that one registration line and changed no permission logic. The old registration in `MainActivity.kt` that only covered the forced guide is removed.

**NAVF-R2: the 400 ms wait in address search**
- A new helper, `PassengerAddressLookup.debounced` in `PassengerPickupDefaults.kt`, now runs the whole search flow. It takes the "newest request" guard before the 400 ms wait and re-checks it after the wait, after the result or error, and before clearing progress.
- The text search in `BookingScreens.kt` now uses it. Manual coordinates, picking a place with the same name as the query, a map pick, cancel or closing the editor all invalidate the guard. A search that is still waiting then never sends a request, shows progress or writes anything.
- Retrying the same text after a failure or cancel still works.

**Tests**
- `PassengerPickupDefaultsTest.kt` runs the real search helper with `runBlocking`. It covers:
  - a manual action, a text change or a newer search during the wait
  - an in-flight search invalidated mid-request, on both the success and failure paths
  - retrying the same text after a failure or cancel
- `PassengerConfirmPromptRulesTest.kt` adds the ranking cases (including no deadlock with the confirm popup) and the claim-only-once rule.
- These rely on `kotlinx.coroutines.runBlocking` and Compose snapshot state being on the unit-test classpath, which I couldn't verify.

**Remaining risk:** if the account switches after an announcement is claimed but before it is shown, that held copy is dropped. Nothing leaks across accounts, but because the claim was already spent, the old account won't see that version again. Still to check on a device:
- opening the address page or settings while a claim is in flight
- the first automatic permission guide together with an announcement
- choosing a place within 400 ms of typing

I updated the central `handoffs/claude.md` card, keeping the protocol 2026-10-26-v3 ACK and session c3342ddb-6fe0-44b6-8311-00c329e73178.
