<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Manual checks owed

Every hand, device, store, and player check still owed, compiled on 2026-10-14 at `fde5a21a6` from the memory notes,
`documentation/TO_DO.md`, the plans in `documentation/plans/`, the "Manual checks owed" sections of review READMEs
still in git, and the hand-check sections of deleted feature plans. Duplicates are merged and keep every origin.

**Release context.** The last release is 4.8.0 (tag of 2026-10-12, Baseline Profile regenerated in it); 90 commits
have landed since, all pushed. Unreleased so far: the song details lookup, mandolin, banjo, and open-tuning diagrams,
the Chords tab, the instrument unification, the 12-string guitar, the import review, tab retuning, the onboarding
rework, and the 29th review's fixes. Checks of those are the most likely release blockers.

**Origins.** `R7-14` is plan 14 of the seventh review (memory note `seventh-review.md`); `R1`–`R6` are the
pre-release reviews, `PERF` the performance review, `PDF` the PDF export review. Feature notes are named by their
memory file (`metronome`, `tuner`, `chord-diagrams`, `mandolin`, `banjo`, `alt-tunings`, `instrument-unification`,
`12-string`, `tab-retuning`, `import-review`, `multi-select`, `tag-colors`, `lookup`, `layout-scoring`); "§n" is that
feature plan's hand-check section, read from git where the plan was deleted.

**Verdicts.**
- **[A-unit]** a plain unit test of pure logic (most on the desktop target, beside the existing tests).
- **[A-ui]** a Compose UI test (`runComposeUiTest` on the desktop JVM target) driving the shared UI with fakes.
- **[A-render]** a screenshot or golden test with the offscreen renderer in `tools/screenshots`.
- **[Manual-device]** needs a real device or OS: system bars, the IME, permissions, audio, store sandboxes, real
  Dropbox, browsers.
- **[Manual-human]** needs judgment: a musician, a native reader, visual taste, or a legal/store text.

An item needing a mix is filed under the verdict of its larger part; the remainder is named in the line.
**BLOCKER** marks what should hold the next release.

## Release blockers at a glance

- **NAV-01** the short-window rail on a phone on its side (R29-45, called release-blocking by the review).
- **ST-01** the privacy policy does not yet cover the song details lookup, which ships in the next release.
- **AND-05** regenerate the Baseline Profile: the navigation chrome, the import screen, and the onboarding changed
  after 4.8.0.
- **IMP-01** the import review, new since 4.8.0, writes and replaces library files; one full pass per platform.
- **CH-01** player review of the hand-typed mandolin, banjo, and open-tuning tables (the plan had gated shipping on
  it; the user chose to finalize, so this is a soft blocker).
