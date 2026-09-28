# Open a link with unencoded characters on iOS 15 and 16 instead of silently doing nothing

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** ios
**Files:** app/ios/src/iosMain/kotlin/com/pandulapeter/campfire/CampfireViewController.kt, app/ios/CLAUDE.md

## Problem
The iOS URL opener (`CampfireViewController.kt:97-101`) is what a link chip in the song details header calls
(`SongDetailsScreen.kt:474`, `onOpenLink = urlOpener`):

```kotlin
private fun openUrl(url: String) {
    NSURL.URLWithString(url)?.let { nsUrl -> UIApplication.sharedApplication.openURL(nsUrl, ...) }
}
```

`ChordProSyntax.webUrl` (and so `{meta: link …}` and `ChordProLinks.usableUrl`) accepts any `http`/`https` address
without whitespace, including characters that are not legal in a URL as written: non-ASCII letters
(`https://hu.wikipedia.org/wiki/Tükörfúrógép`, typed by hand or written into a file), `|`, `^`, `"`, `<`, `>`, `{`,
`}`, `` ` `` and `\`. Before iOS 17, `+[NSURL URLWithString:]` answers `nil` for any of those (iOS 17+ percent-encodes
them for apps built with its SDK). The app's deployment target is iOS 15.3 (`project.pbxproj`,
`IPHONEOS_DEPLOYMENT_TARGET = 15.3`), so on an iOS 15 or 16 device tapping such a chip does nothing at all — no
browser, no message. Every other platform opens it (Android's `Uri.parse` and the browsers are lenient; the desktop
falls back to `open`/`xdg-open` and otherwise says `Message.LinkNotOpened`).

## Fix
1. In `openUrl`, when `NSURL.URLWithString(url)` is null, try once more with the address percent-encoded:
   `(url as NSString).stringByAddingPercentEncodingWithAllowedCharacters(allowed)`, where `allowed` is a
   `NSMutableCharacterSet` built from `NSCharacterSet.URLQueryAllowedCharacterSet` with `"#%"` added (so the
   structure — `/ ? & = : @` — and any `%XX` already in the address survive, and only the illegal characters and
   non-ASCII bytes are escaped). Open that if it parses.
2. KDoc on `openUrl` saying why (iOS 15–16's `URLWithString` refuses what iOS 17 encodes itself). Note in
   `app/ios/CLAUDE.md`'s first bullet ("Links open through `UIApplication.openURL`") that an address NSURL refuses is
   percent-encoded first.
3. A non-ASCII *host* (an IDN) comes out percent-encoded rather than punycoded, which may still not resolve; that is
   acceptable as a fallback for a case that did nothing before. Do not change `:chordpro`: the file keeps the address
   as the user wrote it.

## Verification
iOS 16 simulator (`xcrun simctl` runtime 16.x): add a song line `{meta: link https://hu.wikipedia.org/wiki/Tükörfúrógép}`,
open the song, tap the `hu.wikipedia.org` chip → Safari opens the article. Repeat on an iOS 17+ simulator to confirm
nothing changed there. `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` for the compile check.

## Conflicts
`CampfireViewController.kt` may be touched by an iOS-platform lane (app icon, interface style); the change is
confined to `openUrl`.
