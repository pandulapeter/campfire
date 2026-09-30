# Stop the release on a release-description comment it cannot read, and on notes a store will refuse

**Challenged:** amended — the length errors must not depend on the store's `submit` flag: both store scripts check the limit at the top of `main()`, before `prepare_only` is looked at, so a `submit: false` (prepare-only) run fails on an over-long note as well; and the Apple limit (4 000) applies to `mac-app-store` as well as `app-store`. The tests change accordingly. The script only imports the standard library (as do the three existing ones), `unittest discover` imports only `test_*.py`, and the start directory is on `sys.path`, so `import release_description` in the test works.

**Kind:** bug (CI)  ·  **Severity:** low  ·  **Platforms:** CI
**Files:** `.github/workflows/publish-all.yml` (the "Read the release notes" step), `.github/scripts/release_description.py`
(new), `.github/scripts/test_release_description.py` (new), `.github/workflows/tests.yml` (one step), `CLAUDE.md` (root,
the paragraph on the release description's comments), `.claude/skills/release-notes/SKILL.md` if it lists the comment
format

## Problem

`publish-all.yml` (at 9ab7ca54e, the `Read the release notes` step) reads the stores' instructions out of hidden
comments in the release's description. It refuses a `submit` *value* it cannot read, and says why:

```python
priority = re.search(r"<!--\s*play-store\s+update-priority:\s*([0-5])\s*-->", body)
# Anything but true or false stops the release: a misspelt "flase" read as the default would submit what was
# meant to wait for screenshots.
submit = {}
for store in ["play-store", "app-store", "mac-app-store", "microsoft-store"]:
    flag = re.search(rf"<!--\s*{store}\s+submit:\s*(.*?)\s*-->", body)
    value = flag.group(1).lower() if flag else "true"
    if value not in {"true", "false"}:
        print(f"::error::The release says \"{store} submit: {flag.group(1)}\"; it takes true or false.", file=sys.stderr)
        sys.exit(1)
```

but every other way of misspelling a comment is read as its absence, and the default is the stronger action (verified
by running the regexes against such descriptions):

- `<!-- play_store submit: false -->`, `<!-- playstore submit: false -->` or any store name it does not know: no match,
  `submit` defaults to `true`, and the release the author meant to leave as a draft for screenshots is submitted.
- `<!-- play-store update-priority: 6 -->` (or `10`): the `[0-5]` does not match, the priority is `0`, and the update
  the author meant to block the old version with is left to Play's schedule.
- `<!-- whats-new en-us` or `<!-- whats-new EN-US`: no match, and the visible description, markdown stripped, goes to
  every store as the "What's new" — which can be far longer than the 500 characters Play cuts to, and the 1 500
  Partner Center and 4 000 App Store Connect refuse outright. Those two limits are checked by the store scripts
  (`microsoft_store_submission.py` line 235, `app_store_submission.py` line 171), which run *after* the 20-minute
  Windows build and the iOS archive and upload, so an over-long note fails the release at the very end and leaves an
  uploaded build behind.

## Fix

Move the parser into a script of its own so that it can be tested, and make it strict where a misreading changes what
a store does. New `.github/scripts/release_description.py`, run by the step as
`python3 .github/scripts/release_description.py` with `RELEASE_BODY` and `GITHUB_OUTPUT` in `env:` as now (the step
already passes the body through `env:`, keep that). Inside:

- every `<!-- <word> submit: … -->` whose `<word>` is not one of the four stores, and every `<!-- <words> update-priority: … -->`
  whose words are not `play-store`, is an error naming the comment ("The release names a store I do not know: …");
- an `update-priority` whose value is not a single digit 0–5 is an error, the way a bad `submit` value is;
- the `whats-new` header is matched case-insensitively on `whats-new` and `en-US` (`re.I` on that search only), so
  a case slip still reads; a `whats-new` comment with any *other* language is left alone (every listing is English
  only, see `CLAUDE.md`);
- after the notes are settled, their length is checked against the stores that will get them: an error over 4 000
  characters (App Store Connect, which both `app-store` and `mac-app-store` send the notes to) or 1 500 (Partner
  Center, `microsoft-store`) *whatever that store's `submit` value is* - the store script fails on the length before it
  looks at whether it was asked to submit - and a `::warning::`
  over 500 (Play cuts it to the first sentences that fit, `publish-android.yml`) — one message per store, each naming
  the limit and the length, so that the release fails in `prepare`, before any build starts.

Keep the outputs exactly as they are (`release_notes` heredoc, `update_priority`, the four `*_submit` values), since
six workflows read them. Document the new strictness in the root `CLAUDE.md` paragraph that describes the comment
format (a store name it does not know, or a priority outside 0–5, stops the release) and in
`.claude/skills/release-notes/SKILL.md` if it spells out the format. Tell `tests.yml` to run the parser's tests next
to the Node test (`python3 -m unittest discover -s .github/scripts -p 'test_*.py'`), so that the same job that gates
a release runs them. Put the step before the Gradle one (or give it `if: ${{ !cancelled() }}`): a failed `desktopTest`
skips every later step, and the Node test already suffers from that.

## Tests

`.github/scripts/test_release_description.py` (`unittest`), calling the script's `read(body) -> dict` function:

- a description with no comments gives the visible text as the notes, priority `0`, every store `true`;
- `<!-- whats-new en-US … -->` in either case of `en-us` gives its content, markdown untouched;
- two `whats-new` blocks: the first wins;
- CRLF line endings read the same as LF;
- `submit: False` reads `false`; `submit: flase` raises; `play_store submit: false` raises; `mac-app-store submit: false`
  does not touch `app-store`;
- `update-priority: 4` gives `4`; `update-priority: 6` raises;
- notes of 1 501 characters raise (Partner Center), with `microsoft-store submit: true` and with `false`; 4 001
  raise (App Store Connect) whichever of `app-store` and `mac-app-store` is asked; 501 characters only warn.

## Manual check

None beyond the tests; the next release exercises the step.
