# Block pictures, media, and plain http in the iOS page reader with the macOS content rule list

**Challenged:** amended — the shared compile is a `CompletableDeferred` completed by WebKit's handler, not by the first reader's coroutine (a cancelled first read must not cancel or poison it for later reads); the Linux bullet's "Subresources are the page's, as on iOS" is corrected; the macOS rules name their Kotlin twin.

**Kind:** privacy / performance  ·  **Severity:** low  ·  **Platforms:** iOS
**Files:** `data/source/remote/implementation/src/iosMain/kotlin/com/pandulapeter/campfire/data/source/remote/implementation/page/WebKitBrowser.kt`,
`data/source/remote/implementation/src/desktopMain/native/macos/CampfireWebView.m` (a comment only),
`data/source/remote/implementation/CLAUDE.md` (Pages section, iOS bullet; Desktop section, Linux bullet)

## Problem

Every other page reader leaves pictures and media out: Android turns image loading off, Windows answers them with a
403, Linux turns auto-load of images off, and the macOS helper compiles a content rule list
(`desktopMain/native/macos/CampfireWebView.m`):

```objc
static NSString *const RULES =
    @"[{\"trigger\":{\"url-filter\":\".*\",\"resource-type\":[\"image\",\"media\"]},\"action\":{\"type\":\"block\"}},"
    @"{\"trigger\":{\"url-filter\":\"^http:\"},\"action\":{\"type\":\"block\"}},"
    @"{\"trigger\":{\"url-filter\":\"^ws:\"},\"action\":{\"type\":\"block\"}}]";
```

The iOS `WebKitBrowser.create` sets only a non-persistent data store:

```kotlin
            val configuration = WKWebViewConfiguration().apply { websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore() }
```

and its KDoc says pictures are "left to the page, as they are in Safari". So on the platform where data and memory
cost most, a page read for its text downloads every picture, ad image, and tracking pixel the page names, and the media
it preloads, over the user's mobile data; and the web content process holds them, on the phone where it was just made
to count a terminated process as a failed read (`a347fea00`). It is also the one reader that lets a page reach plain
`http:` and `ws:` subresources.

## Fix

Use the same rules on iOS, compiled once per process before the first view is built:

1. In `WebKitBrowser`'s companion, `private var ruleList: CompletableDeferred<WKContentRuleList?>? = null`, read and
   written only on the main thread (`create()` already runs on `Dispatchers.Main`, so no `Mutex` is needed). The first
   `create()` finds it null, sets it to a new `CompletableDeferred()`, and starts the compile:
   `WKContentRuleListStore.storeWithURL(<Caches>/CampfireWebViewRules)` (Caches, so it is never in the device backup
   and the system may purge it; it is recompiled then) and `compileContentRuleListForIdentifier("campfire", RULES)`,
   whose completion handler calls `complete(list)` (`complete(null)` with the error logged once where it answers an
   error or a nil list; the views then go without it, as on macOS, so a page is still read). The deferred is
   completed by WebKit's handler, never by a coroutine of the first read: wrapping the compile in that read's
   `suspendCancellableCoroutine` or `async` would let a read cancelled during the compile (the user leaving the import)
   cancel or never settle the list every later read awaits. `complete` is thread-safe, whichever thread the handler
   runs on.
2. `create()` awaits it (`ruleList!!.await()`, cancellable for its own read only) and adds it:
   `configuration.userContentController.addContentRuleList(it)`.
3. Copy `RULES` as a Kotlin raw string with a comment naming the macOS helper as its twin ("keep the two in step"), and
   add the same one-line note above `RULES` in `CampfireWebView.m`, naming `WebKitBrowser.kt`.
4. Update the KDoc of `WebKitBrowser` and the iOS bullet of the module `CLAUDE.md`: subresources are no longer all the
   page's; pictures, media, `http:`, and `ws:` are refused by the rule list. The Linux bullet's last sentence,
   "Subresources are the page's, as on iOS.", is then false; make it "Subresources other than pictures are the page's."
   (Plan 05, earlier in this lane, adds a sentence to the same bullet; keep it.)

The first read pays the compile (tens of ms, once per process); later reads reuse the list.

## Tests

None can be written: the module has no iOS test source set, and WebKit runs only in a simulator or device. Compile with
`./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64` and read a page with pictures on the simulator: its text arrives
as before (a rule list that blocked documents would make every read fail).