- **ED-01** the editor keeping caret and keyboard across rotation or a resize past 840dp (R14-23, "release-blocking
  candidate", never confirmed on a device).
- **SYNC-03** the mixed-version two-device sync, which the upgrade to the next build will put every syncing user
  through.

## Sync (Dropbox)

- **SYNC-01** Every Dropbox flow end to end: connect (consent page, redirect), first run, edits, deletions, and
  renames on both sides, disconnect, reconnect, debounce of auto-sync, and an upgrade staying connected. *Android,
  iOS, desktop, web, Mac App Store build.* Origins: R1, R2, R3, R4, R7 lane A, R22 (sync split). **[Manual-device]**
- **SYNC-02** The Dropbox console redirect URI `https://campfire-songbook.com/app/` is registered, and the web build
  connects from that origin. *Web.* Origin: R10. **[Manual-device]**
- **SYNC-03** **BLOCKER** Mixed-version two-device sync, one device on the last release and one on the new build,
  clocks a day apart: an untouched demo song takes the cloud copy, one "Getting started" setlist and no ` (2)`
  copies of undated setlists, tag colors and song accents survive, per-song overrides (capo, tempo) survive quick
  saves and setlist ticks during the other's run. *Any two platforms.* Origins: R19-01, R18-20, R14-10, R25-01,
  R16-10, R16-12, R16-31, R16-33. Simulate both devices over an in-memory provider with old-format documents; one live
  pass before release. **[A-unit]**
- **SYNC-04** Synced preferences robustness: a cloud `preferences.json` hand-broken (trailing comma) or deleted keeps
  every override and is repaired; a hostile keyboard shape value opens the app; a hand-edited unknown instrument
  (`"mandola"`) keeps its drop D tuning. *Any.* Origins: R15-10, R18-12, R29-30. **[A-unit]**
- **SYNC-05** Library deletion across devices: device A deletes the library, device B answers "Keep them and upload"
  and keeps its overrides; an import during a deletion waits. *Any two.* Origins: R15-13, R16-35. **[A-unit]**
- **SYNC-06** Deleting the library while a sync run is going keeps the "delete from the cloud too" answer, and
  Delete stays disabled while a dropped folder imports. *Desktop.* Origins: R10-01, R16-35. **[A-ui]**
- **SYNC-07** Names across platforms: two devices spelling one file name in different case share settings; a song
  titled in Adlam or with 𠀀 keeps those letters, round-trips through export and import, and syncs between an Apple
  device and Android/desktop as one file; a hand-capitalised file plus a zip answered Skip keeps the setlist on the
  library's song. *Two platforms.* Origins: R15-15, R16-06, R16-15. **[A-unit]**
- **SYNC-08** First scan of a large library: a tag added or a song deleted while the first scan or first sync fills
  is not undone, loading stays up, end-of-list covers survive offline, and no "Could not access the cover art cache"
  appears. *Any.* Origins: R14-11, R14-12, R16-13. **[A-unit]**
- **SYNC-09** A sync run of a large library over a throttled link completes (60 s request timeout), and covers load
  on a slow network. *Any.* Origins: R8-01, R7-16. **[Manual-device]**
- **SYNC-10** Revoke Campfire in Dropbox, Sync now, Connect and close the consent page: still "authorization
  refused", and the app recovers. *Any.* Origin: R16-14. **[Manual-device]**
- **SYNC-11** A second synced device and a read-only cloud folder: the conflict and refusal paths behave. *Any two.*
  Origins: R13-20, R13-21, R13-22. **[Manual-device]**
- **SYNC-12** Background sync: the Android sync notification and chained runs, the iOS notification and background
  handover. *Android, iOS.* Origin: R22 (plans 68, 69). **[Manual-device]**
- **SYNC-13** Closing the tab mid-import and mid-first-sync leaves no empty songs and no ` (2)` copies. *Chrome,
  Safari 18.* Origin: R16-18. **[Manual-device]**
- **SYNC-14** Chord shape choices and tag colors made on two devices (different chords, then the same one) merge
  after a sync. *Any two.* Origins: chord-diagrams §8, tag-colors. **[A-unit]**
- **SYNC-15** A sync run deleting a ticked song from another device while a selection sheet is open. *Any.* Origin:
  multi-select §8. **[A-ui]**
- **SYNC-16** Desktop relaunch 6–15 s after quitting mid-sync waits and hands over; a quick relaunch with no sync is
  unchanged; a failed listener costs about 5 s. *macOS, Windows, Linux.* Origin: R14-30. **[Manual-device]**
- **SYNC-17** Restores and reinstalls start disconnected: Android and iOS device backup restore, an iOS reinstall
  (Keychain outlives it), and the Keystore and Keychain credential migrations. *Android, iOS.* Origins: R1, R5.
  **[Manual-device]**

## Import

- **IMP-01** **BLOCKER** The import review end to end: a whole-library zip (loading stage, groups and counts, untick a
  group, Import, snackbar with Details); the same zip again ("Already in your library", "Nothing to import"); two
  edited songs with taken names under Keep both and under Replace, the confirmation worded for the count, the
  "For all of them" row reading Mixed, the menu's padding, and the left-out names in the result; Replace never
  overwriting a song the batch carries unchanged. *Every platform once.* Origins: import-review §10, R29-38, R29-39,
  R29-40, R29-41, R6-21. **[A-ui]**
- **IMP-02** "Open with" a single `.cho` opens the song without a review, a taken name shows the one-row review, a
  conflicting file at launch on a What's new version asks its question first, and a fresh install opened with a file
  conflicting with a demo file shows no welcome over the question. *Android, iOS, desktop.* Origins: import-review
  §10, R12-28, R16-34. **[Manual-device]**
- **IMP-03** A fresh installation opened with a zip: onboarding, then the review slides in (Next and Skip); the app
  ended mid-onboarding and opened with a zip again shows the onboarding first. *Android at 360×640, iOS simulator.*
  Origin: import-review §10. **[Manual-device]**
- **IMP-04** Process death under the import review: rotation keeps the ticks, a dialog over it and Back behave, and
  after `am kill` the "import was interrupted" snackbar shows. *Android.* Origins: import-review §10, R29-37.
  **[Manual-device]**
- **IMP-05** Web import: a dropped folder, the review, browser Back, Forward refused. *Chrome, Firefox, Safari.*
  Origin: import-review §10. **[Manual-device]**
- **IMP-06** Ctrl/Cmd+F into the review's search and Escape out; Escape does nothing while a large import runs; right
  and middle clicks do not step a song. *Desktop, web.* Origins: import-review §10, R21-33, R12-34. **[A-ui]**
- **IMP-07** A file dropped while an import result is open is imported once it closes; dragging `.odt` with a `.cho`
  reports the `.odt` once by name; a `chmod 000` `.cho` is reported as skipped. *Desktop.* Origins: R29-35, R17-12,
  R16-38. **[A-ui]**
- **IMP-08** Opening a duplicate "Already in the library" row does not crash; a zip holding one song twice is listed
  once and pages normally. *Any.* Origins: R16-30, R16-17. **[A-ui]**
- **IMP-09** Huge or hostile inputs stay responsive with a working Cancel: a Word file of more than 8 000 paragraphs, a
  300-page PDF songbook, a crafted 3 000 × 3 000 `{chorus}` file (open, transpose, export), a 2.85 MB song on a 2 GB
  emulator, and a 300-section song at 1400×800 and text 0.5. *Android, web, desktop.* Origins: R12-27, R16-01, R8-06,
  R10-12. **[Manual-device]**
- **IMP-10** Real SongbookPro backups: multi-line copyright and author, and a `dataFile.txt` of `{}`. *Any.* Origins:
  R17-10, R17-11. Needs one real backup as a fixture. **[A-unit]**
- **IMP-11** Parsing edge cases on import: a pre-17 `.txt` with `[Verse 1]` asks the conflict question; `{start_of_abc}`
  closed by `{end_of_verse}` then `{ns}` gives two songs; a Campfire 3 `{c: Chorus}` with `{c: x2}` recalls whole;
  a `{t:}`/`{soc}` copy of an imported song is "already there" without `_2`; two same-title template songs export and
  re-import without a question. *Any.* Origins: R12 D1, R14-02, R16-02, R16-23, R16-16. **[A-unit]**
- **IMP-12** The import screen's search finds `Y.M.C.A.` for `ymca`, and a `&` search works in the lists. *Any.*
  Origins: R14-22, R7-20. **[A-unit]**
- **IMP-13** Hebrew and Arabic PDF exports copied as text in Preview, Chrome, and Acrobat, then re-imported. *macOS,
  any browser.* Origin: R12-16. **[Manual-device]**
- **IMP-14** Desktop exit during an import: closing or Cmd+Q during "Importing" keeps the whole batch and during
  "Reading" keeps nothing, a second launch waits, macOS logout mid-import, and an unanswered conflict question does
  not wait. *Desktop.* Origin: R21-33. **[Manual-device]**
- **IMP-15** A PDF, a Word, and a zip import after the `:data:formats` split. *Any.* Origin: R22. **[A-unit]**

## Onboarding, first run, and What's new

- **ONB-01** First-run smoke: the demo library planted once, the onboarding opens on every start until finished, and
  Settings opens it again; "Add the demo songs" plants the demo setlist undated with its countdown. *Android, iOS,
  desktop, web.* Origins: R19-01, R22 (cold start), R10-02. **[Manual-device]**
- **ONB-02** On the web, finish, skip, or close the onboarding, then Forward does nothing; on a fresh profile,
  connecting Dropbox from the onboarding returns to the Dropbox page. *Web.* Origins: R28-06, R28-07.
  **[Manual-device]**
- **ONB-03** What's new is recorded after 3 s on screen or on close: close within 1 s, or keep it up 5 s then kill,
  after a local version bump. *Any; Play internal track once.* Origin: R13-47. **[A-ui]**
- **ONB-04** Onboarding answers apply: Mandolin and Banjo set their diagrams, a drop D tuner moves the diagrams, and
  the 12-string is left out with the tuner off. *Any.* Origins: mandolin, banjo, alt-tunings §7, 12-string.
  **[A-ui]**

## Navigation and small screens

- **NAV-01** **BLOCKER** A phone on its side shows the rail with Songs, Setlists, Tools, and Settings, and the handover
  works when rotating and when resizing the desktop window across 480dp. *Android, iOS, desktop.* Origin: R29-45.
  **[A-ui]** (the rotation itself once on a device)
- **NAV-02** Switching the Metronome or Setlists feature off and tapping its shrinking navigation item stays on
  Settings. *Any.* Origin: R16-41. **[A-ui]**
- **NAV-03** Static layouts at 360×640dp in English and Hungarian, at font scale 1.0, 1.3, and 2.0: the navigation bar
  (five items, "Beállítások"), chips, label rows, stacked form labels (and whether `MIN_SHORT_FIELDS_ROW_WIDTH` can drop
  to about 380dp), Előnézet, the Settings tab row fading at its ends, the onboarding's last page on one line and its
  balanced footer, the compact tuner, the chord shapes sheet, the instrument and tuning sheets, the 12-string's two
  rows of strings, the banjo's five strings, the selection's Done button with a three-digit count, and the section
  headings. *Shared UI.* Origins: tuner §8, R24-32, R24-33, R24-34, R24-39, R24-43, R24-62, R13-09, R13-37, R29-46,
  R29-47, R28-08, 12-string, banjo, alt-tunings §7, multi-select §8, R13-30, R13-31. **[A-render]**
- **NAV-04** The same 360×640 screen with the keyboard up: the Chords tab's search and results, the import rows and
  their hidden floating button, the lookup sheet in Hungarian, the tag color popup, and the selection's three sheets
  on a phone on its side. *Shared UI, fake IME inset.* Origins: R29-21, R29-36, lookup, tag-colors, multi-select §8.
  **[A-render]** (one device pass for the real IME)
- **NAV-05** Every painted fade in light and dark and in two palettes, the export screen fading top-only at the
  window's bottom, and the zip box at both edges. *Shared UI.* Origins: R20-31, R14-24. **[A-render]**
- **NAV-06** Visual regression after the structure refactor: menus, filters, sort, metronome accents, and a
  `:tools:screenshots:run` compared by eye with the previous build. *Shared UI.* Origin: R22 (plan 03). **[A-render]**

## Sheets, forms, and dialogs

- **SH-01** Keyboard handling in sheets on real phones: Next through New song (Year and Duration included), the
  pickers, the cover sheet, and Delete library; the bottom fade above the keyboard; iOS no longer panning; the web's
  VirtualKeyboard; the keyboard on a tablet and on an iPad. *Android, iOS, web.* Origins: R13-02, R7-18, R7-19.
  **[Manual-device]**
- **SH-02** Plan conditions never checked: revert R13-34 if the caret line was already visible on the 360×640 AVD
  with three-button navigation, and R13-12 if the iPhone number pad already had Next. *Android, iOS.* Origins: R13-34,
  R13-12. **[Manual-device]**
- **SH-03** Cancel and double taps write nothing extra: ✕ or a swipe then Save writes nothing in every form, ✕ then
  the keyboard's Done in New song and Delete library, a double tap on Create, Save, or Delete creates once and the
  sheet slides away, ✕ then press-and-hold keeps it up, and Back then an instant tap on Save PDF opens no picker.
  *Any.* Origins: R13-03, R16-48, R11-07. **[A-ui]**
- **SH-04** The date picker shows only a calendar in the app's language with the phone in another, and survives an
  Android rotation with an unconfirmed pick. *Android, iOS, desktop, web.* Origins: R9-05, R13-14. **[Manual-device]**
- **SH-05** Cover sheet: Cancel on Remove cover returns to a fresh search; a typed address gets `https://`. *Any.*
  Origins: R13-06, R13-10. **[A-ui]**
- **SH-06** Stacked dialogs: Back on Android and iOS with three stacked sheets, and a form sheet with typed text
  restored after `am kill` (it reopens empty if the fields fail to reattach). *Android, iOS.* Origins: lookup, R24-24.
  **[Manual-device]**
- **SH-07** Song details lookup in the form: fill the album in, clear it, fill the same recording again and the album
  returns; Fill in only changes the draft; the editor-draft target's Done and undo. *Any, fake catalogues.* Origins:
  R28-03, lookup. **[A-ui]**
- **SH-08** The lookup against the live catalogues once per platform. *Android, iOS, web.* Origin: lookup.
  **[Manual-device]**
- **SH-09** Pickers return to their first row after search, clear, a chip, or a sort change; a tick keeps the tapped
  row under the finger; New setlist keeps the top row; a picker over about 2 000 songs and Select all over several
  thousand songs stay quick, then typing and ticking in Choose setlists. *Any; one low-end phone.* Origins: R21-01,
  R7-21, R26-10, R26-12. **[A-ui]**
- **SH-10** Manage tags: a tag named `createTag`, typing `rock` with `Rock` in the library, no jump after a tag filter,
  and the color popup opened by touch in a real sheet. *Any.* Origins: R10-03, R10-04, R10-05, tag-colors. **[A-ui]**
- **SH-11** Filter chips: Show all and Show fewer animate both ways in the phone sheet and the desktop side panel, the
  collapsed count is the same after Reset, and with Duration on song cards, widening from about 1250 to 1500dp shows
  the panel once. *Phone, desktop.* Origins: R13-38, R28-05. **[A-ui]**
- **SH-12** Setlist flows: with every setlist archived, a song's naming sheet opens directly; a setlist created from
  the selection's Choose setlists sheet shows ticked and disabled; another device's description survives a title-only
  Edit setlist; Save on a deleted setlist fails with a message. *Any.* Origins: R14-21, R25-11, R16-33. **[A-ui]**
- **SH-13** Setlist reorder: grips animate in and out with no re-wrap or cover flash, a drag during the
  bring-to-top scroll is not frozen, rapid Reorder and Done ends right, and a short setlist's pull then Done leaves no
  faded top card. *Android, iOS, desktop.* Origins: R21-10, R17-23. **[A-ui]**
- **SH-14** A setlist file with `"date": 2026` lists as undated. *Any.* Origin: R9-01. **[A-unit]**
- **SH-15** The licenses sheet lists what each build ships, js-joda on the web included. *Desktop, iOS, web.* Origins:
  R29-48, R29-49. **[Manual-device]**
- **SH-16** Large-library search: close, scroll, reopen without a jump to the top or a flash of old results; close
  restores the position; the report screen's search keyboard causes no per-frame recomposition. *Any.* Origins:
  R16-46, R16-47. **[A-ui]**

## Selection mode

- **SEL-01** The viewport under a tick in one, two, and three columns (first card on screen, first tick of a session,
  last song of a section, a tick in search results, a tick during a fling), and search opened and closed inside the
  mode without jumps. *Any.* Origin: multi-select §8. **[A-ui]**
- **SEL-02** Leaving the mode: Android Back and predictive back, desktop Escape, and browser Back and Forward (search
  first, then the mode, never off the page). *Android, desktop, Chrome, Firefox, Safari.* Origin: multi-select §8.
  **[Manual-device]**
- **SEL-03** Three hundred songs tagged at once: the progress bar, one list update, one sync run, and an
  already-processed song deleted mid-write. *Web (OPFS) and any.* Origins: R25-10, multi-select §8. **[A-unit]** (one
  web smoke)
- **SEL-04** Export a selection in both formats, Save and Share on a phone, and import the zip into an empty library.
  *Android, iOS.* Origins: multi-select §8, R25 D14. **[Manual-device]**
- **SEL-05** A wide window with the filter panel: the menu's entries out as buttons, the button over the list and not
  the panel. *Desktop.* Origin: multi-select §8. **[A-render]**

## Song details, layout, and pedal reading

- **SD-01** Pedal-only reading: Up and Down alone reach every line forward and back through long songs (a fast pedal
  back included), a held key does not chain pages, a text-size change inside a tall section skips nothing, Up hands
  off without skipping a song, a header-only scroll leaves the step buttons room, and a wrapped chord-only line ends
  cleanly; once with a real Bluetooth pedal on a phone. *Desktop sizes, Android, iOS.* Origins: R10-11, R10-15, R10-16,
  R10-17, R10-18, R10-19, R20-33, R20-34. **[A-ui]**
- **SD-02** Scored layout by eye: House of the Rising Sun at 800×600, the three website songs at the laptop and iPad
  sizes, Bad Moon Rising at 80%, a song one line over a page fitted at 97.5%, short lines in four columns, the
  360×640 phone in one column, and `Promo.kt`'s `laptop-song-light` page one ending on a section boundary. *Shared UI.*
  Origins: layout-scoring §9, R27-04, R27 (retake note). **[A-render]**
- **SD-03** The fitted text is as sharp at 95% as at 100%, and the fold chevrons and steppers answer a tap in the
  fitted first section. *Android, iOS, macOS, Windows, web.* Origin: layout-scoring §9. **[Manual-device]**
- **SD-04** Pinch behavior: a pinch through many layouts returns to its row, tab rows look unchanged, the fit glides
  in at release, a swipe away within half a second keeps the new size, and a font scale changed elsewhere after a
  pinch is honored. *Shared UI.* Origins: layout-scoring §9, R26-01, R26-04, R26-05, PERF-02. **[A-ui]**
- **SD-05** Pinch performance and feel on a low-end Android phone and a tablet, which also settles the deferred
  visual pinch scale decision. *Android.* Origins: layout-scoring, R26-04, PERF-10. **[Manual-device]**
- **SD-06** Folding: in a two- or three-column song the headers hold about a second and two quick folds both land, one
  of two narrow side-by-side sections folds on an upright phone with the page held then gliding, and the first frame
  has no hitch. *Desktop, web, iPhone.* Origins: R27-01, R27-02, R26-02. **[A-ui]**
- **SD-07** Short window: the app bar collapses with no jump, the song stays fitted, and the neighbour pages are right
  with the metronome panel opened; rows glide on a bottom-edge drag and on the panel; flings rest on dividers. *Phone
  on its side, short desktop window.* Origins: R26-04, R26-06, R20-33, R16-44. **[A-ui]**
- **SD-08** Edge layouts: a landscape phone or a ~1000×450 window with eight short sections, read-only mode at 360dp
  with a cover and a long title keeping 160dp for the title, a long tab run at a large text size on a phone on its
  side, a grid ending `x2`, a bare tab and a bare grid, and an annotation never drawn into the next song's page.
  *Shared UI.* Origins: R9-04, R9-06, R27-03, R11-01, R11-03, R10-16. **[A-render]**
- **SD-09** Chord diagrams section: heights for four, eight, and sixteen chords on every instrument at the smallest
  and largest text, upright and on its side, with the section shown and folded, and a 48-chord song opening at once
  with its dots filling in without moving anything. *Shared UI.* Origins: chord-diagrams §8, R18-30. **[A-render]**
- **SD-10** The drop condition of R18-39 was never checked: the drawn chord cells must match the composed FlowRow, or
  revert `0e5339952`; then Down lands on whole diagram rows, page 1 is not controls-only, and fold and pencil work.
  *Phone on its side, 800×600 desktop.* Origin: R18-39. **[A-ui]**
- **SD-11** Switching the instrument or tuning on a song crossfades straight to the right shapes with no clipping, the
  first section's height follows the chips, and the chips' text sits on the lyrics' keyline. *Shared UI; one low-end
  Android pass.* Origins: R29-10, R29-11, R29-14, instrument-unification. **[A-ui]**
- **SD-12** Tempo and time in a song: a leading `{tempo: 60}` gives no controls-only page and clicks 60 from page one,
  a tempo below a `{time}` change shows on the timing line, the editor's Tempo with the caret on the first lyric line
  selects the header value, a header `{tempo: Moderato}` + `{tempo: 96}` marks the first line, and a tab label
  `Riff [G]` transposes in the fold. *Any.* Origins: R17-20, R17-04, R17-01, R16-04, R16-05. **[A-ui]**
- **SD-13** A flick that starts on a chord diagram pages, and a tap meant for the page turn does not hit the chord
  button. *Phone.* Origins: R25-31, chord-diagrams §8. **[A-ui]**
- **SD-14** Restoration: a 500-song library restored on a song and on a setlist page, Back-Back on Android and Cmd+Q on
  the desktop keeping text size, metronome volume, tempo, and capo, and kill-and-relaunch. *Android, desktop.*
  Origins: R24-21, R16-36, R22-01. **[Manual-device]**
- **SD-15** Adding a tag or setting a tempo on an open song does not dim the app bar title, and paging still
  cross-fades. *Any.* Origin: R16-42. **[A-ui]**

## Editor

- **ED-01** **BLOCKER** Rotating a phone or resizing the desktop window across 840dp while typing keeps the caret and
  the keyboard, Ctrl/Cmd+S still saves, and an untouched editor takes no focus. *Android, iOS, desktop.* Origin:
  R14-23. **[Manual-device]**
- **ED-02** Unsaved text survives and ends right: an `am kill` with a long (>50k) text restores it, a desktop quit with
  Discard leaves no restored draft, an external edit under an untouched desktop editor is followed, and a process-death
  save after following an edit (known gap). *Android, desktop.* Origins: R24-20, R16-37, R22, R7-17. **[Manual-device]**
- **ED-03** Predictive back on an editor with unsaved text springs back with the dialog and no stuck scrim. *Android.*
  Origin: R15. **[Manual-device]**
- **ED-04** Ctrl/Cmd+S on the unsaved-changes dialog saves; German notation `[Bb]` above the caret then Ctrl+S keeps the
  caret; adding a tag from the menu keeps it in its verse. *Android hardware keyboard, desktop.* Origins: R19-30,
  R16-45. **[A-ui]**
- **ED-05** Find and Find and replace with the keyboard up and down: the match stays above the sheet, a last-line match
  scrolls up, and closing slides back without a jump. *360×640 AVD, ~412×915 phone, short desktop window, iPhone,
  phone-sized browser.* Origin: R28-01. **[Manual-device]**
- **ED-06** The landscape editor on the 360×640 AVD with the keyboard up: three lines, a working chevron, `dumpsys`
  showing `adjust=resize` (API 28 and 29 too), every text-field sheet clearing the keyboard. *Android.* Origins:
  R18-50, R18-51. **[Manual-device]**
- **ED-07** Prettify with the caret mid-song and at a line end; the library transposition shown the same in the
  preview, the field, and the stepper. *Any.* Origins: R12-19, R16-32. **[A-unit]**
- **ED-08** Hostile `{define}`s (repeated `frets`, huge frets or keys): Transpose does not crash and the preview stays
  responsive. *Any.* Origins: R18-01, R18-02, R18-37. **[A-unit]**
- **ED-09** The Chord shape button with the caret in, beside, and outside a chord and in a defined one, per instrument;
  the preview following a typed fret; a bad line marked; transposing up and back restoring the text with one undo
  each; an `H` define in German notation; the baritone tuning writing the standard shape with its snackbar; a banjo
  song transposed in the editor drawing the same diagram as on its page. *Any.* Origins: chord-diagrams §8,
  alt-tunings §7, R29-01. **[A-ui]**
- **ED-10** The editor's Tab button inserts a staff named for the player's tuning, and the preview names strings but
  moves nothing. *Any.* Origin: tab-retuning. **[A-ui]**

## Metronome

- **MET-01** Timing against a reference metronome for five minutes at 120, and the ProGuard desktop images making a
  sound. *Android, iOS, macOS, Windows, Linux, web.* Origin: metronome §9. **[Manual-device]**
- **MET-02** Android playback: the lock screen, the notification's Stop, a headset button, a call, pulled headphones,
  and a swipe-away stop it; rotation and a language change do not; rapid play and stop and a call coming in do not
  crash; a start during a call refuses with a reason; "Don't keep activities"; a click on a fresh install after
  picking a color keeps playing on the lock screen. *Android.* Origins: metronome §9, R15-20, R24-23, R16-63.
  **[Manual-device]**
- **MET-03** iOS playback: lock screen controls, the silent switch ignored, Siri or a call interrupting, AirPods
  removed stopping it, flash and haptics on the beat by ear (after a long locked run too), a click right after tuning,
  and the click with the tuner sheet. *iOS.* Origins: metronome §9, R15-23, tuner §8, R24-50. **[Manual-device]**
- **MET-04** Web playback: a background tab keeps time, the first tap after load sounds, an offline launch clicks, a
  sound chip then Play within 1.5 s sounds, no AudioContext opens with the Metronome off, and pausing from the OS media
  controls clears the session. *Chrome, Firefox, Safari.* Origins: metronome §9, R15-21, R15-24, R15-25.
  **[Manual-device]**
- **MET-05** The beat flash stays on the beat for minutes on a locked phone, and a silent click stops 3 s after the app
  leaves the front (notification teardown, the Hungarian snackbar, a minimised desktop, a hidden web tab). *Android,
  iOS, desktop, web.* Origins: R26-21, R19-10. **[Manual-device]**
- **MET-06** Unplugging a USB headset during a click stops it with "output failed" and CPU and memory settle. *Linux,
  Windows.* Origin: R16-11. **[Manual-device]**
- **MET-07** Click hand-over logic: a song opened over the Metronome tab takes over its click, a dropped `.cho` stops
  it and Back to the tab is silent, Update file name during a 6/8 or 3/4 click keeps tempo, accents, and the beat row,
  paging between songs of different time signatures near a barline, a 4/4 song with a 3/4 bridge, Song defaults
  saving with `{tempo: 320}` and the Metronome off, editor saves keeping it playing, holding Space or M toggling once,
  setlist overrides staying in their setlist and surviving export and import, rename and delete of a song with an
  override, and read-only mode without a tempo stepper. *Any, recording fake.* Origins: R15-01, R15-02, R15-22,
  R15-30, R15-31, R15-33, R16-43, R25-21, R25-22, metronome §9. **[A-ui]**
- **MET-08** The beat blocks' contrast in both themes. *Shared UI.* Origin: R24-38. **[A-render]**

## Tuner

- **TUN-01** Accuracy against a hardware tuner: six guitar strings, a bass's E1, a ukulele, every preset (five-string
  bass and low-G included), a banjo's octave pairs, through laptop and phone microphones; a steady needle on a ringing
  string; no octave jumps as a note dies. Recordings made here can then be dropped into the detector's
  `desktopTest` resources. *Every platform.* Origins: tuner §8, R24, R25-20. **[Manual-device]**
