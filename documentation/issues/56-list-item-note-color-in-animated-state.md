# 56 · Every song row restarts an `AnimatedContent` transition on every frame of a theme cross-fade

**Severity:** medium (jank and ghosting on theme change with a long list) · **Area:** `:presentation` (`ListItems.kt`)

`ListItems.kt:151–166` builds `SongListItemNote(color = MaterialTheme.colorScheme.primary …)`, a data class whose
equality includes the color, and :205–208 uses it as `AnimatedContent`'s `targetState`. `CampfireTheme` lerps the
scheme every frame of a change, so each visible row retargets its crossfade per frame and stacks outgoing copies of
the key label until they finish.

## Fix

Take the color out of the state: `SongListItemNote(text, description, isEmphasized: Boolean)` and resolve
`if (currentNote.isEmphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`
inside the `AnimatedContent` content lambda, where a color read is just a recomposition of the `Text`. The
crossfade then only runs when the text changes (a transposition renames the key, lyrics-only mode removes it), which
is what the comment above it promises.
