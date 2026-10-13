# The twenty-eighth review

Reviewed at `3f8873c06` on `master`. This was a lean review of the 25 commits since the twenty-seventh review
(`b2dc4dc31..3f8873c06`). Four read-only reviewers covered:

- the song details lookup and the dialog stack;
- the onboarding;
- mandolin chord diagrams, the per-song instrument button, and haptics;
- editor find and replace, the tuner changes, the removal of the icon color switches, and the Gradle wrapper upgrade.

There was no live run.

The working tree carries one uncommitted change of the user's, `documentation/TO_DO.md`. No plan touches it.

## Headlines

Nothing loses data and nothing crashes. The findings that matter most:

- **01:** on a phone, the editor's search sheet covers the match it reports, so "3 of 7" moves while the text never
  shows it. On a 360×640 phone with the keyboard up, the Find sheet covers the whole field.
- **03:** a second Fill in from the song details lookup that equals the first one is silently ignored by the form.
- **05:** widening a window can show, hide, and show the Songs filter panel again, once song cards show more details.

## Index

| #  | Plan                                                                                   | Severity   | Lane |
|----|----------------------------------------------------------------------------------------|------------|------|
| 01 | Reveal search matches above the search sheet                                           | medium     | E    |
| 02 | Start the undo history over before Replace all in a long document                      | low        | E    |
| 03 | Give every song details Fill in an identity of its own                                 | low–medium | O    |
| 04 | Give the true reason the songwriters are asked from an effect                          | low (docs) | O    |
| 05 | Decide the expanded rail from the Songs screen's own column width                      | low–medium | O    |
| 06 | Refuse a browser Forward into a finished onboarding                                    | low        | O    |
| 07 | Show a consent answer on a first-run onboarding still on its first page                | low        | O    |
| 08 | Give the onboarding footer buttons the room the page dots leave                        | low–medium | O    |
| 09 | Stop calling the onboarding a dialog in the dialog docs                                | low (docs) | O    |
| 10 | Release an Android tone at once when its fade-out cannot be read                       | low        | T    |
| 11 | Tell a quiet live input from a dead one before calling the tuner silent (follows D1)   | low        | T    |
| 12 | Add first-position C#m and G#m to the mandolin table                                   | low        | C    |

## Lanes

