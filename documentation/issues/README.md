# Fifteenth review

**Reviewed:** `f51b3cc6a` on `master` (the 23 commits since the fourteenth review, `1d86e7ec8..f51b3cc6a`); every
finding re-verified against `ed4a1a5ce` (two later commits by the user: the editor preview's cover button, and the
What's new text and baseline profile).

**Angle:** the work no sweep had looked at: the metronome (engine, the four audio outputs, Android service, iOS Now
Playing, web worker and media session, the tab and the song panel), feature toggles, the synced per-song overrides
(`preferences.json` in the cloud folder), capo handling and the Song defaults sheet. Medium budget: five Opus area
reviewers, no live run, one writer per lane verifying (probe tests in `:chordpro` and `:data:repository:implementation`,
deleted afterwards), then a challenge pass.

**Working tree during the review:** the user had uncommitted edits in `.claude/skills/prepare-release/SKILL.md`,
`documentation/TO_DO.md` and both `strings.xml` files. No plan relies on them; execution needs them committed or set
aside first.

## Headlines

1. **10 — a missing or unreadable `preferences.json` wipes every override on every device.** A hand edit with a
   trailing comma, a non-object, or the file deleted from the cloud folder merges as "the cloud removed everything":
   every transposition, tempo and capo is removed here and `{}` is uploaded for the other devices to follow. Proven.
2. **01 — the last `{tempo}` / `{time}` counts, not the first.** A song with a 3/4 bridge clicks in 3/4 at the
   bridge's tempo, and saving the Song defaults sheet deletes the header's `{time}` and rewrites the bridge. Proven.
3. **30 — a song opened over the Metronome tab ("Open with", the import snackbar's Open) takes over the tab's click**
   instead of stopping it, and Back brings the song's click back onto the tab.
4. **13 / 11 — "Keep them and upload" after another device's library deletion loses every override**, and resetting a
   song's only override is undone by another device's change to a different field of that song. Proven.
5. **33 — Save stays disabled in Song defaults, with nothing said, when the file holds `{tempo: 320}` or
   `{capo: 14}`**, even when that field is hidden by a feature switch.
6. **20 — the Android metronome service can stop before it went into the foreground** (play/stop double tap), which
   Android answers with a crash.

## Index

| #  | Fix | Severity | Lane |
|----|-----|----------|------|
| 01 | Make the first `{tempo}` and `{time}` count, and keep later ones on save | medium | A |
| 02 | Keep a cleared key, tempo or time from promoting the first change in the body | low (decision) | A |
| 03 | Stop marking key values the parser keeps as invalid | low (decision) | A |
| 10 | Read a missing or unreadable preferences document as unchanged (and leave a newer version's alone) | **high** | B |
| 11 | Merge a reset song field by field against a remote change | medium | B |
| 12 | Keep values this version cannot read when writing the local document | low | B |
| 13 | Forget the synced preferences of the songs Keep and upload keeps | medium | B |
| 14 | Let the preferences step swallow only the one change it wrote | low | B |
| 15 | Match preferences entries to songs by folded name | low | B |
| 20 | Stop the metronome service only after it went into the foreground | low (crash) | C |
| 21 | Resume the web audio context on every start | low | C |
| 22 | Ignore held key repeats in the metronome shortcut | low | C |
| 23 | Keep silent player time out of the iOS heard position | low | C |
| 24 | Listen for audio gestures on the web only where a click can start | low (decision) | C |
| 25 | Clear the web media session when the click stops in a hidden tab | low | C |
| 30 | Stop the click when a different screen takes the top of the back stack | medium | D |
| 31 | Keep a playing click steady while its song file is renamed | low | D |
| 32 | Drop a setlist tempo or capo step whose entry is gone | low | D |
| 33 | Validate only the Song defaults fields that are shown and changed | medium | D |
| 34 | Name no transposition override for a song without chords | low | D |
| 35 | Read the played tempo, time and capo in the read-only line | low | D |
| 36 | Document the editor preview's playing line as exempt from the Chords and Metronome switches (decided B) | low (docs) | D |
| 37 | Remember the setlist row's rendered key | low | D |
| 38 | Bring the metronome, feature toggle and sync docs up to date | low (decision inside) | D |

## Lanes

| Lane | Area | Plans, in execution order | Files owned |
|------|------|---------------------------|-------------|
| A | `:chordpro` | 01, 02, 03 | `chordpro/**` |
| B | synced overrides | 10, 11, 12, 13, 14, 15 | `data/repository/implementation/**` |
| C | metronome engine, platform shells | 20, 21, 22, 23, 24, 25 | `metronome/**`, `app/android/**`, `app/ios/**`, `presentation/src/wasmJsMain/**`, `presentation/src/desktopMain/**` |
| D | presentation (common), docs | 30, 31, 32, 33, 34, 35, 36, 37, 38 | `presentation/src/commonMain/**`, `presentation/src/commonTest/**`, `data/model/**` (KDoc and CLAUDE.md only) |

**Merge order: A, B, C, D.** A first, since 35 renders the values A's parser change decides and 38 leaves A's sentence
in `data/model/CLAUDE.md` alone; B and C are self-contained; D last, since it touches the most shared documentation
(`presentation/CLAUDE.md`, root `CLAUDE.md`, `data/model/CLAUDE.md`) and its docs plan 38 rewrites text the others
may have moved.

**Shared files:**
- Root `CLAUDE.md`: A edits only the Metronome bullet's "The first `{tempo}` and `{time}` count" sentence (and
  `{capo}`'s rule); B only the Sync bullet "The library's per-song overrides travel too"; D only the Web address list
  (38) and, optionally, the Metronome bullet's "a click never outlives the screen" sentence (30). Merge word by word.
- `data/model/CLAUDE.md`: A edits the `{tempo}`/`{time}`/`{capo}` clause; D (38) the `tempos`/`capos` sync clause.
- `presentation/CLAUDE.md`: C (22, 24, 25) and D (31, 33, 35, 36, 38) each add or reword clauses; keep every
  sentence from both sides.
- `metronome/api` gains `Metronome.setStartable` (24); `MetronomeImpl` is the only implementation, no fake to update.
- No plan adds strings; if one does at execution, add it to both `strings.xml` files.

## Decisions (answered by the user on 2026-10-05)

1. **02** — should clearing the key, tempo or time in Song defaults stop a change later in the song from becoming the
   song's value? **Answered: A.** (Default was A) — the parser tells header from body (the body-start rule `{transpose}` already uses);
   a cleared header line stays as an empty directive while a body line of that field remains. B: document it, drop 02.
2. **03** — what does the editor's "invalid" mark mean on `{key}`? **Answered: A.** (Default was A) — only what the parser drops, so
   `{key}` is never marked (`a-moll`, `Esz-dúr`, `D dorian` are kept and shown). B: "a key the transposer cannot
   move", keep the mark and teach the transposer lowercase spelled-out keys (larger change).
3. **24** — arm the web build's audio gesture listener only while a click can be started (Metronome tab or song
   details, feature on)? **Answered: yes.** No: document that every press opens the audio device for 3 s.
4. **36** — should the Chords and Metronome switches hide the key/capo and tempo/time in the editor preview's playing
   line? **Answered: B — document the preview as exempt; plan 36 becomes docs-only (no code change).** (Default was A)
5. **38 point 7** — an override equal to the file's new value is only hidden, stays stored and synced, and returns
   when the file changes. **Answered: fix the KDoc** (clearing would write setlist files as a side effect of editing a
   song). Alternative: clear matching overrides in `setSongPlaying`.
6. **01** — `{capo}` stays last-wins (a song-wide setting; a second one is a duplicate), documented. Alternative:
   first-wins like the others (two lines). **Default taken** (not raised separately).

## Checked and found solid

- Engine: sample-count timing (Long counters, no drift), boundary-only timing changes, single-thread engine actions,
  session ids discarding stale callbacks, Android focus/noisy/track release, iOS observers and session teardown,
  desktop fallback to the silent output, web worker timer cached offline, `/metronome` in all route lists.
- Android service: `mediaPlayback` type and permission, MediaStyle exempt from `POST_NOTIFICATIONS`, only started from
  the foreground, `onTaskRemoved` stops the click.
- Click lifecycle: back, tab selection, editor, export, deletion all stop it; pager retarget by `targetPage`; slider and
  stepper writes debounced; beats collected in effects, flash drawn in layers; tap tempo median and reset.
- Feature toggles: every entry point to setlists, the metronome, covers and chord rows gated; web addresses of disabled
  features cut and rewritten, no dead history entries; Settings tabs by enum; inverse `isLyricsOnlyModeEnabled`
  round-trips.
- Synced overrides: both-removed, change-beats-removal, this-device-wins, first sync adds only, unknown keys pass
  through, base saved only on success, conflict re-merge with `mode=update`, runs only after a completed run, renames
  move tempos/capos, library deletion empties the document, the engine never sees `preferences.json`.
- Song details: sounding key (transposition + capo, German, minors, wrap), Song defaults writes byte-identical apart
  from the changed lines, Reset only covers switched-on features, BalancedRows measurement, highlighter on links,
  durations, selectors, grids and tabs.

## Dropped after verification

- **Starting a click writes the preferences** (UI 3): `BaseLocalDataRepository.transformAndWriteData` skips unchanged
  data.
- **"Capo 0" on a lyrics-only song in read-only mode**: by design, the capo stepper is shown for every song while
  Chords is on, so the line matches the editable page.
- **Predictive back on a dirty editor leaves the scrim stuck**: Navigation 3 1.1.1 re-enters the scene as cancelled;
  the leftover flag only matters mid-transition. Manual check kept below.
- **`topLevelDestinations` two hops behind**: both hops resume in one main-thread task on `Main.immediate`, and the
  launch screen covers the chrome.
- **Restored back stack not filtered by feature switches**: switches are not synced or restored, and reaching
  Settings clears the stack, so no saved stack can hold a disabled screen.

## Challenge

Ran on all 24 plans (three fresh agents: lane B; lanes A and D; lane C). **Amended 8, dropped none:**
- **10** — the newer-format path returns `previous ?: {}` so a first run with an account does not report a failure on
  every run; non-convergence while the folder holds a newer format recorded as an accepted trade-off.
- **14** — the "written by sync" marker is set only when the write changed the synced values and kept no concurrent
  local change (a StateFlow can conflate the user's change into the run's write).
- **15** — all three inputs put on one spelling per song before the merge, and `applyTo` compares against the raw
  snapshot, so a device the bug already hit gets its leftover key cleaned up.
- **20** — instead of gating on `isInForeground` (which could leave the service foreground with no click after a
  stop/start/stop), the playback collector is launched in `onStartCommand` right after `startForeground`.
- **23** — buffers are scheduled before `play()` on the engine coroutine (moving `play()` to the feed thread could
  race `stop()` and throw); the silent-frame count is per session.
- **31** — pause the collector for the click's own song during a rename (the use case moves the overrides first, and
  `_songsBeingRenamed` clears before the screen sees the new name), with an old→new record and a last-applied compare.
- **32** — a reset (null) of a gone entry still counts as written, so no spurious "could not be saved".
- **38** — also names the About sheet's Song defaults group as a way into the sheet.
Note for decision 2: `{key: Dm (capo 2)}` = INVALID was asserted deliberately in 86c833418 the same day.

## Manual checks owed (after execution)

- 01/02 a song with `{time: 4/4}{tempo: 90}` and a 3/4 bridge clicks 4/4 at 90; Song defaults time change keeps the bridge.
- 10 hand-break `preferences.json` in the cloud folder (trailing comma), and delete it: overrides survive, file repaired.
- 13 device A deletes the library; device B answers Keep them and upload: B's overrides survive and reach the cloud.
- 20 Android: rapid play/stop on a song panel, and play with a call coming in: no crash.
- 21 Firefox/Safari: tap a sound chip, press Play within 1.5 s: the click sounds.
- 22 hold Space on the tab / M on a song, desktop and web: one toggle.
- 23 iOS device: flash and haptics line up with the click by ear, including after a long locked-screen run.
- 24 web: with the Metronome off, tapping around opens no AudioContext; the click still starts on the tab and the panel.
- 25 web: start a click, hide the tab, pause from the OS media controls: the session clears.
- 30 desktop: drop a `.cho` onto the window while the Metronome tab plays: the click stops; Back to the tab is silent.
- 31 Update file name while a 6/8 song's click plays: no tempo or accent jump, no bar restart.
- 33 a song with `{tempo: 320}`, Metronome off: Song defaults saves a key change.
- Predictive back on an editor with unsaved text (Android): springs back with the dialog, no stuck scrim.
