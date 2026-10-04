# Let Manage links save past an empty row, and mark a named row without an address as an error

**Kind:** bug  ·  **Severity:** low  ·  **Platforms:** all
**Files:** presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongLinksDialog.kt,
presentation/src/commonTest/kotlin/com/pandulapeter/campfire/presentation/ui/dialogs/SongLinksDraftTest.kt (new)

**Challenged:** sound

## Problem
`SongLinksDialog` (`SongLinksDialog.kt` ~103-107 at 800ebde0b) only enables Save when **every** row holds a usable
address:

```kotlin
val links = rows.map { it.link }
val urls = links.map { ChordProLinks.usableUrl(it.url) }
val normalizedLinks = links.map { it.normalized() }
val canSave = urls.all { it != null } && urls.distinct().size == urls.size &&
    normalizedLinks != dialog.links.map { it.normalized() }
```

A row whose address is blank makes `usableUrl` null, so Save is disabled — but the address field only turns red for a
*non-blank* unusable address (`isError = isDuplicate || (link.url.isNotBlank() && ChordProLinks.usableUrl(link.url) == null)`),
so nothing says why. It happens whenever a row is left empty: the Add link (+) button tapped once too often, or a song
that already has links where the user adds a row, changes another link and then decides not to fill the new one. The
user has to find and delete the empty card before Save comes back. (`ChordProLinks.setLinks` already drops unusable
addresses when writing, so an empty row carries no information.)

## Fix
1. Pull the decision into a pure, internal function in `SongLinksDialog.kt` (next to the private `normalized()` it uses),
   with a KDoc:
   ```kotlin
   /**
    * The links a Manage links draft writes, or null while Save has nothing valid to write. A row left wholly empty — the
    * seeded one, or one added and never filled — is no link at all and is left out rather than blocking Save; a row with
    * a name but no address, an address the file would not keep, or two rows with the same address block it, and so does
    * a draft that writes what the song already has.
    */
   internal fun songLinksToSave(draft: List<ChordProLink>, offered: List<ChordProLink>): List<ChordProLink>? {
       val kept = draft.filterNot { it.url.isBlank() && it.name.isNullOrBlank() }
       val urls = kept.map { ChordProLinks.usableUrl(it.url) }
       if (urls.any { it == null } || urls.distinct().size != urls.size) return null
       return kept.takeIf { links -> links.map { it.normalized() } != offered.map { it.normalized() } }
   }
   ```
2. In `SongLinksDialog`: `val linksToSave = songLinksToSave(draft = rows.map { it.link }, offered = dialog.links)`;
   `enabled = linksToSave != null`; on Save pass `links = linksToSave` (non-null there) instead of `links`.
   The per-row `urls` list stays for the duplicate marker.
3. In `SongLinkFields`, also mark the address as an error when the row has a name but no address:
   `isError = isDuplicate || (link.url.isNotBlank() && ChordProLinks.usableUrl(link.url) == null) || (link.url.isBlank() && !link.name.isNullOrBlank())`.
   No new string: the error outline next to the existing hint ("…address…" item at the top of the list) is enough, as
   it is for a malformed address today.

## Tests
New `SongLinksDraftTest` in `presentation/src/commonTest/.../ui/dialogs/` (run with `./gradlew :presentation:desktopTest`):
- `[link A, empty row]` vs offered `[]` → `[link A]`;
- `[empty row]` vs offered `[]` → null (nothing to write);
- `[link A, empty row]` vs offered `[link A]` → null (unchanged once the empty row is ignored);
- `[]` vs offered `[link A]` → `[]` (removing every link is a valid save);
- `[ChordProLink(url = "", name = "Tab")]` → null;
- `[link A, link A]` → null; `[ChordProLink(url = "not a url")]` → null;
- `[ChordProLink(url = "example.com/tab")]` vs `[]` → that link (scheme completion is `usableUrl`'s job).

## Manual check
Song with one link → About the song → Manage links → tap + (an empty card appears) → rename the first link → Save is
enabled and writes the rename; reopening shows one link. Type a name into an empty card without an address → its
address field turns red and Save is disabled.
