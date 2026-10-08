# Move the desktop packaging task classes and the web distribution finisher into build-logic as typed, unit-tested tasks

**Challenged:** amended — the marker plugin is required, not a fallback, and `:app:web` needs it too: `gradle/build-logic` is included through `pluginManagement { includeBuild("gradle") }`, whose classes reach a build script's classpath only through a plugin of it applied there (or on the root project), and neither `:app:desktop` nor `:app:web` applies a `campfire-*` plugin.

**Kind:** build  ·  **Severity:** medium  ·  **Effort:** M  ·  **Risk:** medium  ·  **Platforms:** desktop (Linux, Windows, macOS packaging), web
**Files:** `app/desktop/build.gradle.kts`; `app/web/build.gradle.kts`; `gradle/build-logic/build.gradle.kts`; new
`gradle/build-logic/src/main/kotlin/com/pandulapeter/campfire/buildLogic/tasks/{RecordClassDataArchive,AddLaunchAfterInstallToMsi,PackageMsix,AddStartupWmClassToDeb,FinishWebDistribution}.kt`
and a pure-helpers file next to them (e.g. `tasks/PackagingText.kt`); new `gradle/build-logic/src/test/kotlin/...`;
new `app/desktop/macos/document-types.plist` and `app/desktop/macos/export-compliance.plist` (fragments); `.github/workflows/tests.yml`
(run the build-logic tests); `gradle/build-logic/CLAUDE.md`; `app/desktop/CLAUDE.md`; `app/web/CLAUDE.md`
**Depends on:** none. Coordinate with plan 66 (configuration cache): either order works, but if 66 lands first its
fixes to these script blocks move with them.

## Problem

`app/desktop/build.gradle.kts` is 872 lines. About 450 of them are not configuration but implementation declared
inline in the script: `abstract class RecordClassDataArchive : DefaultTask()` (~80 lines), `AddLaunchAfterInstallToMsi`
(~40), `PackageMsix` (~160, with the publisher-id hash, logo scaling, manifest templating, SDK discovery),
`AddStartupWmClassToDeb` (~55, desktop-entry and `md5sums` rewriting), and three top-level functions returning raw
plist XML (`macChordProDocumentTypes()` ~75 lines of `<dict>`s, `nonExemptEncryptionKey()`), used as
`extraKeysRawXml = macChordProDocumentTypes() + "\n" + nonExemptEncryptionKey()`. Logic that decides whether a Store
package is accepted is untestable where it sits, e.g.

```kotlin
    private fun publisherId(publisher: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(publisher.toByteArray(Charsets.UTF_16LE)).take(8)
        val bits = hash.joinToString("") { (it.toInt() and 0xFF).toString(2).padStart(8, '0') } + "0"
        return bits.chunked(5).map { "0123456789abcdefghjkmnpqrstvwxyz"[it.toInt(2)] }.joinToString("")
    }
```

and `packageVersion = (versionName.split('.') + listOf("0", "0")).take(3).joinToString(".") + ".0"`, the `[JavaOptions]`
line insertion repeated in `RecordClassDataArchive.record()` and `PackageMsix.addPackageFamilyNameToLauncher`, the
`StartupWMClass=` and `md5sums` rewrites.

`app/web/build.gradle.kts`'s `finishWebDistribution` is `tasks.register("finishWebDistribution") { … doLast { … } }`
with no declared inputs or outputs, calling script-level helpers (`ByteArray.sha256()`, `String.toJsonString()`,
`File.gzipTo`, `File.brotliTo`). The manifest it writes into `index.html` and `build.json` is the contract the page's
offline code checks every file against (`app/web/CLAUDE.md`), yet the JSON building has no test, and the doLast
capturing script functions is also a configuration-cache blocker (plan 66).

## Fix

1. **Move the four desktop task classes verbatim** into `gradle/build-logic/src/main/kotlin/com/pandulapeter/campfire/buildLogic/tasks/`,
   one file each, as public `abstract class`es with the same names, annotations and KDoc and the MPL header. The script
   imports them (`import com.pandulapeter.campfire.buildLogic.tasks.PackageMsix` …); the `tasks.register<…>` blocks
   stay in the script unchanged. `build-logic` already has the Gradle API through `kotlin-dsl`; `javax.imageio`/
   `java.awt` are in the JDK. Drop the now-unused imports from the script. A script sees the included build's classes
   only once one of its plugins is applied there, and `:app:desktop` applies none (only `kotlin.jvm` and the two
   Compose plugins), so register an empty marker plugin `campfire-packaging` in `gradle/build-logic/build.gradle.kts`
   (MPL header, a KDoc saying it exists to put the task classes on the script's classpath) and add
   `id("campfire-packaging")` to `app/desktop`'s `plugins {}`; step 4 adds the same line to `app/web` (which applies only
   `kotlin.multiplatform` and the Compose plugins). Applying it from the root `build.gradle.kts` instead would also work
   but puts build-logic on every project's classpath; keep it to the two scripts.
