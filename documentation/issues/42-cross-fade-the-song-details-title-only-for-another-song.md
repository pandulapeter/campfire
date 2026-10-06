# Cross-fade the song details title block only when the song is another one

**Kind:** ux  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/songDetails/SongDetailsScreen.kt
**Challenged:** amended — with the key, a cover set or removed from the song details (or a title or artist edited
there) would change in one frame, since today only the whole block's cross-fade animates it: the cover's presence and
the two texts now animate in place; the content lambda was confirmed to receive the newest song.

## Problem

`SongDetailsScreen.kt:428-444` (8ee010b36):
```kotlin
AnimatedContent(
    modifier = Modifier.titleTouchTarget(...).graphicsLayer { alpha = titleAlpha },
    targetState = currentSong,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
) { song -> ... CoverArtImage ... title ... artist ... SongHeaderNote(key/tempo/duration) }
```
`currentSong` is a `Song`, a data class carrying tags, languages, tempo, time, duration, cover, size and modification
time. Without a `contentKey`, `AnimatedContent` keys on the state itself, so any re-emission of the open song — a tag
added from Manage tags, a `{tempo}` set in Song defaults, a link added, the file rewritten by a sync run — runs a full
cross-fade: the identical title, artist, cover and key are drawn twice, one fading in and one out, and the bar visibly
dims and recovers; the cover is composed afresh too. It also defeats `SongHeaderNote`'s own in-place animation of the
value that changed. Root CLAUDE.md: data arriving is not animated over; the cross-fade is meant for paging.

Relied on: `AnimatedContent`'s implementation (animation 1.12.1, `AnimatedContent.kt` ~:1083-1115): when the new target
has the same `contentKey` as a visible one, it replaces that state in `currentlyVisible` and recomposes the existing
content with it — no enter/exit transition.

## Fix

1. Add `contentKey = { it?.fileName }` to that `AnimatedContent`, with a short comment saying the key is the file so
   that only paging cross-fades. Paging still cross-fades (another file name); an update of the same song recomposes in
   place, and `SongHeaderNote` animates only what changed.

   Verified against animation 1.12.1 (`AnimatedContent.kt` ~:1083-1115, and its KDoc: "there will be no animation when
   switching between targetStates that share the same key"): the visible state with the same key is replaced in
   `currentlyVisible`, `contentMap` is rebuilt because the new target is not in it, and the content lambda is invoked
   with the new `Song`, so the title, artist, cover and the key/tempo/duration line read the newest song. Everything
   else the lambda reads (`transpositions`, `capos`, `tempos`, `chordSpelling`, the feature switches) is the screen's
   current state, as today.

2. The whole-block cross-fade is today also what animates the user's own edits to what the block shows, and the key
   takes that away, so animate those in place (root CLAUDE.md: every change the user causes is animated):
   - **The cover** (Set / Change / Remove cover art from the song details menu or the About the song sheet): the
     cover's presence is decided by `song?.coverArtUrl?.let { … }` around the `AnimatedVisibility`, so with the key a
     cover added or removed appears or vanishes in one frame and the title jumps sideways by the cover's width. Make
     the presence part of the visibility, keeping the last address while it leaves:
     ```kotlin
     val coverUrl = song?.coverArtUrl
     var lastCoverUrl by remember { mutableStateOf(coverUrl) }
     if (coverUrl != null) lastCoverUrl = coverUrl
     AnimatedVisibility(visible = showsCoverInBar && coverUrl != null, enter = …, exit = …) {
         lastCoverUrl?.let { url -> Crossfade(targetState = url) { CoverArtImage(modifier = …, url = it) } }
     }
     ```
     (`remember` here is inside the keyed content, so another song starts from its own cover with no animation, and
     the first frame of a page is already right, which `AnimatedVisibility`'s initial state guarantees.) The
     `Crossfade` makes one address replaced by another fade rather than swap.
   - **The title and the artist** (Edit song details): wrap each `Text` in an `AnimatedContent(targetState = text,
     transitionSpec = { fadeIn() togetherWith fadeOut() })` that carries the `Modifier.weight(1f, fill = false)` the
     `Text` has now, the way `SongHeaderNote` already wraps its text. Paging composes them afresh under a new key, so
     they never animate on their own then.

A rename (Update file name) changes the key and cross-fades the block once with identical text — exactly what happens
today on every re-emission, so no regression; there is nothing in a `Song` that survives the rename to key on.

## Tests

None: composable behaviour.

## Manual check

Open a song, open About the song → Manage tags, add a tag and save: the app bar's title, artist and cover do not dim.
Set a tempo in Song defaults: only the tempo in the bar changes, in place. Set a cover from the menu, then remove it:
the cover grows in and shrinks away with the title sliding beside it. Edit the title in Edit song details: the title
cross-fades in place. Swipe to the next song: the title block still cross-fades, the cover with it.
