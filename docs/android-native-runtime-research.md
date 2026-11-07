# Android native runtime research

This note collects the native compatibility work that could extend PocketDeX's
desktop shell: shared-memory compatibility for Linux userlands, graphics
translation for Windows/Linux workloads, and reproducible Android cross-builds.
It is research and an integration plan, not a claim that PocketDeX currently
installs or manages these components.

## Where PocketDeX fits

PocketDeX is a launcher and runtime selector. Its current local Windows path
hands off to the separately installed Winlator app; its Linux guide describes
Termux:X11 plus PRoot-Distro, which PocketDeX does not manage. The least risky
next step is to improve compatibility reporting and publish reproducible
research artifacts. Driver installation, container management, and native
library loading belong in a separately reviewed runtime provider or upstream
runtime, not in the launcher UI by implication.

The useful ecosystem model is incremental: keep runtime providers behind the
existing `RuntimeProvider` contract, let independent projects own their
specialized runtime pieces, and contribute fixes upstream where possible. A
PocketDeX provider can report which runtime is installed and its declared
capabilities without claiming that a GPU/driver/app combination is validated.

## Shared memory on Android

Android does not have one universal “shared memory syscalls are missing” rule.
Bionic exports the System V functions `shmget`, `shmat`, `shmdt`, and `shmctl`
from API 26, and those functions call the corresponding Linux kernel system
calls. Whether they work depends on the device kernel configuration and
security policy. Termux documents that System V shared memory is unavailable
in its supported environment and packages an emulation library for Linux
userlands. Bionic does not provide POSIX `shm_open` and `shm_unlink`; a usable
`/dev/shm` mount and the needed API implementation cannot be assumed.
[Bionic's symbol map](https://android.googlesource.com/platform/bionic/+/1e52871/libc/libc.arm64.map)
and [Termux's porting notes](https://github.com/termux/termux-packages/wiki/Common-porting-problems)
show these are distinct libc and kernel questions. A `SIGSYS` report should be
diagnosed from the actual syscall, seccomp policy, and device; it is not a
generic consequence of calling `shm*` on every Android release.

For Android-native code, use `ASharedMemory_create` (API 26+) and map the
returned descriptor with `mmap`. Pass descriptors via Binder
`ParcelFileDescriptor` or Unix sockets with `SCM_RIGHTS`. The NDK API docs
specify that protection can be reduced with `ASharedMemory_setProt`; the API
does not provide a way to re-grant permissions after reduction. The JNI helper
`ASharedMemory_dupFromJava` is API 27+.
[Android NDK memory API](https://developer.android.com/ndk/reference/group/memory)

For legacy Linux binaries, the options have different semantics:

| Option | Coverage | Lifecycle and caveats |
| --- | --- | --- |
| `libandroid-shmem` | System V `shm*` preload emulation | Uses Android shared memory and Unix socket descriptor passing. Its README explicitly says segments are destroyed when the creating process exits, unlike kernel System V segments. It does not claim POSIX `shm_open` support. |
| `libwrapdroid` | System V and POSIX shared-memory calls | Uses a server-created namespace and preload libraries. Its upstream README documents Android 4–10 compatibility, incomplete System V termination/fork handling, and resources freed when the server exits. Treat newer device support as unverified until tested. |
| Native Linux IPC | System V/POSIX APIs where kernel, libc, mounts and policy support them | Most faithful semantics, but availability is device/runtime specific. |

References: [Termux libandroid-shmem](https://github.com/termux/libandroid-shmem),
[libwrapdroid](https://github.com/green-green-avk/libwrapdroid),
[Bionic status](https://android.googlesource.com/platform/bionic/+/main/docs/status.md).

Do not equate shared memory with a universal zero-copy guarantee. It avoids
application-level serialize/copy steps for the shared payload, but mapping,
synchronization, cache behavior, page faults, descriptor transfer, and GPU
buffer import can still cost time. Measure a representative workload and
include correctness/lifecycle tests before claiming a performance gain.

## Graphics translation and mobile GPU drivers

Mesa documents Turnip as its Vulkan driver for Qualcomm Adreno GPUs; its
supported device list and feature set change with Mesa revisions. Upstream
Mesa's Android build guide provides an NDK Meson cross file and a Freedreno
configuration. For a specific phone, record the SoC/GPU model, Android build,
kernel/KGSL interface, Mesa commit, Vulkan extensions, and the runtime that
loads the driver. A patch described as “Gen8”, “7xx”, or “8xx” must be tied to
an upstream commit or an independently reviewable patch and tested against the
actual hardware. A successful compile does not establish hardware support.
[Mesa Android build guide](https://docs.mesa3d.org/android),
[Freedreno/Turnip documentation](https://docs.mesa3d.org/drivers/freedreno.html)

DXVK translates Direct3D 8–11 workloads to Vulkan. Reports of performance
changes with a particular Turnip and DXVK pair are workload-specific. DXVK's
issue #4484 documents a reported regression on Adreno 735 with Turnip 24.3.0
and DXVK 2.5.1, while the maintainer requested a bisect and did not establish
a general rule that every DXVK version at or above 2.4.1 regresses. Treat
version pairing advice as a test matrix, not a universal default. DXVK-Sarek is
a separate compatibility-oriented fork with its own target and release
history; verify the exact repository, commit, license, and supported APIs
before adopting it.
[DXVK issue #4484](https://github.com/doitsujin/dxvk/issues/4484),
[DXVK upstream](https://github.com/doitsujin/dxvk),
[DXVK-Sarek](https://github.com/The412Banner/dxvk-sarek).

Do not carry over the supplied virglrenderer figures (125 FPS in an LXC guest
and 191 FPS on a host llvmpipe pipeline) as expected mobile performance. They
are not a controlled comparison: host, guest, renderer, workload, resolution,
thermal state, and synchronization path differ. VirGL is a virtual GPU protocol
and Gallium driver path; Zink translates OpenGL to Vulkan; llvmpipe is a CPU
software renderer. They are separate configurations, and host results do not
predict a phone's guest performance. Record workload, device, renderer/driver
versions, resolution, frame-time percentiles, power/thermal state and run
duration for any comparison.
[Mesa driver overview](https://docs.mesa3d.org/systems.html)

## Cross-compilation and GitHub Actions

The existing [APK workflow](../.github/workflows/build.yml) builds PocketDeX on
GitHub-hosted Ubuntu runners and keeps Gradle compilation off the phone. Native
Mesa or emulator components would need a separate, manually triggered workflow:
their dependencies and build time should not slow every Android UI build.

Use the NDK's LLVM toolchain directly. The old standalone-toolchain generation
scripts are obsolete; invoke target compilers from
`$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/`. For CMake, use the NDK's
`build/cmake/android.toolchain.cmake` with an explicit ABI and minimum platform.
For Mesa/Meson, follow Mesa's maintained Android cross-file example rather than
assuming all projects accept the same generic profile. Mesa's documented
Turnip configuration currently uses Android platform settings, `freedreno`,
and the KGSL kernel interface; re-check options against the pinned Mesa commit.
[NDK CMake guide](https://developer.android.com/ndk/guides/cmake),
[Mesa Android guide](https://docs.mesa3d.org/android).

Recommended first CI scope:

1. Run only by `workflow_dispatch` or an explicit repository label/branch.
2. Pin the Mesa commit, NDK revision, runner image, Meson/Ninja versions, and
   patch series. Record all of them in the artifact metadata.
3. Start with `arm64-v8a`, the relevant Adreno target, and a small build
   configuration. Add `armeabi-v7a` only if a real supported runtime/device
   needs it; it is not a free compatibility switch for a modern GPU driver.
4. Build on the hosted runner, validate ELF machine type and dependencies,
   retain unstripped symbols for diagnostics, then produce a separate stripped
   artifact. `llvm-strip --strip-all` can remove useful diagnostic symbols and
   should not be the only archived output.
5. Upload artifacts for review. Do not automatically publish custom GPU
   binaries as PocketDeX releases until licensing, provenance, device testing,
   and runtime loading have been checked.
6. Test on representative hardware for Vulkan enumeration, feature reporting,
   graphics correctness, app launch, frame pacing, thermals, and recovery to
   the runtime's stock driver. CI compilation alone cannot validate a kernel
   driver interface or GPU behavior.

PocketDeX now has an NDK dependency for its capability probe, but does not
package Mesa, Turnip, DXVK, or shared-memory emulation components. The probe
shows the GPU identity and Vulkan API version reported to the app by Android's
system driver. This is useful hardware
inventory for research, but it cannot inspect the driver selected by Winlator
or prove compatibility. The next product increment should identify installed
runtime/driver versions only through documented interfaces and label
unverified combinations clearly; the app should not infer support from the
Adreno family name alone. The probe is built by the existing GitHub Actions APK
workflow, with a pinned NDK and CMake; no native compilation is required on the
phone.

## Research references

- [Android shared memory NDK API](https://developer.android.com/ndk/reference/group/memory)
- [Bionic System V symbols](https://android.googlesource.com/platform/bionic/+/1e52871/libc/libc.arm64.map)
- [Bionic missing POSIX functions](https://android.googlesource.com/platform/bionic/+/main/docs/status.md)
- [Termux Android porting notes](https://github.com/termux/termux-packages/wiki/Common-porting-problems)
- [Termux libandroid-shmem](https://github.com/termux/libandroid-shmem)
- [libwrapdroid](https://github.com/green-green-avk/libwrapdroid)
- [Mesa Android cross-compilation](https://docs.mesa3d.org/android)
- [Mesa driver overview](https://docs.mesa3d.org/systems.html)
- [NDK CMake toolchain](https://developer.android.com/ndk/guides/cmake)
- [DXVK upstream](https://github.com/doitsujin/dxvk)
- [DXVK-Sarek fork](https://github.com/The412Banner/dxvk-sarek)
