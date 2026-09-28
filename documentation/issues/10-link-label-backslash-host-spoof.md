# Cut a link's host at a backslash too, so a chip cannot name a site the link does not open

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongLyrics.kt, presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/LinkLabelTest.kt

## Problem
A link chip in the song details header is labelled only by its host (`linkLabel`, `SongLyrics.kt:466-475`), and
that label is all the user sees before tapping it:

```kotlin
internal fun linkLabel(url: String): String = url
    .substringAfter("://")
    .substringBefore('/')
    .substringBefore('?')
    .substringBefore('#')
    .substringAfterLast('@')
    ...
```

A backslash does not end the authority here, but every browser (the WHATWG URL parser, which Chrome, Custom Tabs,
Safari, Firefox and `open` / `xdg-open` all hand the address to) reads `\` as `/` in an `http`/`https` URL.
`ChordProSyntax.webUrl` (`chordpro/.../ChordProSyntax.kt:342-345`) accepts a backslash, since it only rejects
whitespace, so a song file can carry:

```
{meta: link https://evil.example\@youtube.com/watch?v=abc}
```

- `linkLabel` → `evil.example\@youtube.com` → after the last `@` → chip reads **youtube.com**.
- The browser opens host **evil.example** (path `/@youtube.com/watch`).

Song files arrive from other people by design (import of a shared archive, a shared sync folder, "open with"), so a
file can present a link to a trusted site that opens another one. The `?` and `#` cases were already thought of
(they are cut before the `@`); the backslash is the one the browsers treat as a separator that was not.

## Fix
1. In `linkLabel`, cut the authority at the first `/`, `\`, `?` or `#`, whichever comes first, before taking what
   follows the last `@` — e.g. `.substringAfter("://").let { rest -> rest.substring(0, rest.indexOfFirst { it == '/' || it == '\\' || it == '?' || it == '#' }.takeIf { it >= 0 } ?: rest.length) }`,
   then the existing `@`, `:`, lowercase and `www.` steps. Update its KDoc to say the authority ends where a browser
   ends it.
2. Add a case to `LinkLabelTest`: `assertEquals("evil.example", linkLabel("https://evil.example\\@youtube.com/watch"))`,
   and one for `https://example.com\\path` → `example.com`.
3. No CLAUDE.md change needed (the root CLAUDE.md's "a chip named by its host" still holds, now truthfully).

Optional, not required: `ChordProSyntax.webUrl` could refuse a backslash in the authority altogether; that would
also affect covers (`ChordProCoverArt`), so keep it out of this change unless the user wants it.

## Verification
`./gradlew :presentation:desktopTest` (the new `LinkLabelTest` cases). Manually: put the line above into a song,
open it in the details screen — the chip reads `evil.example`.

## Conflicts
`SongLyrics.kt` is large and shared with the song details UI work; the change is confined to `linkLabel`.

## Open decision
Whether to also reject such addresses in `ChordProSyntax.webUrl` (affects cover addresses too). Recommended default:
fix the label only.
