# Twenty-sixth review: UI speed, scrolling performance, and CPU usage

Reviewed commit: `ab97d3105` on `master`, with a clean tree. Angle: wasted recompositions, relayouts, and redraws per
frame, work repeated on every scroll, pinch, page, or tick, and CPU and battery spent while idle, in the background, or
at audio rate. Budget: lean, with three area reviewers (song details; lists and their state; tuner, metronome, editor,
and timers), one plan writer per lane, and two challengers. There was no live run, so every cost below is an estimate
from the code or a JVM probe, not a device profile.

## Headlines

- **A tablature run is laid out once per chunk (01).** Every slot unit of a tab run builds its own `TabRows` and
  measures the whole run at every width the search tries. A picking pattern cut into about 11 units costs about 11
  times the text layout on every pinch frame and every page open.
- **A pinch runs the fitted-text search on every frame (04).** Each whole-percent step reruns the grid search at
  0.975, and usually at 0.95 too, which roughly doubles or triples the layout work per pinch frame. Deferring it is a
  decision (below).
- **Choose setlists after Select all is slow (10).** The sheet searches a `List` once per entry per setlist, on every
  keystroke and tap. That measured about 23 ms per pass on a desktop JVM with 5,000 songs selected and 200 setlists,
  against 0.13 ms hashed.
- **Selection mode stops every song card from skipping (11).** The semantics modifier is a new element on every pass,
  so every visible card recomposes on every tick and at every scroll start and end.
- **The metronome wakes 200 times a second while it plays (21).** This includes a phone locked on a music stand, the
  main use. After the fix it wakes about 65 times a second at 120 bpm.

There is no data loss or correctness bug in this sweep. Every plan is a pure performance change.

## Index

| #  | Plan                                                                           | Severity        | Lane |
|----|--------------------------------------------------------------------------------|-----------------|------|
| 01 | Share one `TabRows` (and one set of aligned grid bars) per run                 | high            | D    |
| 02 | Keep every other section's measured sizes when a section is folded             | medium          | D    |
| 03 | Let each chunk of a section compose only its own items                         | low             | D    |
| 04 | Defer the fitted-text search while the layout follows a continuous change      | high / medium   | D    |
| 05 | Settle the neighbour pages' font scale after the current page has settled      | medium          | D    |
| 06 | Keep neighbour pages at the settled viewport height while the height animates  | low / medium    | D    |
| 10 | Count the Choose setlists boxes against a hashed selection, once per pass      | medium          | L    |
| 11 | Remember the selection mode's semantics modifier so song cards can skip        | medium          | L    |
| 12 | Make a tick's regrouping and card lookup avoid touching every song             | low             | L    |
| 13 | Prune the selection without hashing the whole library on every emission        | low             | L    |
| 14 | Rebuild the song picker's songs only when the library or its order changes     | low             | L    |
| 20 | Crossfade the tuner's reading line only when its kind changes                  | medium          | T    |
| 21 | Wake the metronome's beat release when the next tick is due                    | medium          | T    |
| 22 | Reuse the last pitch estimate while the tuner window has not moved             | medium          | T    |
| 23 | Read the web tuner's window through the held typed array                       | low             | T    |

## Lanes

| Lane | Area                    | Plans, in order        | Files owned                                                                                                                                                                                                                                                                                         |
|------|-------------------------|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| T    | Tuner and metronome     | 20, 21, 22, 23         | `:metronome:implementation` (`MetronomeEngine`, `FakeAudioOutput`, tests, `CLAUDE.md`); `:tuner:implementation` (`TunerEngine`, new `LatestEstimate`, `ToneSynthesizer`, `WebAudioInput`, `WebTunerAudio`, tests, `CLAUDE.md`); `:presentation`'s `ui/tuner/` (`TunerDisplay`, `CentsText`, `CentsTextTest`, `CLAUDE.md`); the root `CLAUDE.md` only if 22 needs its test list |
| L    | Lists and their state   | 10, 11, 12, 13, 14     | `:presentation`'s `dialogs/SelectionSetlistsSheet.kt`, `screens/setlists/SetlistSongToggle.kt`, `screens/songs/SongList.kt`, `screens/songs/SongSelectionGroups.kt`, `components/ChecklistOrder.kt`, `state/SongSelection.kt`, `state/SongPickerState.kt`, `search/SongPickerIndex.kt`, and their tests                                       |
| D    | Song details            | 01, 02, 03, 04, 05, 06 | `:presentation`'s `screens/songDetails/` (the files the plans name, its tests, and its `CLAUDE.md`), `fontScale/FontScaleController.kt`, and `CampfireViewModel.kt` (one `settleFontScale()` forward, plan 05)                                                                                       |

