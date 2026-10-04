# Take a cover address typed without its scheme as https, the way Manage links already does

**Kind:** bug (consistency)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProSyntax.kt,
chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProLinks.kt,
chordpro/src/commonMain/kotlin/com/pandulapeter/campfire/chordpro/ChordProCoverArt.kt,
chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProCoverArtTest.kt,
chordpro/src/commonTest/kotlin/com/pandulapeter/campfire/chordpro/ChordProLinksTest.kt,
presentation/src/commonMain/composeResources/values/strings.xml,
presentation/src/commonMain/composeResources/values-hu/strings.xml,
chordpro/CLAUDE.md, presentation/CLAUDE.md

**Challenged:** sound

## Problem
Live run: the cover art sheet's **Web address** tab does not take `picsum.photos/250` (`67_addr_noscheme.png`) — Save
stays disabled and the preview keeps showing the hint "Paste the address of an image on the web, starting with
https://" — while **Manage links** takes `example.com/tab` and writes `https://example.com/tab` (`62_link_noscheme.png`,
`63_link_saved.png`). The two address fields of the same song behave differently. At 800ebde0b:

```kotlin
// ChordProLinks.kt ~101
fun usableUrl(value: String): String? {
    val trimmed = value.trim()
    return ChordProSyntax.webUrl(trimmed) ?: trimmed.takeIf(::startsWithHost)?.let { ChordProSyntax.webUrl("https://$it") }
}

// ChordProCoverArt.kt ~53
fun usableUrl(value: String): String? = ChordProSyntax.webUrl(value)
```

`ChordProCoverArtTest` pins the old behaviour (`assertEquals(null, ChordProCoverArt.usableUrl("coverartarchive.org/release-group/abc/front-250"))`),
written with the iTunes source (c30370fdc), before links learnt scheme completion (bd35c71a4); nothing documents a
reason for covers to differ, and a copied image address from a browser's address bar commonly lacks the scheme.

## Fix
1. Move the completion into `ChordProSyntax` as one shared function, e.g.
   ```kotlin
   /**
    * [value] as a web address the way a field the user types one into reads it: [webUrl] of the trimmed text, or, for
    * text that starts with a host (and perhaps a port) but no scheme - how a browser's address bar shows most
    * addresses - that text as `https`. Anything else (`mailto:`, `me@…`, a mistyped `https:/`) is null rather than an
    * `https` address naming nothing the user meant.
    */
   fun typedWebUrl(value: String): String?
   ```
   carrying `startsWithHost` with it (moved verbatim from `ChordProLinks`, comment included).
2. `ChordProLinks.usableUrl(value) = ChordProSyntax.typedWebUrl(value)` and
   `ChordProCoverArt.usableUrl(value) = ChordProSyntax.typedWebUrl(value)`; update `ChordProCoverArt.usableUrl`'s KDoc
   (an address typed without its scheme is taken as `https`). `ChordProCoverArt.set` keeps using `webUrl`: the sheet
   hands it the completed address.
3. Strings, both files: `cover_art_address_hint` no longer needs "starting with https://" —
   en `Paste the address of an image on the web`, hu `Illeszd be egy weben elérhető kép címét`.
4. Docs: `chordpro/CLAUDE.md`'s `ChordProCoverArt` entry — add that `usableUrl` completes a scheme-less address the
   way `ChordProLinks.usableUrl` does (and point the links entry at the shared `ChordProSyntax.typedWebUrl`);
   `presentation/CLAUDE.md` (`**Web address**: a field for any `http`/`https` address (`ChordProCoverArt.usableUrl`
   decides what counts)`) — add "typed with or without its `https://`".

## Tests
- `ChordProCoverArtTest`: replace the `null` assertion for `coverartarchive.org/release-group/abc/front-250` with
  `"https://coverartarchive.org/release-group/abc/front-250"`; add `picsum.photos/250` → `https://picsum.photos/250`;
  keep `null` for `https://example.com/a b.jpg`, `https://`, and add `null` for `mailto:me@example.com` and `me@host`.
- `ChordProLinksTest`: its existing scheme-completion cases must still pass unchanged (they now exercise the shared
  function).
Run `./gradlew :chordpro:desktopTest :presentation:desktopTest`.

## Manual check
Song details → Change cover art → Web address → type `picsum.photos/250`: after the 500 ms pause the preview loads and
Save is enabled; Save writes `{meta: cover https://picsum.photos/250}`. Type `mailto:x@y.z`: Save stays disabled and
the hint shows (now without "starting with https://"), in English and Hungarian.