- **TUN-02** Permission paths: first ask, refuse, refuse for good, grant from settings and return, revoke, no
  microphone, a call while listening; the Android indicator only while the tab shows and the app still offered to a
  device with no microphone; the iOS orange dot gone on leaving and locking and the Hungarian prompt; Windows listing
  Campfire under microphone privacy with the toggle off read as silent or failed; PipeWire, PulseAudio, and no input on
  Linux; the browser prompt, a block, the indicator, `/tuner` cold, and the status after the prompt. *Android, iOS,
  Windows, Linux, Chrome, Firefox, Safari.* Origins: tuner §8, R24-10, R24-59. (The Mac sandbox check is done.)
  **[Manual-device]**
- **TUN-03** Engine robustness: a tone on then off shows no false "in tune", string changes show no needle swing,
  headphones plugged in and pulled minutes later, a Safari interruption blanks the reading, a Firefox microphone at a
  mismatched rate, an iOS route change during a tone, an Android start under a call on speaker, and a desktop stop and
  start during the 3 s reopen. *Per named platform.* Origins: R24-02 to R24-09. **[Manual-device]**
- **TUN-04** The reference tones by ear against the hardware tuner, with no microphone permission too; an Android tone
  stops at once with no pop; the iOS tone's loudness in `measurement` mode. *Android, iOS, others.* Origins: tuner §8,
  R28-10. **[Manual-device]**
