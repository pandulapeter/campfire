<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# 11 — Mark the :chordpro model stable for the Compose compiler

| | |
|---|---|
| Lane | B |
| Impact | medium (Split-mode preview refreshes, every transposition step and song swap on the details screen) |
| Confidence | high on the mechanism; medium on the size of the win (not profiled on a device) |
| Platforms | all |
| Files | `gradle/compose-stability.conf` (new), `gradle/build-logic/src/main/kotlin/com/pandulapeter/campfire/buildLogic/plugins/ComposeLibraryPlugin.kt`, `gradle/build-logic/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/build-logic/CLAUDE.md`, `chordpro/CLAUDE.md`, `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt` (`@Immutable` annotations only) |
| Depends on / conflicts with | Touches `SongLyrics.kt` (`@Immutable` on `RenderSection` / `SectionPart`), so it textually conflicts with 07 and 08; land after them or rebase. Complements 07 (content-keyed `SectionMeasurements`). |
| Commit message | `Mark the ChordPro model stable for the Compose compiler.` |

## Problem
The ChordPro model classes are passed straight into composables:
- `SongLyrics(song: ChordProSong)` (`SongLyrics.kt:157`)
- `SongMetadataHeader(song: ChordProSong)` (`:337`)
- `SongGridLine(line: ChordProLine.Grid)` (`:1081`)
- `SongLineWithChords(line: ChordProLine.Lyrics)` (`:1658`)
- `SongSectionContent(section: RenderSection.Lines)` (`:488`), whose `parts: List<SectionPart>` holds those lines.

`:chordpro` is not compiled with the Compose compiler, so none of its classes carry stability information. The project also has no stability configuration file (`grep -r stabilityConfiguration` finds nothing). So every one of these parameters counts as **unstable**. With strong skipping on (the Kotlin 2.4 default), an unstable parameter is compared by instance (`===`), never by `equals`.

Every parse produces new instances. So each of these recomposes all of its children:
- a preview refresh after the 150 ms debounce (`SongEditorScreen.kt:678-685`);
- a transposition step;
- a notation change;
- a tag chip being tapped (which rewrites the file).

In the editor's Split pane this means every `SongLineWithChords` in a 300-line song recomposes whenever the user pauses typing, although only the one line being typed changed. The `remember(line, textMeasurements)` blocks inside compare keys by `equals`, so they do hit. But each composable's body, its `Text` node update and its semantics still run. `RenderSection.Lines` / `.Comment` (presentation-owned data classes holding a `List` of a sealed interface) are unstable for the same reason, so `SongSectionContent` never skips either.

## Fix
1. **New file `gradle/compose-stability.conf`**, with a comment header in the file's own `//` syntax that says why it exists. List each class by its fully qualified name. Do not use the `com.pandulapeter.campfire.chordpro.model.**` wildcard: that would silently vouch for any class added to the package later.
   ```
   com.pandulapeter.campfire.chordpro.model.ChordProSong
   com.pandulapeter.campfire.chordpro.model.ChordProMetadata
   com.pandulapeter.campfire.chordpro.model.ChordProSummary
   com.pandulapeter.campfire.chordpro.model.ChordProBlock
   com.pandulapeter.campfire.chordpro.model.ChordProBlock.Section
   com.pandulapeter.campfire.chordpro.model.ChordProBlock.ChorusRecall
   com.pandulapeter.campfire.chordpro.model.ChordProBlock.Comment
   com.pandulapeter.campfire.chordpro.model.ChordProBlock.Transpose
   com.pandulapeter.campfire.chordpro.model.ChordProBlock.Break
   com.pandulapeter.campfire.chordpro.model.SectionType
   com.pandulapeter.campfire.chordpro.model.SectionType.Verse
   com.pandulapeter.campfire.chordpro.model.SectionType.Chorus
   com.pandulapeter.campfire.chordpro.model.SectionType.Bridge
   com.pandulapeter.campfire.chordpro.model.SectionType.Custom
   com.pandulapeter.campfire.chordpro.model.SectionType.Paragraph
   com.pandulapeter.campfire.chordpro.model.ChordProLine
   com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics
   com.pandulapeter.campfire.chordpro.model.ChordProLine.Lyrics.Chord
   com.pandulapeter.campfire.chordpro.model.ChordProLine.Tab
   com.pandulapeter.campfire.chordpro.model.ChordProLine.Grid
   com.pandulapeter.campfire.chordpro.model.ChordProLine.Blank
   com.pandulapeter.campfire.chordpro.model.GridToken
   com.pandulapeter.campfire.chordpro.model.GridToken.Bar
   com.pandulapeter.campfire.chordpro.model.GridToken.Chord
   com.pandulapeter.campfire.chordpro.model.GridToken.Beat
   com.pandulapeter.campfire.chordpro.model.GridToken.Repeat
   com.pandulapeter.campfire.chordpro.model.GridToken.Text
   ```
   `CommentStyle` is an enum and already stable, so it is left out. The sealed interfaces are listed because the compiler cannot infer stability for an interface. A parameter or field typed `ChordProLine` or `List<ChordProBlock>` would otherwise stay unstable.

   **Why each one is really immutable.** This was re-checked against `chordpro/src/commonMain/kotlin/.../model/*.kt` and every construction site.
   - Every class above is a `data class`, a `data object` or a sealed interface over them. Every property is a `val` of type `String`, `Int`, `Boolean`, a nullable of those, another listed model type, or a read-only `List` / `Map`.
   - `ChordProSong.hasChords` is a getter computed from those vals, not a field.
   - The collections are never mutated after the model is built:
     - `ChordProParser` copies the lines of a section (`lines.toList()`, `ChordProParser.kt:428`) and the tags, languages and custom items (`MetadataBuilder.build`, `:579-581`).
     - `parseLyrics` hands out its local `chords` list and keeps no reference (`:313-332`).
     - The top-level `blocks` list is not touched after `parseAsWritten` returns.
     - The transposer and notation code only build new lists (`map`, `copy`).
     - `:presentation` builds a model in two places, both with fresh lists: `SongLyrics.kt:1771` (`mapIndexed`) and `CampfireViewModel.kt:1536` (`emptyList()`).
     - No code casts a model collection back to a mutable type: there is no `as MutableList`, `as ArrayList` or `as MutableMap` anywhere in `chordpro`, `presentation`, `domain` or `data`.
   - Every generated `equals` covers every field, so an equals-based skip can never hide a real difference.

   **What must NOT change:** this is a promise the model has to keep. Record it in `chordpro/CLAUDE.md`, in the `model/` bullet: *"the model is immutable and the Compose compiler is told so (`gradle/compose-stability.conf`): never hand it a collection that is mutated afterwards, and add any new model class to that file only once that holds for it."*

