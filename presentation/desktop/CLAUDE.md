# :presentation:desktop

Thin desktop shell around `:presentation:shared` (re-exported as `api` — TODO to make it an implementation detail).

- `CampfireDesktopApp()` — hosts the shared `CampfireApp`; links open through `java.awt.Desktop`.
- `CampfireViewModel.handleKeyEvent(...)` — desktop has no back gesture, so the window's `onKeyEvent` pops the back stack on Escape.

Anything non-trivial belongs in `:presentation:shared` (desktop-only behavior such as scrollbars is done there with `expect`/`actual`).