- **TUN-05** Tuner screen states: the number changes in place and "In tune" fades, no tone plays from a fading row, the
  Chromatic switch first hides the instrument chip, the update-required cover over the Tuner tab, and the screen
  reader's live notice. *Shared UI, scripted tuner.* Origins: R26-20, R29-24, instrument-unification, R24-57,
  R24-41. **[A-ui]**
- **TUN-06** The silence notice reads as a hint on a quiet macOS interface and still shows on Windows with
  enhancements on. *macOS, Windows.* Origin: R28-11. **[Manual-device]**
- **TUN-07** Tuning performance on an iPhone, and a Chrome and Safari performance profile on the web (revert R26-23
  if it shows no gain). *iOS, web.* Origins: R26-22, R26-23. **[Manual-device]**

## Chords, instruments, and tabs

- **CH-01** **BLOCKER (soft)** A player of each instrument reviews the shapes: guitar, ukulele, and keyboard first
  shapes and variations, the mandolin table and defaults, the five banjo tunings' tables, the open tunings and drop D
  (from the `CAMPFIRE_CHORD_QA_DIR` contact sheets), moved definitions read at every transposition from −5 to +6, the
  fingerings of C69, C#mmaj11, Ebmmaj11, and Abm11, and the demo song's banjo, mandolin, and bass tabs. *Contact
  sheets, any device.* Origins: chord-diagrams §8, mandolin, R28-12, banjo, alt-tunings, R29-02, tab-retuning.
  **[Manual-human]**
