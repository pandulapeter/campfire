# Turn on explicitApi() for :metronome:api and :chordpro, and take ChordProSerializer out of the public API

**Challenged:** amended — explicit API mode also demands explicit return types on public functions and properties (`fun fromId(id: String) = …`, `fun parse(…) = …` and many more), which the steps now include; the serializer move touches six test files, not one; ordering relative to plan 43 stated.

**Kind:** build  ·  **Severity:** low  ·  **Effort:** M (`:metronome:api` S, `:chordpro` M)  ·  **Risk:** low  ·  **Platforms:** all
**Files:** `metronome/api/build.gradle.kts`, every file in `metronome/api/src/commonMain` (8 files); `chordpro/build.gradle.kts`, every file in `chordpro/src/commonMain` that declares a public declaration (≈30 files plus `model/`); `chordpro/src/commonMain/.../ChordProSerializer.kt` (object `ChordProSerializer`); `chordpro/src/commonTest/.../ChordProSerializerTest.kt` and the five other tests that call it (`ChordProCoverArtTest`, `ChordProDefinitionsTest`, `ChordProLanguagesTest`, `ChordProLinksTest`, `ChordProTagsTest` — unchanged, they see it in its new place); `chordpro/src/commonMain/.../ChordProSyntax.kt` (its KDoc names `[ChordProSerializer]`); `chordpro/CLAUDE.md` (the `ChordProSerializer` entry); `metronome/api/CLAUDE.md`; root `CLAUDE.md` Conventions (one line about explicit API mode)
**Depends on:** the :chordpro split lane (which narrows `ChordProLiteralText`, `namesIn`, `rewrittenLine`, `addLink` / `removeLink`, `transposeChord`, `fingerCount` / `pitchClasses` to internal); for `:chordpro`, land after every other open :chordpro plan (40–44, 43 included — 43's phase 2 also decides which package `ChordProSerializer` would sit in, moot once it moves to `commonTest`), since it touches every file

## Problem

`:chordpro` and `:metronome:api` are the two modules other modules see the most of, and both are "depends on nothing"
libraries whose public surface is meant to be deliberate. Nothing enforces that: in `:chordpro` a declaration is public
unless someone remembered `internal`, which is why the split lane has a list of declarations to narrow after the fact
(`ChordProLiteralText`, `namesIn`, `rewrittenLine`, `addLink`, `removeLink`, `transposeChord`, `fingerCount`,
`pitchClasses`). `ChordProSerializer` is the clearest case: a public object with no caller outside `:chordpro`'s own
tests —

```
chordpro/src/commonTest/.../ChordProSerializerTest.kt:26: assertEquals(parsed, ChordProParser.parse(ChordProSerializer.serialize(parsed)))
```

— where it serves as the round-trip oracle `parse(serialize(parse(x))) == parse(x)` that `chordpro/CLAUDE.md` describes.
It ships in every build and is maintained alongside the model for the tests' sake.

## Fix

1. **`:metronome:api` first** (small: `Metronome`, `TapTempo`, the `model/` types). Add to `metronome/api/build.gradle.kts`
   ```kotlin
   kotlin { explicitApi() }
   ```
   and fix every declaration the compiler then reports: add `public` (the interface and its members, the data classes,
   enums and their properties, `TapTempo`, constants such as `BPM_RANGE`), and an explicit return type on every public
   function or property written with an inferred one — explicit API mode reports those too (e.g. `BeatLevel.next()`,
   `BeatLevel.fromId`, `Subdivision.fromId`, `TapTempo.RESET_GAP`; a `const val` and a primary-constructor property are
   exempt). Write the type the compiler inferred, so no signature changes. One commit. Nothing outside the module
   changes.
2. **`ChordProSerializer`** — see Decision. Recommended: move `ChordProSerializer.kt` to
   `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/` unchanged (tests share the module's `internal`
   visibility, so its uses of `ChordProSyntax` / `ChordProDefinitions.line` still compile), fix the `[ChordProSerializer]`
   KDoc link in `ChordProSyntax` (plain text, since main code cannot link to a test class), and move its
   `chordpro/CLAUDE.md` entry under the tests paragraph ("a test-only serializer, the round-trip oracle …").
3. **`:chordpro`**: `kotlin { explicitApi() }` in `chordpro/build.gradle.kts`, then add `public` and the missing
   explicit return types to what the compiler reports (`:chordpro` writes many public functions with expression bodies
   — `ChordProParser.parse`, `summarize`, … — each needs the inferred type spelled out; take it from the IDE's
   "Specify type explicitly" so it is exactly the inferred one, since a widened type would change callers' inference). Before adding `public`, check each reported declaration for a caller outside `:chordpro`
   (`grep -rn "<Name>" --include='*.kt' presentation domain data app tools`); one with none becomes `internal` instead,
   and the commit message lists those. Expect several hundred `public` modifiers; members of `internal` / `private`
   objects need none. Use one commit per few files if the diff is unreadable as one.
4. Add one line to the root `CLAUDE.md` Conventions: `:chordpro` and `:metronome:api` build in explicit API mode, so a
   new public declaration is written `public` on purpose.

Not proposed for other modules: the `api` modules of the data/domain layers are interfaces consumed through Koin and
already reviewed per layer; extending the mode there is a separate decision.

## Tests

No behaviour changes. `./gradlew :metronome:api:desktopTest :metronome:implementation:desktopTest :chordpro:desktopTest`
plus a compile of the consumers on every target (`:presentation:compileKotlinDesktop`, `:app:android:compileDebugKotlin`,
`:app:ios:linkDebugFrameworkIosSimulatorArm64`, `:app:web:compileKotlinWasmJs`) — a declaration wrongly made `internal`
fails there. `ChordProSerializerTest` must still pass from its new place.

## Manual check

none — covered by compilation and tests.

## Decision

1. Explicit API mode:
   - **A (recommended): both `:metronome:api` and `:chordpro`.** The guard makes the split lane's narrowing permanent.
   - **B: `:metronome:api` only.** Cheap and complete; `:chordpro` keeps relying on review.
   - **C: neither.**
2. `ChordProSerializer`:
   - **A (recommended): move it to `commonTest`.** It is a test oracle; shipping it costs binary size on Wasm/iOS and
     maintenance in main code.
   - **B: keep it in main as `internal`.** Keeps the door open for a future feature that writes a model back to text
     (none is planned; the editor works on raw text).
   - **C: keep it public.**
