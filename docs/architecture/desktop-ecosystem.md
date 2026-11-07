# PocketDeX desktop ecosystem architecture

## Product goal

Make an Android phone a useful desktop workstation with a keyboard, mouse and
external display. Keep Android apps native and add PC workloads through an
explicit runtime that can be selected per app. Treat compatibility as something
to measure per device and workload; do not promise that every PC app runs
perfectly on every phone.

Android can host resizable desktop windows on supported devices, but the
available behavior depends on the Android version and device maker. Android
apps run against Android APIs and device ABIs. A Windows or desktop Linux app
needs a compatibility runtime/emulator on the phone or execution on a separate
computer. A cloud service cannot add physical RAM, CPU cores or GPU memory to
the phone: it can run a workload remotely and stream its display and input.

## Runtime class model

Keep the launcher independent of any one PC runtime. The first stable boundary
is an app descriptor plus a runtime provider:

```text
WorkspaceShell
  ├── AppCatalog                 installed Android apps + user-added PC apps
  ├── WindowCoordinator          launch, focus, bounds, display, reconnect
  ├── DeviceCapabilityProfile    Android version, ABI, memory, presentation displays,
  │                               secondary-display activity feature
  └── RuntimeRegistry
        ├── AndroidRuntime       installed Android packages (existing path)
        ├── LinuxRuntime         future local/container or remote Linux session
        └── WindowsRuntime      future local compatibility or remote PC session

RuntimeProvider
  ├── canRun(AppDescriptor, DeviceCapabilityProfile) -> SupportReport
  ├── install(AppDescriptor) -> InstallResult
  ├── launch(AppDescriptor, WorkspaceTarget) -> RuntimeSession
  └── observe(RuntimeSession) -> lifecycle, logs, resource usage

RuntimeSession
  ├── input: keyboard, pointer, clipboard, file transfer
  ├── output: surface/frame stream, audio, session status
  └── controls: suspend, resume, resize, reconnect, stop
```

`SupportReport` must explain prerequisites and limitations instead of returning
only a boolean. A remote session should expose network quality, round-trip
latency, resolution and reconnect state. The launcher must never label remote
resources as local RAM or local GPU capacity.

## Compute and storage boundaries

- **On-device:** Android UI, launch/catalog, local files, keyboard/mouse input,
  and native Android workloads.
- **Optional local PC runtime:** isolated user-installed runtime with explicit
  storage and device permissions. Evaluate performance, licensing, ABI support,
  thermal load and security before selecting one.
- **Remote workstation:** a separately provisioned PC or Linux host runs the
  application. PocketDeX sends input and receives encoded display/audio frames.
  Files move only through an explicit user-selected transfer or mounted remote
  storage flow.
- **AI development assistant:** a later opt-in development feature can research
  compatibility, explain logs and propose patches. Keep provider credentials
  out of the APK; use a secured backend or user-supplied local configuration.
  Require review before applying generated code or commands.

## Delivery sequence

1. **Desktop reliability:** validate launcher behavior across Android versions,
   external displays, keyboard/mouse, permissions, lifecycle and app resizing.
2. **Runtime boundary:** add `AppDescriptor`, `SupportReport`,
   `DeviceCapabilityProfile` and `RuntimeProvider`; route Android launches
   through `AndroidAppRuntime` and allow providers to register by runtime id.
   This boundary now has Android, Guacamole and Winlator providers. The local
   `RuntimeSessionStore` keeps a bounded recent history of launch dispatches;
   Guacamole sessions also record gateway page lifecycle, response time and
   HTTP status. Android/Winlator process lifecycle callbacks and
   session-scoped resource telemetry remain to be added.
3. **Remote workspace prototype:** the first client is implemented through a
   user-managed HTTPS Apache Guacamole web application, with server-side RDP or
   VNC bridging. The client supports reconnect, system file pickers for file
   transfers, a single HTTPS gateway response-time probe, and opt-in activity
   launch on an external display when the device allows it. It does not report
   RDP/VNC input latency or resource usage. Do not provision cloud compute
   until provider, cost and credentials are explicitly chosen.
4. **Local compatibility research:** PocketDeX now detects and launches the
   separately installed Winlator app on ARM64 devices. Next add per-app
   shortcuts and benchmark a published device/app matrix. Do not assume Android's
   Virtualization Framework is an app-accessible desktop VM: AOSP documents
   platform-signature permission requirements for pVM management, while
   Microdroid lacks Android Java APIs and graphics/UI. Evaluate user-space
   emulation and OEM-specific options separately; ship only after licensing,
   security and per-device performance are understood.
5. **AI-assisted engineering:** add opt-in diagnostics and research summaries
   after the runtime boundary exists, with redaction, clear data disclosure and
   user review of every code or shell action.

## Current foundation

PocketDeX has the desktop launcher, app catalog, external-display presentation,
built-in Files/Editor/Terminal/Calculator apps and Android app launch path. The
Android launch path now goes through `AppDescriptor`, `RuntimeProvider`,
`RuntimeRegistry` and `AndroidAppRuntime`, which reports whether an app is
ready, missing, disabled or invalid. `GuacamoleRuntime` checks whether the
remote workspace has a valid HTTPS gateway configured before launching the
remote activity. A capability snapshot records Android API,
supported ABIs, system memory state and connected presentation displays for
runtime decisions and is visible in Settings as a refreshable device details
card. It reports the Android secondary-display activity feature separately
from attached presentation displays; the feature flag does not guarantee that
a particular display launch will be permitted. This snapshot stays local; it
does not claim to measure GPU performance or predict app compatibility. The
registry can accept additional providers.
The Guacamole runtime opens a user-managed HTTPS gateway for RDP/VNC sessions.
`WinlatorRuntime` checks for the separate `com.winlator` app and an ARM64 ABI,
then launches its home screen; PocketDeX does not yet import individual Windows
shortcuts or manage Winlator containers. `RuntimeSession` and
`RuntimeSessionStore` keep the latest 20 dispatch/session snapshots locally,
without credentials or transferred file data. Settings exposes the latest
dispatch; Guacamole updates its state through gateway page load, transport
errors and user disconnect. Native per-app remote windows, actual Android and
Winlator process lifecycle callbacks, and session-scoped resource telemetry
remain future work.

## Platform references

- [Android multi-window support](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode)
- [Android desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing)
- [Android connected displays](https://developer.android.com/develop/adaptive-apps/guides/support-connected-displays)
- [Android secondary-display activity feature](https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)
- [Android ABI guidance](https://developer.android.com/ndk/guides/abis)
- [Android Virtualization Framework overview](https://source.android.com/docs/core/virtualization)
- [AVF security and pVM permissions](https://source.android.com/docs/core/virtualization/security)
- [Microdroid capabilities and limitations](https://source.android.com/docs/core/virtualization/microdroid)
- [Winlator Android app and compatibility notes](https://github.com/brunodev85/winlator)
- [Winlator app ABI/build configuration](https://github.com/brunodev85/winlator-app/blob/main/app/build.gradle)
- [Box64 architecture and platform requirements](https://github.com/ptitSeb/box64)