| Lane | Area                         | Plans, in order       | Files owned                                                                                          |
|------|------------------------------|-----------------------|------------------------------------------------------------------------------------------------------|
| C    | `:chordpro` mandolin table   | 12                    | `ChordVoicingTables.kt`, `ChordVoicingsTest.kt`                                                      |
| T    | `:tuner`                     | 10, 11                | `tuner/**`; `strings.xml` (both) only for option C of D1                                             |
| E    | Song editor search           | 02, 01                | `ui/screens/songEditor/**`, `ui/dialogs/CampfireBottomSheet.kt`                                      |
| O    | Dialogs, navigation, onboarding | 03, 04, 05, 06, 07, 08, 09 | `ui/dialogs/**` except `CampfireBottomSheet.kt`, `ui/navigation/**` (with `wasmJsMain`'s `BrowserHistory.kt`), `ui/screens/onboarding/**`, `ui/platform/SyncConsentOrigin*`, `CampfireViewModel.kt`, `NavigationChrome*.kt`, `CampfireContent.kt`, `CampfireScreens.kt`, `ui/CLAUDE.md`, `wasmJsMain/CLAUDE.md`, the tests beside them |

Lane E runs 02 before 01: 02 is the smaller change to `EditorSearch.kt`, and 01 builds its reveal state on that file.

**Merge order: C, T, E, O.** The small, self-contained lanes go first. E goes before O, since E adds a parameter to
`CampfireBottomSheet`, which O's sheets use. O touches the most shared UI files, so it goes last.

**Shared files:**

- `ui/dialogs/CLAUDE.md`: only lane O writes there.
- `strings.xml` (both languages): only lane T writes there, and only if D1 picks option C.

## Decisions

- **D1 (plan 11): how the tuner tells a quiet live input from a dead one.** **Answered 2026-10-13: C**, soften the text. The options were:
  - **C (recommended after the challenge):** soften the non-Windows "Nothing at all is heard" text, so that it no longer
    points at a permission or mute problem. Add a peak test only after Windows' gated input has been recorded.
  - **A:** the detector also reports the window's peak sample, and the input counts as silent only when that peak is at
    most about 2.5 steps of 16-bit. The challenge showed that A does not rescue a -100 to -110 dBFS interface, whose
    peaks are 0.4 to 1.1 steps. It also depends on what Windows' gated input looks like.
  - **B:** lower `SILENT_LEVEL`. This risks no longer catching Windows' gated input.

## Challenge

Two fresh read-only agents challenged every plan. Seven are sound (02, 03, 05, 07, 09, 10, and 12), five are amended,
and none was dropped.

- **01:**
  - The field's top is now snapshot state, so the reveal also reruns when the field moves under an open sheet.
  - The sheet's top is written only while the search is open.
  - The extra room after the last line grows before the reveal, and when the sheet closes the text is animated back
    before the room shrinks, so it never jumps.
  - `ui/dialogs/CLAUDE.md` is no longer among its files.
- **04:** the corrected wording no longer claims the controller asks once per row. It skips only the row it already holds.
- **06:** `validate` cuts only an onboarding the app is not showing at that moment. A dropped file can put the import
  screen or a song over a live onboarding, and a Forward from there must not close it. `BrowserHistory.kt` joined the files.
- **08:** when the slots and the dots cannot all fit, at a large font or interface scale, the layout falls back to
  today's placement. It uses `placeRelative`, and reads the animating dots' width on every measure pass.
- **11:** the recommendation moved from A to C (see D1). The plan also notes the `peak` name clash in `detect` and the
  three `PitchEstimate` constructions.

## Checked and found solid

- **Lookup parsing and ranking** (MusicBrainz, iTunes):
  - Years, durations, joined artists, the album choice, and the credits are handled correctly.
  - Lucene phrase escaping and UTF-8 encoding are right.
  - The cover search and the lookup share one rate limiter, which honours Retry-After.
  - A new search cancels the old one, so no stale results are shown.
  - The lookup is cancelled once no sheet in the stack has it open.
- **Fill in writes nothing.** Save still compares against what the form was first offered. The dialog stack's slots,
  uncovering, and web history are right.
- **Onboarding:**
  - Each answer is written once, through the same setters as Settings. Skip and Close leave nothing half-set.
  - Reopening from Settings writes nothing.
  - The steps advance by their order. Back steps through the pages. The step is restored after process death.
  - The first-run ordering against the demo import, What's new, and queued imports is right.
  - Preferences decode from 4.8.0 documents. Card details stay per device.
  - The `SyncConnectionManager` change and its test are sound.
  - Both string files have the same keys.
- **Mandolin:**
  - Every table entry sounds only notes of its chord (G3 D4 A4 E5).
  - `{define-mandolin}` survives transposition, notation conversion, the highlighter, and insertion.
  - Slash chords play as the chord over the bass. The instrument chip changes the library-wide preference, as documented.
- **Haptics:** a section and an end arriving together give one notch, and a drag's first frame gives none.
- **Editor search:**
  - Matches never overlap, are never stale, and are literal.
  - Replace all is one undo step, written from the end backwards.
  - Ctrl/Cmd+F and R are caught only while the editor is on top.
  - The sheet counts in `OverlayState`.
  - The query is restored after process death.
- **The tuner's microphone promise holds:** a restored tuner, the web's address, and a configuration change never ask.
  The Windows settings links run without a shell, so there is no injection.
- **Icon switch removal:** nothing is left over.
- **Gradle wrapper upgrade:**
  - `check_license_headers.py` exempts `gradle/wrapper/*`, and it and `spotlessCheck` pass at HEAD.
  - CI's `setup-gradle` validates the wrapper jar.

## Dropped after verification

None. Every finding held when it was checked again at HEAD.

Two remarks were not raised as plans:

- Ctrl/Cmd+S does nothing while the search sheet has focus. This is a possible improvement, not a defect.
- A lookup where one catalogue fails while the other answers shows no failure line. This matches the cover search,
  which was settled earlier.

## Manual checks owed

- **01:** on the 360×640 AVD and a ~412×915 phone, with the keyboard up and down, step through matches in Find and in
  Find and replace. Every current match should show above the sheet, or as much as the window allows.
- **02:** none. The unit tests cover it.
- **03:** fill in the album from the lookup, clear it, then fill in the same recording again. The album should come back.
- **05:** on the desktop, show Duration on song cards, then widen the window slowly from about 1250dp to 1500dp. The
  panel should appear once and stay.
- **06:** on the web, finish the first-run onboarding, then press Forward. Nothing should happen.
- **07:** on the web, on a fresh profile, connect Dropbox from the onboarding with preferences not yet written. The
  onboarding should come back on the Dropbox page.
- **08:** on the 360×640 AVD, in English and Hungarian, check that the last onboarding page's Start playing button
  stays on one line.
- **10:** on Android, start and stop a reference tone. It should stop at once with no pop.
- **11:** on macOS with a quiet audio interface, the tuner's notice should no longer point at a permission. On Windows
  with audio enhancements on, the Windows notice still shows.
- **12:** a mandolin player should look over the table. This review was already owed.
