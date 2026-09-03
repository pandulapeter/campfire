# :data:source:local:api

Local persistence interfaces, one per model. Plain suspend functions over `:data:model` types — no Room, no coroutine flows.

Two platform implementations exist (`:implementation-android`, `:implementation-desktop`); the app modules pick one. Adding a method here means adding it to both.
