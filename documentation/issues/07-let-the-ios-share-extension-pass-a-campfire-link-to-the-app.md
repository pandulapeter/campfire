# Let the iOS share extension pass a Campfire share link to the app instead of refusing it as a web page

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** iOS
**Challenged:** amended — the Swift check copies `ShareLinks`' address regex (fragment required) instead of a bare prefix, so the web app's own address is still refused at once; the `texts.isEmpty` early return no longer skips the URL attachment; the URL item's other representations are named; the test is marked as a pin that passes today.
**Files:** `app/ios/iosApp/ShareExtension/ShareInboxWriter.swift`, `app/ios/iosApp/ShareExtension/Info.plist` (comment
only), `app/ios/CLAUDE.md`,
`presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/state/importing/PastedSongControllerTest.kt`

## Problem

A Campfire share link forwarded to Campfire through the iOS share sheet never reaches the app. Root `CLAUDE.md` says "A
share from another app (Android, the iOS share extension) is imported like a paste", and the song links plan (§9.2, "The
share extension: a shared text or URL that is a share link goes to the inbox as text, as today, and the app side
imports it as a link") meant the extension to pass it on. The app side does take it:
`PastedSongController.importSharedText` has

```kotlin
texts.size == 1 && sharedLinkIn(texts.single()) != null -> newSongFromText(texts.single(), payload.subject)
```

and Android imports the same text. But the extension decides first, in `ShareInboxWriter.write`:

```swift
texts = texts.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
if texts.isEmpty { return hasLink ? .link : .unsupported }
// A page shared by its address is never fetched. Some apps send its title as a text beside the address,
// which is one line, where a chord sheet is many.
if texts.allSatisfy(Self.isOnlyLinks) || hasLink && texts.allSatisfy({ !$0.trimmingCharacters(in: .whitespacesAndNewlines).contains(where: \.isNewline) }) {
    return .link
}
```

with

```swift
static func isOnlyLinks(_ text: String) -> Bool {
    let lines = text.components(separatedBy: .newlines).map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    return !lines.isEmpty && lines.allSatisfy { $0.range(of: #"^https?://\S+$"#, options: [.regularExpression, .caseInsensitive]) != nil }
}
```

So `https://campfire-songbook.com/s/#1.song.…` shared as text (a long press on it in Notes, WhatsApp, Mail, Messages'
Share, or an app that sends the address and its page title as two items) answers "Campfire does not open web pages.
Select the chord sheet's text and share that instead." and nothing is written to the inbox. The friend who received the
link on an iPhone gets a refusal that is wrong (Campfire does open this one), with the paste and the Universal Link the
only ways in. The `campfire://s/…` form is not `https?` and passes, so only the address people actually send is hit.

A share whose only attachment is `public.url` (Safari's Share on the landing page, Messages' link bubble) does not list
Campfire at all, since `NSExtensionActivationRule` in `ShareExtension/Info.plist` names no URL type ("public.url
conforms to none of these, so a page shared by its address alone does not list Campfire"). That one is a deliberate
trade, see Decided below.

## Fix

In `ShareInboxWriter.write`, before the refusal, take a Campfire link out of what was shared and write it as the share's
one text:

1. A static `isCampfireLink(_ text: String) -> Bool`: the trimmed text matches, case-sensitively as `ShareLinks` does,
   `^(?:https://campfire-songbook\.com/(?:s|app)/(?:\?[^#\s]*)?#|campfire://s/)\S+$`: `ShareLinks.kt`'s `address`
   regex (`:data:model`, `com.pandulapeter.campfire.data.model.share`) without its bare-payload alternative, documented
   as a hand copy to keep in step, as the landing page's is. Not a bare prefix match: `https://campfire-songbook.com/app/`
   with no fragment is the web app's own address, which a prefix would pass on to the inbox only for the app to answer
   `SharedLinkNotImported` later, where today the sheet refuses it at once. The payload's own grammar (version, ids,
   title) the app checks again; the extension only tells "maybe a Campfire link" from "another page". The `/` after the
   host keeps `campfire-songbook.com.example` out. A bare payload (`1.song.…`) is not `https?`, so it already passes.
2. In the `files.isEmpty` branch, after the blank texts are filtered out: if some text is a Campfire link and every
   other text is one line (the title that apps send beside an address, the same test the refusal uses), keep only that
   link (`texts = [link]`) and go on to write the share. The page title still arrives as the manifest's `subject`
   where the sender set `attributedTitle`; the app ignores the subject for a link anyway. Two Campfire links in one
   share: keep them all and let the app's existing "several texts are one import" path answer, or take the first;
   recommend the first, since the app imports one link per text and a second would become a `.txt` file.
3. For a `.link` attachment (`Attachment.link`, which today is only recorded as `hasLink`): carry its provider
   (`case link(NSItemProvider)`) and, where no text was a Campfire link, load it with
   `loadItem(forTypeIdentifier: UTType.url.identifier)`; the item may arrive as a `URL`, a `String`, or `Data`
   (`URL(dataRepresentation:relativeTo:)`), so take all three, and a non-file `URL`'s `absoluteString`. If it passes
   `isCampfireLink`, write it as the share's one text. This covers an app that sends the address as a URL beside a title
   text. Never fetch the URL. Do this before today's `if texts.isEmpty { return hasLink ? .link : .unsupported }`, which
   would otherwise refuse a URL attachment whose title text was blank or failed to load.
4. Everything else is unchanged: any other address, alone or beside a one-line title, is still `.link`.

Update `app/ios/CLAUDE.md`'s line "Files beat text in one share; a page's address (alone, or beside a one-line title) is
refused with a sentence." to say a Campfire share link (by its address form, a hand copy of `ShareLinks`' regex) is written
as the share's one text, the app deciding whether it is one, and any other page's address is refused.

**Decided (2026-10-21):** `public.url` is not added to `NSExtensionActivationRule`: Campfire stays out of every web
page's share sheet, and a link shared as a URL only still reaches the app by tapping it, the Universal Link, or by paste.

## Tests

The extension links no Kotlin and the iOS project has no test target, so the Swift check cannot be unit tested here.
Add to `PastedSongControllerTest` the case the extension now produces: `importSharedText(SharedTextPayload(texts =
listOf("https://campfire-songbook.com/s/#<payload>"), subject = "Some page title"))` imports the link (the existing
"a pasted or shared Campfire link is imported as the link" test covers the text alone; this adds the subject beside it),
so the app side the extension relies on stays as it is. It passes before and after this change (a pin, not a proof:
the existing test's subject equals the link's title, this one shows the subject is ignored).

