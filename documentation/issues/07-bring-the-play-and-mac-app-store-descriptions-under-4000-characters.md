# Bring the Play and Mac App Store descriptions back under 4,000 characters and check every listing field's limit on commit

**Challenged:** amended — `tests.yml` ignores pushes that change only `documentation/**` under a comment saying nothing there is read, so the plan now rewrites that comment and names the pre-commit hook and a release's run as what checks a listing-only change.

**Kind:** store rejection / tooling  ·  **Severity:** medium (Play Console and App Store Connect refuse the text)  ·  **Platforms:** Android, macOS (listings)
**Files:** `documentation/store-listings/google-play.md`, `documentation/store-listings/mac-app-store.md`,
`documentation/store-listings/app-store.md`, `documentation/store-listings/microsoft-store.md`,
`.github/scripts/check_store_listings.py` (new), `.github/scripts/test_check_store_listings.py` (new),
`.githooks/pre-commit`, `.github/workflows/tests.yml`, `.claude/skills/add-language/scripts/language.py`,
`.claude/skills/prepare-release/SKILL.md`, `CLAUDE.md` (root, the pre-commit hook sentence)

**Decision (user):** which wording to cut. Recommended: drop the "Long songs fit on fewer pages…" bullet, which repeats
the description's second paragraph, from all four descriptions.

## Problem

Measured as the fenced block under each heading (Python `len()`, as the prepare-release skill counts):

| File | Field | Length | Limit |
|---|---|---|---|
| `google-play.md` | Full description | **4,023** | 4,000 |
| `mac-app-store.md` | Description | **4,029** | 4,000 |
| `app-store.md` | Description | 3,968 | 4,000 |
| `microsoft-store.md` | Description | 4,608 | 10,000 |

Both overflowed when French joined the languages line:

```
• English, Hungarian, Romanian, German, Spanish, and French, switchable inside the app
```

Play Console and App Store Connect refuse a description over the limit, so the next release's listing update cannot be
pasted. Nothing checks the limits: the prepare-release skill (step 8) asks for a count with a real interpreter only for
fields it edits, and the add-language skill's `language.py wiring` greps the languages line
(`rf"\b{english_name}\b[^\n]*, switchable inside the app"`) without measuring the field it grew. The next language will
push App Store's description over too (3,968 + ~10).

## Fix

### 1. The cut (all four descriptions, so they keep saying the same thing)

Remove this line from the description block of each of the four files (it is line 2 of the "Reading" list in each):

```
• Long songs fit on fewer pages, and short ones fill the screen in centered columns
```

It repeats the second paragraph ("a song flows into as many columns as the screen has room for, so you turn as few
pages as possible"). Result: Play 3,939, Mac 3,945, App Store 3,884 (≈ 55–115 characters of room for the next language);
Microsoft 4,524. Keep `, switchable inside the app` and the languages line as they are: `language.py wiring` matches
them.

Alternatives, if the user prefers to keep that bullet (each measured on Play / Mac):
- Drop `• Search by title, artist, or tag` (−34): 3,989 / 3,995, only 5 characters of room; not recommended alone.
- Also shorten `• A lyrics-only view for the people who only sing` to `• A lyrics-only view for singers` (−17): with the
  search line, 3,972 / 3,978.

Never name a competing app in any rewording.

### 2. The check: `.github/scripts/check_store_listings.py` (standard library only, MPL header as the other scripts)

It belongs beside the repo's other commit checks rather than only in `language.py`: the prepare-release skill edits the
listings too, and the pre-commit hook runs on every commit (memory: enforce in the loop, CI only the backstop).

- Reads every `documentation/store-listings/*.md`. For each `## <field> (<limit>)` heading, the fenced blocks between
  it and the next `## ` heading are the field's text (a block's content excludes the fence lines and the final
  newline). Recognised limits:
  - `(N characters max)` (N may have a thousands comma): one block, `len(text) <= N`.
  - `(N bytes max)`: one block, `len(text.encode("utf-8")) <= N` (Apple's keywords).
  - `(K max, N characters each)`: at most K blocks, each `len() <= N` (Microsoft's product features).
  Any other heading is ignored. A limit heading with no block is an error.
- Prints one line per failure, `<file>: <field> is <length> characters, <limit> at most`, and exits 1; prints nothing
  and exits 0 otherwise. A module docstring in the voice of `check_sizes.py` says what it checks and who runs it.
  The listings are small and are never meant to be over, so it judges the whole folder, not the change (no ratchet, no
  exception list).
- Factor the parsing into a function the test can call on a string, e.g. `field_errors(name, text) -> list[str]`.

Run it:
- `.githooks/pre-commit`: `python3 .github/scripts/check_store_listings.py` after `check_sizes.py`.
- `.github/workflows/tests.yml`: a step after "Check the license headers", with a one-line comment like its
  neighbours' ("A store refuses a listing field past its limit; the pre-commit hook checks every commit the same way").
  The workflow's `push` trigger has `paths-ignore: [ 'documentation/**' ]` under the comment "Notes, plans, and the
  README's images, which nothing here reads", which the new step makes false: a push changing only a listing starts no
  run. Keep the ignore (the hook checks every commit, and `publish-all.yml` calls this workflow before a release, so a
  listing past its limit never ships) and rewrite the comment to say so, e.g. "Notes, plans, the README's images, and
  the store listings: only the listings' length check reads any of them, and the pre-commit hook and a release's run
  check those".
- `.claude/skills/add-language/scripts/language.py`, `check_wiring`: after the existing checks, run the script
  (`subprocess.run([sys.executable, str(ROOT / ".github/scripts/check_store_listings.py")], capture_output=True, text=True)`)
  and add each line it prints to `errors`, so `wiring` fails on the listing it just grew rather than at commit time.
  Add `import subprocess`.
- `.claude/skills/prepare-release/SKILL.md` step 8: after "Count every edited field with a real interpreter …", add
  "(`.github/scripts/check_store_listings.py` checks every field with a limit in its heading)".
- Root `CLAUDE.md`, the "Run `git config core.hooksPath .githooks`" bullet: "the pre-commit hook runs the license
  header, size, and store listing length checks (`.github/scripts/check_sizes.py`, limits in the `code-style` skill;
  `check_store_listings.py`, limits in each listing's headings)".

## Tests

`.github/scripts/test_check_store_listings.py` (`unittest`, run by `tests.yml`'s `unittest discover`), on strings:
- `test_a_field_within_its_limit_passes` and `test_a_field_one_character_over_its_limit_fails` (`(4,000 characters max)`
  with the comma, text of 4,000 and 4,001 characters).
- `test_bytes_are_counted_for_a_bytes_limit`: 100 characters, one of them `é`, are 101 bytes and fail `(100 bytes max)`.
- `test_each_block_is_counted_for_a_per_item_limit_and_the_blocks_are_counted_too`.
- `test_a_heading_without_a_limit_is_ignored` (`## Support URL`, `## Search terms (7 max)`).
- `test_the_repository_listings_are_within_their_limits`: runs the check on the real folder and expects no errors
  (fails on HEAD with Play and Mac).
