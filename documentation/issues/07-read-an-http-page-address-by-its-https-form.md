# Read an http page address by its https form instead of failing it

**Kind:** bug (UX)  ·  **Severity:** low  ·  **Platforms:** Android, iOS, desktop
**Decided:** option (a), the https form, by the owner (2026-11-23).
**Files:** `data/source/remote/implementation/src/commonMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/page/OffscreenPageSource.kt`,
`data/source/remote/implementation/src/commonTest/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/page/OffscreenPageSourceTest.kt`,
`data/source/remote/implementation/CLAUDE.md` (Pages section); for option (b) instead:
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SharedTextImport.kt`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/platform/SharedTextImportTest.kt`,
`app/ios/iosApp/ShareExtension/ShareInboxWriter.swift`


## Problem

What counts as a page address takes `http` too. `SharedTextImport.kt:105`:

```kotlin
private val PAGE_ADDRESS = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
```

and the iOS share extension (`ShareInboxWriter.swift`, `isWebAddress`):

```swift
text.trimmingCharacters(in: .whitespacesAndNewlines).range(of: #"^https?://\S+$"#, ...)
```

So a pasted or shared `http://…` address is queued as `ImportSource.Page`, the import screen says it is reading the page,
and `OffscreenPageSource.load` refuses it without opening anything:

```kotlin
        if (!isPublicHttpsAddress(trimmed)) return LoadedPage.NotRead(LoadedPage.NotRead.Reason.UNREADABLE)
```

The user sees "Reading the page…" and then that the page could not be read, which is untrue of a page the browser
showed them a moment ago (most `http` addresses redirect to `https` anyway). Old forum and chord-site links, and links
typed by hand, still carry `http://`.

## Fix

**(a) Recommended: try the `https` form.** In `OffscreenPageSource.load`, before the check, replace a leading `http://`
(case-insensitively) with `https://`. Nothing plain-text ever leaves the device, so the privacy rule ("public `https`
hosts only") holds unchanged; the address the user gave is only read over TLS. A site with no TLS fails to connect and
ends as it does today (unreachable or unreadable). One place, every platform, the share extension included.

**(b) Leave `http` addresses out** of `PAGE_ADDRESS` and `isWebAddress` (`https://` only). A pasted `http` address then
gets the earlier "only links" message, and a lone `http` URL shared on iOS is unsupported by the extension. Honest,
but it refuses pages (a) would read.

For (a), add to the Pages section of the module `CLAUDE.md`: "an `http` address is read by its `https` form".

## Tests

`OffscreenPageSourceTest`: `an address that is not a public https one is never opened` lists
`"http://chords.example.com/a"`; replace it with `"http://localhost/a"` (still refused after the upgrade) and add a test
that `http://chords.example.com/a` opens the fake browser with `https://chords.example.com/a`.
For (b): `SharedTextImportTest` asserts `pageAddressIn("http://example.com/song")` is null.
