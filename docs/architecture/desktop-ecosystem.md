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
  ├── DeviceCapabilityProfile    Android version, ABI, memory, physical keyboard/pointer,
  │                               external presentation displays, secondary-display activity feature
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
   RDP/VNC input latency or resource usage. A gateway may opt in to report
   coarse connection state through PocketDeX's versioned, origin-limited
   WebView message contract; PocketDeX labels it gateway-reported. End-to-end
   input latency still requires a defined acknowledgment or controlled echo
   workload. See [runtime measurement research](../runtime-measurements.md).
   Do not provision cloud compute until provider, cost and credentials are
   explicitly chosen.
4. **Local compatibility research:** PocketDeX now detects and launches the
   separately installed Winlator app on ARM64 devices. Next add per-app
   shortcuts and benchmark a published device/app matrix. Do not assume Android's
   Virtualization Framework is an app-accessible desktop VM: AOSP documents
   platform-signature permission requirements for pVM management, while
   Microdroid lacks Android Java APIs and graphics/UI. AOSP's Android Terminal
   Linux development environment is a separate OS-managed Debian VM on select
   devices, currently terminal-oriented; PocketDeX may launch it through the
   normal Android app catalog but cannot manage its VM. Evaluate user-space
   emulation and OEM-specific options separately; ship only after licensing,
   security and per-device performance are understood.
   Android's companion app-streaming role is also unavailable to ordinary
   installed apps: AOSP restricts role holders to preinstalled privileged
   apps reviewed with the device maker. PocketDeX therefore uses supported
   secondary-display launches and its user-managed Guacamole workspace rather
   than relying on the privileged VirtualDevice streaming APIs or system-level
   input injection.
   A separate Settings guide documents the user-managed Termux:X11 plus
   PRoot-Distro Linux desktop path. The X server requires both its Android app
   and Termux companion package; PRoot-Distro requires shared temporary files
   for X11 sockets. PocketDeX does not call Termux's external command API:
   Termux requires a special permission and an opt-in setting that permits
   other apps to run commands in its environment. See
   [Linux desktop setup research](../linux-desktop-runtime.md), including the
   distinction between per-app background battery restrictions and Termux:X11's
   separate-UID CPU scheduling slowdown. PocketDeX does not change battery policy
   or claim a sustained Linux session/performance guarantee.
5. **AI-assisted engineering:** add opt-in diagnostics and research summaries
   after the runtime boundary exists, with redaction, clear data disclosure and
   user review of every code or shell action.

## Current foundation

PocketDeX has the desktop launcher, app catalog, external-display presentation,
built-in Files/Editor/Terminal/Calculator apps and Android app launch path. The
manifest limits package visibility to apps matching the launcher intent and an
explicit query for Winlator, instead of requesting visibility of all installed
packages. File-picker URI grants make the selected document provider visible
when PocketDeX validates the returned upload URI.
Android launch path now goes through `AppDescriptor`, `RuntimeProvider`,
`RuntimeRegistry` and `AndroidAppRuntime`, which reports whether an app is
ready, missing, disabled or invalid. `GuacamoleRuntime` checks whether the
remote workspace has a valid HTTPS gateway configured before launching the
remote activity. A capability snapshot records Android API, supported ABIs,
Android's advertised and kernel-accessible memory figures, the system-wide
available-memory snapshot, PocketDeX's approximate app heap class and eligible
external presentation displays for runtime decisions
and is visible in Settings as a refreshable device details card. The card also
reports attached non-virtual keyboard and pointer devices so users can identify
missing desktop peripherals. For eligible external presentation displays, it
shows active resolution, refresh rate and logical density from Android's
display APIs. This stays in the live on-device view and is not uploaded or
stored. On API 29+, it also reports the current Android thermal severity and,
on API 30+, Android's ten-second thermal headroom forecast, and on API 36+
PocketDeX-process CPU and device GPU capacity estimates when supported. These
are estimates, not temperatures or benchmark results. It reports the Android
secondary-display activity feature separately
from attached presentation displays; the feature flag does not guarantee that
a particular display launch will be permitted.
Android's freeform/desktop-windowing support varies by OS version and device
maker. Settings reports `FEATURE_FREEFORM_WINDOW_MANAGEMENT`, but PocketDeX
does not treat that device-wide feature as a guarantee that every display will
accept requested launch bounds. Windowed launches skip bounds when Android
does not advertise the feature and tell the user that the app is opening
normally.
Built-in activities declare default desktop-window sizes and minimums for
system-driven launches. Remote PC exposes Android's optional new-window
affordance; when PocketDeX targets an eligible external display on a
freeform-capable device, it requests near-full-display bounds. Android and
device policy may ignore these sizing requests.
On Android versions that expose `Display.isInternal()`, PocketDeX excludes
built-in displays from external-display selection even if they advertise
presentation support. Older versions fall back to the non-default display ID
and presentation category convention.
Android recommends adapting to actual window and display conditions and
handling configuration changes when activities move between displays. Windowed
launch bounds are expressed in screen pixels, so PocketDeX now converts its
minimum window size and cascade offset using the target display's density and
clamps the result to that display's pixel bounds. This snapshot stays local; it
does not claim to measure GPU performance or predict app compatibility. The
registry can accept additional providers.
On Android 15+, Files, Editor, Terminal and Calculator declare
`PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI`. These activities use the default
multiple-instance-compatible launch mode, so supporting System UI's optional
new-window affordance is consistent with their separate per-activity state.
The property only requests a System UI affordance; the OS/device must provide
desktop windowing and actual launch behavior still follows Android task rules.
The Guacamole runtime opens a user-managed HTTPS gateway for RDP/VNC sessions.
Remote file downloads use Android's save picker, validate each redirect against
the configured server origin and obtain the WebView cookie header for the
current URL at each hop. The redirect target never receives a cookie header
captured for a different URL.
`WinlatorRuntime` checks for the separate `com.winlator` app and an ARM64 ABI,
reports the installed package version, then launches its home screen;
`RuntimeSession` stores that version with the dispatch record. PocketDeX does
not yet import individual Windows shortcuts or manage Winlator containers.
The upstream manifest currently keeps the X server display activity and
foreground service private, so this remains a launcher handoff rather than an
app-specific launch integration.
`RuntimeSession` and
`RuntimeSessionStore` keep the latest 20 dispatch/session snapshots locally,
without credentials or transferred file data. Settings exposes the latest
dispatch; Guacamole updates its state through gateway page load, transport
errors and user disconnect. Native per-app remote windows, actual Android and
Winlator process lifecycle callbacks, and session-scoped resource telemetry
remain future work.

