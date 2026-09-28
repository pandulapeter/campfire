# Final-patch audit continuation — 2026-09-28 (`b8cc0bc2`)

Seven additional issue plans follow the original six below. This continuation is a code audit with focused JVM
reproductions, not a repeat of the earlier live desktop stress run. No application fixes have been applied.
Each issue has its own Problem / Evidence / Fix / Verification / Conflicts plan.

## Additional findings

| # | Priority | Finding | Evidence |
|---|---|---|---|
| [07](07-preference-edits-overwrite-newer-preferences.md) | P2 | Rapid preference edits can overwrite one another | Real repository + matching state-flow projections reproduce a lost field |
| [08](08-song-invalidations-can-be-dropped.md) | P2 | A busy invalidation collector can leave open lyrics stale | Blocked collector loses both a named event and a full-refresh event |
| [09](09-song-text-cache-grows-for-the-whole-session.md) | P2 | Browsing retains every visited song text for the session | 1,000 visited entries all remain cached; no eviction exists |
| [10](10-library-export-silently-omits-unscanned-setlists.md) | P1 | Library backup silently omits setlists absent from the parsed cache | Readable unscanned document omitted with an empty skipped list |
| [11](11-export-can-use-stale-cached-song-text.md) | P2 | Export can contain an older song than the file on disk | Freshness probe returns cached old text with `shouldCache = false` |
| [12](12-first-lyrics-preparation-blocks-navigation.md) | P2 | First lyrics preparation runs synchronously during composition | Actual parse/preparation timing: 42.8 ms for 3,000 lines before layout |
| [13](13-library-export-has-unbounded-peak-memory.md) | P1 | Export holds whole-library inputs plus full archive buffers | Allocation-path review; small-heap device stress test still owed |

## Patch order and launch recommendation

Resolve the existing high-severity plans **01 / 06** and the new **10 / 13** before the broad campaign. Backup
completeness and avoiding a memory failure while making that backup deserve the same attention as sync and song
rendering. Include **11** with the export work so the backup is current as well as complete.

The rest are concrete final-patch fixes, not speculative refactoring. Group **08 / 09 / 11** around the song-content
repository; they share a file but solve different problems (delivery, retention, freshness). Group **06 / 12**
around rendering, covering both details and editor preview. **07** needs a repository-level preference transform
before migrating the UI and reference-maintenance callers. **10 / 13** share the exporter and its file listings.
Keep the existing plans 02–05 in the queue; this review did not implement them or independently revalidate every
platform-specific claim they contain.

## Validation and limits of this continuation

- Inspected preference persistence, song-content caches/invalidations, setlist reads, export and archive creation,
  viewer/editor preparation, list/search work, sync planning and guards, cover caching/downloads, and platform
  import boundaries. This was a targeted code audit, not exhaustive certification of every platform.
- Five new behavior probes reproduced the reported preference, invalidation, retention, setlist-export, and stale
  export-read behaviors. A sixth probe measured lyrics preparation. They assert the current unfavorable behavior
  deliberately; their passing results do **not** mean those issues are fixed. Temporary probes were removed after
  validation, leaving the implementation and existing tests unchanged.
- The documented seven-module desktop suite completed successfully in 27 seconds: reports contain **932 tests,
  zero failures/errors/skips**. Repository, domain, and presentation tests ran again; chordpro, local source,
  remote API, and remote implementation tests were up to date. Mobile UI, browser responsiveness, and release-device
  memory/latency still need the manual checks in the individual plans; preparation timings are local JVM
  measurements, not a measured phone frame budget.
- Historical notes below describe the earlier review. In particular, the current `ImportLimits.MAX_IMPORT_SIZE`
  is **24 MiB**, not 200 MB; export already warns when its completed archive exceeds that re-import limit.
  The new export-memory issue concerns allocations **before** that warning. Initial lyrics preparation is
  synchronous; only subsequent preparations currently use `Dispatchers.Default`.

---

# Pre-launch review of 2026-09-28 (at `cf588adf`)

