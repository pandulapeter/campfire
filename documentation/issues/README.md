# Twenty-ninth review

Reviewed at `72d48deb7` on `master` (clean tree); execution starts from `153886ec6` ("Fix some deprecation warnings.",
which touched `DialogType.kt`, `settings/ChordsSection.kt`, `ChordProPrettifier.kt`, the two remote sources, two iOS audio
outputs, and `TO_DO.md` — no plan depends on those lines).

**Angle:** the new work since the 28th review (`25e6152ea..72d48deb7`, 15 commits): the Import review screen, alternate
and open tunings, the Chords tab, banjo and 12-string guitar, the separate instrument and tuning pickers, the onboarding,
the Settings reorganization, and the Open-source licenses sheet — correctness, data loss, migration from 4.8.0,
360×640dp and short windows, platform parity, and stale docs. **Budget:** thorough — five area reviewers, a live run on
the 360×640 AVD in English and Hungarian, and probe tests for the pure findings.

`ui/` below is `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/`.

## Headlines

1. **In landscape on a small phone the navigation rail cannot reach Settings** (45): the Chords tab made six rail items,
   the rail never groups the tools, and 360dp holds about five. Seen live.
2. **A wrong chord shape can be stored and synced from the Chords tab** (20): right after a tuning change the tab still
   draws (and opens) the previous tuning's shapes; the shape sheet then puts that stale default first, and paging away
   and back stores it under the new tuning.
3. **For a moment after a tuning change the song page draws the old tuning's shapes on the new strings** (10), and the
   first section keeps a cached height that ignores what its instrument chips say (11).
4. **Skia's bundled libraries (HarfBuzz, ICU, libjpeg-turbo, libpng, libwebp, Wuffs, Expat, zlib, piex, DNG SDK,
   FreeType) and js-joda are shipped uncredited** (48, 49) on the desktop, iOS, and the web.
5. Nothing loses library data. The import review never overwrites what was not confirmed; its queue can hijack an open
   report (35).

## Index

| # | Plan | Severity | Lane |
|---|---|---|---|
| 01 | Mute a banjo drone outside the chord wherever a definition is drawn (**decision D1**) | low | B |
| 02 | Refinger the four guitar shapes whose barre crosses a lower fret | low | A |
| 03 | Look a stored shape up in the table without building every moved shape | low (perf) | A |
| 04 | Correct the chord docs on playing the chord over, tuning shifts, and the banjo drone | low (docs) | A |
| 10 | Keep the chord cells until the model for the new tuning arrives | medium | B |
| 11 | Key the metadata section's size by what its instrument chips say | medium | B |
| 12 | Crossfade a chord cell only when it holds the same chord | low | B |
| 13 | Leave the PDF tuning line out of the selectable text (**decision D2**: name the instrument) | low | B |
| 14 | Bound the chord crossfade layers to the diagram | low (perf) | B |
| 15 | Skip the second chord walk when the chords are already collected | low (perf) | B |
| 16 | Correct the docs on the instrument chips, `withTuning`, and the PDF tuning line | low (docs) | B |
| 20 | Tag the Chords tab's results with the tuning they were found for | medium | C |
| 21 | Scroll the Chords tab's search field above the keyboard | medium | C |
| 22 | Keep the Chords tab's results across visits so it opens on them | low | C |
| 23 | Stop a leaving tuning chip from opening an empty tuning sheet | low | C |
| 24 | Play a tuner tone only for a string the instrument still has | low | C |
| 25 | Correct the docs on where the tuner and instrument sheets open from | low (docs) | C |
| 30 | Keep the tunings of a preferences document whose instrument is unknown | low | D |
| 31 | Document the shared instrument migration and the tunings read entry by entry | low (docs) | D |
| 35 | Make the import queue wait for an open import screen before starting a batch | medium | E |
| 36 | Hide the import screen's floating button while the keyboard is up | low | E |
| 37 | Say so when an import review did not survive the process (**decision D3**) | low | E |
| 38 | Give the conflict menu's two-line items vertical padding | low | E |
| 39 | Make the "for all" conflict choice show and set what its rows say (**decision D4**) | low | E |
| 40 | Word the Replace confirmation for the number of files it replaces | low | E |
| 41 | Name the files left out of an import as the review named them | low | E |
| 42 | Correct the onboarding history comment and name the import download in the module map | low (docs) | E |
| 45 | Group the tools behind one rail item in a window too short for every item (**decision D5**) | **high** | F |
| 46 | Fade the Settings tab row's edges where more tabs scroll past them | low | F |
| 47 | Share the spare room of the onboarding footer between the dots' two sides | low | F |
| 48 | Credit the libraries Skia bundles in the desktop, iOS, and web builds | medium | F |
| 49 | Credit js-joda in the web build | medium | F |
| 50 | Correct the onboarding reload doc, the README features, and the scroll-to-top items | low (docs) | F |
| 51 | Add the serial comma to the English strings that list three or more things | low | F |

