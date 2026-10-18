# Read the capo and key lines chord sites write, and never drop a metadata line whose value could not be read

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** `chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverter.kt`,
`chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/convert/ChordSheetConverterTest.kt`
**Challenged:** amended — the Roman-numeral pattern matched the empty string and a first word (`Capo: I don't use
one` → capo 1); it now has to be the whole value. `No capo` is decided (read, nothing written). `Tom` and `Tono` need
their colon. `Tempo: 120bpm` already works and is only pinned.

## Problem

`metadata` recognises only these labels:

```kotlin
private val metadata = Regex("^(Capo|Key|Hangnem|Tempo|Tempó|Time|Ütemmutató|Artist|Előadó|words and music by|by)\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
private val unlabelledMetadata = Regex("^(Capo|Key|Hangnem|Tempo|Time|words and music by)\\s+(.+)$", RegexOption.IGNORE_CASE)
```

and a capo's value is read as digits only:

```kotlin
return name to when (name) {
    "capo" -> Regex("[0-9]+").find(value)?.value.orEmpty()
```

while `extractHeader` omits every `METADATA` line, a blank value included:

```kotlin
when {
    value.isBlank() || name == TRANSPOSITION -> Unit
    …
}
omitted += index
```

Probed:

- `Capo: II` → the digit search finds nothing, the value is blank, the line is omitted: **the capo is lost without a
  trace** (Roman numerals are the convention on many Spanish, Italian and Hungarian sites).
- `Capo on 2nd fret`, `Key of G`, `No capo`, `Tom: G` (Portuguese), `Tono: Sol` (Spanish), `Tonart: G` (German),
  `Tonalité: Sol` (French), `Tonacja: G` (Polish) are kept as lyric lines.

## Fix

1. A `METADATA` line whose value comes out blank is not omitted: `extractHeader` leaves it out of `omitted` and
   `writeBody` writes it as an escaped lyric line (`Kind.METADATA -> output += inline(line, …)` only when its index is
   not omitted; simplest: `classify` returns `LYRIC` for such a line by checking the value in the `METADATA` branch).
2. Capo values: digits (as today), or a Roman numeral I–XII that is the **whole** value, optionally followed by a
   fret word (`II`, `IV fret`, `VII traste`): `^(XII|XI|X|IX|IV|V?I{1,3}|V)(\s+(fret|traste|tasto|bund|bundstab))?$`
   after upper-casing, never matching an empty value or a first word (`Capo: I don't use one` stays a lyric by step
   1). `No capo` / `Sin capo` / `Senza capo` / `Nincs capo` is a `METADATA` line that writes nothing and is omitted
   (no capo is the default, so `{capo: 0}` would add a line with no information).
3. New labels, with the colon optional only where the value has the field's own shape (as `unlabelledMetadata` does):
   - key: `Tom`, `Tono`, `Tonalidad`, `Tonart`, `Tonalité`, `Tonalità`, `Tonacja`, `Toonsoort`, and the English
     `Key of G` (`^Key of (chord)$`); `Tom` (a name) and `Tono` (a word) only with their colon, as the sites write them;
   - capo: `Capo on 2nd fret`, `Capo at 3`, `Capotraste: 2` (pt), `Cejilla: 2` (es), `Kapodaster: 2` (de),
     `Capodastre: 2` (fr), `Kapo 2` (hu/de);
   - tempo: `BPM: 120` (`Tempo: 120bpm` already reads as 120; pin it in the test below).
   A key value is accepted only through `isChordOrLatinName(ascii(value))`, as today.

## Tests

- `capo in Roman numerals and spelled-out capo lines become the header`: `Capo: II` → `{capo: 2}`; `Capo on 2nd
  fret` → `{capo: 2}`; `Cejilla: 3` → `{capo: 3}`.
- `key lines of the European chord sites become the header`: `Tom: G`, `Tono: Sol`, `Tonart: H`, `Key of G`, each
  → `{key: …}` (Latin and German spellings are converted later by the import's notation pass, so assert the raw
  value here: `{key: Sol}`).
- `a metadata line whose value cannot be read stays a line of text`: `Capo: see below` and `Capo: I don't use one`
  → the line is in the body, escaped, and no `{capo}` is written; `No capo` → neither the line nor a `{capo}`.
- `a lyric that starts with a key word is not metadata`: `Key of my heart` / `Tono y fuerza` stay lyrics (the
  unlabelled forms require the field's shape).