The last sweep before the marketing campaign, made while the stores review 4.5.0. The angle was what a large
number of users would meet that the seven earlier sweeps did not look for: bad networks, low-end devices, libraries
and files bigger than anybody here keeps, and the failures the app has no crash reporting to tell about. Four
read-only reviewers (network and sync; storage and data integrity; UI performance; platform shells and release
configuration) each verified their findings against the code, and every finding that survived was verified a second
time before it became a plan. A live stress run drove the desktop debug build over a generated library. The full
unit test suite passed at the start.

The result is short on purpose: the codebase is in good shape, and most of what the reviewers set out to check turned
out to be handled already (see "Checked and found solid" below). Six plans, two of them the ones to do before launch.

Each plan is self-contained (Problem / Fix / Verification / Conflicts) and lands as its own commit. Nothing has been
executed.

## Headlines

- **01 — a large library on a slow connection can never finish a sync.** Every request is capped at 60 s end to
  end, a cap that a `list_folder` page of a few thousand songs or one large song exceeds on a 2G or congested link,
  and a timeout ends the run with no retry. The user sees the generic network failure on every launch, every edit
  and every "Sync now".
- **06 — one huge `.cho` file freezes the app for seconds and, on a phone, kills it.** A 2.85 MB song froze the
  desktop UI thread for 7.5 s and took 3 to 4 GB of memory; the import and sync let files of up to 8 MiB in, and the
  details screen lays out the whole song at once. A songbook concatenated into one file is all it takes.

## Index

| # | Kind | Sev. | Platforms | Title |
|---|---|---|---|---|
| 01 | bug | high | all | The 60 s request timeout ends every sync run of a large library on a slow link |
| 02 | robustness | medium | android | A refused launcher-icon switch crashes the app on leaving |
| 03 | robustness | medium | desktop | No log file and no uncaught-exception handler |
| 04 | robustness | low | web (WebKit) | The OPFS worker fallback writes library files in place |
| 05 | bug | low | all (RTL locales) | The song details back arrow is not mirrored |
| 06 | performance | high | all | The song details lays out a huge song all at once |

## Lanes

| Lane | Plans | Area |
|---|---|---|
| **A** | 01 | `:data:source:remote:implementation` (Ktor client, Dropbox provider) |
| **B** | 06, 05 | `:presentation` song details |
| **C** | 02, 03 | `:app:android`, `:app:desktop` |
| **D** | 04 | web storage (`FileStorage.wasmJs.kt`, `opfs-writer.js`) |

The lanes are independent. Cap concurrent Gradle builds at two.

## Stress run measurements (desktop debug build, `:app:desktop:run`, 3000 generated songs, two runs)

| What | Run 1 | Run 2 |
|---|---|---|
| Process start to the first 2048 songs in the list | 2.0 s | 1.9 s |
| Memory with the list shown | 390 to 430 MB | 370 to 410 MB |
| Search keystroke to new result (state) | 1 to 15 ms | 1 to 13 ms |
| Longest frame gap while the batches land at startup (behind the launch screen) | 330 ms | 378 ms |
| Frame gap on the first search keystrokes (sections to flat rows) | 158 to 199 ms | same |
| Open the 2.85 MB / 60 000-line song: UI frozen | 7.8 s | 7.5 s |
| Memory peak / 20 s after leaving that song | 4.4 / 3.4 GB | 3.4 / 2.6 GB |
| 20 000-character line, 0-byte song, 500-song setlist | 17 to 25 ms | same |
| Import of a 2.26 MB zip of the 3000 songs into an empty library | 14.1 s, no frame over 100 ms, +90 MB | |
| 750 songs with a cover on a host that does not resolve | no log noise, no retries, placeholders | |

No exception in any run. The first-keystroke frame gap is worth a profile on a phone (the search state itself is
ready in milliseconds, so it is the grid re-composing its visible rows when the sections give way to flat rows),
but it was not turned into a plan: it is under the "not responding" threshold by an order of magnitude even on a
slow device, and a debug JVM build overstates it.

