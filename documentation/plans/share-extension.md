<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Share extension — implementation plan

Written 2026-10-07 against `a0c6b7789`. Share → Campfire from other apps on the iPhone and the iPad, so that a chord
sheet selected in Safari or a document in Files reaches the ordinary import in one step, and the same behavior on
Android, which already has most of it. Campfire makes no request for any of it: a shared web page is never fetched.

## 1. What is being built

1. **An iOS share extension**, `ShareExtension.appex` inside `Campfire.app`, offered in the share sheet for selected
   text and for the files the import reads: the ChordPro family, `.txt`, `.pdf`, `.docx`, `.zip` and SongbookPro's
   `.sbpbackup` / `.sbp`. It is a small Swift target that does not link the Kotlin framework. It copies what it was
   given into an inbox in an App Group container, says so, and is done.
2. **The app empties that inbox** whenever it comes to the front, and whenever a share completes while it is already
   on screen (an iPad in Split View). Files go into the ordinary import (`PrepareImportUseCase` → `ImportPlan`, the
   import report screen), each share one batch, a single song opened as an "Open with" opens it. Text goes down the
   same path as shared text on Android, the clipboard plan's "text into a new song" flow (one song opens the New
   song sheet filled in from the conversion, several go through the ordinary import).
3. **A link is not a song.** A share of only a web address does not offer Campfire where the system lets the
   extension say so, and is answered with one sentence where it does not: on iOS in the extension, on Android as a
   snackbar in place of today's "skipped" report.
4. **Android's share handling is brought level with the iOS list** (§6): the shared-text rules move into common code
   both platforms use, a trailing page address after selected text is dropped, and the MIME types are checked against
   what real file managers send.

### What is already there, and what is not

- **iOS "Open with" works today** for the ChordPro family only: `UTImportedTypeDeclarations` and
  `CFBundleDocumentTypes` in `app/ios/iosApp/iosApp/Info.plist`, `.onOpenURL` in `iOSApp.swift` →
  `IosFileImportKt.openUrl` (`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/IosFileImport.kt`), which reads the
  URL with `NSURL.readImportedFile(ImportBudget())` (`IosFilePicker.kt`) on the one-at-a-time `inboxScope` and sends it
  into `pendingImports`, which `CampfireViewController` hands to `CampfireIosApp` as `filesToImport`. Zip and plain
  text are deliberately not claimed there, and PDF and Word were never added. So a PDF in Files cannot reach Campfire
  except through the app's own import button, and selected text cannot reach it at all.
- **Android already does nearly all of this** (`CampfireMainActivity.importFrom`, `AndroidFileImport.kt`): `SEND` and
  `SEND_MULTIPLE` for `text/plain`, `application/octet-stream`, both zip types, PDF and Word; shared text becomes a
  `.txt` `ImportedFile` named after `EXTRA_SUBJECT` or its first plain line; a stream beats a caption; a text that is
  only links is passed on empty and reported as skipped. The audit is §6.
- `CampfireViewModel.importFiles(files)` is the one entry for files the system hands over, with
  `shouldOpenSong = true`, queued behind any running import by `importQueue` and behind the first run's demo planting
  by `demoLibraryDecision`. Performance mode leaves it alone ("not a tap that can happen by accident",
  `presentation/CLAUDE.md`).
- `ImportLimits` (`:data:model`) is the size rule: 8 MiB for a text file, 16 MiB for a document, 24 MiB for a
  selection or an archive, applied by `ImportBudget`.
- **Nothing in the iOS project has an entitlements file, a second target or an App Group.** The Keychain items of
  `SecretStore.ios.kt` use the default access group, which an App Group entitlement does not change.
- **The clipboard plan (`documentation/plans/clipboard-import.md`) lands first**: it moves Android's shared-text
  naming into `:presentation`'s `SharedText` and adds `newSongFromText`, which this plan routes both phones' shared
  text into (§5).

### Defaults this plan assumes — veto any of them before the work starts

