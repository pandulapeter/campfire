# Step 11: cleanup and release preparation

**Goal:** no dead code, dependencies, strings or docs left from Campfire 3; version bumped; documentation describes
the new app.

**Depends on:** all previous steps (10 may be partially done).

## 1. Dead code and dependencies

- `grep -rn "TODO(step" --include=*.kt .` must return nothing (excluding `build/`).
- `libs.versions.toml`: confirm `ktor*`, `ktorfit`, `retrosheet`, `softwork-csv`, `androidx-room`,
  `androidx-sqlite`, `kotlin-ksp` are gone; keep `kotlin-browser` (OPFS / DOM interop), `androidx-browser`
  (Custom Tabs), `compose-reorderable` (setlists).
- Root `build.gradle.kts`: remove plugin aliases that no module applies anymore (`ksp`, `ktorfit`,
  `kotlin-serialization` stays: the local source uses it).
- `gradle/build-logic`: nothing should need changing; verify no reference to Room/KSP.
- Unused strings: for each `<string name="x">` in `values/strings.xml`, `grep -rn "Res.string.x"` in `presentation`;
  delete the unused ones from both language files. Check `values-hu` has exactly the same set of names
  (`diff <(grep -o 'name="[^"]*"' values/strings.xml | sort) <(grep -o 'name="[^"]*"' values-hu/strings.xml | sort)`).
- Unused Kotlin: `DataState` variants, `BaseLocalDataRepository` helpers, `Platform` expects, dialog types, icons.
- `kotlin-js-store/yarn.lock`: regenerate with `./gradlew kotlinUpgradeYarnLock` if the wasm dependencies changed.

## 2. Versions

- Root `build.gradle.kts`: `VERSION_NAME` → `4.0.0`, `VERSION_CODE` → 27 (current is 3.0.1 / 26).
- iOS: `MARKETING_VERSION` 4.0.0 / `CURRENT_PROJECT_VERSION` bumped in `app/ios/iosApp/iosApp.xcodeproj/project.pbxproj`
  (both configurations).
- Desktop installers pick the version up from the system property.

## 3. Documentation

- `README.md`: rewrite the description ("Campfire is a lightweight ChordPro viewer and editor…"): offline, local
  `.cho` files, import/export incl. zip, setlists, transposition, all four platforms; remove the "Known issues" entries
  that no longer apply ("Get bundled content" is done by this rewrite) and add a short "File format" section linking
  to chordpro.org and stating the supported directives (from step 01 §3) and the `.setlist.json` format (from the plan
  README). Keep the license and the Play badge. Mark the screenshots as outdated or replace them (see step 12 for how
  to capture).
- `CLAUDE.md`: update the architecture tree, the data-flow sentence (`FileStorage` → local sources → repositories →
  use cases → view model), the conventions (tests exist in `:chordpro` and the local source implementation; run with
  `desktopTest`), delete the whole "Web" section about Room/Retrosheet and replace it with two lines about OPFS and
  the JS interop helpers, and mention `docs/rewrite-plan/` as historical. Keep the build commands.
- Privacy: the app no longer makes network requests; if the website's privacy policy (linked from Settings) describes
  network usage, note in the README that it needs updating (the policy itself is not in this repo).
- Delete `docs/rewrite-plan/samples/` only if the samples were moved somewhere better (e.g. `presentation` test
  resources); otherwise keep them, they document the supported dialect.

## 4. Store metadata reminders (not in the repo, list them in the execution notes for the user)

- Play Store / App Store listing text and screenshots need updating: no more "handpicked library".
- Android: the update wipes existing data (no migration) — release notes should say so.

## Verify

- Full build for all four platforms, all tests pass.
- Fresh install on each platform: empty state → new song → edit → view → setlist → export → delete app data →
  import: everything round-trips.
- `git status` clean after commit, `git grep -i "retrosheet\|google sheets\|database"` only hits this plan folder and
  git history.

## Execution notes

_(filled in by the executing agent)_
