# 65 · The song and setlist grids share one slot pool for every kind of row

**Severity:** low · **Area:** `:presentation` (`SongsScreen.kt:301–388`, `SetlistsScreen.kt:281–427`)

Placeholder, header pills, description rows, "add songs" rows and song rows are all `item`/`items` without a
`contentType`, so reused nodes are the wrong shape and get rebuilt.

## Fix

Add `contentType = "header"`, `"song"`, `"setlist_header"`, `"setlist_description"`, `"setlist_action"`,
`"placeholder"` to each `item`/`items` call. Keys are unchanged.