- **CH-02** Chords tab: Open G and Drop D show no stale shapes, results stay across tab visits, the fading tuning chip
  does nothing when tapped, and a shape picked there is used in every song. *Any.* Origins: R29-20, R29-22, R29-23.
  **[A-ui]**
- **CH-03** Chord text logic: Nashville transposition does not blend two chords, `{key: C-dúr}` transposes to `D-dúr`
  and `B-dúr` shows `H-dúr` in German, and German notation with a keyboard capo. *Any.* Origins: R29-12, R14-01,
  chord-diagrams §8. **[A-unit]**
- **CH-04** The chord shapes sheet on the smallest phone and in a wide window, with four and with thirty chords, a
  step followed by the section behind it, and the button beside the pill at the smallest and largest text. *Shared
  UI.* Origin: chord-diagrams §8. **[A-render]**
- **CH-05** The one instrument picker from all five places (Song details, the Tuner, the Chords tab, Settings, and the
  onboarding), with the same result in each. *Any.* Origin: instrument-unification. **[A-ui]**
- **CH-06** Retuned tabs on phones: the right staff per instrument, "Guitar tab hidden" for a keyboard, notices only for
  judgment calls, on screen and in the PDF. *Android, iOS (shared UI), PDF.* Origin: tab-retuning. **[A-render]**
