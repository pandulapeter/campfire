# 27 · `{start_of_chorus-guitar}` becomes a custom "Chorus-guitar" section, and a negated selector shows as lyrics

**Severity:** minor (all platforms. ChordPro 6 files that use selectors on environments or negated selectors; rare) · **Area:** `:chordpro` (`ChordProSyntax.matchDirective`, `startOfEnvironment` / `endOfEnvironment`, `hasSelectorSuffix`)

## Decision (taken by the user on 2026-09-22)
**B. A selected environment is shown as the environment it selects.** `{start_of_chorus-guitar}` is a chorus:
drawn as one, recalled by `{chorus}`. Campfire has no instrument or voice to match a selector against, and nothing
the user wrote is hidden. A **negated** selector (`{title-guitar!: X}`) holds, since nothing matches the selector it
negates, so it is read as the directive it is written on; today the `!` stops the name scanner and the line is shown
as lyrics.

## Symptom
1. `{start_of_chorus-guitar}` … `{end_of_chorus}` opens `SectionType.Custom("chorus-guitar")`: the song screen
   draws it under the heading "Chorus-guitar", not as the chorus card, and a later `{chorus}` does not recall it.
2. `{title-guitar!: Amazing Grace}` is drawn as a lyric line, braces included, and the song is not titled by it.

## Cause
`hasSelectorSuffix` returns false for every `start_of_` / `end_of_` name
(`chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt:311-315`), so
`startOfEnvironment("start_of_chorus-guitar")` (`:318-319`) is `"chorus-guitar"` and `sectionType` makes it custom
(`ChordProParser.kt:183-188`). `isDirectiveNameCharacter` (`ChordProSyntax.kt:170`) has no `!`, so `matchDirective`
stops at it and, finding neither `:` nor `}`, returns null.

## Fix
1. `ChordProSyntax.kt`:
   - `isDirectiveNameCharacter`: add `|| this == '!'`.
   - The sections an environment selector may be written on:
     ```kotlin
     /**
      * The environments a selector suffix is looked for on. A custom environment may have a dash in its own name
      * (`start_of_pre-chorus`), so only these are taken to have a selector after theirs.
      */
     private val selectableEnvironments = setOf("chorus", "verse", "bridge", "tab", "grid")
     ```
     (With 26 landed, `+ delegateEnvironments`.)
   - `startOfEnvironment` / `endOfEnvironment`: pass the environment through
     ```kotlin
     /**
      * [environment] without a selector suffix. Campfire has nothing to match a selector against, and an environment
      * is part of the song itself, so `{start_of_chorus-guitar}` is shown as the chorus it selects rather than left
      * out; its `{end_of_chorus}` carries no selector, as the spec has it.
      */
     private fun withoutSelector(environment: String): String {
         val base = environment.substringBeforeLast('-', missingDelimiterValue = environment)
         return if (base in selectableEnvironments) base else environment
     }
     ```
     i.e. `startShortNames[name] ?: name.takeIf { … }?.substring(START_OF_PREFIX.length)?.let(::withoutSelector)`
     and the same for `endOfEnvironment`. Apply it to the name after `removeSuffix("!")` too, so
     `{start_of_chorus-guitar!}` is a chorus.
   - In 19's `walkDirective`, right after the name is read: a negated selector names the directive it is on.
     ```kotlin
     val writtenName = trimmedLine.substring(nameStartIndex, nameEndIndex).lowercase()
     // A negated selector holds wherever nothing matches the selector, which in Campfire is everywhere, so the
     // directive is read as the one it is written on. A `!` anywhere else is not part of a directive name.
     val name = if ('!' in writtenName) negatedSelectorBase(writtenName) ?: return null else writtenName
     ```
     ```kotlin
     /** `title` for `title-guitar!`: the known directive a negated selector is written on, or null. */
     private fun negatedSelectorBase(name: String): String? {
         if (!name.endsWith('!') || name.count { it == '!' } != 1) return null
         val base = name.dropLast(1).substringBeforeLast('-', missingDelimiterValue = "")
         return base.takeIf { it.isNotEmpty() && isKnownName(it) }
     }
     ```
     `isKnownName` covers `start_of_x` / `end_of_x`; for those, `startOfEnvironment` then strips nothing more.
   - `hasSelectorSuffix`'s KDoc: "True for names such as `title-guitar`: a known directive with a (non-negated)
     selector suffix, which Campfire matches nothing against and drops. Environments are the exception, see
     [startOfEnvironment]."

The tag and language editors, the header inserter and the transposer need nothing: a negated directive now arrives
under its base name through `matchDirective`, and a selected environment through `startOfEnvironment`.

## Tests
`ChordProParserTest.kt`, new `an environment with a selector is the environment it selects`:
```kotlin
val blocks = ChordProParser.parse("{start_of_chorus-guitar}\n[C]la\n{end_of_chorus}\n{chorus}").blocks
assertEquals(SectionType.Chorus, (blocks[0] as ChordProBlock.Section).type)
assertEquals(ChordProBlock.ChorusRecall(null), blocks[1])
```
`{start_of_pre-chorus}` is still `Custom("pre-chorus")`; `{start_of_tab-guitar}` opens tablature.
New `a negated selector is read as the directive it is written on`: `{title-guitar!: X}` → title `X`;
`{title-guitar: Y}` still dropped (the existing `unknown and selector suffixed directives are ignored` stands);
`{tit!le: X}` and `{title!: X}` are lyrics (`matchDirective` returns null).
`ChordProSyntaxTest.kt`: `matchDirective("{tag-guitar!: Folk}") == Directive("tag", "Folk")`.

## Verify
A song with `{start_of_chorus-guitar}` shows the chorus card and a `{chorus}` after it recalls it.
`./gradlew :chordpro:desktopTest`.

## Docs
`chordpro/CLAUDE.md`, `ChordProParser` bullet, after "Unknown directives are ignored;": "a directive with a selector
suffix (`{title-guitar}`) is dropped, since there is nothing to match it against, and one with a negated selector
(`{title-guitar!}`) is read as the directive it is on for the same reason; an environment with a selector is the
environment it selects, since its lines are the song itself;".

## Touches
- `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProParserTest.kt`
- `chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntaxTest.kt`
- `chordpro/CLAUDE.md`

## Depends on
19 (`walkDirective`, `isKnownName`). Optional: 26 (`delegateEnvironments` in `selectableEnvironments`).
