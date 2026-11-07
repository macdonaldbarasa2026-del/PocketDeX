# Android app windows and remote Windows programs

## What can run inside PocketDeX

PocketDeX's Files, Editor, Terminal, Calculator and Settings have native
PocketDeX views. The desktop can place those views in movable windows with
minimize, restore, maximize and close controls. They are local and remain
available when no Remote PC server is configured or connected.

An installed app from another Android package is different. Its activity is
owned by that package and Android's task/window manager. A regular launcher
cannot take its view or process and forcibly reparent it into PocketDeX. On
devices that advertise freeform window management, PocketDeX can request
window bounds and Android supplies the app window and its system controls. The
new **Open Android apps in windows by default** setting makes that request for
normal launches; the per-app menu still offers a fullscreen launch. Android can
ignore the window request or open normally when the device does not support
freeform windows. See [ActivityOptions launch bounds](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchBounds(android.graphics.Rect))
and [Android desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing).

## Conditional cross-app embedding

Android 13+ has cross-app Activity Embedding. It is not a universal workaround
for third-party app windows:

- The target app must explicitly trust the host certificate or opt in to
  untrusted embedding. The default is to reject untrusted embedding.
- AndroidX WindowManager presents an activity split inside one task; it is a
  pane layout, not an arbitrary floating multi-window surface that PocketDeX
  can freely move, minimize and restore.
- The feature also depends on device window extensions and system support.
- Android warns that embedding lets the host resize, crop and reposition UI,
  which creates clickjacking risks. PocketDeX must not bypass the target app's
  opt-in or trust decision.

Therefore, PocketDeX uses native in-app windows for its own app views and
Android-managed desktop windows for other installed apps. A future opt-in
embedding path could be evaluated for target apps that explicitly allow it,
but it would remain a separate split-screen mode. See the Android
[cross-application embedding trust model](https://developer.android.com/develop/ui/views/layout/activity-embedding#cross-application-embedding)
and [WindowManager release notes](https://developer.android.com/jetpack/androidx/releases/window).

## Offline and remote PC behavior

The local desktop and built-in apps do not depend on Remote PC. Local Android
apps continue to run on Android, and the optional local Windows runtime remains
Winlator. Connecting to Guacamole opens a separate remote desktop session; it
does not migrate, close or replace Android apps. A Windows `.exe` must run on a
Windows environment, locally through a compatible runtime such as Winlator or
on a remote Windows host.

PocketDeX's Remote PC activity already supports Guacamole's HTML file upload
picker. To move an `.exe` to the remote computer, connect to the Guacamole
session, open its file-transfer menu, choose **Upload Files**, then select the
file in Android's picker. The remote administrator must enable upload/file
transfer for that connection; Guacamole disables RDP drive transfer by default
unless configured. The file is transferred to the server/remote session, where
the user can launch it. This is an explicit transfer, not automatic APK or app
migration. See the Apache Guacamole
[file transfer guide](https://guacamole.apache.org/doc/gug/using-guacamole.html#transferring-files)
and [RDP transfer configuration](https://guacamole.apache.org/doc/gug/configuring-guacamole.html#rdp).

Winlator's current PocketDeX integration opens Winlator's home screen. It does
not expose a documented cross-app API for PocketDeX to import `.exe` shortcuts,
create containers, or embed the Windows app surface. Those tasks stay in
Winlator unless its upstream project documents a supported integration
contract. See [local Windows runtime notes](local-windows-runtime.md).
