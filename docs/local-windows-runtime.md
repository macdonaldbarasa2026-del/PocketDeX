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
those compatibility settings to Winlator until upstream documents an external
launch contract.

References: [Winlator project](https://github.com/brunodev85/winlator),
[Winlator app build configuration](https://github.com/brunodev85/winlator-app/blob/main/app/build.gradle),
and [Box64](https://github.com/ptitSeb/box64).

## Current PocketDeX limits

- Winlator must be installed separately. PocketDeX only launches its home UI;
  it does not yet import individual `.exe` shortcuts, manage containers, or
  present Winlator applications as PocketDeX-managed windows.
- The local runtime is unavailable on devices without `arm64-v8a` support.
- Do not treat Android RAM, CPU or GPU capacity as expandable through this
  local runtime. Remote compute is a separate Remote PC workflow.
