# :presentation:android-debug-menu

Build-variant-swapped wrapper around the Beagle debug drawer, so release builds don't ship it.

- `src/main` — `DebugMenuContract`, an interface with default no-op `initialize()` and `log()`.
- `src/debug` — `object DebugMenu : DebugMenuContract` that actually initializes Beagle and builds its sections (`sections/`: header, general, logs, shortcuts, testing).
- `src/release` — `object DebugMenu : DebugMenuContract` with nothing in it.

Beagle is a `debugImplementation` dependency only. Callers (`:app:android`) always call `DebugMenu.x(...)`; the variant decides whether it does anything. New debug functionality means adding a default no-op to the contract and implementing it in the debug source set only.
