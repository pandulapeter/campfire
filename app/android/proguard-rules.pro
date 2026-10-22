# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

# kotlinx.serialization ships its own consumer rules; only the coroutine machinery needs an explicit keep.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# The process log names screens and dialogs by class (`logBreadcrumbs`), which reads as nothing once R8 has renamed them.
-keepnames class com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination$**
-keepnames class com.pandulapeter.campfire.presentation.ui.dialogs.DialogType$**
