# Name German scales so that they read right after a key: "A-Moll natürlich", not "A-natürlich Moll"

**Challenged:** amended — the two family names keep "Modi von harmonisch / melodisch Moll", German jazz theory's own
term for the parent scale, since a family name never follows a key; it also names the one search the new order loses.

**Kind:** translation  ·  **Severity:** low  ·  **Platforms:** all (German UI)
**Files:** `presentation/src/commonMain/composeResources/values-de/strings.xml`,
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/scales/CLAUDE.md`

## Problem

German joins a key and a scale with a hyphen (`values-de/strings.xml`):

```xml
<string name="scales_sheet_title">%1$s-%2$s</string>
```

`scaleTitle` (`ui/scales/ScaleTexts.kt`) fills it with the key and the name string as is:

```kotlin
internal fun scaleTitle(scale: Scale, rootName: String, notation: ChordNotation) =
    stringResource(Res.string.scales_sheet_title, shownChordName(rootName, notation), scaleTitleName(scale))
```

and `standaloneScaleName` only capitalizes the first letter of the same string where a name stands alone. The
`scales/CLAUDE.md` rule is "the name strings hold the form a title uses". That form works for German names that are a
noun or a capitalized mode (`A-Dur`, `A-Dorisch`, `A-Moll-Pentatonik`), but eleven German names start with a lowercase
adjective, so the title glues the adjective to the key and puts it before the noun, which no German musician writes. The
live run's song details "Tonleitern" section for House of the Rising Sun shows `A-natürlich Moll` and
`A-harmonisch Moll`; the Scales tab and the scale sheet show the same.

```xml
<string name="scale_natural_minor">natürlich Moll</string>
<string name="scale_harmonic_minor">harmonisch Moll</string>
<string name="scale_ultralocrian">ultralokrisch</string>
<string name="scale_melodic_minor">melodisch Moll</string>
<string name="scale_altered">alteriert</string>
<string name="scale_harmonic_major">harmonisch Dur</string>
<string name="scale_double_harmonic">doppelt harmonisch</string>
<string name="scale_augmented">übermäßig</string>
<string name="scale_diminished">vermindert (Ganzton–Halbton)</string>
<string name="scale_dominant_diminished">dominant-vermindert (Halbton–Ganzton)</string>
<string name="scale_chromatic">chromatisch</string>
```

Hungarian (`C-dúr`, `A-természetes moll`, lowercase names, every title hyphenated) is the user's decision of the 38th
review and is correct Hungarian; it stays. English, French, Spanish, and Romanian join with a space and are not touched.

## Fix

Strings only, `values-de/strings.xml`. The rule German follows, after the hyphen as in `A-Dur`: the name starts with a
capital, and the minor and major forms name the noun first, as German theory books write the trio ("a-Moll natürlich,
harmonisch, melodisch"). The same string still reads right standing alone ("Moll harmonisch" is how the three are listed
side by side).

| Key | Now | New | Title on A |
|---|---|---|---|
| `scale_natural_minor` | natürlich Moll | Moll natürlich | A-Moll natürlich |
| `scale_harmonic_minor` | harmonisch Moll | Moll harmonisch | A-Moll harmonisch |
| `scale_melodic_minor` | melodisch Moll | Moll melodisch | A-Moll melodisch |
| `scale_harmonic_major` | harmonisch Dur | Dur harmonisch | A-Dur harmonisch |
| `scale_double_harmonic` | doppelt harmonisch | Doppelharmonisch | A-Doppelharmonisch |
| `scale_ultralocrian` | ultralokrisch | Ultralokrisch | A-Ultralokrisch |
| `scale_altered` | alteriert | Alteriert | A-Alteriert |
| `scale_augmented` | übermäßig | Übermäßig | A-Übermäßig |
| `scale_diminished` | vermindert (Ganzton–Halbton) | Vermindert (Ganzton–Halbton) | A-Vermindert (Ganzton–Halbton) |
| `scale_dominant_diminished` | dominant-vermindert (Halbton–Ganzton) | Dominant-vermindert (Halbton–Ganzton) | A-Dominant-vermindert (Halbton–Ganzton) |
| `scale_chromatic` | chromatisch | Chromatisch | A-Chromatisch |

The families keep their names: `scale_family_harmonic_minor_modes` "Modi von harmonisch Moll" and
`scale_family_melodic_minor_modes` "Modi von melodisch Moll" are how German jazz pedagogy names those parents ("die
Modi von melodisch Moll", "HM5"), and a family name stands alone, never after a key, so nothing glues to it. The same
holds for `scale_bebop_melodic_minor` / `scale_bebop_harmonic_minor` ("Bebop-melodisch-Moll"), one hyphenated compound
that reads right after a key ("A-Bebop-melodisch-Moll").

`scale_aliases_hungarian_minor` ("Gypsy Minor, Doppelt harmonisch Moll") stays: an alias is an independent name, read
in a list, not after a key.

Search is a substring of the folded name (`scaleLibraryResults`, `ScaleLibrary.kt`), and a leading note is split off
(`a-moll …`), so "a-moll harmonisch" now finds A harmonic minor; "harmonisch" alone still finds both harmonic scales.
The jazz word order is no longer a substring of a name ("harmonisch moll" now finds only Hungarian minor, through its
alias "Doppelt harmonisch Moll"). Accept it: adding the old order as an alias would print it under the title on the scale
sheet (`ScaleSheet`'s aliases line, "Moll natürlich" over "Natürlich Moll"), and "harmonisch" or "melodisch" alone still
finds the scale.

`ui/screens/scales/CLAUDE.md`, the bullet "**The name strings hold the form a title uses**": after `"C-dúr", the
Hungarian title hyphenated)` add "; German names start with a capital, the noun first where an adjective qualifies it
(`A-Moll harmonisch`)". The German root stays uppercase (`A-Moll`, not the classical `a-Moll`): chord and key names
follow the chord notation everywhere in the app.

## Tests

None: this changes translations only, and a test asserting a German title would copy the string it checks. The UI test
`ScaleSheetTitleTest` (English, the longest name) keeps guarding that a long title is shown whole; the German names
grow by no character.
