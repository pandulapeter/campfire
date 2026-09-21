# 25 · Android: moving to a new phone, or restoring one, brings Campfire back with an empty library

**Severity:** data loss (Android; certain for every user without sync who changes or resets their phone) ·
**Area:** `:app:android` (`AndroidManifest.xml`, `res/xml`), `:data:source:local:implementation`
(`AndroidFileStorage` KDoc), docs · **Decision:** `library/` and `preferences/preferences.json` travel in BOTH the
cloud backup and the device-to-device transfer; `preferences/sync-credentials.bin` and `preferences/sync-index.json`
stay on the device they were written on.

## Symptom

1. A user who has not connected sync (the default) writes or imports 200 songs on their phone.
2. They buy a new phone and move over with Google's cable / Wi-Fi transfer, or they factory-reset and restore from
   their Google backup.
3. Campfire is installed again by the restore, opens as if it had never been used: the two demo songs, the demo
   setlist, default settings. Nothing says that anything was left behind, and by then the old phone is often wiped.

The KDoc of `AndroidFileStorage` says the directory "is backed up with the app", and the root `CLAUDE.md` says "the
library is the only copy of the user's own work" — so the code's own documentation expects the opposite of what
happens.

## Cause

Verified at `29820b93`.

`app/android/src/main/AndroidManifest.xml:30-35`:

```xml
<application
    android:name=".CampfireAndroidApplication"
    android:allowBackup="false"
    android:dataExtractionRules="@xml/data_extraction_rules"
    android:enableOnBackInvokedCallback="true"
    android:fullBackupContent="true"
```

`app/android/src/main/res/xml/data_extraction_rules.xml` (whole content):

```xml
<data-extraction-rules>
    <cloud-backup>
        <include
            domain="sharedpref"
            path="." />
    </cloud-backup>
    <device-transfer>
        <include
            domain="sharedpref"
            path="." />
    </device-transfer>
</data-extraction-rules>
```

Three things add up:

