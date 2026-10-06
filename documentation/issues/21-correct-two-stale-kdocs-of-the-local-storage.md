# Correct two stale KDocs of the local storage: `JvmFileStorage`'s reason for two copies and `loadSongs`' progress cadence

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all (Android and desktop for the first)
**Files:** `data/source/local/implementation/src/desktopMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`,
`data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/JvmFileStorage.kt`,
`data/source/local/api/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/local/api/SongLocalSource.kt`,
`data/source/local/implementation/CLAUDE.md`

## Problem

1. Both copies of `JvmFileStorage` (`:37-38` at 8ee010b36) say:

   ```kotlin
    * The Android copy of this class is identical - the two platforms cannot share a source set until the `roomMain`
    * hierarchy template is gone.
   ```

   Nothing in `gradle/build-logic` or the version catalog mentions Room, a `roomMain` source set or a hierarchy
   template any more. The real reason is that the `campfire-library` convention plugin
   (`gradle/build-logic/.../plugins/LibraryPlugin.kt`, `extensions/KotlinMultiplatform.kt`) declares no source set
   shared by `androidMain` and `desktopMain`, and Kotlin's default hierarchy has no JVM-and-Android group. A reader
   fixing one copy has no true statement of why the other exists. (The two files differ only in this comment line.)

2. `SongLocalSource.loadSongs`' KDoc (`SongLocalSource.kt:21`) promises "[onProgress] is called with everything read
   so far, once per batch and never with the last one", while `SongLocalSourceImpl.loadSongs` (`:62-73`) — and
   `data/source/local/implementation/CLAUDE.md:137` — hand the list over after the first batch and then only when it
   has doubled, deliberately (per-batch hand-overs were quadratic downstream).

## Fix

1. In the desktop copy: "The Android copy of this class is identical: the `campfire-library` convention plugin
   declares no source set shared by `androidMain` and `desktopMain`, so a JVM class both need is kept twice, and a
   change to one is made to the other." The Android copy says the same with "desktop copy". Keep the two files
   identical apart from that word. (Adding a shared JVM source set to the convention plugin and keeping one copy is the alternative,
   but touches `gradle/build-logic`, lane E's — not recommended for a docs fix.) Make the `implementation/CLAUDE.md`
   line that mentions the two copies ("one copy each because they are separate source sets") say the same.
2. In `SongLocalSource.loadSongs`' KDoc: "[onProgress] is called with everything read so far after the first batch and
   then whenever the list has doubled, never with the last one (which is the returned list)".

## Tests

None (comments only).

## Manual check

None.
