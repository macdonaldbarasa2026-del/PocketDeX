# PocketDeX

PocketDeX is an Android **desktop-mode launcher** that turns your phone or tablet
into a PC-style workspace. It replaces the home screen with a desktop, a Start
menu and a taskbar, and it can extend that desktop to an external monitor.

> PocketDeX is a launcher, not a bootloader or a replacement operating system.
> It runs on top of Android and uses the multi-window / multi-display features
> that your device exposes.

## PocketDeX ecosystem (v2.0)

The launcher ships with its own built-in desktop apps, so the experience works
without depending on whatever is installed on the phone:

- **Files** — storage file manager over Android's Storage Access Framework.
  Pick a storage root once, then browse folders, open text files in the
  built-in Editor, open other files with any installed app, create folders
  and files, rename and delete. Hold and drag a file to share it with another
  app using a temporary read-only URI grant.
- **Editor** — plain-text editor with load/save over SAF, "Save As",
  word-wrap toggle, live line/column status, monospace typography and hardware
  keyboard shortcuts (`Ctrl+S` to save, `Ctrl+Shift+S` to save as). Drop one
  text item or text file into the editor to insert it at the caret.
- **Terminal** — an embedded `/system/bin/sh` session inside the launcher with
  Ctrl+C to stop a running command, clear/stop controls and quick command
  buttons, plus hardware keyboard input.
- **Calculator** — expression calculator with live result preview
  (`+ - * / % ( ) ^`).
- **Remote PC** — connect to a user-managed Apache Guacamole HTTPS gateway for
  remote RDP/VNC desktops. PocketDeX displays the remote session, offers an
  Android 14+ fullscreen request from its toolbar when running windowed, and
  the PC runs on the configured remote host. Gateway owners can optionally
  report coarse connection state; a stopped Android WebView renderer can be
  replaced through Reconnect.
- **Local Windows** — launch the separately installed Winlator runtime on
  supported 64-bit ARM devices to run compatible x86-64 Windows applications.
- **Linux desktop setup** — Settings includes a guide for a separately managed
  Termux + PRoot-Distro + Termux:X11 desktop. It shares Android's kernel and
  device resources; it is not a VM and PocketDeX does not execute Termux
  commands or manage Linux sessions. On select devices, Android also offers a
  separate Debian-based Linux terminal VM in Developer options; it is a
  command-line environment, not a graphical desktop. Termux/X11 background
  reliability and CPU scheduling depend on Android and device-maker policies.

Ecosystem apps appear in the Start menu tiles, default taskbar dock, desktop
grid and the empty-desktop right-click menu. You can pin or unpin them like
any other app, and toggle the desktop ecosystem section in Settings.

## Features

- **PC-style desktop** with your wallpaper, an app icon grid, labels and a
  wallpaper dim control.
- **Start menu** with instant app search, all-apps grid and quick actions
  (wallpaper, default-home settings, PocketDeX settings).
- **Taskbar** with Start button, pinned apps, recent apps, clock/date, battery,
  network status and a settings shortcut.
- **Windowed launching** through Android freeform window bounds where the device
  supports it (fallback: normal fullscreen). Built-in apps request usable
  default sizes, and Remote PC can open as a separate system window.
- **External display desktop** over HDMI / USB-C using a second `Presentation`
  desktop, and per-app "Open on external display".
- Per-app external-display launches follow Android's device and display policy;
  PocketDeX reports when Android falls back to opening the app on the phone.
- The PocketDeX `Presentation` desktop refreshes its layout when Android reports
  changes to its attached display. Supported Android 16 QPR3 devices can also
  provide an OS-managed connected-display desktop workspace; it is separate
  from PocketDeX's optional presentation.
- **Desktop setup check** in Settings reports detected physical keyboard and
  pointer devices, active mode and density for usable external presentation
  displays, Android-advertised and kernel-accessible memory, a system-wide
  available-memory snapshot, PocketDeX's approximate app heap class, Android
  thermal status and forecast, plus Android 16 PocketDeX-process CPU and
  device-GPU capacity estimates when supported, secondary-display activity
  support and whether Android advertises
  freeform window management. Freeform behavior can still
  vary by display and device maker.
- **Multiple built-in workspaces** — on Android 15+ System UI can offer new
  windows for Files, Editor, Terminal and Calculator. Availability depends on
  the device's desktop windowing UI.
- **Remote PC status** — HTTPS gateway response timing is distinct from remote
  session state. A gateway administrator may opt in to a versioned,
  origin-limited Guacamole connection-state message; unsupported gateways are
  reported as unavailable. Neither signal measures RDP/VNC input latency.
- **Keyboard & mouse support**:
  - Start / Meta key or Search key: open the Start menu
  - Esc: close the Start menu
  - Tab/arrow keys + Enter: focus and open the selected desktop or Start item
  - Right-click: context menu
  - Alt+Tab: switch recent apps
  - Remote PC: toolbar Full button or Alt+Enter requests fullscreen on Android 14+
  - F5: reload the app list; Start, menu and open actions trigger once per key press
- **Customization**: icon size, automatic or fixed grid columns, labels,
  double-tap to launch, taskbar fade, fullscreen, clock seconds and external
  desktop toggle.
- **Live updates**: package install/uninstall broadcasts refresh the app list.
- **Limited app visibility**: the launcher queries apps with launcher activities
  and declares Winlator by package; it does not request visibility of every
  installed package.
- **Set as default home**: press Home and choose PocketDeX.

## Install

1. Download `app-debug.apk` from the latest GitHub Release.
2. Allow "Install unknown apps" for your file manager/browser if prompted.
3. Install and open PocketDeX.
4. Press the Home button and choose **PocketDeX** as your home app.
   You can also open PocketDeX Settings → **Set PocketDeX as default home**.

## Build

The APK is built on GitHub Actions, not on the phone:

- Push to `main` (or run the workflow manually).
- The workflow builds `app/build/outputs/apk/debug/app-debug.apk`, uploads it as
  an artifact and publishes it to the `latest` release.

Local build (requires a desktop Android SDK):

```bash
./gradlew assembleDebug
```

## Remote PC

Remote PC requires an existing Apache Guacamole server configured for RDP or
VNC. Enter its HTTPS web address in PocketDeX Settings → **Remote PC workspace**.
See [the remote workspace setup notes](docs/remote-workspace.md) for the
connection model and security requirements. Remote downloads use Android's
save picker and follow only HTTPS redirects on the configured server origin.

Local Windows execution requires Winlator to be installed separately. See
[the local compatibility notes](docs/local-windows-runtime.md) for support
limits and the official download source.

For a separately managed Linux desktop, see Settings or the
[Linux runtime research](docs/linux-desktop-runtime.md).

## Known limitations

- **Freeform windowed apps** require the device/ROM to support freeform
  multi-window. On phones that do not, "Open in window" falls back to
  fullscreen.
- **External-display desktop** is shown while PocketDeX is the visible home app.
  Android does not allow ordinary apps to keep a presentation desktop on an
  external display while they are fully in the background.
- Launching another app on a secondary display can be restricted by the OEM.
  PocketDeX catches the failure and falls back to the phone display.
- The taskbar lives inside the launcher. A system-wide floating taskbar over
  other apps would require the "display over other apps" permission and was
  intentionally left out of this version.
- PocketDeX does not unlock bootloaders, root devices or change the operating
  system.
