# Local Windows runtime

PocketDeX exposes a **Local Windows** entry that detects and launches Winlator
when it is installed. Winlator is a separate Android app that runs some x86-64
Windows applications using Wine and Box86/Box64. It does not run every Windows
program, and compatibility and performance depend on the app, phone CPU/GPU,
drivers, and configuration.

## Requirements and setup

- A 64-bit ARM Android device (`arm64-v8a`). Winlator's current Android app
  declares ARM64 as its supported ABI and Android API 26 as its minimum.
- Install Winlator from its [official GitHub releases](https://github.com/brunodev85/winlator/releases/latest).
- Open **Local Windows** in PocketDeX to launch Winlator, then create/configure
  containers and install Windows applications in Winlator itself.

## Stage an installer from PocketDeX Files

On Android 10 or later, select an `.exe` in PocketDeX Files and choose
**Copy to Winlator and open**. PocketDeX streams the selected document into
the shared `Downloads/PocketDeX` folder, then opens Winlator. In a configured
Winlator container, open the `D:` drive, enter `PocketDeX`, and run the
installer. The copy uses Android's Downloads media collection and handles
large files as a stream; a same-name file receives a numeric suffix rather
than being overwritten. PocketDeX sanitizes the filename for Windows path
rules and keeps the staged guest path below the legacy 260-character limit.

This flow transfers the executable but does not install it, choose a container,
create a Winlator shortcut, or report whether the Windows program runs. The
user selects and runs it from Winlator. Android 8 and 9 do not have this
PocketDeX transfer action; copy the installer into Winlator's shared Downloads
folder using a file manager on those versions.

PocketDeX does not bundle, modify, or silently install Winlator. It checks the
package and device ABI, reports the installed package version, then opens
Winlator's exported launcher activity. The local dispatch history records
that version, but cannot observe container or Windows app health. The Windows
application setup and compatibility controls remain in Winlator.
The package is listed explicitly in Android's package-visibility queries; the
launcher does not need broad visibility of unrelated installed apps to detect
this optional runtime.

The [upstream Android manifest](https://github.com/brunodev85/winlator-app/blob/main/app/src/main/AndroidManifest.xml)
currently exports `MainActivity`, while `XServerDisplayActivity` and the
foreground service are not exported. The [upstream app build file](https://github.com/brunodev85/winlator-app/blob/main/app/build.gradle)
identifies the package version in that source checkout. This is not a documented
cross-app launch or status API, and can change between installed releases; the
version capture helps keep integration observations tied to a specific build.

## Compatibility research

Winlator's project describes Wine plus Box86/Box64 and notes application-level
stability/performance tuning. Box64's own documentation says it is a Linux
userspace x86-64 emulator that relies on host ARM64 system libraries; Wine and
graphics translation add further compatibility layers. This makes per-app and
per-device results essential. Keep a compatibility matrix for tested device,
Android version, SoC/GPU, Winlator version, app version, graphics backend and
observed result before making performance claims.

The upstream release page distinguishes beta and final releases, so PocketDeX
reports the installed package version instead of assuming a particular release
is suitable or universally compatible.

Winlator's own guidance directs users to configure per-game options through its
home-screen shortcuts. PocketDeX therefore leaves executable selection and
those compatibility settings to Winlator. Source review confirms that current
upstream Winlator maps its public Downloads directory to the container's `D:`
drive and that its container file manager can start a selected executable.
Its exported `MainActivity` has no `.exe` intent filter or documented
cross-app install/execute API, so PocketDeX stages the file and leaves the
actual run request to the user inside Winlator. These are upstream source
observations, not a stable integration contract.

References: [Winlator project](https://github.com/brunodev85/winlator),
[Winlator app build configuration](https://github.com/brunodev85/winlator-app/blob/main/app/build.gradle),
the [Winlator container drive defaults](https://github.com/brunodev85/winlator-app/blob/main/app/src/main/java/com/winlator/container/Container.java),
the [Winlator file manager launch path](https://github.com/brunodev85/winlator-app/blob/main/app/src/main/java/com/winlator/ContainerFileManagerFragment.java),
Android's [Downloads MediaStore documentation](https://developer.android.com/training/data-storage/shared/media),
and [Box64](https://github.com/ptitSeb/box64).

## Current PocketDeX limits

- Winlator must be installed separately. PocketDeX can stage an `.exe` in the
  shared Downloads folder but cannot manage containers, create Winlator
  shortcuts, confirm program health, or present Winlator apps as
  PocketDeX-managed windows.
- The local runtime is unavailable on devices without `arm64-v8a` support.
- Do not treat Android RAM, CPU or GPU capacity as expandable through this
  local runtime. Remote compute is a separate Remote PC workflow.
