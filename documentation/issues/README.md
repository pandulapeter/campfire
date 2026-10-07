# Twenty-first review: the changes since the twentieth

**Reviewed commit:** `1b26dfb94` on `master`. The tree was clean. HEAD moved to `a9911c859` during the review. That commit
only changes a keyline padding in `ListItems.kt` / `SetlistsScreen.kt` / `presentation/CLAUDE.md`, and every plan's
quotes still match.

**Angle:** a lean review of the ten commits since the twentieth review's plans were removed (`58113d788..1b26dfb94`):
- the sepia (background warmth) slider;
- the multi-select UX of the checklist pickers, and the Manage links crash fix;
- the setlist rows outside reorder mode;
- the desktop title bar and JetBrains Runtime build;
- the desktop close confirmation;
- the What's new text.

Five area reviewers ran, then one verifier/writer per lane, then two challengers.

## Headlines

- **01:** in Choose songs, Choose setlists, Manage tags and Manage languages, a search, a cleared search, a filter chip
  or a sort change no longer returns the list to its top. The new re-anchoring scroll overrides the scroll to the
  start whenever the number of matching selected rows changes.
- **10:** entering or leaving setlist reorder mode pops every row's drag handle in or out in one frame, and the rows
  jump. This is a regression from `e7245858d`.
- **33** (pre-existing): on desktop, Escape behind the import progress dialog offers to close the app or pops the
  screen. Closing mid-import keeps part of the songs and none of the setlists, with no report.

## Index

| #  | Plan | Severity | Lane |
|----|------|----------|------|
| 01 | Re-anchor the checklist's rows only after a tick, and by key | medium | P |
| 02 | Describe the picker chips' and the multi-select plan's order as it now is | low | P |
| 10 | Keep the setlist drag handle animated across the reorder mode switch | medium | S |
| 20 | Test every color role under background warmth by reflection | low | T |
| 21 | Test the stored background warmth clamp | low | T |
| 31 | Restore the shared iOS run configuration | low | E |
| 32 | Document the Escape exit confirmation on desktop | low | E |
| 33 | Let a running import finish before the desktop app exits | low | E |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|------|------|-----------------|-------------|
| T | theme tests | 20, 21 | `BackgroundWarmthRolesTest.kt` (new, desktopTest), `BackgroundWarmth.kt` (one `internal`), `UserPreferencesMappersTest.kt` |
| P | checklist pickers | 01, 02 | `ChecklistOrder.kt`, `ChecklistOrderTest.kt`, `Dialogs.kt` (KDocs only), `documentation/plans/multi-select.md` |
| S | Setlists screen | 10 | `SetlistsScreen.kt` |
| E | desktop, docs | 32, 33, 31 | `CampfireDesktopApp.kt`, `CampfireViewModel.kt`, `SingleInstance.kt`, `CampfireDesktopApplication.kt` (comments), `app/desktop/CLAUDE.md`, `.run/iOS.run.xml` |

**Merge order: T, P, S, E.**
- T is tests only and touches the least.
- P and S each own one area of `:presentation`.
- E goes last because it touches the most shared files: `CampfireViewModel.kt` and the `CampfireDesktopApp.kt`
  bullet in `presentation/CLAUDE.md`.

**Shared-file rules:**
- `presentation/CLAUDE.md` is edited by plan 10 (the setlist-row paragraph), plan 20 (the `ui/theme/` entry), plan 33
  (the `CampfireDesktopApp.kt` bullet) and, if needed, plan 01. Each lane edits only its own paragraph. A conflict is
  merged at word level, keeping both sides.
- `Dialogs.kt` is touched by lane P only.

## Decisions (answered 2026-10-07)

- **D-30: leave the What's new message as it is.** Swapping the SongbookPro bullet for sepia was deliberate, so plan 30
  was deleted.
- **D-31: restore `.run/iOS.run.xml`** byte for byte from `9d2f40947^` (option A of plan 31).
- **D-33: a+b.**
  - (a) Escape is swallowed while the import progress dialog is up.
  - (b) The exit waits up to 30 s for a running import, and the single-instance wait is raised to 60 s.
- **D-CC: keep the close confirmation as shipped.** It is always on, Escape-only and desktop-only, with no preference.

## Checked and found solid

