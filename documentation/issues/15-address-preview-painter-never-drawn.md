# Draw the typed address's preview painter while it loads, or it never finishes loading

**Kind:** bug  ·  **Severity:** medium  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/CoverArtSearchSheet.kt

## Problem
The "Web address" tab of the cover search sheet previews the typed address with a painter that is only ever drawn
once it has succeeded (CoverArtSearchSheet.kt:447-491):

```kotlin
val painter = rememberAsyncImagePainter(model = url?.let(::CoverArt), contentScale = ContentScale.Crop)
val painterState by painter.state.collectAsState()
AnimatedContent(targetState = when {
    url == null -> HINT
    painterState is AsyncImagePainter.State.Success -> IMAGE   // only here is `painter` drawn
    painterState is AsyncImagePainter.State.Error -> FAILED
    else -> LOADING
}) { … AddressPreviewContent.IMAGE -> Image(painter = painter, …) … }
```

`rememberAsyncImagePainter` without an explicit size (Coil 3.6, `contentScale` other than `None`) resolves the
request's size with a `DrawScopeSizeResolver`, which suspends until the painter's `onDraw` reports a size — Coil's own
KDoc warns that such a painter "will not finish loading if `AsyncImagePainter.onDraw` is not called". Here it is
not drawn in the `LOADING` branch, so the request waits for a size forever: the state stays `Loading`, the
`ContainedLoadingIndicator` spins indefinitely, and `CoverArtFetcher` (and therefore the download) is never even
reached. Size resolution happens before the memory cache lookup, so an address already shown elsewhere does not
help either.

Scenario: open a song with a cover → "Find cover art…" → "Web address" tab. The field is prefilled with the song's
address and the preview area shows a spinner that never turns into the image; typing any other valid image address
does the same. (The tiles of the Search tab use `AsyncImage`, which sizes from constraints, and are unaffected.)

## Fix
Make the painter drawn from the start, or give it a size it does not have to draw to learn:
1. Preferred: replace the painter + `Image` pair with an `AsyncImage` that is always composed inside the `Surface`,
   `Modifier.fillMaxSize()`, `contentScale = ContentScale.Crop`, and keep the state in a
   `var previewState by remember(url) { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }`
   fed by `onState = { previewState = it }`. Draw the indicator / the hint / the failure text as an overlay above it
   (the image is transparent until it succeeds), crossfaded on the same `AddressPreviewContent` derived from
   `previewState` — `IMAGE` then simply shows nothing on top.
2. Alternative with minimal change: build the model as an `ImageRequest` with an explicit size, e.g.
   `ImageRequest.Builder(LocalPlatformContext.current).data(CoverArt(url)).size(with(LocalDensity.current) { ADDRESS_PREVIEW_SIZE.roundToPx() }).build()`,
   so no draw is needed before the request starts. Remember it keyed on `url` and density.
Keep `ADDRESS_PREVIEW_DELAY` and the rest of the tab as they are. No CLAUDE.md change needed (the presentation
CLAUDE.md already describes the intended behaviour).

## Verification
First confirm the bug on any platform (`./gradlew :app:desktop:run` is quickest): open a song that has a cover
(the demo songs can be given one through the Search tab), open the cover sheet, switch to "Web address". Before the
fix: an endless spinner. After: the image appears; a 404 address shows the "could not load" text; clearing the field
shows the hint. If the preview already works before the fix, drop this plan.

## Conflicts
Only `CoverArtSearchSheet.kt`; plan 16 touches the Search tab of the same file (different composables).
