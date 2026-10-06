# Say in the privacy policy that sync also carries the library's per-song settings and the chosen chord shapes

**Kind:** docs / store-policy · **Severity:** medium · **Platforms:** all (the policy URL of every store listing)
**Challenged:** amended — a third sentence turns false with the same change: *Deleting your data* says deleting the
library while connected "empties the *Apps/Campfire* folder", but `DeleteLibraryUseCase` clears only the per-song maps
and nothing ever deletes the folder's `preferences.json`, so the chord shapes (`chordVoicings`, kept on purpose as a
library-wide habit) stay in it. That bullet is now edited too. The synced fields were checked against
`SyncedPreferences.kt` (exactly `transpositions`, `tempos`, `capos`, `chords`).
**Files:** `../CampfireWebsite/privacy/index.html` — **in the `campfire-website` repository, not this one** (see
Execution below)

## Problem

The privacy policy at https://campfire-songbook.com/privacy/ (`CampfireWebsite/privacy/index.html`), linked from
Settings and from every store listing, says under *Cloud sync*:

```html
			<li><strong>Only your library is synced.</strong> That means your song files and your setlist files. Your app settings, chosen theme and language, text size and transpositions are never uploaded by sync and stay on the device where you set them (your device's own backup can still carry them to a new device, see <em>Device backup</em> below).</li>
```

Since `f51b3cc6a` and `ba4b5ece3` (both in 4.7.0, after 4.6.1), every completed sync run also settles a
`preferences.json` at the top of *Apps/Campfire*: the transposition, tempo and capo of each song opened from the library
(`UserPreferences.transpositions`, `tempos`, `capos`) and the player's chosen chord shapes (`chordVoicings`) — see the
root `CLAUDE.md`, Sync, "The library's per-song overrides travel too". So from 4.7.0 the sentence "transpositions are
never uploaded" is false. The in-app text is already right (`settings_sync_description`: "…along with how each song is
transposed, capoed and played"). Apple 5.1.1(i) and Play's User Data policy require the policy to match the app.

The version line is stale as well:

```html
		<p class="updated"><em>Last updated: 29 September 2026. This version covers Campfire 4.0 and later; cover art and deleting the whole library from the settings arrived in Campfire 4.5.</em></p>
```

## Fix

1. Replace the bullet with:

```html
			<li><strong>Only your library, and how you play it, is synced.</strong> That means your song files and your setlist files, and, in a <em>preferences.json</em> file next to them in the same folder, the transposition, capo and tempo you set for the songs you open from the library and the chord shapes you chose for each instrument. Your other app settings, such as your chosen theme and language and the text size, are never uploaded by sync and stay on the device where you set them (your device's own backup can still carry them to a new device, see <em>Device backup</em> below).</li>
```

2. Replace the version line with (use the date the change is made):

```html
		<p class="updated"><em>Last updated: <day> October 2026. This version covers Campfire 4.0 and later; cover art and deleting the whole library from the settings arrived in Campfire 4.5, and syncing the per-song transposition, capo and tempo and the chosen chord shapes in Campfire 4.7.</em></p>
```

3. In *Deleting your data*, replace

```html
			<li><strong>In your Dropbox, if you used sync:</strong> deleting your library while connected, as above, empties the <em>Apps/Campfire</em> folder. Otherwise choose
```

(only that opening, up to and including "Otherwise choose"; the rest of the line stays) with

```html
			<li><strong>In your Dropbox, if you used sync:</strong> deleting your library while connected, as above, removes your songs and setlists from the <em>Apps/Campfire</em> folder, leaving only the <em>preferences.json</em> file with the chord shapes you chose. To remove everything, choose
```

Change nothing else on the page.

## Execution

The website repository has the user's own uncommitted work in progress (including other edits to this very file, in
its `<head>` and navigation). **Do not stage, commit, stash or reset anything there.** Make only the three text edits
above in the working tree with exact string replacement, leave them uncommitted, and report that the user is to commit
and publish them with their own changes before or together with the 4.7.0 release. Nothing is committed in the
Campfire repository for this plan except the removal of this plan file.

## Tests

None.

## Manual check

After the website is published: https://campfire-songbook.com/privacy/ shows the new bullet and date.
