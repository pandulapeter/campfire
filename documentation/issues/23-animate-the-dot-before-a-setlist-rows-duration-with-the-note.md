# Animate the dot in front of a setlist row's duration together with the key note it separates it from

**Kind:** animation  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt

**Challenged:** sound

## Problem

`SongListItem`'s second line is artist · note · duration. The note (the key, or "Lyrics only") is crossfaded and its
width animated by an `AnimatedContent` (`ListItems.kt:241-271` at 800ebde0b), but the dot in front of the duration —
shown only on setlist rows, the one caller passing `duration` (`SetlistsScreen.kt:611`) — is keyed on the note's
target state and appears or disappears in one frame (`ListItems.kt:272-279`):

```kotlin
if (displayedDuration != null) {
    if (song.artist.isNotBlank() || note != null) {
        Icon(
            modifier = Modifier.size(if (isNarrowSongCardWindow) NARROW_DOT_SIZE else DOT_SIZE),
            painter = painterResource(Res.drawable.ic_dot),
            contentDescription = null,
        )
    }
    Text(text = displayedDuration, …)
}
```

For a song with no artist and a `{duration}`, turning lyrics only mode on (note becomes null) or off removes or adds the
24dp (16dp narrow) dot at once while the note is still fading and shrinking, so the duration jumps sideways and then
slides. The user caused the change, so it should be narrated (root `CLAUDE.md`, "Nearly every change the user can see
is animated").

## Fix

Wrap the dot in an `AnimatedVisibility` that enters and leaves the way the note's box resizes — in the `Row` scope this
is the horizontal variant:

```kotlin
if (displayedDuration != null) {
    // Follows the note, which fades and closes up where it stands rather than leaving the line in one frame.
    AnimatedVisibility(
        visible = song.artist.isNotBlank() || note != null,
        enter = fadeIn() + expandHorizontally(),
        exit = fadeOut() + shrinkHorizontally(),
    ) {
        Icon(…unchanged…)
    }
    Text(…)
}
```

Use the default specs, which are what `AnimatedContent`'s fade and `SizeTransform` use, so the dot and the note close
up together. Check the imports (`AnimatedVisibility`, `expandHorizontally`, `shrinkHorizontally` are likely already
imported in this file; `fadeIn`/`fadeOut` are).

A song gaining or losing its artist is data arriving, not a change the user made on this row, but the same
`AnimatedVisibility` narrates it harmlessly; no first-frame issue arises since `AnimatedVisibility` starts in its
target state on first composition.

## Tests

None: a composable's animation.

## Manual check

Give a song no `{artist}` and a `{duration: 3:30}`, put it in a setlist, open the Setlists screen and toggle lyrics
only mode (song details' overflow or Settings). The dot in front of 3:30 fades and closes up with the key instead of
vanishing first and letting the duration jump.