- **CH-07** Wording read by people: the Hungarian tab-retuning notices by a native reader, and the English strings
  read with the serial comma. *Shared UI.* Origins: tab-retuning, R29-51. **[Manual-human]**

## PDF export

- **PDF-01** A printed page per platform family against an old print, and the PDF opened in Preview, Chrome, Adobe
  Reader, and the Android and iOS Files apps; a printed drop D sheet read by someone who did not set it. *macOS,
  Windows, Android, iOS.* Origins: PDF-13, PDF-19, PDF-20, alt-tunings §7. **[Manual-device]**
- **PDF-02** Large exports: a 40+ song setlist on the web stays responsive with flat memory, and a 100+ page export on an
  iPhone. *Web, iOS.* Origins: PDF-12, PDF-17, PDF-18, PDF-19, PDF-21. **[Manual-device]**
- **PDF-03** System integration: rotation keeps selection and progress, Share to a messaging app and to a print
  service, navigation bar insets, a rotation inside the system save screen closes the export after saving, and a
  sandboxed Mac export without an extension saves. *Android, Mac App Store build.* Origins: PDF-23, PDF-26, PDF-28,
  R16-40, R16-39. **[Manual-device]**
- **PDF-04** The export screen across breakpoints: swipe, keys, and zoom; Tab and Shift+Tab; Down, Page Down, and the
  pedal not moving the song with nothing ticked; paging the preview from 9 to 10 on a phone on its side; PDF cancel.
  *Desktop, web, phone sizes.* Origins: PDF-24, PDF-25, R24-40, R11-06, R25-32, R22-01. **[A-ui]**
