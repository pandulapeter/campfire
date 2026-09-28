# Journal the OPFS worker's in-place writes so a killed tab cannot leave a song half written

**Kind:** robustness  ·  **Severity:** low  ·  **Platforms:** web (WebKit: Safari before 26, every browser on iOS 18)
**Files:** app/web/src/wasmJsMain/resources/opfs-writer.js, data/source/local/implementation/src/wasmJsMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.wasmJs.kt, data/source/local/implementation/CLAUDE.md, app/web/CLAUDE.md

## Problem
`OpfsFileStorage.writeFile` (`FileStorage.wasmJs.kt:349-388`) writes through `createWritable()` where the browser
has it, which lands in a swap file and is atomic. Where it does not (WebKit before Safari 26, and so every browser on
iOS 18, since they all use WebKit), the write goes to the `opfs-writer.js` worker, whose `createSyncAccessHandle()`
writes **in place** (`opfs-writer.js:24-42`): it grows the file, writes the new bytes over the old ones and truncates.
The worker keeps the old content in memory and puts it back if the write throws (`restore`, lines 83-93), which covers
a quota refusal. It cannot cover the worker being terminated: iOS Safari reclaims background tabs under memory
pressure and reloads them, a tab closed or navigated away during a save ends the worker, and a browser crash does the
same. A kill between the first `write` and the `flush` leaves the song or setlist as a splice of new and old bytes,
which the next scan reads as a real song with no sign anything is wrong (a setlist that no longer parses is
skipped, and so simply gone from the list).

The window is milliseconds per save, so this is rare, and the native iOS app is the recommended way to use Campfire
on an iPhone; it is filed because the library is the only copy of the user's work and the JVM and iOS actuals were
given atomic writes for the same reason.

## Fix
A three-step journal in the worker, using only what WebKit's OPFS offers (no `move`, no rename):
1. Write the new content to a fresh sibling `<name>.campfire-tmp` with a sync access handle, flush and close. A fresh
   file has nothing to preserve, so a kill here leaves the real file untouched.
2. Create an empty `<name>.campfire-commit` marker (creating a file is atomic). From here on the temp file is the
   truth.
3. Write in place as now, flush, then remove the marker, then the temp file.

Recovery at read time, in `OpfsFileStorage` (the scan and the single-file read): a `.campfire-commit` marker next to
a file means its `.campfire-tmp` is complete and newer, so copy the temp file over the real one (through the same
worker, so the copy itself is journaled the same way), then remove marker and temp. A temp file without a marker is
an abandoned step 1 and is deleted. Both suffixes are outside `LibraryFileKind`, so the scan, the export and the
sync engine never see them as songs; make sure the directory listing that feeds the scan skips them explicitly rather
than by luck.

Keep the existing `restore` path for an in-worker failure. Document the journal in both `CLAUDE.md` files; the
worker's address is content-hashed, so a protocol change ships with the page that uses it.

## Verification
No test reaches the worker. Manual, per the web recipe: in Safari with `createWritable` unavailable (Safari 18), save
a large song and reload the tab mid-save with the CPU throttled; the song must come back either whole-old or
whole-new. Also confirm a `.campfire-tmp` without a marker left in the folder disappears on the next load and never
shows in the list.

## Conflicts
`FileStorage.wasmJs.kt` and the worker; nothing else in this batch touches the web storage.