## Lanes

| Lane | Area | Plans, in order | Files owned |
|---|---|---|---|
| A | `:chordpro` tables and lookup | 02, 03, 04 | `GuitarChordTable.kt`, `DropDChordTable.kt`, `ChordVoicingSearch.kt`, `ChordVoicings.kt`, `ChordTuning.kt` (KDoc), their tests, `chordpro/CLAUDE.md` (03, 04 paragraphs) |
| D | Preferences storage | 30, 31 | `UserPreferencesMappers.kt`, its test, `data/source/local/implementation/CLAUDE.md` |
| B | Song details page, print, definitions | 10, 11, 12, 13, 14, 15, 16, 01 | `ui/screens/songDetails/*` (SongLyrics, ChordCells, RenderSection, ChordShapeCrossfade, SongChordsSection, SongLyricsModel, SongDetailsScreen, SongInstrumentChips), `ui/components/InstrumentChips.kt` (11's parameter only), `ui/print/*`, `ui/chords/SongChords.kt`, `ui/chords/CLAUDE.md`, `ui/print/CLAUDE.md`, `ChordProDefinitions.kt` and its tests, `chordpro/CLAUDE.md` (01's paragraph) |
| C | Chords tab, shape sheets, pickers, tuner UI | 20, 21, 22, 23, 24, 25 | `ui/screens/chords/*`, `ui/dialogs/ChordShapePager.kt`, `ChordShapeSheet.kt`, `ChordReferenceSheet.kt`, `TuningSheet.kt`, `TunerSheet.kt`, `DialogType.kt` (KDoc), `ui/components/InstrumentChips.kt`, `ui/tuner/*` |
| E | Import review | 35, 36, 37, 38, 39, 40, 41, 42 | `ui/state/ImportController.kt`, `ui/screens/importReport/*`, `ui/navigation/Navigator.kt`, `BrowserRoutes.kt`, `ui/messages/*`, `ui/state/SavedStateStore.kt`, `importReport/ImportReport.kt`, `ui/dialogs/CampfireDialogs.kt`, `CampfireViewModel.kt`, `ImportFilesUseCaseImpl.kt` and test, `ImportResult.kt`, `ImportChoices.kt` (KDoc), `domain/implementation/CLAUDE.md` |
| F | Navigation chrome, Settings, onboarding, credits, strings | 45, 46, 47, 48, 49, 50, 51 | `ui/NavigationBarItems.kt`, `NavigationChrome.kt`, `CampfireContent.kt`, `CampfireScreens.kt`, `ToolsTabRow.kt`, `ToolScreenFrame.kt`, `ui/CLAUDE.md`, `ui/screens/settings/*`, `ui/components/EdgeFade.kt`, `ui/screens/onboarding/*`, `OpenSourceComponents.kt`, its test, licence texts, `README.md` |

Plan 01 is in Lane B, not A: its draw-time call sites are `SongChords.kt` and `ChordCells.kt`, which plans 10 and 11
reshape first.

**Merge order: A, D, B, C, E, F.** A and D are self-contained bases (B's plan 01 edits `chordpro/CLAUDE.md` after A's
plans 03 and 04 did). B before C because both read `ui/chords` rules the B docs plan rewrites. E and F both edit the
shared strings files and the root `CLAUDE.md`; F touches the most shared UI files (strings, README, `ui/CLAUDE.md`), so
it goes last.

### Shared files

- **`values/strings.xml`, `values-hu/strings.xml`:** E adds `import_interrupted` (37, after `import_failed`),
  `import_conflicts_mixed` (39, after `import_conflicts_replace_description`), and the plurals `import_replace_message`
  (40, after `import_replace_title`); F's plan 51 edits English lines only. No other lane touches them. Merge word by
  word, keeping every key.
- **Root `CLAUDE.md`:** E's 42 edits the `:data:source:remote` module-map lines; F's 45 edits the "Six top level screens"
  product rule.