- **PDF-05** The PDF's tuning line names the instrument (not for guitar or keyboard), cannot be selected, and is not
  re-imported. *Any.* Origin: R29-13. **[A-unit]**

## Accessibility

- **A11Y-01** Semantics: a selection card read as one checkbox with its state, the button's count, steppers,
  headings, the tempo slider, beat blocks, the chord diagram rows and sheet, the tuner with a guitar, the PDF preview,
  a song with no section directives through the export screen, and the progress semantics. *Shared UI.* Origins:
  R26-11, multi-select §8, R24-30, R24-31, R24-34 to R24-37, R24-52 to R24-56, R18-39, chord-diagrams §8, PDF-31,
  R11-04, R11-08, R10-13. **[A-ui]**
- **A11Y-02** One real TalkBack and one VoiceOver pass over the same, confirming the live regions are announced
  (otherwise add the fallback wrapper of R24-41 and R24-56). *Android, iOS.* Origins: as A11Y-01. **[Manual-device]**

## Platform-specific

### Android

- **AND-01** Share intents into Campfire, shared text imported as a song. *Android.* Origins: R22 (plan 69), R2.
  **[Manual-device]**
- **AND-02** With Chrome disabled, a link shows the Hungarian "no app can open the link" snackbar. *Android.* Origin:
  R19-31. **[Manual-device]**
- **AND-03** A priority 4–5 release on the internal track shows the in-app update flow. *Android.* Origin: R20-14.
  **[Manual-device]**
- **AND-04** Low-end phone performance: the first search keystroke's frame, a throttled fling, and R20-33's drop
  condition on a slow phone. *Android.* Origins: R8, R7-14, R20-33. **[Manual-device]**
- **AND-05** **BLOCKER** Regenerate the Baseline Profile (`:app:android:generateBaselineProfile`); it still names the
  deleted `ImportProgressDialogHost`, and the navigation chrome changed after 4.8.0. *Android emulator.* Origins:
  R22, R23, R24, R26, R29-45, import-review, metronome. **[Manual-device]**

### Desktop: Windows, macOS, and Linux

- **WIN-01** On the low-end Windows 2-in-1: renderers compared (default, OPENGL, SOFTWARE_FAST, vsync off) against
  Chrome, `campfire.log` naming ANGLE, scrolling at least as good as before, `SKIKO_RENDER_API=DIRECT3D` still forcing
  Direct3D 12, and cold and warm start times; no regression on an ordinary Windows machine. *Windows.* Origin: R20.
  **[Manual-device]**
- **WIN-02** The `.msix` and the `.deb` map the class data sharing archive (`-Xlog:cds`). *Windows, Linux.* Origin:
  R20-01. **[Manual-device]**

### Web

- **WEB-01** Deep links, Back and Forward, the audio permission, `…/app/import` reloading offline and on the dev
  server, a bfcache return after another tab edited, and Safari's pinch left to the page. *Chrome, Firefox, Safari.*
  Origins: R4, R22, R14-31, R24-22, R10 D3. **[Manual-device]**

### Build tooling

- **TOOL-01** The iOS run configuration is listed and runs from a fresh clone on a Mac. *macOS.* Origin: R21-31.
  **[Manual-device]**
- **TOOL-02** A release with an empty notes block gives every store "Bug fixes and improvements." before any build.
  *CI.* Origin: R16-61. Cover with a case in `.github/scripts`' Python test. **[A-unit]**

## Store listings and website

- **ST-01** **BLOCKER** Widen the privacy policy on campfire-songbook.com to the song details lookup (a title sent to
  MusicBrainz and the iTunes Search API from the Edit song details sheet); today it names only the cover search. *Website.*
  Origin: lookup. **[Manual-human]**
