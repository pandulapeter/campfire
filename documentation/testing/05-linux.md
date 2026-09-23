<!--
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
-->
# Manual tests: Linux desktop

Linux users get only the `.deb` from the latest GitHub release: there is no store and no signature. What can break
there is:

- the package's dependencies on either side of Ubuntu's `t64` transition;
- the window being matched to its launcher (alt-tab, the dock, pinning);
- links opened with no desktop integration;
- X11 drag sources.

Everything else is in `00-core-functional.md`.

## Before you start

- **Machines.** You need Ubuntu **22.04** and Ubuntu **24.04** desktops. Two VMs in UTM, VirtualBox or Parallels
  are fine, and so are live USBs. Use GNOME, the default on both, and add a KDE session (Kubuntu, or Plasma
  installed) if you can.
- **Artifact.** Use `campfire-<version>-linux-amd64.deb` from the latest GitHub release, built on Ubuntu 22.04 by
  `desktop-publish.yml` with `-Pcampfire.desktop.distribution=linux`. Use `linux-arm64.deb` on an ARM VM, which is the
  natural choice on an Apple silicon Mac.
  - To build one yourself on the 22.04 machine: `./gradlew :app:desktop:packageReleaseDeb
    -Pcampfire.desktop.distribution=linux`. This needs `fakeroot` and `dpkg-dev`
    (`sudo apt install fakeroot dpkg-dev`). The output is in `app/desktop/build/compose/binaries/main-release/deb/`.
- **Library folder:** `~/.local/share/Campfire`, or `$XDG_DATA_HOME/Campfire` when that variable is set.
- **Fixtures:** copy the `campfire-fixtures/` folder from `documentation/testing/fixtures/make_fixtures.py`.
- **Tools:** `sudo apt install x11-utils desktop-file-utils xdotool`. You need `xprop` and `xdotool` in an **X11**
  session (choose "Ubuntu on Xorg" at login). Test the default Wayland session separately where noted.
- **Time:** about 1.5 hours across both releases.
- **Record:** the Ubuntu version, X11 or Wayland, GNOME or KDE, and amd64 or arm64. For failures, run
  `/opt/campfire/bin/Campfire` from a terminal and keep its output.

Legend: **P0** can lose or corrupt the library, or blocks the release. **P1** is visible breakage. **P2** is polish.

## Smoke (10 minutes, on 24.04)

- [ ] **LIN-S01** (P0) The package installs
  1. Run `sudo apt install ./campfire-*-linux-amd64.deb`.

  **Expected:** it installs without `--force-depends` and without "unmet dependencies".
  <sub>[r5-18]</sub>
- [ ] **LIN-S02** (P0) The first start
  1. Start Campfire from the app grid.

  **Expected:** the window opens with the demo library. `~/.local/share/Campfire/library/songs` holds the two `.cho`
  files, and the terminal output (run as in "Record" above) shows no exception.
  <sub>[CLAUDE]</sub>
- [ ] **LIN-S03** (P0) Edit, save, restart
  1. Edit a song, press Ctrl+S, close the app and start it again.

  **Expected:** the edit is kept.
  <sub>[features]</sub>

## 1. The package and the `t64` transition

- [ ] **LIN-001** (P0) No `t64` dependencies
  1. Run `dpkg-deb --field campfire-*-linux-amd64.deb Depends`.

  **Expected:** no package name ends in `t64` (for example `libasound2`, not `libasound2t64`).
  <sub>[r5-18]</sub>
- [ ] **LIN-002** (P0) Install and start on 22.04
  1. On Ubuntu 22.04, `sudo apt install ./campfire-*.deb` and start the app.

  **Expected:** it installs and starts, with the window and the demo library.
  <sub>[r5-18]</sub>
- [ ] **LIN-003** (P0) Install and start on 24.04
  1. On Ubuntu 24.04, install the same `.deb` and start the app.

  **Expected:** it installs and starts. The one package works on both sides of the transition.
  <sub>[r5-18]</sub>
- [ ] **LIN-004** (P1) A clean container install on 22.04
  1. Run `docker run --rm -it -v "$PWD:/pkg" ubuntu:22.04 bash -c 'apt-get update && apt-get install -y
     /pkg/campfire-*-linux-amd64.deb && echo INSTALLED'`.

  **Expected:** it prints INSTALLED. This proves the dependency list on a clean system.
  <sub>[r5-18]</sub>
- [ ] **LIN-005** (P1) Upgrade and removal
  1. Install an older `.deb`, then the new one over it.
  2. Run `sudo apt remove campfire`.

  **Expected:** the upgrade replaces the old version and the library is untouched. Removal leaves
  `~/.local/share/Campfire` in place.
  <sub>[app/desktop]</sub>
- [ ] **LIN-006** (P2) The arm64 package
  1. On an ARM VM, repeat LIN-002 and LIN-003 with `linux-arm64.deb`.

  **Expected:** it installs and starts.
  <sub>[r5-18]</sub>

## 2. Matching the window to its launcher

- [ ] **LIN-010** (P1) The desktop entry has a `StartupWMClass`
  1. Run `grep StartupWMClass /usr/share/applications/*ampfire*.desktop`.
  2. Run `desktop-file-validate` on that file.

  **Expected:** exactly one `StartupWMClass=Campfire` line, and the file validates with no errors.
  <sub>[r5-23]</sub>
