# 16 · The desktop installers claim ChordPro's shared extensions, and on macOS every kind of file, with no rank

**Severity:** minor (desktop. macOS: after installing the DMG, Campfire can appear in Finder's Open With for files
that are not songs, and can become the default for `.pro`/`.crd` where nothing else claims them. Windows: the MSI can
take over `.pro` and `.crd` on machines with no explicit choice for them. Nothing is lost or overwritten) ·
**Area:** `:app:desktop` (`build.gradle.kts`)

## Decision (taken by the user on 2026-09-22)
**A. Windows claims `.cho`, `.chopro` and `.chordpro`**, which no other application uses, and macOS is fixed as
below. The MSI can only claim an extension outright (jpackage's WiX template writes the default handler under
`HKCU\Software\Classes`, with no equivalent of iOS's `Alternate` rank through the Compose DSL), so `.crd`, `.chord`
and `.pro` are no longer double-click targets on Windows; they are still imported by dropping them on the window,
through the in-app import, or with Open with → Choose another app. This is the rule iOS and Android already follow
(claim what is ChordPro's alone), as closely as an MSI can follow it.

## Symptom
- macOS: Finder's Open With lists Campfire for arbitrary files (the document type carries the "all documents" OS
  type), and Campfire, with the implicit rank, can be the default opener of `.pro` and `.crd` files.
- Windows: after installing the MSI, double-clicking a Qt `.pro` file on a machine without an explicit user choice for
  `.pro` opens Campfire, which offers to import it as a song.

## Cause
`app/desktop/build.gradle.kts:55,59,102-105` registers all six extensions for macOS and Windows through the Compose
DSL:

```kotlin
fun org.jetbrains.compose.desktop.application.dsl.AbstractPlatformSettings.chordProFileAssociations() =
    listOf("cho", "chopro", "chordpro", "crd", "chord", "pro").forEach { extension ->
        fileAssociation(mimeType = "text/plain", extension = extension, description = "ChordPro song")
    }
```

On macOS the Compose Gradle plugin (1.12.0, `AbstractJPackageTask.setInfoPlistValues`, `AbstractJPackageTask.kt:732-747`)
turns them into one document type of its own making, grouped by MIME type and description:

```kotlin
PlistKeys.CFBundleTypeRole to InfoPlistStringValue("Editor"),
PlistKeys.CFBundleTypeExtensions to InfoPlistListValue(extensions.map { InfoPlistStringValue(it.extension) }),
PlistKeys.CFBundleTypeIconFile to InfoPlistStringValue(iconPath ?: "$packageName.icns"),
PlistKeys.CFBundleTypeMIMETypes to InfoPlistStringValue(mimeType),
PlistKeys.CFBundleTypeName to InfoPlistStringValue(description),
PlistKeys.CFBundleTypeOSTypes to InfoPlistListValue(InfoPlistStringValue("****")),
```

which is what the locally built `app/desktop/build/compose/binaries/main/app/Campfire.app/Contents/Info.plist`
holds: `CFBundleTypeOSTypes = ("****")` (Apple: the type code that opens documents of any type), no `LSHandlerRank`,
no `LSItemContentTypes`. The DSL (`fileAssociation(mimeType, extension, description, iconFile)`) exposes neither rank
nor UTI, so it cannot be fixed through it. On Windows the same six go to jpackage as `--file-associations`
properties files (`AbstractJPackageTask.kt:447-468`), each of which the MSI registers as the extension's handler.

iOS (`app/ios/iosApp/iosApp/Info.plist`) and Android claim `.cho` as the default and the shared extensions only as
`Alternate`, and `app/ios/CLAUDE.md` says why.

## Fix
`app/desktop/build.gradle.kts`:

1. macOS: stop using the DSL there and write the document types the iOS app has, through the plugin's raw plist
   hook (`InfoPlistSettings.extraKeysRawXml`, appended inside the generated plist's top dict). Replace
   `chordProFileAssociations()` in the `macOS { }` block (`:55`) with:

   ```kotlin
                // Not fileAssociation(): the plugin writes its own document type with the "****" OS type, which claims
                // every kind of file, and with no rank or content type. These are the iOS app's document types.
                infoPlist {
                    extraKeysRawXml = macChordProDocumentTypes()
                }
   ```

2. Windows: keep `chordProFileAssociations()` in the `windows { }` block (`:59`) and narrow the function. Replace
   `:95-105` with:

   ```kotlin
   /**
    * The ChordPro extensions the Windows installer claims: the ones no other application uses. An MSI can only make an
    * application an extension's handler outright - there is no Alternate rank to register the shared ones with, the
    * way iOS and macOS do - so ".crd", ".chord" and ".pro" (Qt's project files among others) are left to whoever has
    * them, and are imported from inside the app. They are all registered as plain text, which is what they are; zip and
    * .txt are deliberately not here, since an app that claims those would be answering for every archive and note on
    * the machine.
    *
    * A subset of `LibraryFiles.SONG_EXTENSIONS` in `:data:model`, which the build file cannot see.
    */
   fun org.jetbrains.compose.desktop.application.dsl.AbstractPlatformSettings.chordProFileAssociations() =
       listOf("cho", "chopro", "chordpro").forEach { extension ->
           fileAssociation(mimeType = "text/plain", extension = extension, description = "ChordPro song")
       }

   /**
    * The macOS document types, the same as the iOS app's (`app/ios/iosApp/iosApp/Info.plist`): ".cho" claimed as the
    * default, the rest of the family as an alternate, since those extensions are shared with other kinds of file, all
    * of them imported as types that conform to plain text. Kept in step with `LibraryFiles.SONG_EXTENSIONS` in
    * `:data:model`, which the build file cannot see.
    */
   fun macChordProDocumentTypes() = """
       <key>UTImportedTypeDeclarations</key>
       <array>
           <dict>
               <key>UTTypeIdentifier</key>
               <string>org.chordpro.cho</string>
               <key>UTTypeDescription</key>
               <string>ChordPro song</string>
               <key>UTTypeConformsTo</key>
               <array>
                   <string>public.plain-text</string>
               </array>
               <key>UTTypeTagSpecification</key>
               <dict>
                   <key>public.filename-extension</key>
                   <array>
                       <string>cho</string>
                   </array>
               </dict>
           </dict>
           <dict>
               <key>UTTypeIdentifier</key>
               <string>org.chordpro.chordpro</string>
               <key>UTTypeDescription</key>
               <string>ChordPro song</string>
               <key>UTTypeConformsTo</key>
               <array>
                   <string>public.plain-text</string>
               </array>
               <key>UTTypeTagSpecification</key>
               <dict>
                   <key>public.filename-extension</key>
                   <array>
                       <string>chopro</string>
                       <string>chordpro</string>
                       <string>crd</string>
                       <string>chord</string>
                       <string>pro</string>
                   </array>
               </dict>
           </dict>
       </array>
       <key>CFBundleDocumentTypes</key>
       <array>
           <dict>
               <key>CFBundleTypeName</key>
               <string>ChordPro song</string>
               <key>CFBundleTypeRole</key>
               <string>Editor</string>
               <key>CFBundleTypeIconFile</key>
               <string>Campfire.icns</string>
               <key>LSHandlerRank</key>
               <string>Default</string>
               <key>LSItemContentTypes</key>
               <array>
                   <string>org.chordpro.cho</string>
               </array>
           </dict>
           <dict>
               <key>CFBundleTypeName</key>
               <string>ChordPro song</string>
               <key>CFBundleTypeRole</key>
               <string>Editor</string>
               <key>CFBundleTypeIconFile</key>
               <string>Campfire.icns</string>
               <key>LSHandlerRank</key>
               <string>Alternate</string>
               <key>LSItemContentTypes</key>
               <array>
                   <string>org.chordpro.chordpro</string>
               </array>
           </dict>
       </array>
   """.trimIndent()
   ```

   Both are functions rather than top-level `val`s on purpose: a script's `val` is initialized in order, and these are
   used by the `compose.desktop` block above them. `Campfire.icns` is the name the plugin gives the bundle icon
   (`"$packageName.icns"`, `packageName = "Campfire"`), which is also what it used for the document icon before.

The open-file handler (`OpenedFiles.listenForSystemRequests`) needs nothing: macOS sends `odoc` for any document type
the bundle declares, whichever way it was declared. The Linux package keeps no association, as before.

## Tests
None (build configuration).

## Verify
1. macOS: `./gradlew :app:desktop:createDistributable`, then
   `plutil -p app/desktop/build/compose/binaries/main/app/Campfire.app/Contents/Info.plist`: exactly one
   `CFBundleDocumentTypes` (two entries, `Default` for `org.chordpro.cho`, `Alternate` for `org.chordpro.chordpro`),
   `UTImportedTypeDeclarations` with both types, and no `CFBundleTypeOSTypes` anywhere. `plutil -lint` on it is `OK`.
2. Copy that `Campfire.app` to `/Applications` (or register it with
   `/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister -f <app>`):
   Finder's Open With on a `.png` does not list Campfire; on `song.cho` it is offered (and is the default where
   nothing else claims `.cho`); on `x.pro` it is in the Open With list but not the default when another app claims
   it. Double-clicking `song.cho` while Campfire runs imports it (the `odoc` path).
3. Windows (if a machine is at hand): `./gradlew :app:desktop:packageMsi`, install; `song.cho` opens with Campfire,
   a `.pro` file keeps its previous handler.
4. `./gradlew :app:desktop:run` still starts (the build script compiles).

## Docs
`app/desktop/CLAUDE.md`, the Packaging paragraph: replace "`chordProFileAssociations()` in `build.gradle.kts`
registers the six ChordPro extensions as `text/plain` for macOS and Windows — not for Linux, where an association is
keyed by the MIME type and that would make Campfire a handler of every text file; deliberately not zip or `.txt`, and
kept in step with `LibraryFiles.SONG_EXTENSIONS`, which a build file cannot see." with:

"File associations follow iOS and Android: `.cho` is claimed, the extensions ChordPro shares with other kinds of file
(`.crd`, `.chord`, `.pro`) are at most an alternative. On macOS the document types are written as raw plist keys
(`macChordProDocumentTypes()`, through `infoPlist.extraKeysRawXml`) — the iOS app's imported types, `.cho` at rank
`Default` and the rest `Alternate` — because the Compose DSL's `fileAssociation` writes a document type with the
`****` OS type, which claims every kind of file, and offers no rank. On Windows `chordProFileAssociations()` registers
only `.cho`, `.chopro` and `.chordpro` as `text/plain`, since an MSI can only make an app an extension's handler
outright. Linux has none: an association there is keyed by the MIME type, and that would make Campfire a handler of
every text file. Deliberately not zip or `.txt`, and kept in step with `LibraryFiles.SONG_EXTENSIONS`, which a build
file cannot see."

## Touches
- `app/desktop/build.gradle.kts`
- `app/desktop/CLAUDE.md`

## Depends on
Nothing. 54 edits another paragraph of `app/desktop/CLAUDE.md`.