- **`README.md`:** F only (48, 49 add table rows; 50 edits the feature paragraphs).
- **`chordpro/CLAUDE.md`:** A's 03 and 04, then B's 01, each a different paragraph.
- **`ui/chords/CLAUDE.md`:** B only (10, 11, 16, and 13 under option b).
- **`CampfireViewModel.kt`:** E only (37, 39).
- **`ui/components/InstrumentChips.kt`:** B's 11 adds one optional parameter (the tuning chip's visibility as a
  `MutableTransitionState`, also `songDetails/SongInstrumentChips.kt`); C's 23 then disables the leaving chip on top of
  it. B merges before C.

## Decisions

- **D1 (plan 01), answered 2026-10-13: the recommendation.** the banjo drone under transposition. (a) Mute an open drone whose note is not in
  the chord wherever a definition is drawn (`ChordProDefinitions.sounding`), and take the mute out of `transposed`, so
  the text round-trips and both paths draw the same shape — **recommended**; (b) write `x` into the text on transposition,
  which breaks the round trip.
- **D2 (plan 13), answered 2026-10-13: the recommendation.** should the PDF name the instrument? (b) Name it whenever it is not the guitar
  (6- or 12-string) or the keyboard, with the tuning when it is not standard ("Ukulele", "Mandolin · Tuning: GDGD") —
  **recommended**, no new strings; (a) leave it; (c) always name it. The unselectable fix stands either way.
- **D3 (plan 37), answered 2026-10-13: the recommendation.** an import review lost to process death or a web reload. (a) A snackbar after the
  restore, "The import was interrupted. Open the files again to import them." — **recommended**; (b) (a) plus confirming
  Back when choices were changed (the importReport CLAUDE.md calls the unconfirmed Back intended); (c) document only.
- **D4 (plan 39), answered 2026-10-13: the recommendation.** changes a documented, tested rule. The "For all of them" control sets every row it
  shows (as the group tick box does) and reads "Mixed" when rows differ — **recommended**; the alternative leaves rows set
  by hand alone and only fixes what the header shows, which can leave the control visibly doing nothing.
- **D5 (plan 45), answered 2026-10-13: the recommendation.** the rail in a short window. (a) Group the tools behind one rail item below
  `SHORT_WINDOW_HEIGHT` (480dp) — **recommended**; (b) a scrolling rail, which hides Settings until scrolled; (c) a rail
  without labels, which still does not fit six items in 360dp.

## Challenge

Ran on every plan (three fresh agents, one per group of lanes): 18 sound, 16 amended, none dropped.

- **03:** the optional skip by string count leaves the banjo drone out, since a move rewrites it.
- **11:** the tuning chip's visibility moves up into `SongLyrics` as a `MutableTransitionState`, so the section is
  measured again only after a leaving chip has gone (otherwise its height is cached with the fading chip in it).
- **20:** the pager puts nothing stale first even where the new tuning has no shapes for the chord: a definition pages
  alone, a player's shape goes in front only when missing, everything else is the list.
- **21:** the keyboard is read through `rememberUpdatedState` (iOS and the web hand out a new insets instance per
  composition); the field stops 8dp below the top fade; a short window also takes the status bar away while typing.
- **22:** problem text corrected (the defaults cache hides the empty page until it is emptied); the fix is unchanged.
- **24:** the guard moved from `TunerController.toggleTunerTone` to `TunerStringsSetting`, since the controller also
  plays the A4 reference button, which the first version would have silenced.
- **30:** the Tests section's description of the pre-fix result corrected.
- **35:** the test runs the never-ending queue in `backgroundScope`; the CLAUDE.md names a result reopened from Details.
- **37:** a saved stack cannot tell an unfinished review from a Finished result, so option (a) saves an "unfinished"
  flag in `SavedStateStore` instead of reading the stack; the web is left out of (a) for the same reason.
- **40:** the message wording matches the app's other irreversible confirmation ("This cannot be undone.").
- **41:** `leftOut.distinct()` is removed too, and the domain CLAUDE.md sentence it falsifies is corrected.
- **42:** the replacement comment names the one real way a song lands on an open onboarding.
- **45:** the chrome derives the grouping from an `isShortWindow` flag for its own kind, so an outgoing chrome does not
  regroup while it fades; rotating keeps the tools grouped both ways.