- [ ] **LIN-011** (P1) The window's class (X11)
  1. In an X11 session, run `xprop WM_CLASS` and click the Campfire window.

  **Expected:** `WM_CLASS(STRING) = "campfire", "Campfire"`, not `com-pandulapeter-campfire-…`.
  <sub>[r5-23]</sub>
- [ ] **LIN-012** (P1) Alt-tab and the overview on GNOME
  1. On GNOME, start the app and press Alt+Tab, then open the Activities overview.

  **Expected:** both show "Campfire" with the app's icon, not a generic gear or a Java name.
  <sub>[r5-23]</sub>
- [ ] **LIN-013** (P1) Pinning does not add a second entry
  1. On GNOME, right-click the running app in the dash and choose Pin to Dash.
  2. Close the app and start it from the pinned icon.

  **Expected:** there is one dash entry, not a second one beside the pinned launcher. The running app is shown on the
  pinned icon.
  <sub>[r5-23]</sub>
- [ ] **LIN-014** (P2) The task manager on KDE
  1. On KDE, repeat LIN-012 and LIN-013 with the task manager.

  **Expected:** the same result.
  <sub>[r5-23]</sub>
- [ ] **LIN-015** (P2) Scaling
  1. Under Wayland at 200% (or fractional) scaling, start the app.

  **Expected:** the window opens at a sensible scale and the text is sharp.
  <sub>[r2-44]</sub>

## 3. Links and the sync consent page

- [ ] **LIN-020** (P0) Connect Dropbox
  1. Settings → Library → Connect.

  **Expected:** the default browser opens the consent page, and after consent the app shows the account.
  <sub>[r5-31]</sub>
- [ ] **LIN-021** (P1) Connect with a broken default browser
  1. Point the default browser at something missing:
     `xdg-settings set default-web-browser nonexistent.desktop`.
  2. Press Connect.

  **Expected:** Connect reports the failure **at once**, with no five-minute wait. Restore the browser with
  `xdg-settings set default-web-browser firefox_firefox.desktop`, or your own.
  <sub>[r5-31]</sub>
- [ ] **LIN-022** (P1) Links through `xdg-open`
  1. In a minimal session without GNOME libraries (for example a plain `openbox` session), open an About link.

  **Expected:** `xdg-open` opens it and the window stays. Where nothing can open it, a snackbar names the address.
  <sub>[r2-45][r5-31]</sub>
- [ ] **LIN-023** (P1) The About section on Linux
  1. Open Settings → About.

  **Expected:** there is no rating row (Linux has no store). The coffee row is shown, and "Every version of Campfire"
  opens the README.
  <sub>[r5-16]</sub>

## 4. Files, the single instance and input

- [ ] **LIN-030** (P1) Drag and drop from the file manager
  1. Drop files, and a folder, from Nautilus (and Dolphin on KDE).

  **Expected:** they are imported, taking only the files directly inside the folder.
  <sub>[r2-43]</sub>
- [ ] **LIN-031** (P2) A broken drag source
  1. **(X11)** Drag something whose transfer fails (for example a file from an archive viewer that is closed
     mid-drag), then do an ordinary drag from Nautilus.

  **Expected:** the broken drop is refused with one log line, and the ordinary drag imports.
  <sub>[r3-25]</sub>
- [ ] **LIN-032** (P1) No file association
  1. Double-click a `.cho` in Nautilus.

  **Expected:** it does **not** open in Campfire by default. This is intentional, because a MIME association would
  claim every text file. Open With → Campfire (if it is listed), or `campfire song.cho` from a terminal, imports it.
  <sub>[app/desktop]</sub>
- [ ] **LIN-033** (P1) A second launch hands over
  1. With the app running, start `/opt/campfire/bin/Campfire ~/song.cho`.

  **Expected:** the running window comes forward and imports the song, and the second process exits.
  <sub>[r2-49]</sub>
- [ ] **LIN-034** (P2) Relaunch while closing
  1. Use `xdotool` to close the window, then immediately start the app with a song. Loop this five times.

  **Expected:** the song opens every time.
  <sub>[r3-24]</sub>
- [ ] **LIN-035** (P2) A read-only data folder
  1. Run `XDG_DATA_HOME=/tmp/ro` with `/tmp/ro` read-only, then start the app.

  **Expected:** it starts and reports the error clearly instead of hanging. The single-instance check fails open.
  <sub>[r2-49]</sub>
- [ ] **LIN-036** (P2) Typing through an input method
  1. With IBus and a Hungarian layout, type `ő ű` into the editor and the search.
  2. With an IBus Pinyin method, type a Chinese title.

  **Expected:** the characters are committed correctly.
  <sub>[r3-20]</sub>
- [ ] **LIN-037** (P1) Fonts for other scripts
  1. Import fixture: RTL song and a Cyrillic song.

  **Expected:** the Hebrew renders (install `fonts-noto` if it shows boxes, and note it) and is right-aligned, with
  chords over their words. The Cyrillic song reads correctly.
  <sub>[r5-35]</sub>
- [ ] **LIN-038** (P1) Language names are localized
  1. Set the app to Hungarian and open the language filter.

  **Expected:** the chips read *angol* / *magyar*. This proves the packaged runtime carries `jdk.localedata`.
  <sub>[r5-17]</sub>
