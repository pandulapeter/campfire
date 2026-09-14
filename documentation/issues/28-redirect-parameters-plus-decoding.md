# 28 · `redirectParameters` decodes `+` as a space in every parameter

**Severity:** low (no trigger with Dropbox; a second provider whose codes contain `+` would fail every exchange) · **Area:** `:data:source:remote:api`

`RedirectUri.kt:47`: OAuth redirect parameters are percent-encoded, not form-encoded; Dropbox's `error_description`
*is* form-encoded (spaces as `+`), which is why the branch exists.

## Fix

Decode `+` as a space only for the two human-readable keys (`error`, `error_description`); leave it a literal `+`
for everything else (`code`, `state`). Implement by passing the key name into the value decoder, or by post-processing
those two keys. Update the test in `data/source/remote/api/src/commonTest` (there is one for the authorization URL;
add one for `redirectParameters("…?code=a+b&error_description=x+y")` → `code == "a+b"`, `error_description == "x y"`).
