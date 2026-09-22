# 51 · The Microsoft Store guide says the Windows build has never run; it builds in CI and is on the 4.2.2 release

**Severity:** docs (desktop / Windows) · **Area:** `documentation/publishing/microsoft-store.md`

## Symptom
The guide says "nothing about the Windows build has ever been run — not the installer, not the app on Windows", and
its first box asks for Publish Desktop to be dispatched to see `gradlew` under Git Bash, WiX being downloaded and the
accented vendor name going through the MSI tooling for the first time.

## Cause
That has happened: Publish Desktop run 35566083608 (on `7a99d646`) succeeded on all five legs, Windows included
(`gh run view 35566083608`), and `campfire-4.2.2-windows-x64-unsigned.msi` is attached to the 4.2.2 release
(`gh release view 4.2.2`). Only installing and using the app on Windows is still open.
`documentation/publishing/microsoft-store.md:13-15` and `:21-23`.

## Fix
`documentation/publishing/microsoft-store.md`:

1. `:13-15`, replace "None of the steps below has been done yet, **and nothing about the Windows build has ever been
   run** — not the installer, not the app on Windows — so step 1 comes before everything else." with "None of the
   steps below has been done yet. The installer builds in CI, but **it has never been installed and used on
   Windows**, so step 1 comes before everything else."
2. `:21-23`, tick the first box and state what it established:

   ```markdown
   - [x] Dispatch *Publish Desktop* by hand for the latest release tag and check that the Windows leg goes through.
         Done: `gradlew` runs under Git Bash, WiX is downloaded, and the accented vendor name goes through the MSI
         tooling; the 4.2.2 release carries the `.msi` it built.
   ```

## Tests
None (docs).

## Verify
Read back. Nothing to compile.

## Docs
This is the doc change.

## Touches
- `documentation/publishing/microsoft-store.md`

## Depends on
None.