The optional PocketDeX external desktop is an Android `Presentation` with
display-specific resources. It refreshes its view when Android reports that
the attached display's properties changed, including size or density. On
supported Android 16 QPR3 devices, the OS can also provide a separate connected
display desktop-windowing workspace; PocketDeX does not control that system
workspace. See [Android connected-display support](https://developer.android.com/blog/posts/android-devices-extend-seamlessly-to-connected-displays)
and the [Presentation display callbacks](https://developer.android.com/reference/android/app/Presentation).
Launching an installed app onto a public external display uses Android's
`ActivityOptions.setLaunchDisplayId` when the device advertises secondary
display activity support. PocketDeX checks display policy where available and
falls back to the phone with a message if Android rejects that launch.
Remote PC includes a user-triggered fullscreen request for Android 14 and
newer. The platform can approve or reject a transition from a multi-window
state, and PocketDeX only changes the toolbar control after approval. It does
not change the remote compute host or the Guacamole connection.
The built-in Editor accepts one text item or `content://` text file by drag and
drop, obtains Android's temporary drag permission before reading, rejects other
URI schemes and non-text MIME types, and enforces the existing 3 MB editor
limit. The text is inserted at the current selection and is not saved
automatically. See [Android desktop drag-and-drop guidance](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/experiences/desktop)
and [cross-app URI drag permissions](https://developer.android.com/develop/ui/views/touch-and-input/drag-drop/multi-window).
The built-in Files list can also start a global, read-only drag for a document
URI from the user-selected SAF tree. It does not request persistable access for
the receiving app; each target must accept and request the temporary grant.

## Platform references

- [Android multi-window support](https://developer.android.com/develop/ui/views/layout/support-multi-window-mode)
- [Android desktop windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing)
- [Android Activity fullscreen-mode request](https://developer.android.com/reference/android/app/Activity#requestFullscreenMode(int,android.os.OutcomeReceiver))
- [Android multi-instance system UI property](https://developer.android.com/reference/android/view/WindowManager#PROPERTY_SUPPORTS_MULTI_INSTANCE_SYSTEM_UI)
- [Android connected displays](https://developer.android.com/develop/adaptive-apps/guides/support-connected-displays)
- [Android ActivityOptions launch bounds](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchBounds(android.graphics.Rect))
- [Android freeform window management feature](https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_FREEFORM_WINDOW_MANAGEMENT)
- [Android display-specific Context resources](https://developer.android.com/reference/android/content/Context#createDisplayContext(android.view.Display))
- [Android Presentation behavior](https://developer.android.com/reference/android/app/Presentation)
- [Android Display.isInternal API](https://developer.android.com/reference/android/view/Display#isInternal())
- [Android Display.Mode API](https://developer.android.com/reference/android/view/Display.Mode)
- [Android display-specific window metrics](https://developer.android.com/develop/adaptive-apps/guides/support-multi-window-mode#window-metrics)
- [Android large-screen keyboard and pointer input](https://developer.android.com/develop/ui/views/touch-and-input/input-compatibility-on-large-screens)
- [Android InputDevice API](https://developer.android.com/reference/android/view/InputDevice)
- [Android ActivityManager.MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo)
- [Android ActivityManager memory class](https://developer.android.com/reference/android/app/ActivityManager#getMemoryClass())
- [Android adaptive desktop app quality guidelines](https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/experiences/desktop)
- [Android multi-window drag-and-drop](https://developer.android.com/develop/ui/views/touch-and-input/drag-drop/multi-window)
- [Android global URI drag flags](https://developer.android.com/reference/android/view/View#DRAG_FLAG_GLOBAL_URI_READ)
- [Android secondary-display activity feature](https://developer.android.com/reference/android/content/pm/PackageManager#FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS)
- [Android package visibility declarations](https://developer.android.com/training/package-visibility/declaring)
- [Android ABI guidance](https://developer.android.com/ndk/guides/abis)
- [Android Virtualization Framework overview](https://source.android.com/docs/core/virtualization)
- [Android companion app-streaming role requirements](https://source.android.com/docs/core/permissions/app-streaming)
- [AVF security and pVM permissions](https://source.android.com/docs/core/virtualization/security)
- [Microdroid capabilities and limitations](https://source.android.com/docs/core/virtualization/microdroid)
- [Winlator Android app and compatibility notes](https://github.com/brunodev85/winlator)
- [Winlator app ABI/build configuration](https://github.com/brunodev85/winlator-app/blob/main/app/build.gradle)
- [Termux:X11 project and setup guide](https://github.com/termux/termux-x11)
- [PRoot-Distro project and commands](https://github.com/termux/proot-distro)
- [Box64 architecture and platform requirements](https://github.com/ptitSeb/box64)
