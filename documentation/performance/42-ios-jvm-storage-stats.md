<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 42 — Fewer file-system calls per file in the iOS and JVM storages

| | |
|---|---|
| Lane | E |
| Impact | low-medium (scan and sync preparation on large libraries, mostly iOS) |
| Confidence | medium |
| Platforms | iOS, Android, desktop |
| Files | `data/source/local/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.ios.kt`, `data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`, `data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt` (kept identical to the desktop copy), `data/source/local/implementation/src/desktopTest/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorageTest.kt` |
| Depends on / conflicts with | — |
| Commit message | `Resolve storage directories once and list files with one attribute read each on iOS and the JVM.` |

## Problem
### iOS (`FileStorage.ios.kt`)
- Every read, write, `exists` and `info` goes through `filePath` → `directoryPath` (152-158). That calls `URLForDirectory(...)` and `createDirectoryAtPath(it, true, null, null)` every time.
- `readData` (140-150) adds a `fileExistsAtPath` before reading.
- So a scan read of one song costs a directory lookup, a directory creation attempt, a stat and the read.
- `list` (62-71) calls `attributesOfItemAtPath` for every entry, one after another on one IO thread, and builds an attribute dictionary each time. For 2000 files that is 2000 serial attribute reads before the first song is read.

### JVM (`JvmFileStorage.kt`, both copies)
- `directoryFile` (181-186) calls `mkdirs()` on every operation.
- `list` (46-56) makes three stat calls per entry: `it.isFile`, `it.length()` and `it.lastModified()`.
- `readText` (71-73) adds `isFile` before `Files.readAllBytes`.
- On Android flash each call is cheap, but they add up to several thousand system calls per scan.

## Fix
### iOS
1. Cache the root path per `StorageDirectory` in a map filled on first use. Call `createDirectoryAtPath` **only on the write paths** (`writeBytes`, and `writeText` through it).
   - A read or list of a directory that is missing already answers null or empty through the existing branches (`contentsOfDirectoryAtPath` → null → `fileExistsAtPath` false → empty).
   - That keeps the behaviour when the Files app deletes the library folder under the running app: sync's guard sees an empty library, and the next write recreates the folder.
2. `list`: enumerate with `contentsOfDirectoryAtURL(url, includingPropertiesForKeys = listOf(NSURLIsRegularFileKey, NSURLFileSizeKey, NSURLContentModificationDateKey), options = 0u, error = …)`, and read each URL's `resourceValuesForKeys` from what the enumeration fetched in advance.
   - Keep the "directory there but not listable → `LibraryStorageException`" rule.
3. `readData`: read first. Only when `dataWithContentsOfFile` returns nil, ask `fileExistsAtPath` to tell "absent" (null) from "unreadable" (throw). It already asks again on nil, so drop only the check before the read.

### JVM
1. Resolve each directory's `File` once, in a map.
   - Keep `mkdirs()` on the write paths (`writeAtomically`), so a folder deleted under the desktop app is recreated by the next save as it is now.
   - Keep the one-time leftover sweep (`sweptDirectories`).
2. `list`: `Files.newDirectoryStream(dir)`, then `Files.readAttributes(path, BasicFileAttributes::class.java)` per entry: one stat giving regular-file, size and modification time.
   - `NoSuchFileException` on the directory → empty.
   - Any other `IOException` → `LibraryStorageException`, as today.
   - Keep the temp-file filter, `toLibraryName()` and the sort.
3. `readText` / `readBytes`: drop the `isFile` check. `readingAsStorage` already maps `NoSuchFileException` to null.
   - A directory under a song's name would throw an `IOException` (not a `NoSuchFileException`), which then becomes `LibraryStorageException` rather than null. Keep the `isFile` check if that difference matters to a caller; it is cheap compared with the listing change.

### Must not change
The null-vs-throw contract of the `FileStorage` KDoc, the Windows device-name mapping and retries, and atomic writes.

## Verification
- Tests: `./gradlew :data:source:local:implementation:desktopTest`. `JvmFileStorageTest` and `LibraryListingTest` cover listing, the missing directory and unreadable-directory cases. Add a case: delete the directory, `list` answers empty, then `writeText` recreates it.
- iOS: `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64`, then run in the simulator with about 2000 songs in the documents directory. Compare cold time to a full list (Instruments, File Activity) before and after.
- Android: `./gradlew :app:android:assembleDebug`, and a Perfetto trace of a cold start with about 2000 songs to compare the scan's duration.
