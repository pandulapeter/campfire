# This file is part of Campfire.
# Copyright (c) Pandula Péter 2017-2026.
# https://github.com/pandulapeter/campfire
#
# This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
# If a copy of the MPL was not distributed with this file, You can obtain one at
# https://mozilla.org/MPL/2.0/.

"""
Writes every app icon Campfire ships, in every theme color the app offers, out of the orange ones drawn by hand.

    python3 app/generate_theme_icons.py

Run from anywhere, with Pillow and NumPy installed (and on a Mac, for `iconutil`), after any of the sources in
`app/icons` has changed; the output is committed. The sources are the only icons that are drawn: `ios/` is the iOS
icon's light, dark and tinted appearance, `appIcon.icns` and `appIcon.ico` the packaged macOS and Windows icons,
`app_icon.png` the round one of the desktop windows and Linux, `icon-192.png` the web's, and `play_icon.png` the
512 pixel one a store listing asks for. Every other icon is one of those with its orange moved to the seed of a palette
(see MaterialColorSchemes.kt, whose seeds SEEDS repeats), in Oklab so that the move keeps what the eye reads as the
same lightness and saturation. A pixel is moved in proportion to how much orange it has, so the white of the mark, the
shadows and the dark icon's near-black stay what they are while the fills, gradients and glows follow the color.

The app's own color, and so every packaged icon, is gray: the icons the system shows while the app is not running -
the installed app, the Start menu, a store's page - are the ones no theme color can reach, and gray is the one color
that goes with all of them. The orange is a theme color like the rest, whose icons are the sources as they are.

What is written, for every color:

- iOS: the `AppIcon` set for the app's own color, and an `AppIcon-<Color>` alternate icon set for the others, each
  with its light, dark and tinted appearance.
- The web: `icon-192-<color>.png`, the favicon and the loading screen's icon. The app's own is named like the others
  rather than after the page, because a browser keeps a favicon by its address and would go on showing an earlier icon
  that had the same one.
- The desktop: `app_icon.png` and `app_icon_<color>.png`, the window icon of Windows and Linux, and the macOS Dock icon
  `dock_icon_<color>.png`, set while the app runs.

And, in the app's own color alone, the packaged icons: `appIcon.icns`, `appIcon.ico` and Android's `appIcon.png`.
Android's launcher icons are not images - an adaptive icon over each seed, see `res/mipmap-anydpi` - so the seeds
below are repeated in `res/values/colors.xml` rather than written there.
"""

import os
import shutil
import subprocess
import tempfile

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCES = os.path.join(ROOT, 'app/icons')
IOS_ASSETS = os.path.join(ROOT, 'app/ios/iosApp/iosApp/Assets.xcassets')
WEB_RESOURCES = os.path.join(ROOT, 'app/web/src/wasmJsMain/resources')
DESKTOP_DRAWABLES = os.path.join(ROOT, 'app/desktop/src/main/composeResources/drawable')
DESKTOP_RESOURCES = os.path.join(ROOT, 'app/desktop/src/main/resources')
ANDROID = os.path.join(ROOT, 'app/android')

SOURCE_SEED = '#F57C00'

# A gray as light as the orange, for the dark iOS icon: its mark is a glow on near-black, which any darker gray dims.
SAME_LIGHTNESS_GRAY = 'same-lightness-gray'

# UserPreferences.ThemeColor ids, in the order the preference declares them, with the light and the dark seed of each.
# SYSTEM has no icon of its own: where it is offered the operating system draws the icon, and everywhere else it falls
# back to the app's own color.
SEEDS = {
    'campfire': ('#707070', SAME_LIGHTNESS_GRAY),
    'red': ('#D32F2F', '#D32F2F'),
    'orange': (SOURCE_SEED, SOURCE_SEED),
    'yellow': ('#FBC02D', '#FBC02D'),
    'green': ('#43A047', '#43A047'),
    'teal': ('#00796B', '#00796B'),
    'blue': ('#1976D2', '#1976D2'),
    'purple': ('#7B1FA2', '#7B1FA2'),
    'pink': ('#E91EAF', '#E91EAF'),
}

DOCK_ICON_SIZE = 512

# The sizes Windows asks an icon file for, up to the largest one the format has room for.
ICO_SIZES = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]


def srgb_to_linear(c):
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def linear_to_srgb(c):
    c = np.clip(c, 0, 1)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * c ** (1 / 2.4) - 0.055)


def to_oklab(rgb):
    r, g, b = (srgb_to_linear(rgb[..., i]) for i in range(3))
    l = np.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    m = np.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    s = np.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return np.stack([
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    ], -1)