1. **The extension does not open Campfire.** It says "Saved to Campfire" and that opening Campfire finishes the
   import, and the app empties the inbox the next time it is in front. The alternative is the responder-chain trick
   (walking `next` up to `UIApplication` and calling `open(_:)` on it): it is not API, Apple's extension guidelines say
   only a Today widget may open its app, iOS 18 already broke the `openURL:` variant of it, and App Review rejects it
   when it notices. A route that may stop working with any iOS release, or get a release refused, is not one to build
   the import on. The cost is one tap on the Campfire icon.
2. **No new document types.** Declaring PDF, Word, zip and plain text in `CFBundleDocumentTypes` would put Campfire in
   the sheet's row of apps and open it directly, with no extension at all — for files. It would also make Campfire an
   "Open with" choice for every archive and note on the device, which `Info.plist`'s comment rules out on purpose, and
   it does nothing for selected text, which is the case this is for. The extension is offered from the share sheet
   only, which is where the user is already choosing.
3. **The extension accepts the ChordPro family too**, so that every file takes one route. Files already lists Campfire
   for a `.cho` through its document type; if the sheet shows Campfire twice for one, the activation rule leaves the
   ChordPro types out (§2.2) and that file keeps the route it has.
4. **A web page is not fetched, and a link is answered.** The activation rule does not take `public.url`. Where an app
   shares a page with a text beside the address (a title), the extension opens and says: "Campfire does not open web
   pages. Select the chord sheet's text and share that instead." Nothing is written.
5. **Files beat text** within one share, as on Android: a text next to a file is its caption.
6. **One share is one import batch**, in the order the shares were made, each with its own `ImportBudget`.
7. **The inbox is bounded**: at most 20 shares and 64 MiB waiting. A share past either is refused in the extension
   with "Some shares are still waiting. Open Campfire to finish them first." Within a share the import's own limits
   apply, checked in the extension before anything is copied.
8. **Read only mode imports a share**, as it imports an "Open with"; shared text there takes the import path rather
   than the editor, which read only mode cannot reach.
9. **Shared text never replaces unsaved editor text**: with unsaved text in the editor, a shared song goes through the
   import and is offered with Open, as the clipboard plan's `createSongFromText` already does for this caller.
10. **The extension speaks the system's language** (`en` and `hu`, from its own string tables), not the language
    chosen inside the app, which it cannot read without the app writing its preference into the group. Worth doing
    only if somebody asks.
11. **Android keeps offering Campfire for a shared page**, since `text/plain` is the type of every shared text and no
    filter can tell an address from a song; it answers with the same sentence, as a snackbar.

## 2. iOS: the extension target

### 2.1 Files

All new, in `app/ios/iosApp/ShareExtension/`, each with the MPL header:

| File | What it is |
| --- | --- |
| `ShareViewController.swift` | The principal class (`NSExtensionPrincipalClass` = `$(PRODUCT_MODULE_NAME).ShareViewController`, no storyboard): reads `extensionContext.inputItems`, hands them to the writer, hosts the view, calls `completeRequest` on Done |
| `ShareInboxWriter.swift` | Everything that touches the disk (§2.3) |
| `ShareView.swift` | A SwiftUI view in a `UIHostingController`: the state of the share and one Done button |
| `Info.plist` | `NSExtension` (below), `CFBundleDisplayName` Campfire, `CFBundleLocalizations` `en` and `hu`, `CampfireAppGroup` = `$(APP_GROUP_ID)`, version keys at `0` like the app's |
| `ShareExtension.entitlements` | `com.apple.security.application-groups` = `$(APP_GROUP_ID)` |
| `PrivacyInfo.xcprivacy` | No tracking, no collected data, no required-reason category (see §2.3 for the APIs it avoids) |
| `en.lproj/Localizable.strings`, `hu.lproj/Localizable.strings` | Its strings (§2.4) |
| `Assets.xcassets` | `AccentColor`, the app's purple (#5A49CA) and its dark-theme tone |

