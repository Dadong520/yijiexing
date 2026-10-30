I replaced the 2 strings word for word; nothing was compiled or tested (no Bash).

- **Source changes:**
  - `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/ProfileScreens.kt`: the capability note now reads exactly as you specified.
  - `apps/passenger-android/app/src/main/java/cn/hbzjhs/passenger/MainActivity.kt`: the second Note on the login screen now reads exactly as you specified.
  - Nothing else changed: no other source, imports, formatting, tests, Gradle, permissions, key, version (still version 20) or business logic.
- **Short card:** I added a phase-4 line to the central `handoffs/claude.md`. The protocol 2026-10-26-v3 ACK and session c3342ddb-6fe0-44b6-8311-00c329e73178 are unchanged.
- **Status:** not committed, deployed or dispatched. Build and tests are NOT_RUN; Codex needs to rebuild the passenger app from the new SHA and check the two pages.