def from_oklab(lab):
    lightness, a, b = lab[..., 0], lab[..., 1], lab[..., 2]
    l = (lightness + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (lightness - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (lightness - 0.0894841775 * a - 1.2914855480 * b) ** 3
    return np.stack([
        linear_to_srgb(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
        linear_to_srgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
        linear_to_srgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
    ], -1)


def oklch(hex_color):
    rgb = np.array([int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)])
    lightness, a, b = to_oklab(rgb)
    return lightness, np.hypot(a, b), np.arctan2(b, a)


def recolor(image, seed):
    if seed == SOURCE_SEED:
        return image.convert('RGBA')
    pixels = np.asarray(image.convert('RGBA')).astype(float) / 255
    lab = to_oklab(pixels[..., :3])
    lightness, chroma, hue = lab[..., 0], np.hypot(lab[..., 1], lab[..., 2]), np.arctan2(lab[..., 2], lab[..., 1])
    source_lightness, source_chroma, source_hue = oklch(SOURCE_SEED)
    if seed == SAME_LIGHTNESS_GRAY:
        target_lightness, target_chroma, target_hue = source_lightness, 0, source_hue
    else:
        target_lightness, target_chroma, target_hue = oklch(seed)
    # How much of the orange a pixel carries: the white mark and a gray shadow carry none and keep their lightness,
    # which moving every pixel by the same amount would turn into a tinted mark on a light color.
    weight = np.clip(chroma / source_chroma, 0, 1)
    lightness = lightness + (target_lightness - source_lightness) * weight
    chroma = chroma * target_chroma / source_chroma
    hue = hue + target_hue - source_hue
    rgb = from_oklab(np.stack([lightness, chroma * np.cos(hue), chroma * np.sin(hue)], -1))
    result = np.concatenate([rgb, pixels[..., 3:]], -1)
    return Image.fromarray((result * 255 + 0.5).astype(np.uint8), 'RGBA')


def save(image, path, opaque=False):
    # The iOS icons have to be opaque: App Store Connect refuses an icon with an alpha channel.
    (image.convert('RGB') if opaque else image).save(path, optimize=True)


def iconset(icns, directory):
    """The images of an `.icns` file, as the `.iconset` folder `iconutil` makes of it."""
    path = os.path.join(directory, 'icon.iconset')
    subprocess.run(['iconutil', '-c', 'iconset', icns, '-o', path], check=True)
    return path


def write_ios_icon_set(name, light, dark, tinted):
    directory = os.path.join(IOS_ASSETS, f'{name}.appiconset')
    os.makedirs(directory, exist_ok=True)
    save(light, os.path.join(directory, 'icon.png'), opaque=True)
    save(dark, os.path.join(directory, 'icon-dark.png'), opaque=True)
    # The tinted appearance is a gray image the system colors itself, so it is the same for every color.
    save(tinted, os.path.join(directory, 'icon-tinted.png'), opaque=True)
    shutil.copyfile(os.path.join(SOURCES, 'ios/Contents.json'), os.path.join(directory, 'Contents.json'))


def write_packaged_icons(seed):
    with tempfile.TemporaryDirectory() as directory:
        source = iconset(os.path.join(SOURCES, 'appIcon.icns'), directory)
        for name in os.listdir(source):
            save(recolor(Image.open(os.path.join(source, name)), seed), os.path.join(source, name))
        subprocess.run(['iconutil', '-c', 'icns', source, '-o', os.path.join(DESKTOP_RESOURCES, 'appIcon.icns')], check=True)
    ico = recolor(Image.open(os.path.join(SOURCES, 'appIcon.ico')), seed)
    ico.save(os.path.join(DESKTOP_RESOURCES, 'appIcon.ico'), format='ICO', sizes=ICO_SIZES)
    save(recolor(Image.open(os.path.join(SOURCES, 'play_icon.png')), seed), os.path.join(ANDROID, 'appIcon.png'))


def main():
    ios_light = Image.open(os.path.join(SOURCES, 'ios/icon.png'))
    ios_dark = Image.open(os.path.join(SOURCES, 'ios/icon-dark.png'))
    ios_tinted = Image.open(os.path.join(SOURCES, 'ios/icon-tinted.png'))
    web_icon = Image.open(os.path.join(SOURCES, 'icon-192.png'))
    window_icon = Image.open(os.path.join(SOURCES, 'app_icon.png'))
    with tempfile.TemporaryDirectory() as directory:
        dock_icon = Image.open(os.path.join(iconset(os.path.join(SOURCES, 'appIcon.icns'), directory), 'icon_512x512@2x.png'))
        dock_icon = dock_icon.convert('RGBA').resize((DOCK_ICON_SIZE, DOCK_ICON_SIZE), Image.LANCZOS)
    for color, (seed, dark_seed) in SEEDS.items():
        is_own = color == 'campfire'
        write_ios_icon_set(
            name='AppIcon' if is_own else f'AppIcon-{color.capitalize()}',
            light=recolor(ios_light, seed),
            dark=recolor(ios_dark, dark_seed),
            tinted=ios_tinted,
        )
        save(recolor(web_icon, seed), os.path.join(WEB_RESOURCES, f'icon-192-{color}.png'))
        save(recolor(window_icon, seed), os.path.join(DESKTOP_DRAWABLES, 'app_icon.png' if is_own else f'app_icon_{color}.png'))
        save(recolor(dock_icon, seed), os.path.join(DESKTOP_DRAWABLES, f'dock_icon_{color}.png'))
        if is_own:
            write_packaged_icons(seed)


if __name__ == '__main__':
    main()
