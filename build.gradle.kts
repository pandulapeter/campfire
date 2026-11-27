/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
plugins {
    idea
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.multiplatformLibrary) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hyperether.localization) apply false
    alias(libs.plugins.koin.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// Every session works in a git worktree under .claude/worktrees, a whole checkout inside this one. The IDE would index
// each of them, and read each one's .run/ as more run configurations, which it then keeps as local ones after the
// worktree is gone.
idea {
    module {
        excludeDirs.add(file(".claude"))
    }
}
