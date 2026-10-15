# 33rd review: the features since the 32nd sweep

Reviewed commit `4bde19afe` on `master`. Angle: the 20 commits since the last sweep, broad: the Android and iOS
share target, clipboard paste, defining chord shapes in the editor, tag and language search, the stepper reset marks,
and typed tempo and pitch. Lean budget: three Opus area reviewers (paste and share input, platform shells, new UI)
plus the docs reviewer, and no live run.

## Headlines

- **A second pasted or shared text replaces the first New song sheet and keeps its values** (01, high). Create then
  writes the third song under the first one's title and artist, and the first two are lost. iOS hits this whenever
  several shares wait in the inbox.
- **iOS deletes every waiting share before anything is written** (03, medium). If the process ends while the first
  review is up, the rest are gone, even though the extension had said "Saved to Campfire".
- **Every song an import writes lists the whole songs folder** since `f0bbc4bfd` (05, medium). This is quadratic on
  OPFS and iOS.
- **On a phone, the reset mark of a typed stepper is a ~22 dp target** between the field and + (06, medium).

## Fixed during the review (`4bde19afe..5a53eca32`, 26 commits)

- `09e873072` Name the 8 MB limit for a shared text in the iOS share extension's size message.
- `828daa2fc` Leave Campfire itself out of the iOS share sheet of an export.
- `ba48861fb` Say in the tempo stepper's documentation that its reset mark, not a tap on the value, resets it.
- `878380146` Call the Settings screen's setup group "Kezdeti beállítás" in Hungarian, so it no longer reads as the screen's own name.
- `e956a2604` Have screen readers say a typed stepper's value once, with its unit.
- `b340ef7da` Drop the floating Reset a typed stepper no longer has from the overlay state's documentation.
- `8d5cbbbb8`..`90babec0b`, `892ccecb4`, `5a53eca32`: 20 commits for the docs reviewer's findings. They:
  - fixed false statements: the tempo reset in the song details notes, which packages have a `CLAUDE.md`, imports that
    skip the review, and read-only mode still importing shared texts;
  - cut narration that repeated KDoc (paste and share, clipboard, steppers, filter search, navigation, Android, iOS);
  - brought `.github/CLAUDE.md` and `ui/navigation/CLAUDE.md` back within budget;
  - rewrapped overlong lines.

  The regression read of the fixes found one lost rule (a pasted song the library already holds opens without a
  review), and `5a53eca32` restored it. It found no code regressions.

## Index

| #  | Plan                                                                 | Severity | Lane |
|----|----------------------------------------------------------------------|----------|------|
| 01 | Show the New song sheet of a paste or share only over nothing, one at a time, after the onboarding | high | S |
| 02 | Answer a share of a one-line title beside a page address as a link   | low      | S    |
| 03 | Keep a share in the iOS inbox until the app has taken it             | medium   | I    |
| 05 | List the songs folder once per import batch, not once per song       | medium   | D    |
| 06 | Give a typed stepper's reset mark a full-size touch target           | medium   | U    |
| 07 | Show the tuner button in one place while the instrument chips fade   | low      | U    |
| 08 | Never let the placeholder chord shape override the song's `{define}` | low      | U    |
| 09 | Reopen the New song sheet of a small pasted or shared text after process death | low (decision) | S |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|------|------|-----------------|-------------|
| D | storage | 05 | `data/source/local/*`, `data/repository/*`, `domain/implementation` importing, their tests and fakes, `data/sync/implementation` test fakes, `data/source/local/implementation/CLAUDE.md` |
| S | paste and share | 01 → 02 → 09 | `ui/state/importing/*`, `ui/dialogs/SavedDialog.kt`, `ui/dialogs/SavedDialogRestorer.kt`, `ui/dialogs/DialogType.kt`, `ui/dialogs/CLAUDE.md`, `ui/navigation/CLAUDE.md`, `ui/platform/SharedTextImport.kt`, `ui/dialogs/forms/NewSongDialog.kt`, `ui/dialogs/CampfireDialogs.kt`, `presentation/src/desktopMain/.../CampfireDesktopApp.kt`, their tests, `presentation/CLAUDE.md` |
| U | controls and editor UI | 06 → 07 → 08 | `ui/components/controls/*`, `ui/songView/firstSection/*`, `ui/chords/ChordShapeInsertion.kt` and its test, `tools/screenshots` `TypedTempoTest`, goldens, those packages' `CLAUDE.md` |
| I | iOS share inbox | 03 | `app/ios/src/iosMain/*` (inbox, view controller), `presentation/src/iosMain/.../CampfireIosApp.kt`, plus the `:presentation` signatures plan 03 names (`ImportController.importFiles`, `PastedSongController`, `CampfireViewModel`), `app/ios/CLAUDE.md` |

