# 07 · A setlist with a longer title loses its menu on a phone: it cannot be renamed, archived, exported or deleted

**Severity:** wrong behaviour / stuck state (all platforms, in practice phones and narrow windows; likely: the app's
own dialog accepts titles of 60 characters and a 360dp phone runs out of room at about 37, and imported or synced
setlists have no limit at all) · **Area:** `:presentation` (`components/ListItems.kt`: `SectionHeader`)

## Symptom
1. Android phone (or a desktop window about 360dp wide). Setlists tab → New setlist → title
   `Friday evening campfire songs for the kids` (43 characters) → Create.
2. Look at the setlist's header pill.

The title wraps onto a second line and the ⋮ button at the end of the pill is gone (squashed to a few pixels or to
nothing). Tapping where it should be scrolls the list to the header instead. Everything behind that menu — Edit,
Song assignments, Duplicate, Archive, Export, Delete — is unreachable for that setlist, and since Edit is the only
way to rename it, nothing inside the app can make the title short again. A setlist imported from an archive or synced
down with a title of any length is in the same state at every window width it overflows.

With a title just short of wrapping the button is partly squeezed instead: narrower than its 32dp, its icon clipped.

## Cause
`presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt:440-473`:
the pill is a `Row` of the archive icon, the title and the action, and the title is an ordinary (weightless) child:

```kotlin
Row(
    modifier = Modifier.padding(start = SECTION_HEADER_PADDING),
    verticalAlignment = Alignment.CenterVertically
) {
    ...
    Text(
        modifier = Modifier.padding(
            end = if (action == null) SECTION_HEADER_PADDING else 4.dp,
            top = 6.dp,
            bottom = 6.dp,
        ),
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    action?.invoke()
}
```

A `Row` measures its weightless children one after another, each against whatever width the previous ones left.
The title is measured before the action with the whole width of the pill, wraps to fill all of it, and the action is
then measured with a maximum width of 0: `SectionHeaderAction`'s `Modifier.size(32.dp)` (`ListItems.kt:487-488`) is
coerced into those constraints, so the `IconButton` inside `SetlistActionsMenu` is laid out 0px wide and cannot be
hit. The width available is the grid cell minus 8dp of header padding, 12dp of pill padding and, on both list
screens, the fast scroller's 24dp column — about 300dp for the text on a 360dp phone. The setlist title is only capped
in `SetlistDetailsDialog` (`Dialogs.kt`, `MAX_TITLE_LENGTH = 60`), which already exceeds that, and not at all for
files that arrive by import or sync.

The same pill is the songs screen's artist header (no action there, so nothing is lost, but an imported song with a
paragraph for an artist gets a sticky pill several lines tall that covers the rows under it).

## Fix
In `SectionHeader`, give the title the leftover width instead of the first claim on it, and cap it:

```kotlin
Text(
    modifier = Modifier
        // Measured after the action rather than before it, so that a title of any length leaves the action its
        // room: a weightless title took the whole pill and laid the menu out with no width at all, which for a
        // setlist is the only way to rename it.
        .weight(1f, fill = false)
        .padding(
            end = if (action == null) SECTION_HEADER_PADDING else 4.dp,
            top = 6.dp,
            bottom = 6.dp,
        ),
    text = text,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    // A sticky header stays over the rows while its section scrolls under it, and a name of any length from an
    // imported file must not be able to cover them.
    maxLines = SECTION_HEADER_MAX_LINES,
    overflow = TextOverflow.Ellipsis,
)
```

with `private const val SECTION_HEADER_MAX_LINES = 2` next to the other `SECTION_HEADER_*` constants (KDoc saying
what it is for). `fill = false` keeps the pill hugging a short title exactly as it does now; only a title that would
not fit changes. `TextOverflow` needs importing in `ListItems.kt` if it is not already (it is used by
`ListItemHeadline`, so it is).

Do **not**:
- make the pill `fillMaxWidth()` to get the room — it is a label sized to its text on purpose;
- move the action before the title or into a separate row;
- cap the title in the data layer; the fix belongs where it is drawn, and the full title is still shown by the edit
  dialog and the song picker's sheet header.

## Tests
None (UI is untested).

## Verify
1. Android phone (`./gradlew :app:android:assembleDebug`), setlists tab: create setlists titled with 10, 40 and 60
   characters. Before: the 40 and 60 character ones have no ⋮ button. After: all three have it at the end of the
   pill; the long titles wrap to two lines, the 60 character one ends in an ellipsis only if it needs a third line.
   Open each menu, rename the 60 character one to something short via Edit: the pill shrinks back to one line.
2. Archive the long one with "Show archived" on: the archive icon fades in before the title and the ⋮ stays.
3. Desktop (`./gradlew :app:desktop:run`), import a `.setlist.json` whose `title` is 500 characters: its pill is two
   lines with an ellipsis, the menu works, rows scroll under the pinned pill normally.
4. Songs screen sorted by artist, a song with a 300 character `{artist}`: its header pill is two lines at most.
5. Short titles and artist names look exactly as before (pill width hugs the text).
6. Compile checks: `./gradlew :app:ios:linkDebugFrameworkIosSimulatorArm64 :app:web:wasmJsBrowserDistribution`.

## Docs
`presentation/CLAUDE.md`, the `ui/components/CampfireTopAppBar.kt` bullet, after "The section headers of the lists are
raised pills (`SectionHeader`) for the same reason.": add "The pill's text is laid out after its action and runs to
two lines at most, so a long setlist title can neither squeeze the setlist's menu out of the pill nor grow a sticky
header over the rows."

## Touches
- `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/ListItems.kt`
- `presentation/CLAUDE.md`

## Depends on
Nothing. 08 edits `ListPlaceholder`'s KDoc in the same file; schedule the two one after another.
