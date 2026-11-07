# Linux desktop runtime research

## Android's built-in Linux terminal

On select devices, Android provides a Developer options switch for a Linux
development environment. The Android Terminal app downloads a Debian-based
image and runs it in an OS-managed VM through AVF. AOSP describes terminal
access through a WebView connected to a VM service; graphical Linux apps are a
future partner use case in the current documentation, so this is not a GUI
desktop replacement. Availability and activation remain controlled by Android
and the device maker. PocketDeX can launch the Terminal app when Android exposes
it as a launcher app, but does not manage the VM or report its state.

AVF's protected-VM management is restricted to apps signed with the platform
key. Microdroid also lacks Android Java APIs and graphics/UI, so PocketDeX
cannot create a general-purpose AVF desktop VM as an ordinary installed app.
The built-in Android Terminal is a system-managed product path, separate from
PocketDeX's built-in Terminal and from the Termux:X11 path below.

## Supported user-managed path

Termux:X11 is an Android X server add-on paired with a Termux package. Its
upstream guide says Android 8 or later is required and both the Android app and
the companion package must be installed. The app is distributed from the
project's nightly release; Termux installs the companion package from its
graphical package repository. The shared-UID APK variant only works with the
GitHub-signed Termux app, so installations from other distribution sources
must not mix that variant's signatures.

PRoot-Distro can install and manage a Linux userland without root. For X11
applications in a PRoot container, Termux:X11's guide requires `--shared-tmp`
(or a correctly mapped `TMPDIR`) so the container can reach the X11 socket.
The example below follows the upstream Debian/XFCE pattern:

1. Install Termux and Termux:X11 from compatible sources and open Termux once.
2. In Termux, run `pkg install x11-repo`, then `pkg install termux-x11-nightly proot-distro`.
3. Create a Debian environment with `proot-distro install debian`.
4. Start the server in Termux with `termux-x11 :1 &`.
5. Enter the container with `proot-distro login debian --shared-tmp`.
6. Inside Debian, install a desktop: `apt update && apt install xfce4 dbus-x11`.
7. Start it inside Debian: `DISPLAY=:1 dbus-launch --exit-with-session xfce4-session`.

Termux:X11 also supports tagged X displays as separate Android windows; its
documented example starts `termux-x11 :1 -tag main` and
`termux-x11 :2 -tag second`. These are X-server windows managed by Termux:X11,
not a PocketDeX feature or a guarantee that a given OEM will place either
window on a particular monitor. Its README also warns that Android can give
Termux less CPU time while the regular Termux:X11 app is in front. The optional
shared-UID APK can avoid that scheduling penalty, but is compatible only with
the GitHub-signed Termux app.

If Android stops Termux or Termux:X11 after they are backgrounded, check the
per-app battery controls for both applications. Android's **Unrestricted**
setting allows more background work and may consume more battery; manufacturer
policies and device state still affect behavior. This is separate from the
regular Termux:X11 build's reported CPU-priority slowdown when X11 itself is
foreground. The shared-UID build is the upstream workaround for that scheduling
case, but requires the matching GitHub-signed Termux app. Neither setting nor
build guarantees a particular CPU performance or session lifetime.

This is a Linux userland sharing Android's kernel, CPU, memory, GPU access and
thermal limits. PRoot is not a hardware virtual machine and does not add
physical RAM or GPU capacity. Linux GUI support is through the X server; app
compatibility, graphics acceleration, input and performance depend on the
device and packages. Architecture-sensitive apps also need binaries compatible
with the phone's userspace/ABI unless the user separately configures emulation.

## PocketDeX integration boundary

PocketDeX Settings presents this as a manual setup guide with links to the
upstream projects. The launcher does not install packages, invoke Termux,
configure a distribution or report Linux desktop session state. Termux's
`RUN_COMMAND` interface allows other apps to execute commands in its user
environment only after the user grants `com.termux.permission.RUN_COMMAND` and
enables `allow-external-apps`; the latter explicitly permits arbitrary external
commands. Adding that bridge would be a separate security-sensitive feature,
not a harmless launch intent.

Upstream references:

- [AOSP Linux development environment use case](https://source.android.com/docs/core/virtualization/usecases)
- [AOSP AVF security requirements](https://source.android.com/docs/core/virtualization/security)
- [Android 15 Linux terminal setup on select devices](https://developer.android.com/about/versions/15/release-notes#enable_the_linux_development_environment_on_device_using_developer_options)
- [Android background power limits and battery settings](https://developer.android.com/topic/performance/power/power-details)
- [Termux:X11 README and setup guide](https://github.com/termux/termux-x11)
- [PRoot-Distro README](https://github.com/termux/proot-distro)
- [Termux RUN_COMMAND intent and permission requirements](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Termux external-app configuration](https://github.com/termux/termux-packages/blob/master/doc/termux.1.md.in)