The lanes share no file. Inside lane D, 03 builds on 01, and 02 and 04 both edit `SongLyrics.kt`, so its order is
fixed. Lane D's amended 04 and 06 both add a `rememberContinuousChange` in `SongDetailsPage.kt`, and 06 is written to
work with 04's version.

**Merge order: T, L, D.** T is in other modules and touches nothing the others use. L and D are both in
`:presentation`, and D is the riskiest lane (timing and animation) and touches the most shared UI code, so it goes
last.

**Shared-file rules:** each lane updates only the `CLAUDE.md` sentences its plans quote. The root `CLAUDE.md` is
touched only by plan 22, and only if the root test list names the tuner's test classes. No plan adds a string, so
`strings.xml` is not touched.

## Decisions

- **04: defer the fitted-text search during a pinch?** With the fix, the text shows at the reader's exact size while
  the pinch is under way. If fitting it 2.5% or 5% smaller saves a page, that change lands as one glide when the pinch
  ends or a window edge stops moving. App-bar, title-row, and metronome-panel animations still fit in their one search,
  as today. **Answered 2026-10-11: defer** (the recommendation).

## Challenge

Two fresh read-only agents challenged every plan. They traced each fix through the documented usage, in-repo callers,
and the other plans, and checked the threading and lifecycle each fix introduces. Eleven came back sound, four were
amended, and none were dropped.

- **04 amended:** deferring on `SectionMotion.GLIDE` would also have deferred the one search at the end of every
  app-bar, short-window title-row, and metronome-panel burst. A fitted song would have shown unfitted for 200 ms and
  then fitted again, every time the title row collapsed. It now defers only for a change of width or font scale,
  through a second `rememberContinuousChange` on the settled height that is passed down as `SongLyrics(canFitText)`.
- **05 amended:** with a 500 ms settle, a swipe started inside the window would lay the song being left out again at
  the pre-pinch scale while it was still half on screen. The scale now settles at once when the pager starts
  scrolling.
- **06 amended:** the `onRowsPlaced` lambda read `maxWidth` and `maxHeight` from the `BoxWithConstraints` scope, so it
  was a new lambda every frame and the neighbours would still have recomposed. The viewport size is now computed
  outside the lambda.
- **21 amended:** a wait computed straight to the tick's frame assumes a continuous clock, but iOS, the desktop, and
  the web report their position in steps of up to about 20 ms. The loop now waits a chunk (20 ms) until 50 ms before
  the next tick, then polls every 5 ms as today, and polls every 5 ms until the session's first beat. A stepped-clock
  test pins it.

The writers also corrected three fixes against the code before the challenge:
- 10 counts over the small sets, since `(held + added).size` broke an existing test case.
- 14 compares the domain's `sortedSongs` by identity, since `search.byFileName` is a new map on every update.
- 22 caches the estimate instead of skipping the tracker step, since the tracker is fed repeated windows on purpose.

## Checked and found solid

- **Song details:**
  - The grid cache per `SectionGridKey`, and intrinsic heights cached per unit and width and reused across edits and
    transpositions.
  - Pinch input: at most one report per frame, quantised to whole percent.
  - The text measurement caches, which ignore color, so a theme crossfade does not re-measure.
  - Glides read only in placement.
  - The steppers and progress read through `derivedStateOf` and in draw.
  - The chord diagram strip, which is drawn rather than composed.
  - No idle tickers.