- **Sepia:**
  - Contrast is kept in all ten palettes and in dynamic color, and the test passes.
  - Older, newer and hand-edited `preferences.json` decode safely, and out-of-range values are clamped.
  - The setting is local only (not synced, not exported). Every surface that should be warmed is.
  - Theme changes cross-fade, and nothing is computed per frame.
- **Multi-select:** no duplicate lazy keys are possible; the sheet height budget, Ctrl/Cmd+S and the strings are
  fine.
- **Manage links:** the crash fix holds in every add, remove and move sequence.
- **Setlists outside reorder mode:**
  - The performance claim holds.
  - Item animations still land on the row's root.
  - Ending the mode mid-drag still writes the order.
- **Desktop:**
  - Every workflow now sets up the JetBrains Runtime; runners and Ubuntu versions are unchanged.
  - The `doFirst` release guard covers every release task.
  - The `TitleBar` full-screen reflection has a fallback.
  - The close confirmation never blocks Cmd+Q, Dock Quit, the window close button or an OS logout.
- **Strings:** both languages have the same keys, none is unused, and every one is read with the in-app
  `stringResource`.
- **Unit tests:** pass at `1b26dfb94`.

## Dropped after verification

- **SongLinksDialog reuses a removed row's id:** `LazyLayoutItemAnimator` cancels the disappearance and runs the
  appearance. There is no crash and no lost fade-in, only a partial-alpha start if Add is tapped mid-fade.
- **Leaving reorder mode cuts placement animations:**
  - Ordinary placement animations are kept per key.
  - The only losses are a dropped row's last ~300 ms of settle and a drag cut short.
  - The proposed "stay wrapped until settled" fix could leave a drag that never stops (`detectDragGestures` doesn't
    call `onDragCancel` on cancellation).
- **Narrow the reorder branch to the reordered setlist:** `ReorderableCollectionItem` removes a key only when
  `enabled` turns false, so stale drop targets would bring back the refused-move freeze. Only the docs are reworded,
  in plan 10.
- **`isRearranging` outside the mode:** outside the mode the list never auto-scrolls during a drag, and that is the
  only case the flag exists for.

## Not plans, noted

- **Mac App Store and JetBrains Runtime (risk, unverified):** the first `.pkg` bundling the JetBrains Runtime has not
  been through App Store validation. Dispatch `publish-macos.yml` with `submit` off, or run `altool --validate-app`,
  before the release.
- **Gradle configuration without a JetBrains Runtime:** on a machine with no JetBrains Runtime 21, every Gradle
  configuration of `:app:desktop` asks foojay. Accepted as documented.
- **Sepia slider:** every step crossed is a whole-app cross-fade plus a preferences write. This is deliberate, with
  five positions.

## The challenge

Two fresh agents ran, one on 01, 02 and 10, the other on 20, 21 and 30–33 (30 was later dropped by the user).

**Sound:** 02, 20, 21, 31, 32.

**Amended:**
- **01:** the helper also returns null when the anchor's index would not change, so it never issues a no-op scroll
  mid-drag. A test was added for it.
- **10:** accounts for `Transition.onDisposed` writing its target into the shared state. A switch mid-animation
  jumps, which is accepted, and a manual step was added for it.
- **33:** the longer exit broke `SingleInstance`'s invariant that `CLOSING_INSTANCE_WAIT_MILLIS` (30 s) exceeds the
  exit wait. It is raised to 60 s, and the manual checks now cover a second launch during the exit and a macOS logout.

**Dropped:** none by the challenge. The user then dropped plan 30 (D-30).

## Manual checks owed

- **01:** in each picker, search, clear, toggle a chip and change the sort; the list returns to its first row. A tick
  keeps the tapped row under the finger, and New setlist keeps the top row.
- **10:** on Android and desktop, enter and leave reorder mode; the grips fade and expand in and out, nothing
  re-wraps in one frame, and covers don't flash. A drag during the bring-to-top scroll isn't frozen. A rapid
  Reorder/Done ends correct.
- **31:** the iOS run configuration is listed and runs from a fresh clone on a Mac.
- **33:** with a large import on desktop:
  - Escape does nothing while the import runs;
  - closing during "Importing" keeps the whole batch, and closing during "Reading" keeps nothing;
  - Cmd+Q does the same;
  - a second launch waits for the first to close;
  - a macOS logout mid-import;
  - closing with an unanswered conflict question doesn't wait.
- **Before release:** Mac App Store validation of the JetBrains Runtime `.pkg` (see "Not plans").
