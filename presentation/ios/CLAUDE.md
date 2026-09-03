# :presentation:ios

Thin iOS shell around `:presentation:shared` (re-exported as `api` — TODO to make it an implementation detail). Kotlin/Native only (`iosArm64`, `iosSimulatorArm64`), sources in `src/iosMain`.

- `CampfireIosApp(urlOpener)` — hosts the shared `CampfireApp`. Takes a `urlOpener` callback so the module stays free of UIKit.

Back navigation (including the swipe back gesture) is handled by Navigation 3 inside `:presentation:shared`.