- **Lists:**
  - `SongSearchIndex`'s incremental re-folding and bucketed ranking.
  - The domain's sort memo.
  - `indexedSongs` being distinct.
  - The fast scroller's binary-searched section index.
  - The pushed headers, read in layout and draw.
  - Cover art stored at 250 px, decoded at its laid-out size, and in a placeholder of fixed size.
  - The card's memoized lambdas, and `LocalTagColors`' structural equality.
- **Tuner and metronome:**
  - State published only when it changes.
  - Buffers allocated once.
  - No allocation per chunk on the capture threads.
  - Listening tied to the lifecycle.
  - The needle and the beat flash drawn in the draw phase.
  - The feed threads block rather than spin.
  - The web worker's context is suspended when idle.
- **Editor:**
  - `ChordProTokenCache` is effective: about 161 µs per full tokenize of a 494-line song on a JVM.
  - The summary cache skips lyric keystrokes.
  - The draft is written to disk only on pause.
  - The preview is debounced and drops stale renders.
- **Timers:** `rememberElapsed` and `rememberToday` wake once a minute while composed, the sync scheduler is
  event-driven, and there is no `infiniteRepeatable` in `:presentation`.

## Dropped after verification

- **The section headers reading `selection.size` per tick (part of 11):** the headers that are not the Selection header
  ignore the count, so `SectionHeader` still skips.
- **The song cards' `userPreferences?.areSetlistsEnabled` read:** the lambda captures the `State` delegate rather than
  the value, so a preference write recomposes only that slot, not the whole card.
- **`tagColorOf` without an early return for an empty map:** micro, well under a frame.
- **`WebToneOutput`'s per-sample hand-over (part of 23):** it copies once per tone, the per-sample choice the
  metronome's docs record as cheaper than any bulk hand-over.
- **Micro-costs in song details:**
  - `SectionGlides.follow` rebuilding its key set.
  - `ChordRowsBlock`'s recomposition one frame late per width.
  - The phone path's unused score.

## Measurements

These come from JVM probes on an M-series Mac, not devices:
- `PitchDetector.detect` at 48 kHz: about 203 µs.
- `ChordProHighlighter.tokenize` on 494 lines: about 161 µs.
- The Choose setlists pass: 23 ms, against 0.13 ms hashed.
- Regrouping per tick at 4,800 songs: 0.35–1 ms, plus 0.25–0.8 ms for `itemIndexOf`.
- The walk of a 3,000-line section per pass: about 16 ms warm and 64 ms cold.

## Manual checks owed

- **01:** a song with a long picking pattern on a phone. Pinch, page to it, and check that the tab rows look the same
  as before.
- **02:** fold and unfold sections of a long song. The fold animates as before, with no hitch on its first frame.
- **04:**
  - Pinch a three-page song on a phone and on a tablet. The text follows at exact size, and a fit that saves a page
    glides in at release.
  - Collapse the title row in a short window, and open the metronome panel. A fitted song stays fitted, with no
    flash.
- **05:** pinch, then swipe to the next song within half a second. The song being left keeps its new size.
- **06:** in a short window, scroll so that the app bar collapses, and open the metronome panel. Neighbour pages show
  correctly when you swipe to them.
- **10 and 12:** Select all on a library of several thousand songs, open Choose setlists, type, and tick. Tick songs
  in selection mode on a low-end phone.
- **11:** TalkBack or VoiceOver still read a card in selection mode as one checkbox.
- **20:** tune a string. The number changes in place, and the in-tune line still fades.
- **21:**
  - Play the click with the phone locked for several minutes on Android and on iOS. The beat flash stays on the beat
    when the phone is unlocked.
  - Check the web and the desktop too.
- **22:** tune on an iPhone. The reading responds as it did before.
- **23:** tune in the web build, in Chrome and Safari.
- **Before the next release:** the Android Baseline Profile, since lane D changes the song details' startup path a
  little.