2. **Extract the pure parts** into internal top-level functions in `tasks/PackagingText.kt`, called from the moved
   classes: `msixPackageVersion(versionName)`, `msixPublisherId(publisher)`, `xmlEscaped(text)`,
   `withJavaOption(cfgLines, option)` (the `[JavaOptions]` insertion, throwing the same message when the section is
   missing), `withoutJavaOptionsStartingWith(cfgLines, prefix)`, `withStartupWmClass(desktopEntryLines, className)`,
   `withUpdatedMd5(md5sumsLines, path, hash)`, `newestSdkVersionKey(name)` (the version fold in
   `newestWindowsSdkBinDirectory`). The `packageVersion` expression in the script calls `msixPackageVersion`.
3. **Plist fragments as resource files**: `app/desktop/macos/document-types.plist` and `export-compliance.plist`
   holding exactly the text the two functions return (no XML declaration, same indentation after `trimIndent()`),
   with the KDoc of each function moved into an XML comment at the top of its file **only if** the comment does not
   reach `Info.plist` — otherwise keep the explanation in `app/desktop/CLAUDE.md`. The script reads them with
   `file("macos/document-types.plist").readText().trim()`. Keep `chordProFileAssociations()` in the script (it is DSL).
4. **`FinishWebDistribution`** typed task in build-logic: `@get:InputFile pageTemplate`, `@get:Input precompress`,
   `@get:Input placeholder`, `@get:Input transportSuffixes / compressibleSuffixes / unversionedFiles`, `@get:Internal
   distributionDirectory` with `doNotTrackState("Rewrites the distribution in place")` (it edits the directory
   `wasmJsBrowserDistribution` wrote, like `RecordClassDataArchive` edits the image). Move the doLast body verbatim
   into `@TaskAction`; move `sha256`, `toJsonString`, `gzipTo`, `brotliTo` into the build-logic file as private
   functions; extract the manifest building into a pure
   `webBuildManifest(files: List<Pair<String, ByteArray>>, template: String): WebBuildManifest` returning the id, the
   `index.html` manifest JSON and the `build.json` text minus the page digest. The script keeps the `finalizedBy`
   wiring and the KDoc on the registration, and applies `id("campfire-packaging")` (see step 1). Every value the
   `doLast` read through `project` (`campfire.web.precompress` and the like) becomes an `@Input` set at registration,
   so the action reaches no script object (what plan 66 needs).
5. Add `testImplementation(kotlin("test"))` (and JUnit 5 platform if needed) to `gradle/build-logic/build.gradle.kts`,
   and run the tests in `tests.yml`: `./gradlew -p gradle :build-logic:test` (the included build's root is `gradle/`).
6. Docs: `gradle/build-logic/CLAUDE.md` gets a "Tasks" section (what each class does, that the registration and its
   wiring live in the consuming script); `app/desktop/CLAUDE.md` and `app/web/CLAUDE.md` point there where they name
   the classes / `finishWebDistribution`.

Steps 1, 2+5, 3 and 4 are separate commits.

## Tests

In `gradle/build-logic/src/test/kotlin/.../tasks/`:
- `msixPackageVersion`: `"4.7.1"` → `"4.7.1.0"`, `"5"` → `"5.0.0.0"`, `"4.7"` → `"4.7.0.0"`.
- `msixPublisherId`: characterisation test — pin the value for `CN=AC1B9E39-11E9-4460-B47B-C69CEB0B1DAF` (the
  `campfire.windows.publisher` in `gradle.properties`) computed by the current code before the move; cross-check it
  against Partner Center's "Package/Identity/PublisherId" if the author has it.
- `withJavaOption`: inserts right after `[JavaOptions]`, keeps other lines, throws without the section;
  `withoutJavaOptionsStartingWith` removes only matching lines.
- `withStartupWmClass`: replaces an existing `StartupWMClass=` line, appends otherwise, ends with `\n`.
- `withUpdatedMd5`: replaces only the line whose path matches after the two spaces.
- `xmlEscaped`: `&<>"` escaped.
- `webBuildManifest`: for two fixed files and a template, the exact `index.html` manifest string and `build.json`
  string (golden, taken from the current code's output before the move), sorted paths, the 16-hex id, `binaryCount` /
  `binaryBytes` counting `.wasm` only, a path with `"` and `\` escaped.

## Manual check

Equivalence of outputs, before vs after (build both from the same commit's sources):
- macOS: `./gradlew :app:desktop:createReleaseDistributable` and `diff` the two
  `build/compose/binaries/main-release/app/Campfire.app/Contents/Info.plist` files — must be identical.
- Web: `./gradlew :app:web:wasmJsBrowserDistribution`; `diff -r` the two `build/dist/wasmJs/productionExecutable`
  trees — `index.html` and `build.json` must be byte-identical.
- Linux: dispatch `publish-linux.yml` with the latest release tag (re-attaches the same `.deb`s); its start check and
  the `.desktop` entry (`dpkg-deb -c` / extract and read `StartupWMClass=Campfire`) confirm the moved task.
- Windows: at the next release with `<!-- microsoft-store submit: false -->`, download the run's `campfire-msix`
  artifact and compare its `AppxManifest.xml` and `app/Campfire.cfg` (`-Dcampfire.packageFamilyName=…`) with the
  previous release's package; then submit from Partner Center.