**Merge order: D, S, U, then I.** D and S run first, two Gradle builds at once. U starts when either finishes. I is
cut from `master` after S has merged, because 03 needs 01's "a text settles when its form leaves" and edits the files
S owns. Cherry-picking I last keeps the shared `:presentation` importing files in one line of history.

**Shared files:** `presentation/CLAUDE.md` is S's, and I re-reads it after S merges. Plan 03 owns `app/ios/CLAUDE.md`.
No plan adds strings. Goldens change only in U (06); re-record them on Linux as `tools/screenshots/CLAUDE.md` says,
never on a Mac.

## Decisions

1. **06 makes the song's tempo pill and the Tuner's reference pitch pill ~14 dp wider, always**, to give the reset
   mark a 36 dp target the size of − and +. Compose has no way to widen a touch area past its layout between two
   other pressables. **Decided: accept the wider pill** (the recommendation).
2. **A New song sheet from a share is not restored after process death.** Its text lives only in the dialog, and saved
   state (an Android Bundle) cannot safely hold up to 8 MB. On iOS, plan 03 offers the share again instead.
   **Decided, against the recommendation: restore small texts.** This is plan 09, lane S, after 02. After its challenge
   the limit is `LONG_DOCUMENT_LENGTH` (50,000 characters), and the restore is Android-only, as for every
   `SavedDialog`.

## Open, unverified

- **Typed values in a restored form sheet may never come back.** 09's challenger raised this. Dialog slots are
  `key(dialog.slotKey)` with `slotKey = this::class`, and a `KClass` hash is identity-based. The `rememberSaveable` key
  inside a slot may therefore differ in a new process. If so, the typed values of `NewSong` and the metadata sheets are
  not restored either. Check it on Android with "Don't keep activities" before filing a plan. Lane S reports what it
  sees.

## Checked and found solid

- **iOS share extension:**
  - inbox protocol: `.partial` rename, the path traversal guard, newer manifest versions left alone;
  - memory: copies rather than loads, an 8 MB text cap, every continuation resumed once;
  - backup exclusion and the extension's privacy manifest;
  - App Group and signing in `publish-ios.yml`, and the version phase on both targets;
  - `Localizable.strings` keys match between en and hu.
- **Android intents:** no double import on rotation or process recreation, the `EXTRA_STREAM` grant, and `SEND_MULTIPLE`.
- **Clipboard readers on all four platforms:** the web paste listener, the web text drop, and the order of
  `PastedSongController`'s checks.
- **Paste parsing:** `withoutSourceLink`, `sharedTextName`, `ChordProHeader.write`, `ChordProMetadataFields.declare`,
  `CreateSongUseCaseImpl` overrides, and `PastedTextFormatting`'s single undo step.
- **Chord shape menu:** instruments, stored shapes, keyboard `keys`, and notation.
- **Tag and language search:** `chipsMatching` and its empty state.
- **Typed tempo and pitch:** Escape, the Space and M gating, out-of-range values never clamped, and the root press
  listener.
- **Reset marks:** the pill length stays constant and the first frame is right.

## Dropped after verification

- **04, offer the iOS extension only for archives it reads.** On every iOS runtime installed and on macOS, OpenXML
  (docx, xlsx, pptx) and EPUB do not conform to `public.zip-archive`, so they never matched. Narrowing the rule would
  break other apps' zip-based backups.
- **Doc: "every search field is `SearchField`".** It is untrue: the editor's Find / Replace sheet uses an
  `OutlinedTextField`.
