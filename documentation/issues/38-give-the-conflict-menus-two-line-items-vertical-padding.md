# Give the Keep both / Replace menu's two-line items vertical padding, as the notation menu's are given

**Kind:** bug (layout)  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/screens/importReport/ConflictResolutionDropdown.kt`

## Problem

Each option of the import review's conflict menu is a title and a description that wraps to two lines
(`ConflictResolutionDropdown.kt`, at 72d48deb7):

```kotlin
DropdownMenuItem(
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(option.label))
            Text(
                text = stringResource(option.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    },
    …
)
```

Material's `DropdownMenuItem` has no vertical content padding (it is laid out for one line at its minimum height), so
three lines fill the item edge to edge: "Replace" sits flush under the last line of Keep both's description, and the
menu reads as one block of text (live run: `83-dropdown.png`, `95-dd-en.png`, `99-entry-dd.png`; English and Hungarian).

The settings screen's notation menu solved the same thing (`ui/screens/settings/ChordsSection.kt`):

```kotlin
// A menu item is as tight as a one-line one, and these descriptions run to several lines.
Column(
    modifier = Modifier.padding(vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
) { … }
```

## Fix

Give the `Column` in `ConflictResolutionDropdown` the same `Modifier.padding(vertical = 8.dp)`, with the same one-line
comment. The leading check mark is centered by the item, so it stays beside the title block. The "Show the one you
have" item below the divider is one line and keeps Material's own height.

## Tests

None: a layout constant.

## Manual check

Import an archive with a song whose name the library already has. On the review, open the **Keep both** button of the
Names already taken row and of its "For all of them" header: Keep both and Replace each have their own breathing room,
the gap between Keep both's description and the word Replace as wide as the gap above Keep both. Check in Hungarian and
at the largest system font size that nothing is cut.