- `allowBackup="false"` switches Auto Backup off altogether on Android 9–11 (the app's `minSdk` is 28), cloud and
  transfer alike, and switches the cloud half off on Android 12+.
- On Android 12+ (the app targets 37) the `<device-transfer>` section is honoured whatever `allowBackup` says, so a
  transfer does run — but an `<include>` turns a section into an allow-list, and the only thing on the list is the
  `sharedpref` domain. Campfire has no SharedPreferences at all (`grep -rn getSharedPreferences` finds nothing), so the
  list names nothing.
- Everything the app stores is in the `file` domain. `AndroidFileStorage`
  (`data/source/local/implementation/src/androidMain/…/storage/file/FileStorage.android.kt:16-22`) roots
  `JvmFileStorage` at `context.applicationContext.filesDir`, and `StorageDirectory.pathSegments`
  (`…/commonMain/…/storage/file/FileStorage.kt:75-79`) puts the files under it as

  ```
  files/library/songs/*.cho
  files/library/setlists/*.setlist.json
  files/preferences/preferences.json
  files/preferences/sync-credentials.bin     AndroidSecretStore: IV + ciphertext, the key never leaves the Keystore
  files/preferences/sync-index.json
  ```

  The only other thing the app writes is `cache/shared/` (`FilePicker.android.kt:112`), which Auto Backup never
  touches.

`fullBackupContent="true"` is not a rules file either: the attribute wants an `@xml/` resource, and the literal is
simply read as "no rules". The rules file predates the file-based library (`git log` on it: the relicensing, a
dependency update, and "Separate API and Implementation modules"), when the app's data was Room plus
SharedPreferences.

## Fix

No Kotlin changes behaviour here; it is two XML files, one manifest attribute pair and the documentation.

### 1. `app/android/src/main/res/xml/data_extraction_rules.xml` — Android 12 and later

Replace the body (keep the MPL header as it is):

```xml
<!--
  What leaves the device with a backup or a transfer to a new phone, on Android 12 and later; full_backup_content.xml
  says the same for the versions before it, and the two have to be changed together.

  Each section is an allow-list - one <include> is enough to make it one - so whatever is not named here stays
  behind, and that is how the two files that must not travel are kept out. sync-credentials.bin is encrypted with a
  key that never leaves this device's Keystore, so a copy of it is noise anywhere else. sync-index.json records what
  the last sync run saw from this device, and a copy that is days old, arriving on a device that has no account
  connected, is a statement about a folder nobody can check: without it the first run on the new device compares the
  two sides by content, which costs a listing and duplicates nothing. There are no <exclude> rules for them because
  lint (FullBackupContent, fatal, so it fails a release build) rejects an exclude that is not inside an included path.

  The paths are relative to Context.getFilesDir(), which is the root AndroidFileStorage hands to JvmFileStorage, and
  have to follow StorageDirectory.pathSegments and UserPreferencesLocalSourceImpl.FILE_NAME if those ever move.
-->
<data-extraction-rules>
    <cloud-backup>
        <include
            domain="file"
            path="library/" />
        <include
            domain="file"
            path="preferences/preferences.json" />
    </cloud-backup>
    <device-transfer>
        <include
            domain="file"
            path="library/" />
        <include
            domain="file"
            path="preferences/preferences.json" />
    </device-transfer>
</data-extraction-rules>
```

Notes for whoever is tempted to vary it:

- `domain="file"` is `getFilesDir()`. Not `root` (that is the parent of `files/`, and would make the path
  `files/library/`), not `external`.
- A directory path covers everything under it recursively, so `library/` is both `songs/` and `setlists/`. The
  framework canonicalizes the path, so the trailing slash is only there to make it read as a directory. `..` and `//`
  are rejected by the parser.
- Do **not** set `disableIfNoEncryptionCapabilities="true"` on `<cloud-backup>`. It would switch the cloud backup off
  on a phone with no lock screen, and the decision is that the library travels; lyrics and chords are not a secret,
  and the one secret the app has is not on the list.
- Do **not** add a `<cross-platform-transfer>` section, a `BackupAgent`, `android:backupInForeground`,
  `android:fullBackupOnly` or `android:restoreAnyVersion`. The defaults are right: the system stops the app before it
  reads the files and skips an app that is in the foreground, and a backup written by a newer version of the app is
  not restored into an older one.
- The JVM storage's temporary files (`*.tmp`) live inside `library/songs` and would travel if one happened to be
  there. They are never listed and never synced, and plan 29 sweeps them, so this needs nothing.

### 2. New file `app/android/src/main/res/xml/full_backup_content.xml` — Android 9 to 11

These versions know nothing of `dataExtractionRules`; one set of rules covers both the cloud backup and the transfer
(no `requireFlags`, so neither is singled out).

```xml
<?xml version="1.0" encoding="utf-8"?>
<!--
 This file is part of Campfire.
 Copyright (c) Pandula Péter 2017-2026.
 https://github.com/pandulapeter/campfire

 This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one at
 https://mozilla.org/MPL/2.0/.
-->
<!--
  The backup rules of Android 11 and earlier, where one list answers for the cloud backup and for a transfer to a new
  phone alike. It has to say exactly what data_extraction_rules.xml says, which is also where the reasons are.
-->
<full-backup-content>
    <include
        domain="file"
        path="library/" />
    <include
        domain="file"
        path="preferences/preferences.json" />
</full-backup-content>
```

### 3. `app/android/src/main/AndroidManifest.xml`

Change the two attributes, keep their alphabetical place, and put the reason in front of the element (a comment
cannot sit between attributes). `tools:ignore="UnusedAttribute"` and `tools:targetApi="s"` stay — they are there for
`dataExtractionRules`, which does not exist on API 28–30.

```xml
    <!--
      The library is the only copy of the user's own work, so it is in the device's backup and in a transfer to a
      new phone, together with the settings: see the two rules files, one per generation of Android. That copy is
      made by the system, to wherever the user's device backs up to - the app's own process still makes no request
      for it. What connects the app to a cloud folder is deliberately not on the list.
    -->
    <application
        android:name=".CampfireAndroidApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:enableOnBackInvokedCallback="true"
        android:fullBackupContent="@xml/full_backup_content"
```

The debug build type inherits all of this: it is the same manifest with `applicationIdSuffix = ".debug"`, which makes
`com.pandulapeter.campfire.debug` a separate package with a separate backup set under its own signature. Nothing
crosses between the two, which is what one wants, and it is what makes the flow testable without a Play build.

### 4. `data/source/local/implementation/src/androidMain/…/storage/file/FileStorage.android.kt` — the KDoc

The sentence is about to become true, but it should say which part and point at the rules, since the two are in
different modules and only hold together by path:

```kotlin
/**
 * The app-private `files` directory, which is removed when the app is uninstalled. The system's backup and its
 * transfer to a new device carry `library/` and `preferences/preferences.json` out of it and nothing else, by the
 * rules in `:app:android`'s `res/xml` - which name those paths literally, so they follow [StorageDirectory] by hand.
 */
```

### 5. What the restored app does on its first launch (nothing to change — this is the check that it is safe)

The restore runs after the APK is installed and before the app can be launched, in a restricted mode that
instantiates the base `Application`, so `CampfireAndroidApplication` and Koin are not involved and nothing of the app
runs against half-restored files. Then, on the first launch:

- `IsFirstRunUseCaseImpl` is `!hasStoredUserPreferences()`, which is `exists(PREFERENCES, "preferences.json")` — the
  file came with the backup, so `CampfireViewModel.plantDemoLibraryOnFirstRun` (`CampfireViewModel.kt:1111`) does
  nothing: no demo songs on top of a restored library, and none come back that the user had deleted. Were the
  preferences ever missing from a backup, the second condition (an empty library) still keeps the demo out.
- `preferences.json` carries the transpositions keyed by song file name; the file names are restored unchanged, so
  they still match.
- `AndroidSecretStore.load` finds no `sync-credentials.bin` → `null` → Settings shows sync as not connected. (A
  restored copy would have ended there too — `load` deletes what it cannot decrypt — but only after a logged failure.)
- No `sync-index.json`: once the user connects the same Dropbox folder again, `SyncPlanner.operationFor` sees
  `indexEntry == null` for every key. A file on both sides is a `Resolve`, which `SyncEngine` turns into a plain
  record when the content hashes match (confirmed by the sync review), so an unchanged library transfers nothing and
  duplicates nothing; one side only is an `Upload` or a `Download`. The accepted cost: a song deleted on another
  device between the backup and the restore comes back, because without the index "missing over there" reads as "new
  here". That is the same "an edit beats a deletion" direction the app errs in everywhere else, and no deletion can
  be planned at all, so the wipe guard cannot trip.

### 6. The 25 MB quota (nothing to change; goes into the docs)

Auto Backup allows 25 MB per app. Above it the system calls `BackupAgent.onQuotaExceeded` and uploads **nothing**
for that app — it is all or nothing, not a truncation — and it keeps checking on later runs. A library of plain text
is far below that (3000 songs at 3 KB are about 9 MB), and the device-to-device transfer has no documented quota.
Do not add a `BackupAgent` to find out: the export in Settings is the answer for a library that large, and sync is
the better one.

## Tests

None — XML configuration in `:app:android`, which has no tests. The `FullBackupContent` lint check validates both
files as part of `lintVitalRelease`, which `assembleRelease` runs.

## Verify

Compile: `./gradlew :app:android:assembleDebug :app:android:lintDebug` (lint must report neither `FullBackupContent`
nor `DataExtractionRules`), and once `./gradlew :app:android:assembleRelease` for the fatal-only lint of a release.

By hand, on an emulator or device with Google Play services, with the debug build
(`P=com.pandulapeter.campfire.debug`, `APK=app/android/build/outputs/apk/debug/android-debug.apk` — check the
name). Run it once on an API 30 image and once on API 34+, since the two read different files.

Cloud backup:

1. Install, open, delete one demo song, create a song of your own, change the theme color and transpose a song.
   If a Dropbox key is configured, connect sync too and let a run finish.
2. `adb shell bmgr enable true`
3. `adb shell bmgr transport com.android.localtransport/.LocalTransport`
4. `adb shell settings put secure backup_local_transport_parameters 'is_encrypted=true'`
5. `adb shell bmgr backupnow $P` → `Package com.pandulapeter.campfire.debug with result: Success`.
6. `adb uninstall $P && adb install $APK` — the restore runs as part of the installation. (Or, with the app still
   installed and its data cleared: `adb shell bmgr list sets`, then `adb shell bmgr restore <token> $P`.)
7. Open the app: your song is there, the deleted demo song is **not** back, the theme color and the transposition are
   as you left them, and Settings shows sync as **not connected**.
8. `adb shell run-as $P ls -R files` → `library/songs`, `library/setlists`, `preferences/preferences.json`, and
   neither `sync-credentials.bin` nor `sync-index.json`.
9. Connect the same Dropbox account and sync: no ` (2)` copies appear and nothing is uploaded or downloaded for the
   files that were already in step.
10. Put the transport back: `adb shell bmgr transport com.google.android.gms/.backup.BackupTransportService`.

Device-to-device transfer (Android 12+ only):

1. `adb shell settings put secure backup_enable_d2d_test_mode 1`
2. `adb shell bmgr transport com.google.android.gms/.backup.migrate.service.D2dTransport`
3. `adb shell bmgr init com.google.android.gms/.backup.migrate.service.D2dTransport`
4. `adb shell bmgr backupnow $P`, then `adb uninstall $P && adb install $APK`, open the app and repeat checks 7–8.
5. `adb shell bmgr transport com.google.android.gms/.backup.BackupTransportService` and
   `adb shell settings put secure backup_enable_d2d_test_mode 0`.

`adb logcat -s BackupManagerService FullBackup` shows which files were visited if something is missing; a path that
the parser rejected is logged there as well.

## Docs

- **Root `CLAUDE.md`, first paragraph.** "**The only thing that ever reaches the network is sync**" stays true of the
  app's own process, but it needs the same kind of parenthesis the Play update check has. After the sentence about
  Play, inside the same parentheses or as one more sentence:
  "On Android and iOS the system's own device backup also carries the library and the settings — to the user's Google
  or iCloud backup, or straight to their next phone — but that is the operating system copying the app's files on the
  user's backup settings; Campfire's process makes no request for it, and the sync credentials and the sync index are
  not part of it (`app/android/src/main/res/xml`)."
- **Root `CLAUDE.md`, the library layout block.** Under the listing add: "On Android `library/` and
  `preferences/preferences.json` are in the system backup and the device-to-device transfer;
  `sync-credentials.bin` and `sync-index.json` are not, so a restored installation starts disconnected and its first
  sync run compares by content."
- **`app/android/CLAUDE.md`.** One paragraph after the `FileProvider` one: the two rules files (`data_extraction_rules.xml`
  for API 31+, `full_backup_content.xml` for 28–30), that they must say the same thing, that they are allow-lists and
  why there are no `<exclude>`s (lint), the 25 MB all-or-nothing quota, and that the `.debug` build is a package of its
  own with its own backup set.
- **`data/source/local/implementation/CLAUDE.md`**, the `storage/file` bullet listing the directories: add that the
  Android backup rules in `:app:android` name `library/` and `preferences/preferences.json` by path, so moving a
  directory or renaming the preferences file means changing those two XML files as well.
- **`README.md`, "Your songs are yours".** "Nothing is uploaded" is no longer exact on a phone with backups on (and
  never was on iOS, where Documents and Application Support are in the iCloud backup by default). Suggested wording:
  "The library is a folder of ordinary text files in Campfire's own storage on your device. Campfire uploads nothing
  and analyzes nothing, and it collects nothing at all — … On a phone, the library and your settings are part of the
  device's own backup (Google's or iCloud's, whichever you have switched on), so they follow you to a new phone; that
  copy is made by the system and is yours, and Campfire never sees it."
- **`documentation/sync.md`, "Only the library is synced. Your settings … stay on the device they were made on."**
  Still true of sync; add "(a phone's own backup does carry them to its replacement)" so the two documents do not
  seem to disagree.
- **Outside this repository (flag to the user, do not attempt):** the privacy policy at
  `pandulapeter.com/legal/privacy_policy-campfire.html` should gain the same sentence as the README, and the Play
  Console's Data safety form should be re-read — data that only the OS backup copies is not "collected" by the
  developer, but the form asks the question and the answer should be given knowingly.

## Touches

- `app/android/src/main/AndroidManifest.xml`
- `app/android/src/main/res/xml/data_extraction_rules.xml`
- `app/android/src/main/res/xml/full_backup_content.xml` (new)
- `data/source/local/implementation/src/androidMain/kotlin/com/pandulapeter/campfire/data/source/local/implementation/storage/file/FileStorage.android.kt`
- `CLAUDE.md`
- `app/android/CLAUDE.md`
- `data/source/local/implementation/CLAUDE.md`
- `README.md`
- `documentation/sync.md`

## Depends on

nothing. It shares `AndroidManifest.xml` with plans 11 and 52 (both change a comment in it only), and the root
`CLAUDE.md` with many; schedule them one after another rather than side by side. Plan 20 (the index keyed by a
stable account id) and plan 29 (leftover `.tmp` files) are neighbours in behaviour but not in code.
