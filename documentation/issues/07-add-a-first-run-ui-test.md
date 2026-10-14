# Add a first-run UI test: onboarding, the demo library planted once, and What's new held back

**Challenged:** amended — the relaunch case dropped (it needs `withCampfireApp`, in `src/main`, outside the lane, to
keep its home across two starts); the first run's language pinned (it follows the system's); demo names as literals;
What's new checked after the onboarding leaves, where a regression would show it.

**Kind:** test  ·  **Severity:** medium  ·  **Platforms:** desktop (UI tests)
**Files:** `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/screenshots/AppTest.kt`,
new `tools/screenshots/src/test/kotlin/com/pandulapeter/campfire/presentation/ui/firstRun/FirstRunTest.kt`,
`tools/screenshots/CLAUDE.md`

## Problem

The first run's ordering (root `CLAUDE.md`: demo library planted once on a fresh install; onboarding first; What's new
after startup import questions, `ui/firstRun`) is the area reviews keep revisiting, and no UI test can reach it:
`writeLibrary` always writes a preferences document and the demo songs, so every test starts as a returning user.

## Fix

1. Harness (`AppTest.kt` only): an `isFirstRun: Boolean = false` parameter on `runAppTest`; when true, `writeLibrary`
   writes nothing (no preferences, no songs, no setlists; `songs`, `setlists`, `fontScale`, and `isCoverArtEnabled`
   are then meaningless — `require` they are left at their defaults). With no preferences the app's language is
   `SYSTEM_DEFAULT`, which follows the JVM's default locale, and the test JVM's is not pinned (`build.gradle.kts` sets
   no `user.language`): set `Locale.setDefault(Locale.ENGLISH)` for a first-run test and restore it afterwards (or
   resolve `AppTestScope`'s locale the way the app resolves the system default), so `string(...)` reads what the
   screen shows on any machine. Covers stay offline (`TestFakes`), and `Fakes.kt`'s connected sync runs nothing; if the
   onboarding or the first run's credential forgetting behaves differently because the fake says "connected", note it
   rather than add a fake. Update the `tools/screenshots/CLAUDE.md` line "A preferences document is written, so no
   onboarding and no planted demo" to name the exception, in one line.
2. `FirstRunTest`, one case: `a first run opens the onboarding over the planted demo library, and What's new waits`:
   - the onboarding's first page is shown (`Res.string.onboarding_welcome_title`);
   - `waitForLibrary` until the library holds exactly `HOUSE_OF_THE_RISING_SUN`, `HOME_ON_THE_RANGE`, and
     `setlists/getting_started.setlist.json` (literals: a resource folder cannot be listed through `Res`), names only;
   - press `Res.string.onboarding_skip`, wait for the Songs screen (both demo titles shown), `waitForIdle`, and assert
     no node with the What's new title (`Res.string.whats_new_title`, formatted with
     `System.getProperty("campfire.screenshots.versionName")`) exists — the first installed version is recorded, not
     shown (`ui/firstRun/CLAUDE.md`), and a regression would show it as the onboarding leaves.

Not planned: a relaunch case. "Planted once" rests on the preferences document written after planting, and every other
UI test already starts from a preferences document over the demo songs and sees nothing planted.

## Tests

This is the test.
