# 39 · The language setting names each language in the current language, so someone stuck in the wrong one cannot find their own

**Severity:** minor (all platforms. Unlikely but hard to get out of: an English speaker whose app is in Hungarian
sees "Rendszer / Angol / Magyar" and nothing reading "English") · **Area:** `:presentation`
(`composeResources/values*/strings.xml`, `ui/screens/settings/SettingsScreen.kt`)

## Decision (taken by the user on 2026-09-22)
**A. Each language is named in its own language**: "English" and "Magyar" in both UI languages; only "System" is
translated. It is what Android's, iOS's and most apps' own pickers do, and it is a strings-only change. Not through
`languageDisplayName(code, code)` (the song languages' platform lookup): `java.util.Locale` answers "magyar" in lower
case, and the web's `Intl.DisplayNames` and iOS differ in capitalisation, so the list would look different per
platform for no gain over two fixed words.

## Symptom
With the app in Hungarian: Beállítások → Általános → Nyelv lists "Rendszer", "Angol", "Magyar". A reader who does not
know Hungarian has no word to recognise. (The other direction works only because "English"/"Hungarian" is widely
understood.)

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsScreen.kt:383-385`
reads `settings_user_interface_language_english` / `_hungarian`, which are translated per UI language
(`values-hu/strings.xml:187` "Angol", `values/strings.xml:188` "Hungarian").

## Fix
Strings only; the code, keys and their use stay as they are.

- `values/strings.xml:188`: `<string name="settings_user_interface_language_hungarian">Magyar</string>`
- `values-hu/strings.xml:187`: `<string name="settings_user_interface_language_english">English</string>`

Put a one-line XML comment above the two language entries in both files, so a later translation does not "fix" them
back: `<!-- Each language is named in itself, so that it can be found by whoever reads it whatever the app is set to. -->`
And extend the comment above the list in `SettingsScreen.kt:377-378` with: "Each language is named in itself rather
than in the language the app is in, so that somebody who ended up in one they cannot read can still find their own."

## Tests
None (UI).

## Verify
1. App in English: "System", "English", "Magyar". App in Hungarian: "Rendszer", "English", "Magyar".
2. Switching between them from either side works with one tap on a recognisable word.
3. Compile: `:presentation:compileKotlinDesktop`.

## Docs
None beyond the in-file comments.

## Touches
- `presentation/src/commonMain/composeResources/values/strings.xml`
- `presentation/src/commonMain/composeResources/values-hu/strings.xml`
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/settings/SettingsScreen.kt` (comment only)

## Depends on
Nothing. 31 and 38 edit other lines of the strings files.
