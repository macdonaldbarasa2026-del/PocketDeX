# PocketDeX and Winlator integration audit

Audit date: 2026-11-08. The upstream code findings below are tied to the
Winlator superproject commit `30aafbdb9eea47b92669201621e03cd2cf04540d` and its
`app` submodule pin `a030f552f452158a2db64fdb32b490fa19c0b48d`. Upstream
changes; check
the relevant pin and notices again before each imported-code or binary update.

## Executive summary

PocketDeX is an Android desktop launcher with a runtime-provider boundary. It
already has a Winlator handoff and a SAF-to-Downloads `.exe` staging path, but
it does not contain a Windows execution engine. Winlator is not a single
library that can be added to PocketDeX: the inspected source is a full Android
application, a large private Wine/Linux userspace, native ARM64 libraries,
graphics backends, and an Android-hosted X server. Its container, process,
audio, graphics and input code is coupled to its own app, resources, private
storage and native build.

The safe first increment is a device-neutral hardware preflight integrated into
PocketDeX's existing runtime/settings architecture. It reports ARM64 ABI,
memory relative to the requested 6 GiB planning target, and whether Android's
system Vulkan loader exposes a device. It never identifies a GPU family as a
compatibility guarantee, never treats RAM as a hard launch gate, and clearly
separates Android's driver from Winlator's selected renderer. It does not claim
that Wine or a Windows program runs inside PocketDeX.

## PocketDeX repository audit

- `app/src/main/AndroidManifest.xml` declares PocketDeX as a regular Android
  home/launcher app. There are no virtualization, root, broad storage, or
  package-install permissions. Winlator is visible by its explicit package
  query; Android app launches remain subject to Android's package visibility
  and activity/window policy.
- `DesktopView` owns PocketDeX's in-process windows for built-in Files, Editor,
  Terminal, Calculator and Settings. Those are Android views/activities, not a
  general X11 or Wine window server.
- `RuntimeProvider`, `RuntimeRegistry`, `AppDescriptor` and `SupportReport`
  form an existing backend boundary. `WinlatorRuntime` checks ARM64 and the
  installed package, starts Winlator's exported launcher, and records dispatch;
  Android does not expose Winlator container or process health to PocketDeX.
- `WinlatorExecutableTransfer` copies user-selected SAF documents into shared
  Downloads, and `WinlatorExecutableLauncher` hands control to Winlator. The
  user still selects a container and runs the installer inside Winlator.
- `VulkanDeviceProbe` currently enumerates Vulkan devices visible to the
  PocketDeX process through Android's loader. `DeviceCapabilityProfile`
  captures ABI, memory, thermal, display and input information. Neither probe
  can identify a graphics driver selected by another app.
- `app/src/main/cpp/CMakeLists.txt` builds only PocketDeX's small Vulkan probe
  and links the Android system Vulkan library. The Android app has no bundled
  Wine, Box64, Box86, Mesa, DXVK, VKD3D, PRoot, PulseAudio or Windows system
  files.
- Gradle dependencies are AndroidX WebKit and JUnit; Android NDK/CMake are used
  for the Vulkan probe. There are no other third-party native archives in
  PocketDeX's app source.
- No root `LICENSE`, `NOTICE`, or dependency-license inventory was present in
  the audited PocketDeX checkout. PocketDeX code's redistribution terms are
  therefore unspecified in this checkout; do not silently copy or redistribute
  upstream implementation code until project ownership/licensing is resolved.

## Winlator source and component audit

