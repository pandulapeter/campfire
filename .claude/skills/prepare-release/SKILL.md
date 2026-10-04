---
name: prepare-release
description: Prepare a Campfire release by writing a paste-ready GitHub release-notes draft (with the stores' "what's new" blurb and Play's update priority embedded in it), updating the localized in-app update message, and regenerating Android baseline profiles. Invoke this skill WHENEVER the task involves Campfire release preparation or release notes — cutting a release, bumping `campfire.versionName`, or any request to draft, write, summarize or update release notes / a changelog / "what's new" / "what changed since the last version". This is the ONLY correct way to produce these notes; never summarize the changes by hand instead. Not triggered by ordinary code edits — only by release work.
---

# Campfire release preparation

Produce **one** throwaway markdown file whose whole contents the user pastes into the description of a
new GitHub release: the notes people read, followed by the hidden block `publish-all.yml` reads. Publishing
that release is the entire release process, so the file has to be complete as it is — nothing to fill
in, nothing to cut before pasting. Match the style of the existing notes at
https://github.com/pandulapeter/campfire/releases.

Also update the tracked in-app message resources and regenerate the tracked Android profiles before the release
is tagged. These are part of preparing every release, including a small release with empty notes.

**Campfire is an app, not a library.** The notes are for the people who use it — musicians — not for
developers reading the source. Nothing about modules, classes, Gradle, Koin or the build belongs in
them. If a change has no effect a user could notice, it is not in the notes.

## Process

1. **Determine the version.** The release version is `campfire.versionName` in `gradle.properties`
   (`campfire.buildNumber`, which every platform shares, moves with it). The
   GitHub tag is that number without the `v` prefix (`4.0.1`), even though the bump commit spells it
   with one.

2. **Find the previous release boundary** — the last release **tag**, never a version bump commit (a bump can
   land for a version that was never published, and a release is what the tag marks):
   ```bash
   grep -n 'campfire.versionName' gradle.properties
   git describe --tags --abbrev=0 --match '[0-9]*.[0-9]*.[0-9]*' --exclude "$(sed -n 's/^campfire\.versionName=//p' gradle.properties)"
   git tag --list '[0-9]*.[0-9]*.[0-9]*' --sort=-v:refname | head -3
   ```
   The `git describe` line prints `<last tag>`: release tags are bare version numbers, so older `v`-prefixed tags
   (`v1.0.0`) are never a boundary, and the version being prepared is excluded so that a run after that version was
   already tagged (on HEAD or on a commit before it) still finds the release before it. The range is `<last tag>..HEAD`, for the GitHub notes, the stores' blurb and the in-app message alike: every
   change since the last published release is in scope, whatever else was prepared or reverted in between.

3. **List the commits in the range and group them by feature.** Many commits usually build one feature (a PDF
   export is dozens), so cluster them by the user-visible capability before judging importance:
   ```bash
   git log <last tag>..HEAD --format='%H %s'
   git diff <last tag>..HEAD --stat -- presentation domain data chordpro app | tail -40
   ```
   Do not stop at the strings diff: a feature that rebuilds an existing screen (a new reading layout, new ways to
   page through a song) adds few strings and is easy to miss. Check which screens and `CLAUDE.md` sections
   changed most, and read those diffs.

4. **Read the actual diffs — never summarize from commit subjects.** Subjects are lossy and often name
   the mechanism rather than the effect. For each commit:
   ```bash
   git show <hash>                                   # the change itself
   git show --name-only --format='' <hash>           # which areas it touched
   ```
   Pay particular attention to two files, because they say what a user will *see*:
   ```bash
   git diff <last tag>..HEAD -- presentation/src/commonMain/composeResources/values/strings.xml
   git diff <last tag>..HEAD --stat -- presentation domain data chordpro app
   ```
   New or changed strings are almost always a new feature, a new setting or a new message worth a bullet.

5. **Judge every change against the last release tag, not against the previous commit.** A user only knows what
   was in the last published release. A fix for a bug introduced after that tag, for a feature or dialog that did
   not exist in it, or for a rough edge of something still unreleased, is part of that new work and never gets a
   bullet of its own: the feature's bullet already describes it as it now is. Test each candidate fix by asking
   whether somebody running the last release could have hit the problem. Never pad the list with recent small
   commits; if no fix qualifies, there is no fixes bullet. The same goes for polish of new features (layout
   tweaks, accessibility labels, performance of code that is new): it belongs inside the feature's description or
   nowhere.

   Then sort what you found into user-facing and not. Keep: new features, new settings, changed behavior
   somebody relied on, fixed bugs a user could hit, visible design changes, new or improved platform
   support, new languages, performance a user can feel (loading, scrolling, sync speed). Drop: refactors,
   dependency bumps, Lint and warning cleanups, test changes, CI and build configuration, documentation
   and `CLAUDE.md` edits — unless the build change is itself the user-visible thing (a smaller download,
   a fixed release build, a new distributable).

6. **Note which platforms each surviving change affects.** Campfire ships four builds from one codebase,
   and a bullet that is only true on one of them has to say so ("on iOS", "on the web"). A change in
   `commonMain` is everywhere and needs no qualifier; a change under `app/<platform>` or in a
   `<platform>Main` source set usually needs one.

7. **Always update the in-app message.** Follow `.claude/skills/code-style/SKILL.md` before editing resources.
   Set `whats_new_message` in both:
   - `presentation/src/commonMain/composeResources/values/strings.xml` (English);
   - `presentation/src/commonMain/composeResources/values-hu/strings.xml` (Hungarian).

   Write the bullets by **Writing the changelog** below: at most six, each a bold headline and a one-sentence
   description, sorted by importance. Before writing, list the feature clusters from step 3, rank them, and check
   that the six (or fewer) that made it are the ones a user would miss most.

   Store each bullet on one line as `• **Headline** Description` (the literal `• `, the headline between `**`
   pairs, then a space and the description), and separate bullets with `\n` in the XML string (for example,
   `• **Print in up to four columns** Fit more of a song on each page.\n• **...** ...`). The dialog draws the
   headline bold on its own line with the description under it, as spaced rows in a scrollable area with fades at
   the top and bottom; its title and **Get started** button stay visible. No other Markdown,
   links, emoji, introductory paragraph or version number in the body.
   Translate the same bullets into natural Hungarian and escape XML correctly. Keep `whats_new_title` as the
   localized title with its `%1$s` version placeholder. Replace the previous message rather than appending history.
   **Empty notes are valid:** when the user wants no notes for a very small release, or there is no user-facing
   change to announce, explicitly set `<string name="whats_new_message"></string>` in **both** files. Never leave
   the previous release's text behind. Blank or whitespace-only messages suppress the dialog; the app still records
   the version as handled. The first installed version is always skipped, whatever its message contains.

8. **Always regenerate Android baseline profiles, and only once every other source change is in place** — the
   in-app message and any code or resource edit made while preparing the release (step 7 included), since the
   profile is recorded from the built app and a later change would leave it stale. If anything under `presentation`,
   `app` or the shared modules changes after a recording, record it again. Follow `app/baselineprofile/CLAUDE.md`'s recording procedure:
   use an English emulator (boot `Resizable_Experimental` if needed), wait for it to finish booting, and select it
   explicitly with `ANDROID_SERIAL` when several devices are connected. Uninstall `com.pandulapeter.campfire` only
   from that recording emulator if present, to start with a fresh library and avoid signing conflicts; never
   uninstall the `.debug` app or touch a user's physical device. Run:
   ```bash
   ./gradlew :app:android:generateBaselineProfile
   ```
   Verify that both `baseline-prof.txt` and `startup-prof.txt` in
   `app/android/src/main/generated/baselineProfiles` are nonempty and include app rules; the baseline profile must
   also contain `androidx/navigation3` and `org/koin` rules. Leave both generated files in the working tree for
   review with the localized messages. Run this even for empty notes and releases without startup changes; never
   move generation into publishing CI. If recording is blocked, finish the resources and draft, report the exact
   blocker and command still needed, and do not claim the release preparation is complete.

9. **Write the draft** to `.release-notes/<version>.md` (create the directory; `/.release-notes/` is
   gitignored, so these scratch files stay untracked). The file's contents are the release body and
   nothing else — no preamble, no headings above the bullets, no "generated by", no meta commentary —
   ending in the hidden store block described below. For empty notes omit the visible bullets and leave the
   `whats-new en-US` block empty; still include every store-control comment. Never write a second draft file.
   The tracked resources and generated profiles above are required changes, not extra drafts. Then tell the
   user, briefly:
   - whether the in-app message was updated or cleared, and whether both Android profiles were regenerated;
   - the path, and that the file is a throwaway to delete after pasting;
   - to paste **all of it** into the description of a new release tagged `<version>` on the commit
     that carries that `campfire.versionName` (`publish-all.yml` refuses a tag that disagrees with it), with
     "Set as a pre-release" left off, since a pre-release ships nothing;
   - that **publishing the release is what ships it**: `publish-all.yml` sends the builds to Play and the
     website, submits the iOS and macOS builds for App Review and the Windows build for Microsoft Store
     certification (each published as soon as it passes), and attaches the Linux packages to the release
     itself — so the notes never list or link downloads.

10. **Do not create the tag, the release, or commit anything.** Leave the draft, resources and generated profiles
    ready for review. Their commit must be included in the version's tag before publishing.

## Writing the changelog

The in-app message, the GitHub notes and the stores' block are one changelog in three formats: the same
bullets, in the same order, with the same wording (the stores' block may drop bullets to fit, never reword
them longer). These rules apply to all three.

**Six bullets at most**, however big the release. A patch has one to three. When there are more than six
candidates, merge the related ones (one bullet per area, not per commit) and drop the least important rather than
squeezing them in; a fix that is not worth its own bullet goes into the bullet of the feature it touches, or
nowhere. Never pad a small release up to six.

**Each bullet is a headline and one sentence.**
- The **headline** names the change in at most six words, the way the app names it (a Settings row, a menu
  entry, a screen): `Export a setlist as a zip`, not `Better sharing`.
- The **description** is one sentence of **at most 25 words** (about 160 characters): what is different now,
  then what that lets the user do or spares them — the concrete benefit, not an adjective. Add where to find it
  only when it is not obvious from the headline, and only if it fits the limit. Count the words of every
  description before writing it out; one over 25 is rewritten, not kept.

**Say what changed and why it is better — no more, no less.**
- Describe the change as a fact a user can check in the app: `Songs now open where you left off.` A claim
  about quality (`faster`, `smoother`, `more reliable`) is allowed only when the diff gives it a concrete
  meaning, and then states it: `Large libraries open without the long pause on the launch screen.`
- **No marketing words**: never `powerful`, `seamless`, `effortless`, `amazing`, `brand-new`, `exciting`,
  `revamped`, `supercharged`, `game-changing`, `beautiful`, `stunning`, `lightning-fast`, `enhanced experience`,
  `take … to the next level`, `we're thrilled`, `you'll love`. No exclamation marks, no emoji.
- **No vague bullets either**: never `various improvements`, `bug fixes and performance improvements`,
  `minor tweaks`, `polish`. A fix is described by what used to go wrong and no longer does:
  `Imported Word documents no longer lose their last page.` If that cannot be said in one sentence, the fix
  does not get a bullet.
- **Don't undersell**: a change that alters how people use the app every day is said plainly at the top,
  with its real benefit, not buried as `small improvements to the editor`. A new capability is called new.
- Address the user as `you` where it reads naturally; no `we`, no developer vocabulary, no version numbers.
- Platform-only bullets name the platform (`On Android, …`, `… on the web.`).

**Sort by importance from the user's perspective**, not by commit count or chronology: what changes how
people read and play songs every day first, then getting songs in and out, then new capabilities, then
conveniences, then fixes.

Examples of the tone:

| Too much | Too little | Right |
|---|---|---|
| **A stunning new reading experience**: Enjoy your songs like never before with our completely revamped song view! | **Song view changes**: Various improvements. | **Columns on wide screens**: Long songs are set in several columns, so a tablet shows a whole song without scrolling. |
| **Lightning-fast sync**: Sync is now blazing fast and super reliable. | **Sync fixes**: Fixed some issues. | **Faster first sync**: Connecting a large library uploads several files at once instead of one by one. |

## Style — the GitHub release body

Mirror the existing releases:

- **A flat list of `-` bullets.** No sections, no grouping by type, no "Bug fixes" / "Features"
  headings.
- **The bullets of Writing the changelog**, at most six, in the same order and words as the in-app message,
  each `- **Headline**: description.` — the headline with no trailing period, the description one sentence.
- **Plain language, the user's vocabulary.** "Sync your library through your own Dropbox folder", not
  "Implement `SyncEngine` batching". Name features the way the app names them in Settings.
- **Link where a link helps** — the web build (`[here](https://campfire-songbook.com/app/)`), the
  ChordPro site, a contributor's profile — in the markdown style the existing notes use.
- **Thank outside contributors inline.** Find them with `git log <last tag>..HEAD --format='%an' | sort -u`
  and credit anyone who is not the maintainer (Pandula Péter).
- **Say so plainly when something was removed or now works differently**, so nobody is surprised — e.g.
  the editor's auto save being taken out.

## Style — the store "what's new"

`publish-all.yml` reads the stores' "what's new" text out of the release body itself, from HTML comments that
the rendered release page does not show. They go at the very end of the draft, after a blank line, and
all of them are always written:

```
<!-- whats-new en-US
- bullet one
- bullet two
-->
<!-- play-store update-priority: 3 -->
<!-- play-store submit: true -->
<!-- app-store submit: true -->
<!-- mac-app-store submit: true -->
<!-- microsoft-store submit: true -->
```

- **`whats-new en-US`** is the changelog every store gets, not Play's alone — the App Store and the Mac App
  Store read the same block as the "What's New" of the version submitted for review, and the Microsoft Store
  as the "What's new in this version" of its submission — so nothing in it may
  be about one store or assume one platform unless the bullet itself is about that platform. The
  release's bullets, trimmed to the ones a store visitor would care about, in the same voice as the
  GitHub notes, one per line as `- Headline: description.` (the same six-at-most bullets, without the bold).
- **500 characters at most**, newlines included — Play's limit, the tightest of the stores. Check it with
  a real interpreter (e.g. Python's `len()` on the text between the comment's first line and its `-->`),
  not by eyeballing it, and report the count back. Shorten wording before dropping a bullet.
- **An empty block is valid** for a small release with no notes. It has zero characters and no placeholder bullet.
- **No markdown**: no links, no bold, no backticks. Plain `-` bullets and plain text only. Nothing in the
  block may contain `-->`.
- **`play-store update-priority`** is Play's in-app update priority. Always write it, with **0** unless the user asked for something else, so the user can see
  it and change it before publishing: 0–1 leaves the update to Play's own schedule, 2–3 offers it inside
  the installed app, 4–5 blocks the app until it is installed (see the Updates section of `CLAUDE.md`).
  Never pick a number above 0 on your own; mention the line when reporting back, so a release that fixes
  something serious can be raised before it is published.
- **`<store> submit`** says, per store, whether the release is sent for review / certification (`true`) or
  only uploaded and left as a draft there (`false`) — for new screenshots, say, which the pipeline cannot
  add, to be put in by hand before sending it from the store's console. Always write all four, **`true`**
  unless the user asked otherwise, so they can see them and flip one before publishing; anything but
  `true` or `false` stops the release. Mention them when reporting back.
- **Nothing misspelt is taken as absent.** `.github/scripts/release_description.py` stops the release on a
  store name it does not know (`play_store`, `playstore`), a `submit` other than `true` or `false` and a
  priority outside 0–5, and on notes over App Store Connect's 4 000 or Partner Center's 1 500 characters, so
  write the four store names exactly as above.

Where `publish-android.yml` is dispatched by hand instead, its `release_notes` input is a single-line
field that takes the same text with a literal `\n` for every line break.
