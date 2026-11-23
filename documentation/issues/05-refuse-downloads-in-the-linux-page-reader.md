# Refuse downloads in the Linux page reader, as the other platforms do

**Kind:** privacy  ·  **Severity:** medium  ·  **Platforms:** desktop (Linux)
**Files:** `data/source/remote/implementation/src/desktopMain/native/linux/CampfireWebView.c`,
`data/source/remote/implementation/CLAUDE.md`

## Problem

The root `CLAUDE.md` promises that a page read from its link keeps "only its text for the import; nothing of it is
shown or stored". On Linux a page can store a file in the user's Downloads folder.

`decidePolicy` (`CampfireWebView.c:204`) handles the navigation and new-window decisions and hands every other one back
to WebKitGTK:

```c
    if (type != NAVIGATION_ACTION) return 0;
```

The other type is `WEBKIT_POLICY_DECISION_TYPE_RESPONSE` (2). WebKitGTK's default handler downloads a response marked
`Content-Disposition: attachment` (`webkit_policy_decision_download`), and with no `download-started` /
`decide-destination` handler connected, a `WebKitDownload` saves into the XDG Downloads directory. An ephemeral context
does not stop that: it only keeps cookies and caches in memory. A link to a page that answers with an attachment (a
PDF or `.txt` chord sheet offered as a download, a redirect to a file host) therefore leaves a file behind, written by
a reader the user never sees.

The other platforms keep nothing: Windows cancels `DownloadStarting` (`windows/CampfireWebView.cpp`, `put_Cancel(TRUE)`);
the macOS helper and iOS answer no response policy with a download (WKWebView downloads only when the delegate asks for
it); Android's `WebView` without a `DownloadListener` drops it.

## Fix

Both, so a download started any other way (a script's `<a download>`) is caught too:

1. **Answer the response decision.** In `decidePolicy`, for type `2` (add `RESPONSE_ACTION = 2` to the enum): use the
   response only where `webkit_response_policy_decision_is_mime_type_supported(decision)` is true and it is no
   attachment, else `webkit_policy_decision_ignore(decision)`; return 1. An attachment is a `Content-Disposition`
   header starting (case-insensitively, after spaces) with `attachment`: `webkit_response_policy_decision_get_response`
   → `webkit_uri_response_get_http_headers` → `soup_message_headers_get_one(headers, "Content-Disposition")`
   (libsoup 2 and 3 have the same signature; `dlsym` on the WebKitGTK handle finds it among the libraries it brings,
   as it finds GLib's). A null header table (a `data:` or `about:` response) is no attachment. An ignored main-frame
   response fails the load with WebKit's policy error, which `NativeLoadFailure` already reads as `FAILED`, so the read
   ends as unreadable, as a file link does on the other platforms.
2. **Cancel any download.** In `build`, after `webkit_web_context_new_ephemeral()` and before the context is unref'd,
   connect `download-started` on the context (WebKitGTK 4.0 and 4.1 both emit it there) to a handler calling
   `webkit_download_cancel(download)`. It runs before `decide-destination`, so no file is created.

Add the new functions to the `gtk` struct and to `SYMBOLS` in the same order (the `_Static_assert` checks the count):
`webkit_response_policy_decision_is_mime_type_supported`, `webkit_response_policy_decision_get_response`,
`webkit_uri_response_get_http_headers`, `soup_message_headers_get_one`, `webkit_download_cancel`. All exist in
WebKitGTK 2.4+, older than both `webkit2gtk-4.0` and `4.1` as shipped today, so `loadWebKit` refuses no engine it took
before.

In the module `CLAUDE.md`'s **Linux** bullet, add: "Downloads are refused: a response that is an attachment or that
the view cannot show is ignored, and any download started is cancelled."

**What compiles it:** `CompileWebViewHelper` (`gradle/build-logic/.../tasks/CompileWebViewHelper.kt`) builds only the
host's helper, Linux with `cc -shared -fPIC -Os -Wall -Werror` against the bundled JNI headers. CI's `compile` job in
`.github/workflows/tests.yml` (`ubuntu-latest`) runs `:app:desktop:compileWebViewHelper`, so the change is compiled
with `-Werror` on every PR; the task skips (not fails) where there is no `cc`. Run that task locally if on Linux,
else rely on the CI job.

## Tests

None can be written: the policy is decided inside WebKitGTK, and no test runs the native helper. `NativeLoadFailureTest`
already covers how the ignored response's failure is read.

## Manual check

On a Linux desktop with WebKitGTK, paste `https://httpbin.org/response-headers?Content-Disposition=attachment` (or any
address answering with an attachment) and confirm the read ends as "could not read" and nothing new is in
`~/Downloads`. A mistake here either keeps writing files (the privacy promise) or, if the MIME check is inverted, makes
every page unreadable on Linux, which no CI job would notice.
