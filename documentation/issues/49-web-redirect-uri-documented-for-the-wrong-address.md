# 49 · The web build's Dropbox redirect URI is documented for github.io, but the deployed page sends pandulapeter.com

(Two reviewers found this; merged into one plan.)

**Severity:** docs (web. Web sync works in production since `74a08e02`, so the App Console holds the right entry; the
doc would mislead whoever registers a new app key, a fork or a second provider) · **Area:** `app/web/CLAUDE.md`

## Symptom
`app/web/CLAUDE.md` names `https://pandulapeter.github.io/campfire/` as the redirect URI of the deployment. Somebody
setting up a Dropbox app from it registers that address only, and "Connect Dropbox" on the published web build then
stops on Dropbox's page with an invalid `redirect_uri` error.

## Cause
The deployment is served from `https://pandulapeter.com/campfire/`: `pandulapeter.github.io/campfire/` answers
`301 → https://pandulapeter.com/campfire/` (checked with `curl -I`), and that is also where `Distribution.WEB`
(`presentation/…/ui/platform/Platform.kt:79`), `README.md:23` and the release-notes skill point. The authenticator
sends the page's own origin and `<base>` folder (`data/source/remote/implementation/src/wasmJsMain/…/auth/
SyncAuthenticator.wasmJs.kt:62-65`, `currentPageUrl()` = `window.location.origin` + base folder), so the page asks
for `https://pandulapeter.com/campfire/`. Dropbox compares `redirect_uri` as an exact string against the registered
list before any HTTP redirect is involved, so the github.io entry never matches anything the page sends. The
sentence predates the move to the custom domain (`48fb1920`) and was carried forward by `aa52ae8f`.

## Fix
Docs only. The maintainer has confirmed that the two addresses redirect to each other and that web sync works; no
App Console change is needed (the github.io entry may stay registered, harmlessly).

`app/web/CLAUDE.md:96-97`, replace

```
`https://pandulapeter.github.io/campfire/` for the deployment and `http://localhost:8080/` for the development server
are the two entries.
```

with

```
`https://pandulapeter.com/campfire/` for the deployment and `http://localhost:8080/` for the development server are
the two entries. The deployment's is the custom domain rather than `pandulapeter.github.io/campfire/`, which
redirects to it: the page is always running on the custom domain when it asks, and the service compares the URI as a
string before any redirect could come into it.
```

## Tests
None (docs).

## Verify
Read the paragraph back against `currentPageUrl()` in `SyncAuthenticator.wasmJs.kt`. Nothing to compile.

## Docs
This is the doc change. `data/source/remote/implementation/CLAUDE.md:26-28` ("the exact page URL for a web
deployment") stays true.

## Touches
- `app/web/CLAUDE.md`

## Depends on
None.