The extension shows under the containing app's primary icon; nothing is added for it.

### 2.2 What it is offered for

`NSExtensionPointIdentifier` `com.apple.share-services`, and `NSExtensionActivationRule` as a predicate string, since
the dictionary form's file key takes any file at all:

```
SUBQUERY (extensionItems, $item,
  SUBQUERY ($item.attachments, $attachment,
       ANY $attachment.registeredTypeIdentifiers UTI-CONFORMS-TO "public.plain-text"
    || ANY $attachment.registeredTypeIdentifiers UTI-CONFORMS-TO "com.adobe.pdf"
    || ANY $attachment.registeredTypeIdentifiers UTI-CONFORMS-TO "org.openxmlformats.wordprocessingml.document"
    || ANY $attachment.registeredTypeIdentifiers UTI-CONFORMS-TO "public.zip-archive"
    || ANY $attachment.registeredTypeIdentifiers UTI-CONFORMS-TO "com.pandulapeter.campfire.songbookpro-backup"
  ).@count >= 1
).@count >= 1
```

- `public.plain-text` is selected text, `.txt`, and the ChordPro family, whose two types in the app's `Info.plist`
  conform to it. `public.url` conforms to none of these, so a page shared by its address alone does not list
  Campfire. Should default 3 be vetoed, the ChordPro types are taken out by an `AND NOT` clause on `org.chordpro.cho`
  and `org.chordpro.chordpro`.
- **SongbookPro's backups get an imported type** in the app's `UTImportedTypeDeclarations`:
  `com.pandulapeter.campfire.songbookpro-backup`, extensions `sbpbackup` and `sbp`, conforming to `public.zip-archive`
  and `public.data`, with no `CFBundleDocumentTypes` entry, so it names the files for the rule without claiming them.
  If SongbookPro's own app exports a type for these extensions (to be seen on a device with it installed), the rule
  names that one and the declaration is left out, since an exported type wins anyway. Keep the rule in step with
  `LibraryFiles.IMPORTABLE_EXTENSIONS`, as the document types are with `SONG_EXTENSIONS`.
