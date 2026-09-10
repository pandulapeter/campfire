#
# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.
#
# ProGuard rules for the release desktop distribution, on top of the ones Compose Desktop supplies.

# ProGuard narrows a parameter or return type to the single class it sees passed at every call site,
# but does not insert the `checkcast` the verifier then wants, so the JVM refuses the caller with a
# `VerifyError` the moment it is loaded. Generic functions are where this bites: their signature is
# erased to the upper bound, so a caller that passes a subclass makes the parameter look monomorphic.
#
# It killed the release build on the first frame: `rememberNavigationEventState(currentInfo: T)` in
# Navigation 3, whose only caller is `NavDisplay`, had its parameter rewritten from
# `NavigationEventInfo` to `SceneInfo`, and the whole application went down with
#
#   Exception in thread "main" java.lang.VerifyError: Bad type on operand stack
#     Location: androidx/navigation3/ui/NavDisplayKt__NavDisplayKt.NavDisplay(...)
#     Reason: Type 'androidx/navigationevent/NavigationEventInfo' is not assignable to
#             'androidx/navigation3/scene/SceneInfo'
#
# Compose Desktop already carries a `-keep` for the same bug against `**Kt__*` classes; this turns the
# family of optimizations off outright, because the next generic function to hit it will be somewhere
# else and nothing about the crash points at the flag that caused it. They are worth a fraction of a
# percent of the output, which is not worth an application that does not start.
#
# https://github.com/Guardsquare/proguard/issues/533
-optimizations !method/specialization/*,!method/generalization/*,!field/specialization/*,!field/generalization/*
