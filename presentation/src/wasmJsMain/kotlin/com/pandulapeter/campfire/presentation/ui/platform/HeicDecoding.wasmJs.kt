/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.pandulapeter.campfire.presentation.ui.platform

import kotlin.js.ExperimentalWasmJsInterop

/**
 * Starts finding out, once, whether this browser decodes HEIC (Safari does, Chrome and Firefox do not), by decoding a
 * 16 × 16 one the way the re-encoder decodes a picture (`createImageBitmap`): no browser says so any other way, and
 * `<img>` support is not the same question. The answer arrives in a moment, long before anybody opens a picker; until
 * then, and wherever it is no, [canDecodeHeic] says no. The picture is a grey 16 × 16 HEIC, as macOS writes one.
 */
internal fun startHeicDecodingProbe(): Unit = js(
    """(function () {
        if (globalThis.campfireHeicDecodable !== undefined) return;
        globalThis.campfireHeicDecodable = false;
        try {
            var text = atob(
                'AAAAJGZ0eXBoZWljAAAAAG1pZjFNaVBybWlhZk1pSEJoZWljAAABhm1ldGEAAAAAAAAAIWhkbHIAAAAAAAAAAHBpY3QAAAAAAAAAAAAAAAAAAAAAJGRpbmYAAAAcZHJlZgAAAAAAAAABAAAADHVybCAAAAABAAAADnBpdG0AAAAAAAEAAAAjaWluZgAAAAAAAQAAABVpbmZlAgAAAAABAABodmMxAAAAAOZpcHJwAAAAxWlwY28AAAATY29scm5jbHgAAgACAAaAAAAADGNsbGkAywBAAAAAFGlzcGUAAAAAAAAAEAAAABAAAAAJaXJvdAAAAAAQcGl4aQAAAAADCAgIAAAAcWh2Y0MBA3AAAACwAAAAAAAe8AD8/fj4AAALA6AAAQAXQAEMAf//A3AAAAMAsAAAAwAAAwAecCShAAEAI0IBAQNwAAADALAAAAMAAAMAHqAUIEHAkwziHuRZVNwICBgCogABAAlEAcBh4shEU2QAAAAZaXBtYQAAAAAAAAABAAEGgQIDBYaEAAAAHmlsb2MAAAAARAAAAQABAAAAAQAAAboAAAAWAAAAAW1kYXQAAAAAAAAAJgAAABIoAa+noPi95iz7BUCgRp7QyhA='
            );
            var bytes = new Uint8Array(text.length);
            for (var i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i);
            createImageBitmap(new Blob([bytes], { type: 'image/heic' })).then(function (bitmap) {
                globalThis.campfireHeicDecodable = bitmap.width === 16;
                bitmap.close();
            }, function () {});
        } catch (error) {
        }
    })()"""
)

/** Whether the probe [startHeicDecodingProbe] started found that this browser decodes HEIC. */
internal fun canDecodeHeic(): Boolean = js("globalThis.campfireHeicDecodable === true")
