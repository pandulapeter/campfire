# Say in ui/components/CLAUDE.md where the cover search and a song's tags are edited now that the menus no longer hold them

**Kind:** docs  ·  **Severity:** low  ·  **Platforms:** all
**Files:** `presentation/src/commonMain/kotlin/com/pandulapeter/campfire/presentation/ui/components/CLAUDE.md`

## Problem

Two sentences of `ui/components/CLAUDE.md` at d650f3cb0 point at menu entries that are gone:

1. ~L302–305, about the editor's bar drawing the cover:

   > …and with no press, since the search is in the
   > editor's menu.

   `EditorMenu` (`screens/songEditor/EditorMenu.kt`) holds About the song, Prettify, the ChordPro reference, and Revert,
   and no cover art entry (its `coverArtAction` was removed). The cover search is opened by a tap on the cover (or on the
   empty place for one) in the About the song sheet and in the editor preview's card — which the next sentence of the
   same paragraph already says ("where the cover search is offered … a tap on it opens that search").

2. ~L343–346, about the tag and language pills of a song row:

   > a row has no
   > chip that writes the song's tags, which is the details overflow menu's business;

   and, two lines on, "Tags and languages in the song details card are read-only; only their dialogs edit them." The song
   details overflow menu has no tag entry any more; a song's tags are written from the song card's menu on the songs
   screen (`songLabelActions`, passed as `fileEditItems` in `screens/songs/SongList.kt`), from the selection mode's
   Manage tags, and from the About the song sheet's Tags tile. The song details screen has no "card" of its own: the
   tags are shown as label-only pills in the About the song sheet and in the editor preview's card.

## Fix

Match each sentence by its text (other lanes may have moved lines):

1. Replace "and with no press, since the search is in the editor's menu." with "and with no press, since the search is
   opened from the cover in the About the song sheet and in the preview's card."
2. Replace "which is the details overflow menu's business;" with "which is the business of the song card's menu, the
   selection mode, and the About the song sheet's Tags tile;".
3. Replace "Tags and languages in the song details card are read-only; only their dialogs edit them." with "Tags and
   languages in the About the song sheet and the editor preview's card are labels only; a tap on their tile opens their
   dialogs."

## Tests

None: documentation only.

## Manual check

None.