Web distribution: the two wasm binaries are 8.6 MB and 8.0 MB, served gzipped by GitHub Pages at 3.4 MB and 2.4 MB
(checked against the live site, `content-encoding: gzip`, `max-age=600`). At 1 Mbit/s that is about 46 s behind
the loading page's progress bar, at a typical 4G a few seconds. Nothing to do here short of shrinking Skia.

## Checked and found solid

- **Offline and airplane mode:** a launch sync fails cleanly as a network failure, nothing retries in a loop,
  later automatic runs are not blocked.
- **Token lifecycle:** refreshes are serialized through one mutex, a 401 is answered with one refresh, a revoked
  refresh token lands in a clear "reconnect" state.
- **Scale on Dropbox:** cursor pagination, batch deletion polled as a job, 429 / 5xx / 409 back-off with jitter,
  non-song files never listed or downloaded, oversized files skipped on both sides.
- **Upload retried after a lost response:** resolved by the content hash, no duplicate.
- **Atomic writes** on the JVM and iOS (temp file, flush, rename; disk full surfaced as a storage error) and OPFS
  where `createWritable` exists; preferences keep the last good document beside a `.bad` one.
- **Memory at scale:** songs are scanned in batches of 64 off the main thread and published incrementally; the
  models hold metadata only; a 200 MB zip is refused before it is buffered; zip bombs are charged by declared size.
- **Startup:** nothing reads the whole library before the first frame; demo planting and the credentials-forget on
  first run cannot fire on a restored backup.
- **Foreign files:** directories, symlinks, BOMs, CRLF, invalid UTF-8 and empty files in `library/` are skipped or
  read, never crash the scan.
- **UI thread discipline:** search index, sectioning, setlist assembly, tag counting and the picker's list are
  built on `Dispatchers.Default`; the per-keystroke filters are substring scans over pre-normalized strings.
- **Lists:** every `LazyColumn` row keyed and typed, no per-row `graphicsLayer` or blur, no infinite animation kept
  alive off screen; covers are decoded to the laid-out size and their downloads bounded.
- **Accessibility:** every icon button has a content description, rows grow with the font size instead of clipping.
- **Robustness in `:presentation`:** no `!!`, no unguarded `first()` / `single()`, the bracket and section loops
  provably terminate.
- **Android:** foreground service type and `POST_NOTIFICATIONS` gated by SDK, 16 KB page alignment verified on the
  release APK, backup allow-list matches the paths, `FileProvider` paths, R8 rules for serialization / Ktor / Coil,
  Baseline Profile packaged.
- **iOS:** nothing used above the deployment target, background task with an expiration handler, Keychain item
  device-bound, import budget enforced off the main thread, alternate icon names match the catalog.
- **Desktop:** the single-instance `FileLock` is released by the OS on a crash, ProGuard rules cover reflection,
  the jlink module list is complete, sandbox entitlements match the picker.
- **Web:** OPFS and Wasm GC are probed before the download, the second-tab lock cannot deadlock, `beforeunload` only
  with unsaved text, a stale cached `index.html` after a deploy is fixed by the reload "Try again" does (a reload
  revalidates the document).
- **Release configuration:** no publish workflow builds a debug variant, every one refuses an empty Dropbox key,
  the only pre-release dependency is the documented Material 3 pin.

## Dropped after verification

- A song picker filter "on the main thread": it is the same cheap substring scan the main search uses.
- A stale `index.html` looping on "Try again": a reload revalidates the document with the server.
- The wasm loader not reporting a stream that fails mid-body: the app's own read of the same response fails and
  reaches the page's `unhandledrejection` handler, which shows the failure.
- A process death between a song's deletion and the walk that removes it from setlists: the window is
  milliseconds, and the result is the "File not found" entry the setlist screen already shows and lets the user
  remove.
- The quadratic section flow on a window resize: several conditions have to stack and no stutter was measured.

## Manual checks owed

- 01 on a real slow link (Network Link Conditioner "Edge" or the emulator's GPRS profile) with about 2000 songs.
- 06 on the Android emulator with a 2 GB RAM profile and a 3 MB `.cho`.
- The first-keystroke frame gap of the songs search, profiled on a low-end phone.