- **48:** the library markers are text markers checked on the stripped dylib, not exported symbols.
- **51:** a thirteenth string, `songs_empty_hint`.
- **25:** a stale note about the uncommitted `DialogType.kt` change removed.

## Checked and found solid

- **Import review:** plan entries and choices line up by the plan's own index; Replace only ever writes over a confirmed
  conflict (the confirmed count can only exceed the overwritten one); cancellation in every window; writes under
  `NonCancellable`; the demo library never races a user import; the onboarding gate holds the import screen and an
  opened song; rotation keeps every tick; source bytes are released once planned. Live: a zip with a collision,
  Replace, untick, and the written files were exactly right; the replaced song kept its star.
- **Preferences:** 4.8.0 documents migrate correctly (every tuner id resolves, chromatic only when explicit, old keys
  never written again); instrument and tunings are never synced; chord shapes sync by `voicingKey`, and 4.8.0 passes the
  new keys through; a tuning foreign to the instrument is never used; the `.bad` path is unchanged.
- **Tuner:** course partners (12-string octaves on the four low courses, unison on B and E), `strings` as the distinct
  union, `rangeFor`/`nearestString`/`targetOf` on it, ticks cleared on a change.
- **Chord tables:** ~7,600 shapes checked by script — no duplicates, no 4-fret stretches, no lowest fret at 12 or above,
  no unfingered muted gaps, no finger at two frets, no dead slash chords; all 39 double drop D shapes by hand; every
  shift tuning a uniform interval; `voicingKey` sharing sound; `{define}` selectors unambiguous; the editor's Chord
  shape button consistent.
- **Song page:** chip keyline at `CARD_PADDING`; `animateContentSize` passes intrinsics through; chips right on the first
  frame; the crossfade mechanics otherwise; stored shapes per `voicingKey`; paging keeps every line reachable.
- **Navigation and Settings:** feature switches cut `/chords` on the web and refuse a disabled tool; the Tools grouping
  and `lastTool`; the Settings tab enum unchanged, so no restored index is out of range; both strings files have the
  same keys and arguments; the 28th review's onboarding fixes (web Forward, Dropbox from the onboarding) still hold.
- **Live (Android, 360×640, English and Hungarian):** no crash anywhere; every onboarding page forward and back, skip,
  and reopen from Settings; every instrument and tuning through every picker; diagrams correct by hand for guitar, Drop
  D, 12-string, banjo open G and double C, mandolin, and DADGAD; German notation throughout; the microphone asked only on
  a tap.

## Dropped after verification

- **The Chords tab's key, search, scroll, and open shape sheet lost on process death:** by design —
  `screens/chords/CLAUDE.md` makes them the session's, and the chord sheets hold no draft (`ui/dialogs/CLAUDE.md`). The
  scroll loss on revisiting is the real cause behind plan 22.
- **The Chords grid "pops in without a transition":** it uses `listItemAnimation` (a fade-in).
- **"Other" on the Tuner reads chromatically with the switch off:** by design — `ui/tuner/CLAUDE.md`: "an instrument
  without strings (the keyboard, Other) is read chromatically either way."
- **The 12-string-to-mandolin partner row swapping in one frame:** the first row swaps in the same frame (plan 24 notes
  it).
- **The onboarding clipped in landscape:** by design — a page "is centered while it fits and scrolls with faded edges
  while it does not", and a phone on its side is named as such a case.
- **Hungarian "Rendszer" without a check mark:** by design — `SegmentedChoice` drops the icon where a row has no room.
- **`ws` 8.20.1 in the web build:** in the generated `package.json` but not in the bundle.
- **A web reload racing the owed onboarding against the address:** the onboarding is always put on the stack first;
  the doc contradiction is in plan 50.
- **Back mid-review drops the choices without asking:** documented as intended; kept only as D3's option (b).
- **Not planned:** the tuner's ~155dp below its reading at 360×640 (it scrolls); the banjo and mandolin chips reading
  "Standard tuning" for their standard tuning; Material's scrim description in the system language; the fast scroller's
  gutter beside song cards.

## Measurements

- `ChordVoicings.read` with a chord: 33–112 µs, against 0.6–2.7 µs without one (plan 03), per stored shape per cell build.
- Plan 01's probe: the editor's transposition writes `0 4 2 3 4` for banjo D where the reader draws `x 4 2 3 4`.

## Manual checks owed

(Filled in from the plans when they land.)