2. **Wire the file in `ComposeLibraryPlugin`**. It applies to every `campfire-compose-library` module, which today is `:presentation` only; the config is read by the compiler that compiles the *consumer*, so `:chordpro` itself needs nothing.
   ```kotlin
   extensions.configure<ComposeCompilerGradlePluginExtension> {
       stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("gradle/compose-stability.conf"))
   }
   ```
   `import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension`. That needs the plugin's artifact on the build-logic classpath, added the same way the Kotlin Gradle plugin already is:
   - add `kotlin-composeCompiler = { module = "org.jetbrains.kotlin:compose-compiler-gradle-plugin", version.ref = "kotlin" }` to `libs.versions.toml`;
   - add `implementation(libs.kotlin.composeCompiler)` to `gradle/build-logic/build.gradle.kts`.

   The version must follow `kotlin`, so it stays paired on every Kotlin bump. If the typed extension ever gives a classloader conflict, the fallback is `composeCompiler { stabilityConfigurationFiles.add(...) }` in `presentation/build.gradle.kts`. The fallback must still point at the same file.

3. **`@Immutable` on `RenderSection` and `SectionPart`, and on their three data classes `RenderSection.Lines`, `RenderSection.Comment` and `SectionPart.Lines`,** in `SongLyrics.kt` (`:1400`-`:1433`). Put it on the data classes too, and do not rely on the compiler reading a marker from a supertype: `RenderSection.Lines` holds a `List`, which it would otherwise infer as unstable. These are presentation's own sealed interfaces over data classes whose fields are `String`, `Boolean`, `CommentStyle` and `List`s of model lines or of `SectionPart`. They are built fresh in `toRenderSections` and never mutated. The `val`s derived in the class body (`lines`, `wholeFoldableKind` and `firstEnvironmentLabel` at `:1414-1416`, and `SectionPart.Lines.runs` at `:1431`) are computed from those fields. Annotating them makes `SongSectionContent` skip for a section whose content did not change. Nothing else in the file changes.

4. **Do not add `kotlin.collections.*` to the file.** It would change the skipping of every `List` / `Set` / `Map` parameter in `:presentation`, and this plan does not audit those. The explicit list above already covers the model's own collections: a class listed as stable is stable regardless of its fields.

## Verification
- Build and run the tests:
  `./gradlew :chordpro:desktopTest :presentation:desktopTest :presentation:compileKotlinWasmJs :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:android:assembleDebug`
- Check the compiler's view. Temporarily add `reportsDestination = layout.buildDirectory.dir("compose_reports")` to the same `composeCompiler` block (do not commit it) and build `:presentation:compileKotlinDesktop`. In the `*-composables.txt` report:
  - `SongLineWithChords`, `SongGridLine`, `SongMetadataHeader`, `SongSectionContent` and `SongLyrics` must be `restartable skippable`;
  - their `line` / `song` / `section` parameters must be listed as `stable`.

  Before the change they are `unstable`.
- Manual check. Run the Android debug build on a tablet emulator, open a long song in the editor in Split, and open Layout Inspector with recomposition counts. Type a character into one lyric line and wait for the preview. Only the edited line's `SongLineWithChords` (and its section's `SongSectionContent`) should increase its count; before the change every line does.
- Stale-UI check. On the details screen, confirm that transposing, switching the notation and the accidentals, lyrics-only mode and tagging still update what is drawn. On the Split preview, confirm that typing still updates it.
- Documentation to update in the same commit:
  - `gradle/build-logic/CLAUDE.md`: a bullet for the stability file under `ComposeLibraryPlugin`;
  - `chordpro/CLAUDE.md`: the immutability promise from step 1.
