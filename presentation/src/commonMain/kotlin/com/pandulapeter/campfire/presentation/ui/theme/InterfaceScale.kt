/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.theme

import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform

/**
 * How many of the platform's units one dp of the interface takes up. Material's sizes are made for a phone held at
 * reading distance, and read as oversized on a monitor at arm's length, so a pointer driven interface is drawn smaller:
 * Material's 16sp body text comes to about 13.6pt there, next to the 13pt the desktop systems set their own text in.
 * On the web it is the input that decides rather than the platform, so a phone opening the page keeps the size a phone
 * is meant to have.
 *
 * It is also what the desktop window turns the smallest size the layouts are made for into a minimum window size with.
 */
val interfaceScale get() = if (isDesktopPlatform) 0.85f else 1f
