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
  ├── DeviceCapabilityProfile    Android version, ABI, memory, GPU, displays
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
   `RuntimeProvider`, `RuntimeSession` and a registry; adapt current Android
   launches without changing their behavior.
3. **Remote workspace prototype:** connect to a user-owned host over an
   authenticated, encrypted protocol; stream a single desktop session and
   support keyboard/mouse, resize and reconnect. Do not provision cloud compute
   until provider, cost and credentials are explicitly chosen.
4. **Local compatibility research:** benchmark candidate Linux and Windows
   runtimes against a published device/app matrix. Ship only after licensing,
   security and per-device performance are understood.
5. **AI-assisted engineering:** add opt-in diagnostics and research summaries
   after the runtime boundary exists, with redaction, clear data disclosure and
   user review of every code or shell action.

## Current foundation

PocketDeX already has the desktop launcher, app catalog, external-display
presentation, built-in Files/Editor/Terminal/Calculator apps and Android app
launch path. The next code change should introduce the runtime model around
that launch path before adding emulation or cloud-provider dependencies. This
keeps the existing launcher usable while new runtime types are developed.

## Platform references

- [Android multi-window support](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode)
- [Android desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing)
- [Android connected displays](https://developer.android.com/develop/adaptive-apps/guides/support-connected-displays)
- [Android ABI guidance](https://developer.android.com/ndk/guides/abis)
