# Count songs in `hoistedTimings` the way `prettify` does, ignoring a `{new_song}` inside an open environment

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Challenged:** sound
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProPrettifier.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProPrettifierTest.kt`

## Problem

`hoistedTimings` returns, per song index, the body lines `prettify` moves into that song's header, and `prettify`
looks them up by its own song counter (`hoisted[song]`). The two count songs differently. `hoistedTimings` advances
on every `{new_song}` / `{ns}` outside a *delegated* environment (`ChordProPrettifier.kt:252-257` at 1c52e5347):

```kotlin
if (directive?.name == "new_song" || directive?.name == "ns") {
    finishSong()
    song++
    isHeader = true
    return@forEachIndexed
}
```

while `prettify` copies every line inside *any* open environment raw and never reaches its `isNewSong` branch for it
(`ChordProPrettifier.kt:101-112`):

```kotlin
if (environments.isNotEmpty()) {
    output += rawLine
    …
    continue
}
```

So a `{new_song}` inside an open chorus advances one counter and not the other, and every song after it gets the
hoisted lines of the song before. Verified at 1c52e5347:
`{title: A}\n{start_of_chorus}\nla\n{new_song}\n{title: B}\nlb\n{tempo: 100}\n{end_of_chorus}\n{new_song}\n{title: C}\n\nlc`
prettifies to `…{end_of_chorus}\n\n{new_song}\n\n{title: C}\n{tempo: 100}\n\nlc\n` — `{tempo: 100}` stays inside the
chorus *and* is copied into song C's header, which never had it — and prettifying that again adds a second
`{tempo: 100}` to C (not idempotent). An import splits a file into songs before it prettifies them, so this reaches only the editor's Prettify on a text
that holds several songs, where it adds a line that changes a later song's tempo.

The KDoc of `hoistedTimings` also reads ambiguously: "the first readable `{tempo}` and `{time}` of the body of a song
whose header has no line of that kind, empty ones included" — only readable body values are recorded (`isReadable`);
"empty ones included" refers to the *header* lines, an empty `{tempo}` there counting as a line of that kind (the
parser then reads the body's first tempo as a change, see "an empty header tempo opens at none" in
`ChordProParserTest`).

## Fix

Make `hoistedTimings` mirror `prettify`'s walk: handle `{new_song}` / `{ns}` only when no environment is open
(`environments.isEmpty()`), and otherwise treat it like any other line inside the environment (it is not a tempo or a
time, so it is simply skipped). Move the check after the environment bookkeeping or guard it with
`environments.isEmpty() &&`. The `{tempo: 100}` inside the chorus then belongs to song A, whose header has no tempo,
and is hoisted into A's header — which is what `ChordProParser` reads it as when the whole text is parsed, since the
parser ignores `{new_song}` anywhere ("Splitting is ChordProSplitter's job"). (`ChordProSplitter` does split at a
`{new_song}` inside a non-delegated environment; that disagreement about malformed input is outside this plan, which
only makes Prettify consistent with itself.)

Reword the KDoc: "…the first readable `{tempo}` and `{time}` of the body of a song whose header has no line of that
kind — an empty header line counting as one — …", and add that songs are counted as `prettify` counts them, a
`{new_song}` inside an open environment being that environment's text.

## Tests

In `ChordProPrettifierTest`, for
`{title: A}\n{start_of_chorus}\nla\n{new_song}\n{title: B}\nlb\n{tempo: 100}\n{end_of_chorus}\n{new_song}\n{title: C}\n\nlc`:
`prettify(prettify(x)) == prettify(x)`; the output has exactly one `{tempo: 100}` line; the part after the last
`{new_song}` contains no `{tempo`. Add the input to the existing
`formatting is idempotent for complete and unfinished documents` list as well. The existing
`the song's own tempo and time in the body move into the header` keeps passing.

## Manual check

None: malformed multi-song text only reaches this through the editor's Prettify, which the tests cover through
`prettify`.