The public Winlator superproject points to three Git submodules: `app`
(`brunodev85/winlator-app`), `gladio`, and `vortek`. The app pin inspected here
contains 299 Java paths under `com.winlator`, an Android manifest, a sizable
native tree and packaged rootfs/driver assets. The source tree is available at
[Winlator](https://github.com/brunodev85/winlator),
[winlator-app at the inspected pin](https://github.com/brunodev85/winlator-app/tree/a030f552f452158a2db64fdb32b490fa19c0b48d),
[Gladio](https://github.com/brunodev85/gladio), and
[Vortek](https://github.com/brunodev85/vortek).

| Area | Evidence in the inspected source | PocketDeX integration consequence |
| --- | --- | --- |
| App/runtime lifecycle | `MainActivity`, `RootFSInstaller`, `RootFS`, `XEnvironment`, `Container`, `ContainerManager`, `GuestProgramLauncherComponent` | Runtime lifecycle is private to Winlator. The launcher handoff is the supported cross-app boundary found; no container launch API was found. |
| Wine and x86 translation | Wine installers and Wine helpers; `box64/box64-0.4.4.tzst` and Box64 settings/preset code | Wine/Box64 use a packaged Linux rootfs and per-container config. The inspected Android app targets `arm64-v8a`; this is not a drop-in PocketDeX Java dependency. |
| Box86 | Upstream project description references Box86/Box64; the inspected asset tree exposes a standalone Box64 archive but no standalone Box86 source/build target. | Do not claim a separately integrated Box86 backend from this snapshot. Determine the exact 32-bit guest path from rootfs contents and release provenance before representing compatibility. |
| Graphics | Driver archives include Turnip, VirGL, Zink, Vortek and Gladio; wrapper archives include DXVK and VKD3D. Native CMake includes Winlator, Vortek renderer, VirGL renderer, MIDI handler, libadrenotools and Gladio renderer. | GPU vendor and runtime backend must be detected/configured by Winlator. PocketDeX's system Vulkan probe alone cannot select or validate any of them. |
| Linux userspace/filesystem | `rootfs.tzst`, container templates, rootfs installer, container drive mapping and container file manager | The Linux rootfs and Windows prefixes are installed and managed by Winlator in its own app storage. SAF permission does not grant PocketDeX access to that private tree. Shared Downloads is the supported file handoff. |
| Audio | PulseAudio archive/library plus Java ALSA/PulseAudio components and native audio code | Audio is a multi-process/native runtime feature, not a simple Android audio API flag. PocketDeX has no current audio bridge into Winlator. |
| Keyboard, mouse and windows | Winlator's X server, XServerView, input/device handlers, gamepad controls, and its own display activity | These windows are rendered inside Winlator's Android activity. Android does not let PocketDeX reparent another app's private X server or windows into its desktop. |
| PRoot | No PRoot source module, executable asset, or Gradle dependency appears in the inspected app tree. | Do not list PRoot as a Winlator component based on assumption. Winlator uses its own rootfs/container launch implementation. |
| Build dependencies | Gradle: AppCompat 1.4.0, AndroidX Preference 1.2.1, Material 1.4.0, zstd-jni 1.5.2-3, XZ 1.7, Commons Compress 1.20, lifecycle-process 2.5.1. NDK 24.0.8215888; CMake 3.22.1; compile SDK 35; min SDK 26. | These are upstream snapshot versions, not recommendations to transplant wholesale. Native build graph and asset extraction must be reviewed/pinned separately. |

The upstream manifest exports `com.winlator.MainActivity` with only MAIN and
LAUNCHER actions. Its X server display activity and foreground service are not
exported. `ContainerFileManagerFragment` starts that private display activity
with internal `container_id` and `exec_path` extras; it does not register an
external `.exe` VIEW entry point. Opening a selected EXE in Winlator's private
activity is therefore not available through a stable cross-app Android
contract. This source path is also why PocketDeX cannot just copy the two
intent extras and expect Windows execution to work.

## Reuse, incompatibilities, and licensing

- The Winlator superproject and inspected app submodule carry GNU LGPL 2.1.
  Gladio carries LGPL 2.1, Vortek carries LGPL 2.1, and the inspected bundled
  `libadrenotools` source carries BSD 2-Clause. These are only a partial
  inventory: archives and native subtrees contain additional upstream projects
  and notices that must be enumerated individually before redistribution.
- Wine, Box64, Mesa/Turnip, DXVK, VKD3D, audio libraries, fonts, codecs and
  Windows redistributables each have their own licensing and attribution
  requirements. The compressed rootfs/driver archives need a file-level SBOM
  and source offer/source-code plan before they can be shipped by PocketDeX.
  No Microsoft Windows installation or proprietary system files may be
  bundled.
- Reusable *architectural patterns* include per-container configuration,
  application shortcuts, runtime-owned filesystem mappings, backend-specific
  support reports, and explicit device/renderer selection. The current
  implementation classes are tied to Winlator resources and its container
  manager. Importing those classes without the app's storage, native libraries,
  JNI registration, resources and license notices will not work.
- PocketDeX has no declared root license. Before copying LGPL code, resolve
  PocketDeX's own licensing and satisfy the LGPL notice/source and relinking
  requirements for any distributed combined work. GPL components require
  separate distribution analysis. The current branch now pins the Winlator app
  and Box64 as source submodules and adds an embedded runtime flavor. Do not
  treat the notices as a complete binary SBOM: compressed rootfs and native
  payload provenance and license/source parity still need a full file-level
  audit before claiming distribution compliance.

## Embedded runtime implementation status

- `third_party/winlator-app` pins upstream app commit
  `a030f552f452158a2db64fdb32b490fa19c0b48d`; `third_party/box64` pins
  `2f130fab1d6e1a4ee8a71dc60cfdfcc839ad192a`. PocketDeX's source adaptations
  are maintained as `tools/patches/winlator-pocketdex-scoped-storage.patch`
  and applied by the runtime module during `preBuild`.
- `:winlator-runtime` compiles Winlator's Java/resources/native CMake project
  as an Android library. `embeddedWinlator` packages its activities and ARM64
  native payload into the same APK. `standard` remains a separate flavor. The
  embedded flavor excludes upstream Microsoft Windows component archives and
  SONiVOX soundfont files; containers default those Windows components off.
- The patch redirects Winlator's D:/E: storage roots, logs and control exports
  to PocketDeX app-specific directories, removes the startup broad-storage
  permission gate, disables default Windows redistributable downloads, and
  makes restart target the bundled Winlator activity. EXE import copies a
  SAF-selected file to PocketDeX private external storage before launching
  Winlator. Android still owns lifecycle and activity windows.
- The APK's embedded activity opens Winlator's own container UI. This is a
  real runtime inclusion, but not yet PocketDeX-managed X11 windows or a
  one-click EXE/container launcher. Do not claim either capability until
  implemented and tested. Android activity embedding is a possible later
  route; it requires explicit host activity/window orchestration.
- The release workflow checks out submodules, provisions both NDK versions,
  tests both variants, builds both APKs, and checks the embedded APK contains
  Winlator native code and rootfs. CI success demonstrates packaging/build,
  not successful EXE execution on a physical device.

## Phased implementation plan

1. **Repository and licensing audit — completed in this increment.** Preserve
   the component/pin/dependency findings here. Resolve PocketDeX license and
   create a per-file/per-archive SBOM before any upstream code or runtime asset
   is redistributed.
2. **ARM64 compatibility engine — first working slice implemented.** Build
   testable PocketDeX-side preflight from Android ABI, kernel-visible memory,
   and Vulkan visibility. It is advisory; 6 GiB is a planning reference, not an
   upstream hard minimum. Next: add durable device/runtime/backend test records
   and a structured Vulkan capability model.
3. **Wine and Box64 integration.** Evaluate a pinned upstream Winlator runtime
   as a separate, user-installed runtime versus a source fork. Reuse only
   license-cleared code and build artifacts; first define environment bootstrap,
   Wine prefix lifetime, process supervision and crash recovery. Do not bundle
   Box86 unless its exact source and ABI path are confirmed.
4. **Linux userspace and filesystem.** Design app-private per-user prefixes,
   scoped SAF import/export and explicit shared directories. Never grant broad
   storage or attempt access to Winlator's private data. Do not add PRoot unless
   a reviewed concrete need exists; it is not a requirement demonstrated by
   the inspected Winlator app.
5. **Graphics and audio.** Add capability enumeration and tested backend
   selection across GPU vendors. Track Vulkan extensions, Android driver,
   Winlator/Mesa build, DXVK/VKD3D version, audio path and device together.
   Keep fallback paths and avoid replacing OEM drivers.
6. **PocketDeX desktop integration.** Introduce owned runtime sessions and
   window surfaces only after an in-process runtime API exists. Keep Winlator's
   private Activity as an external app window until then; do not claim its
   windows are PocketDeX-managed.
7. **Windows application launcher.** Add app records, per-app config and
   shortcut creation only after PocketDeX can reliably identify, supervise and
   stop guest processes. Until then retain the staged-file/manual Winlator flow.
8. **Performance and testing.** Use CI for deterministic unit/native builds;
   require physical arm64 Android tests across GPU vendors, 6 GiB baseline,
   thermal state, RAM pressure, input, audio, graphics correctness and
   lifecycle recovery before compatibility/performance claims.

## First implementation in this increment

`WinlatorCompatibilityEngine` evaluates ABI support, whether total RAM meets
the requested 6 GiB planning reference, and whether the Android system Vulkan
loader reported a physical device. It is shown in Settings alongside the
existing hardware inventory. Tests cover ARM64 and non-ARM64, unknown data,
below-baseline RAM remaining launchable, and vendor-neutral Vulkan reporting.
This is a PocketDeX preflight, not a copied Winlator runtime and not proof that a
particular EXE will work.