- Left out on purpose: `.json` (a setlist alone means nothing without its songs, and `public.json` is every app's),
  the legacy `.doc` (the import only ever reports it as having no readable text), images.
- `NSExtensionActivationRule` cannot cap the count; the writer's bounds do.

### 2.3 Writing a share (`ShareInboxWriter`)

The extension's memory limit is about 120 MB, and a share is up to 24 MiB, so nothing is loaded that can be copied.

- **Where**: `FileManager.containerURL(forSecurityApplicationGroupIdentifier:)` with the `CampfireAppGroup` key of
  its `Info.plist`, then `ShareInbox/`. Created with `isExcludedFromBackup` set on the directory, which covers
  everything inside it — an App Group container is in the device backup otherwise. No container (a build signed
  without the capability) is the "could not save" state.
- **Each share is one folder**, written as `ShareInbox/<ISO timestamp>-<UUID>.partial/` and renamed to drop the
  `.partial` only once everything is in it, so the app never reads half a share: the rename is atomic, and a folder
  that still ends in `.partial` is one the app does not touch until it is an hour old (§3.1).
- **Inside it**: `files/<n>/<original name>` per file (a folder each, so two files with one name keep it),
  `texts/<n>.txt` per text, and `share.json`:
  `{"version": 1, "subject": "…", "files": ["files/0/Song.pdf"], "texts": ["texts/0.txt"]}` — `subject` the item's
  `attributedTitle`, absent where there is none.
- **Reading an attachment**: a provider with a registered type among the file types of §2.2, or a plain-text one whose
  `suggestedName` ends in an importable extension, is a file: `loadFileRepresentation(forTypeIdentifier:)`, copied
  with `FileManager.copyItem` inside the completion handler (the URL is gone after it), named from `suggestedName` or
  the URL's last component. Any other plain-text provider is a text: `loadItem(forTypeIdentifier: "public.plain-text")`,
  which answers a `String`, `Data` or a file `URL`, each turned into UTF-8.
- **Files beat text**: where a share holds a file, its texts are not written.
- **The bounds**, mirroring `ImportLimits` with a comment naming it: a file over its own limit by its extension (8 MiB
  for text and ChordPro, 16 MiB for PDF and Word, 24 MiB for an archive), a share over 24 MiB in all, a text over
  8 MiB, are refused as "too large" before they are copied. Sizes come from `URLResourceValues.fileSize`, not from
  `attributesOfItem`, which is a file timestamp API and would need a privacy reason.
- **A link**: a share with no file whose every text is nothing but `http(s)` addresses (the rule of
  `SharedText.isOnlyLinks`, §5, written again in twelve lines of Swift), or whose texts are one line each beside a
  `public.url` attachment (a page's title), writes nothing and shows the link sentence.
- **The inbox limits** of default 7 are counted from the folders already there before anything is written.
- **When the folder is in place**, a Darwin notification, `com.pandulapeter.campfire.share-inbox`, through
  `CFNotificationCenterGetDarwinNotifyCenter`: no payload, and the one way an extension can tell a running app that
  something is waiting.

### 2.4 What it says

The view is a sheet in the system's appearance with the accent color: the Campfire name, one line of state, one line
under it, and **Done**. It does not close on its own, since the second line is the instruction.

| State | Line | Under it |
| --- | --- | --- |
| Working | Saving to Campfire… | — |
| Saved | Saved to Campfire | Open Campfire to add it to your library. |
| A link | Campfire does not open web pages. | Select the chord sheet's text and share that instead. |
| Nothing it reads | Campfire cannot import this. | It imports songs, plain text, PDF and Word documents and zip archives. |
| Too large | This is too large to import. | Campfire imports up to 24 MB at a time, and 16 MB for one document. |
| Inbox full | Some shares are still waiting. | Open Campfire to finish them first. |
| Failed | This could not be saved for Campfire. | — |

English as given, Hungarian in `hu.lproj` in the voice of `values-hu/strings.xml`. These are the extension's own
string tables, not Compose resources: the extension has no Kotlin to read those with.

### 2.5 The target in `iosApp.xcodeproj`

- A `com.apple.product-type.app-extension` target **ShareExtension**: `PRODUCT_BUNDLE_IDENTIFIER =
  $(BUNDLE_ID).ShareExtension`, `INFOPLIST_FILE`, `CODE_SIGN_ENTITLEMENTS`, `CODE_SIGN_STYLE = Automatic`,
  `DEVELOPMENT_TEAM = $(TEAM_ID)` in both configurations, `IPHONEOS_DEPLOYMENT_TARGET = 15.3`, `TARGETED_DEVICE_FAMILY =
  "1,2"`, `APPLICATION_EXTENSION_API_ONLY = YES`, `SKIP_INSTALL = YES`, the same `EXCLUDED_ARCHS`,
  `SUPPORTS_MACCATALYST` and designed-for-iPad settings as the app, `SWIFT_VERSION = 5.0`. It inherits
  `Config.xcconfig` through the project's configurations, as the app does.
- `Config.xcconfig` gains `APP_GROUP_ID = group.$(BUNDLE_ID)`, so the group follows the bundle id like everything
  else there.
- **The app target** gains `iosApp/iosApp/iosApp.entitlements` (the same file `song-links.md` creates for Associated
  Domains: whichever plan lands second adds its key to it) (`com.apple.security.application-groups` =
  `$(APP_GROUP_ID)`) as its `CODE_SIGN_ENTITLEMENTS`, `CampfireAppGroup` = `$(APP_GROUP_ID)` in its `Info.plist`, a
  dependency on ShareExtension and an **Embed Foundation Extensions** copy phase (`PlugIns`). Its Release
  configuration's literal `DEVELOPMENT_TEAM = N45A6ZHDGY` becomes `$(TEAM_ID)` in the same commit, which is what
  `app/ios/CLAUDE.md` says the target already does.
- **The version**: App Store Connect refuses an extension whose `CFBundleShortVersionString` or `CFBundleVersion`
  differs from its app's. The "Set version from the Gradle properties" phase moves into
  `app/ios/iosApp/Configuration/set-version.sh`, called by the app's phase and by an identical phase that is the
  extension's last. The extension sets its own: the app's phase cannot write into the embedded `Info.plist`, which by
  then is signed.
- The "Compile Kotlin Framework" phase stays on the app target alone.

## 3. iOS: the app's side

### 3.1 `IosShareInbox.kt` (`app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/`)

`fun drainShareInbox()`, top level and called from Swift like `openUrl`. `pendingImports` and `inboxScope` in
`IosFileImport.kt` become `internal`, so a share is read in the same one-at-a-time order as a file opened with the
app, and a drain asked for twice in a row reads each share once.

- The container from `NSBundle.mainBundle.objectForInfoDictionaryKey("CampfireAppGroup")`; none, nothing to do.
- The finished folders oldest first (their names sort by time). For each: `share.json` read and decoded by
  `decodeShareInboxManifest` (§5); a `version` newer than this one's is left where it is for the next version of the
  app. Each file through `NSURL.readImportedFile(budget)` with one `ImportBudget` for the share, sent into
  `pendingImports` (an unreadable one as `ImportedFile.unread`, as `openUrl` does, so it is reported as skipped);
  the texts, read through the same budget as `.txt` names, sent as one `SharedTextPayload(texts, subject)` into a new
  `pendingSharedTexts` channel. Then the folder is deleted.
- A manifest that cannot be read is deleted with its folder and logged: the `.partial` rename means only a disk
  failure produces one, and nothing in it says what the user shared.
- `.partial` folders older than an hour are an extension that was killed mid-share, and are deleted.
- `isExcludedFromBackup` is set again on `ShareInbox/`, for a directory an older build or a restore made.

`CampfireViewController` passes `sharedTexts = sharedTextsToImport` (the channel's flow) to `CampfireIosApp`.

### 3.2 Swift

`iOSApp.swift`: the existing `.onChange(of: scenePhase)` calls `IosShareInboxKt.drainShareInbox()` on `.active`, which
is also the first phase of a cold launch; an `init()` registers a Darwin observer for
`com.pandulapeter.campfire.share-inbox` that calls the same function, for a share made in the other half of a Split
View while Campfire stays active. `cleanImportInbox()` stays what it is: it is about `Documents/Inbox`, not this.

### 3.3 `app/ios/CLAUDE.md`

A paragraph on the extension and the group (what it accepts, the inbox's shape, the rename, the backup exclusion, the
Darwin notification, why it does not open the app), the version script shared by both targets, and the line about
"Zip and plain text are deliberately absent" extended to say that the share extension is where they are accepted.

## 4. Signing and CI

- **What automatic signing covers**: `publish-ios.yml` archives with `-allowProvisioningUpdates` and the App Store
  Connect API key, and exports with `signingStyle` automatic; that makes an App Store profile for every target with a
  bundle id, the embedded extension included, and re-signs it on export. What it cannot be relied on for is
  registering the identifiers and assigning an App Group with an API key, so those are done once in the developer
  portal by hand (§12), after which every profile it makes carries the group. Nothing about them expires.
  `app_store_signing.py` is not involved for iOS.
- **Changing the App ID's capabilities invalidates its profiles.** The iOS one is remade by the next archive. The Mac
  App Store build has the same identifier (`bundleID` in `app/desktop/build.gradle.kts`): if the portal holds one
  App ID for both, its `MAC_APP_STORE` profile goes invalid too, and `app_store_signing.py profile`, which takes only
  `ACTIVE` ones, makes a new one on the next `publish-macos.yml` run. A developer's local
  `app/desktop/embedded.provisionprofile` (not committed) has to be downloaded again. The Mac build claims no group in
  `app-store.entitlements`, and a profile allowing more than the entitlements claim is valid, so nothing else changes
  for it.
- **`publish-ios.yml`'s "Check the archived version"** also reads
  `Campfire.app/PlugIns/ShareExtension.appex/Info.plist` and fails on any difference from the app's, before the upload.
- **The Mac App Store build is not affected otherwise**: it is a Compose desktop app packaged by jpackage, not
  Catalyst, and has no extension. A Share menu entry on macOS would be a separate `.appex` inside that bundle,
  outside this plan.
- No new secret.

## 5. `:presentation`: shared text, once for both phones

**This plan lands after `clipboard-import.md`**, whose §3.1 already moves the naming out of `AndroidFileImport.kt` into
`:presentation`'s `ui/SharedText.kt` (`SharedText.fileOf`, `isOnlyLinks`, `fitsImport`) and whose §4.2 adds
`CampfireViewModel.newSongFromText(text, subject)`. This plan builds on both instead of moving the helpers a second time:

- `data class SharedTextPayload(val texts: List<String>, val subject: String?)`, next to the clipboard plan's object
  (the name `SharedText` is that object's).
- `SharedText.withoutSourceLink(text)`, the rule below, which `newSongFromText` applies before anything else, so a
  paste of a highlight copied with its page address gets it too.
- **A page address after selected text is dropped**: where the last non-blank line is only a link and the lines above
  it are not, that line is the page the text came from (Chrome's "Share" of a highlight), and is left out; matching
  quotation marks around what remains are taken off with it. Nothing else of the text is touched.
- `decodeShareInboxManifest(json: String): ShareInboxManifest?` and the `ShareInboxManifest(version, subject, files,
  texts)` it decodes, with `kotlinx.serialization`, which `:presentation` already applies. Here rather than in
  `:app:ios` so that the test is a desktop one.

`CampfireApp`, `CampfireAndroidApp` and `CampfireIosApp` take `sharedTexts: Flow<SharedTextPayload> = emptyFlow()`,
collected like `filesToImport` into `CampfireViewModel.importSharedText(payload: SharedTextPayload)`:

- only links: `Message.SharedLinkNotImported`, a snackbar — `shared_link_not_imported`, "Campfire does not open web
  pages. Select the chord sheet's text and share that instead.", with its Hungarian in `values-hu`. The clipboard plan's
  `Message.PastedOnlyLinks` says the same about a paste; the two share one string;
- one text: `newSongFromText(text, subject)`, the clipboard plan's flow, with defaults 8 and 9 — in read only mode
  `newSongFromText` takes its import half (the plan applied, the song offered with Open, no New song sheet), which
  the clipboard plan adds for this caller since its own entries are not there in read only mode; with unsaved editor
  text it already announces the song with Open rather than pushing the editor (clipboard plan §4.2);
- several texts: `enqueueImport(payload.texts.mapIndexed { i, t -> SharedText.fileOf(t, payload.subject, i + 1) },
  shouldOpenSong = true)`, which is today's Android behavior exactly.

`presentation/CLAUDE.md`'s "Android shared text" becomes "shared text from either phone".

## 6. Android: the audit, and what changes

| What | Today | Change |
| --- | --- | --- |
| `VIEW` | ChordPro extensions by path, plus `text/plain` and `application/octet-stream` content URIs | None. iOS opens the same family; PDF, Word and zip stay off "Open with" on both, per the root `CLAUDE.md` |
| `SEND` / `SEND_MULTIPLE` file types | `text/plain`, `application/octet-stream`, `application/zip`, `application/x-zip-compressed`, PDF, Word | Checked by hand against Files by Google, Samsung My Files, Drive and Gmail for `.cho` and `.sbpbackup` (§11); a type is added only where one of them is seen sending it (`application/x-zip` and `text/x-chordpro` are the candidates). `text/*` is not: contacts and calendars share as text types |
| Shared text | `importSharedTexts` → `.txt` → import | Sent as a `SharedTextPayload` into a new `pendingSharedTexts` channel in `AndroidFileImport.kt`, handed to `CampfireAndroidApp` as `sharedTexts`; one text goes to `newSongFromText` (§5) |
| `EXTRA_SUBJECT` | The file name, so the title where the text has no `{title}` | Unchanged, and the extension's `attributedTitle` does the same on iOS |
| A page shared from Chrome (`EXTRA_TEXT` the address, `EXTRA_SUBJECT` the title) | An empty file reported as skipped | The link snackbar |
| Selected text with the page address after it | Imported with the address as its last line, in quotes | The address and the quotes dropped (§5) |
| A stream and a text together | The stream wins | Unchanged; the same on iOS |
| Read only mode | Imported | Unchanged |
| Images, `text/html` | Not offered | Unchanged on both |

`app/android/CLAUDE.md`'s paragraph on shared text points at `SharedText.kt` and the snackbar.

## 7. Desktop and web

Nothing is added. The desktop already has the parallels: "Open with" (arguments on Windows and Linux, the `odoc`
event on macOS, `OpenedFiles.open`), dragging files or a folder onto the window, and the picker. A macOS Share menu
entry would need a native extension inside the jpackage bundle (§4). The web has the picker and dropping onto the
page; the Web Share Target API needs an installed web app with a manifest, which the web build deliberately is not.
Text pasted into either is the clipboard plan's.

## 8. Tests (`:presentation:desktopTest`)

- `SharedTextTest` (the clipboard plan's, extended): a
  subject with `/` or control characters; only links, links among blank lines; a link inside a song kept; the trailing
  page address dropped with matching quotes, and kept where it is the only line; an empty share answered with one
  empty file.
- `ShareInboxManifestTest`: a manifest decoded; missing optional fields; an unknown field ignored; a newer `version`
  reported as such; garbage answered with null.

The Swift side is untested by code, like the rest of the iOS shell.

## 9. Corner cases, and what each one does

| Case | Behavior |
| --- | --- |
| Campfire not running | The share waits; the next launch's `.active` imports it, after the first run's demo planting where it is a first run |
| Campfire in the background | Imported as it comes to the front |
| Campfire on screen beside the sharing app (iPad) | The Darwin notification drains it at once |
| An import running, or its report on screen | The batch queues behind it in `importQueue`, as a second "Open with" does |
| A sync run going | Unaffected; the import's writes schedule a run as any import does |
| Read only mode | Files imported; text through the import, offered with Open |
| Unsaved text in the editor | Files imported behind it; a text song through the import, never into the editor |
| A share of a page | No Campfire in the sheet; where the app adds a title beside the address, the link sentence |
| Several shares before Campfire is opened | One batch each, oldest first; the fourth import report waits for the third to be left |
| 21st share, or past 64 MiB waiting | Refused in the extension |
| A 30 MB PDF, a 25 MB zip | Refused in the extension as too large |
| A file the import cannot read inside an accepted type (a scanned PDF) | Saved, then reported by the import as having no readable text, as from the picker |
| The extension killed while copying | A `.partial` folder, ignored and deleted after an hour |
| A newer build's manifest read by an older one (a downgrade) | Left in the inbox |
| Uninstall | The group container goes with the last app of the group |
| Device backup and restore, a new phone | The inbox is excluded; a share waiting at backup time is not restored |
| A build without the App Group (a local build signed without it) | The extension says it could not save; the app finds no container |
| The library | Never touched by the extension: it lives in the app's own container, which an extension cannot reach, so no lock is needed |
| Low memory | The extension copies files rather than loading them; the app reads a share with the same budget an "Open with" uses |
| Android, Chrome page share | The link snackbar |
| Android, the same share twice | Disregarded by the import as a file already there, as today |

## 10. Order of work

Each step builds and tests green on its own, one commit each. Step 1 first, because it is what could change the plan.

1. **A spike, thrown away**: an empty share extension with the predicate of §2.2 on a device, shared to from Safari
   (a selection and a page), Chrome, Files (each type) and Mail, logging the attachments' registered types,
   `suggestedName` and `attributedTitle`. It settles which provider is a file and which a text, whether a page share
   carries a title beside its address, and whether a `.cho` lists Campfire twice.
2. The portal steps of §12, so that every later step can run on a device.
3. `:presentation`, on top of the landed clipboard plan: `SharedTextPayload`, `withoutSourceLink`, the manifest, their
   tests, `sharedTexts` through the three shells, `importSharedText` routed to `newSongFromText` and the link snackbar;
   Android moved onto it.
4. Android: the MIME types found by §11's check, if any.
5. iOS project: the extension target, the entitlements, the group, the shared version script, the archive check in
   `publish-ios.yml`.
6. The extension's writer, view and strings.
7. `IosShareInbox.kt` and the Swift calls.
8. (Nothing: the clipboard plan's flow is already the route, step 3.)
9. Docs: the root `CLAUDE.md` (the opening paragraph's account of what reaches Campfire, the Conventions entry on
   documents, the Build section's iOS line), `app/ios/CLAUDE.md`, `app/android/CLAUDE.md`, `presentation/CLAUDE.md`,
   the README's feature list.

## 11. Checks owed by hand

- **iOS**, on a device and on an iPad: a selection from Safari and from Chrome; a page from each; `.cho`, `.txt`,
  `.pdf`, `.docx`, `.zip`, `.sbpbackup` from Files, iCloud Drive (not downloaded yet) and a Mail attachment; several
  files at once; a file plus a caption; a text from Notes; each refusal of §2.4; the extension in Hungarian and in dark
  mode; Campfire killed, in the background, and beside Safari in Split View; a share during an import and during a
  sync run; read only mode; unsaved editor text; the inbox gone from an encrypted local backup (Finder) after a share
  was imported, and its folder excluded before.
- **TestFlight**: the build with the extension uploads without a version or entitlement complaint, installs, and
  shares; the Keychain still holds the sync connection after the update (the default access group is unchanged).
- **Mac App Store**: the next `publish-macos.yml` run after the capability change makes a new profile and the build
  still starts in its sandbox.
- **Android**: a page from Chrome (the snackbar), a selection, a highlight's share, a `.cho` and a `.sbpbackup` from
  Files by Google, Samsung My Files, Drive and Gmail, noting the MIME type each sends.

## 12. Outside the repository

- **Apple developer portal**, once: an App Group `group.com.pandulapeter.campfire`; the App Groups capability on the
  App ID `com.pandulapeter.campfire`, with that group; a new App ID `com.pandulapeter.campfire.ShareExtension` with the
  same capability and group. Kubriko's identifiers and the shared signing key are not touched.
- **App Store Connect**: no new record, since the extension ships inside the app. A line in the review notes saying
  where the extension is and that it saves only to the app's own container. The privacy label is unchanged: nothing
  leaves the device. A screenshot of the share sheet is optional.
- **Play Console**: nothing.
- `campfire-website`: a support answer, "How do I get a chord sheet from a web page into Campfire?" — select the text
  and share it; a page's address is not fetched.
- A What's new message, by the prepare-release skill.

## 13. Not in this plan

- Fetching a shared web page, or reading a page through a Safari JavaScript preprocessing file
  (`NSExtensionJavaScriptPreprocessingFile`), which would read the page's text on the device without a request but
  would mean taking `public.url` and parsing arbitrary pages. A separate idea, and the privacy promise's to decide.
- Opening Campfire from the extension, by any route (default 1).
- A share extension or Share menu entry on macOS, Android's Direct Share targets, and anything on the web.
- Importing inside the extension (it would have to link the Kotlin framework and hold the library), and choosing a
  setlist or tags in the share sheet.
- Images of chord sheets (OCR), `.json` setlists alone and the legacy `.doc`.
- The extension following the in-app language (default 10).