- **ST-02** Update the website, the promo video, the store screenshots, and the store descriptions for the chord
  diagram library, and retake the website screenshots dropping `Website.kt`'s per-song scales. *Stores, website.*
  Origins: TO_DO.md, layout-scoring. **[Manual-human]** (the renders themselves are the `store-screenshots` skill)

## Catch-all

- **CA-01** The per-plan UI checks that no memory note copied: R13 plans 07, 08, 10, and 11, R17 plans 02, 03, 13, and
  21, and R18 plans 03–08, 10, 11, 14–16, 21–24, 31–36, 38, and 52–57. Their texts are in git in each plan's own
  fix commit (`git log --grep` on the review's range). Triage once; most are [A-ui]. **[A-ui]**

## Dropped as done, moved, or not yet reachable

- Store-side steps proven by the 4.7.0 and 4.8.0 releases through every store: the Play foreground-service
  declaration, the App Review background-audio notes, ITMS-91053, `publish-windows` and the Linux and Windows publish
  runs, the release description check, prepare-release diffing from the last tag, the `.deb` and `.msix` built on
  their hosts, and the tuner's store privacy entries.
- The Mac App Store launch (live since 2026-10-04, listing URL set), the JetBrains Runtime `.pkg` validation, and the
  tuner's sandbox microphone check (done 2026-10-11).
- The tuner and metronome paragraphs of the privacy policy (present on the website).
- Welcome dialog checks (R14-20, R18-54): the welcome dialog was replaced by the onboarding.
- The 14-chip tuning row and the five-chip instrument row (alt-tunings, banjo): replaced by the instrument and tuning
  chips with their own sheets, now in NAV-03.
- R8 plans 12 and 13: removed by the user.
- `importFromAddress` (a Dropbox link, a 404, airplane mode, 30 MB, Retry): nothing calls it yet; owed once a caller
  exists.
- The clipboard import, share extension, and song links plans: not implemented, so their hand checks are not owed.
- Not checks: telling users of the old web address, Dropbox production status after 50 users, and the scoped
  `CLAUDE.md` loading and configuration-cache runs, which every session exercises.

## Automation proposal

Ordered by value: what it protects against (data loss and release blockers first) per day of work.

1. **Two-device sync simulation in `:data:sync:implementation`** (covers SYNC-03, SYNC-04, SYNC-05, SYNC-07, SYNC-08,
   SYNC-14, SEL-03). The engine and planner are already tested with fakes; add a scenario harness of two engines over
   one in-memory `SyncProvider`, each with its own storage, preferences, and clock, plus fixture documents in the
   4.7.0 and 4.8.0 formats (undated setlists, no `syncedPreferencesFields`, legacy instrument keys). About 1–2 days.
   It leaves only the real Dropbox and OAuth checks (SYNC-01, SYNC-02, SYNC-09 to SYNC-13) by hand.
2. **A Compose UI test harness on the desktop JVM** (covers most [A-ui]: IMP-01, SH-03, SH-07, SH-09 to SH-13,
   MET-07, TUN-05, CH-02, CH-05, ED-09, NAV-01, and A11Y-01). Put it in a test source set of a JVM module beside
   `tools/screenshots` (a new `:tools:uitest`, or `tools/screenshots/src/test`), since `:presentation` cannot depend on
   `:app:di`. Reuse what the screenshot tool already solved: the owners from `Render.kt`, `CampfireDesktopApp()`, the
   isolated `user.home` with a fixture library, the friend path into `:presentation`, `Device.kt`'s sizes with fake
   system bar insets, and `Fakes.kt` (`ConnectedSyncRepository`, `SilentMetronome`, `ScriptedTuner`). Add
   `org.jetbrains.compose.ui:ui-test` (`runComposeUiTest`, `mainClock.autoAdvance = false` for the animated cases), and
   new fakes: a recording `Metronome` and `Tuner` (calls and tones), a scriptable `SyncRepository` (states, remote
   deletions), a `FilePicker` and launch-intent fake that hands in files, fake MusicBrainz and iTunes sources for the
   lookup, and a seam for the desktop-only flags (`interfaceScale`, `isDesktopPlatform`, `canShare`) so a run can
   pass for a phone. Harness about 3 days, then roughly half a day per area's tests.
3. **Pedal coverage walk** on that harness (SD-01, SD-10, SD-07): render demo and stress songs at five window sizes and
   three text sizes, press Down to the end and Up to the start, and assert from the layout that every line was fully
   on screen at some rest and no step jumped past the readable height. The tenth review did this live by hand once
   (7 747 rests); as a test it guards the user's hard requirement. About 1–2 days.
4. **Golden renders in `tools/screenshots`** (covers NAV-03 to NAV-06, SD-02, SD-08, SD-09, CH-04, CH-06, SEL-05,
   MET-08): a matrix of about 30 screens and sheets at 360×640dp, in English and Hungarian, at font scale 1.0, 1.3,
   and 2.0, light and dark, with and without a keyboard inset (add `ime` to the fake `PlatformWindowInsets`, and check
   first that the shared UI honours it offscreen), compared to committed PNGs with a small tolerance. The pinned fonts
   in `Fonts.kt` make it deterministic on one CI OS (Linux). About 2–3 days. Visual taste stays human; the goldens only
   catch changes.
5. **Semantics assertions** for A11Y-01 on the UI harness: roles, merged selection cards, headings, state
   descriptions, and live regions, leaving one real TalkBack and VoiceOver pass (A11Y-02). About 1 day.

What stays manual by nature: audio timing and route changes (MET-01 to MET-06, TUN-01 to TUN-04), the IME and
rotation on real phones (SH-01, ED-01, ED-05, ED-06), OS permissions and intents, the stores, real Dropbox, the
browsers' history and storage, and the players' and native readers' review.
