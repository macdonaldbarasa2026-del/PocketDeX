# Runtime measurement research

## Remote PC measurements

PocketDeX currently measures an HTTPS response from the configured Guacamole
gateway. This is a gateway reachability measurement only. It does not cover
authentication, tunnel creation, the selected RDP/VNC host, display updates or
input processing.

Apache Guacamole's JavaScript API exposes client state changes and errors. A
Guacamole client reports a connected state after its tunnel and protocol
handshake succeed, which is a useful session-level signal when the client is
instrumented. A user-managed gateway can now opt in through the documented
`PocketDeXRuntime` WebView message contract below. The listener accepts only
versioned, bounded messages from the configured HTTPS origin's main frame; it
stores only a coarse connection state and fixed detail text, never connection
names, credentials, screen data, or arbitrary gateway messages. The signal is
gateway-reported and is not independently verified by PocketDeX. Without a
compatible gateway integration, remote connection state remains unavailable.

Input round-trip latency needs a request and a corresponding acknowledgment.
The normal keyboard and pointer events do not provide a generic acknowledgment
that an RDP/VNC server processed the event or rendered its result. Measuring
time from Android dispatch to local WebView input handling would only measure
the client path. A defensible end-to-end estimate needs a controlled remote
echo workload (for example, an agreed test screen that visibly responds to
input) or explicit instrumentation on both the Guacamole client and remote
host. Guacamole's protocol synchronization keeps display updates ordered; it
should not be presented as proof that a specific user input was applied.
The client API exposes `Client.onstatechange` and `Client.onsync`. A sync event
indicates the server finished processing earlier client operations and sent
their results. These hooks can support instrumentation in a gateway page, but
do not make a generic input's remote visual response measurable without a
controlled echo workload.

### Safe implementation path

1. Keep the existing HTTPS gateway probe labeled as gateway response time.
2. A gateway owner can bridge Guacamole's `Client.onstatechange` and
   `Client.onerror` events to the configured page with
   `window.PocketDeXRuntime.postMessage(JSON.stringify({version: 1, event:
   "connection", state: "connected"}))`. Send `connecting`, `disconnected`,
   or `error` for the corresponding state. PocketDeX accepts this only from
   the configured HTTPS origin's main frame; show “not available” when no
   compatible integration is installed.
3. Add input latency only with a documented acknowledgment method and report
   its measurement scope (client-to-gateway, tunnel round trip, or full remote
   visual response). Do not infer it from a gateway HEAD request or input send
   time.
4. Keep credentials, clipboard contents, file names and remote screen data out
   of PocketDeX's diagnostic history. Store only bounded session state and
   aggregate timings unless a user explicitly exports diagnostics.

Remote downloads validate every redirect against the configured HTTPS origin.
They retrieve the WebView cookie header for each validated URL separately and
do not save or report cookie values in runtime diagnostics.

Apache references: [Guacamole JavaScript client API](https://guacamole.apache.org/doc/guacamole-common-js/Guacamole.Client.html),
[Guacamole extension capabilities](https://guacamole.apache.org/doc/gug/guacamole-ext.html),
and [Guacamole protocol synchronization](https://guacamole.apache.org/doc/gug/protocol-reference.html).
The origin-limited Android message API is documented in the
[WebViewCompat reference](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addWebMessageListener(android.webkit.WebView,java.lang.String,java.util.Set,androidx.webkit.WebViewCompat.WebMessageListener));
the Android security guidance recommends origin-aware messaging over a broad
JavaScript interface for web content that is not authored by the app.
PocketDeX also handles [Android WebView renderer termination](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination)
by discarding the stopped view and letting the user reconnect with a new one.

## Device thermal status

The Settings capability card samples Android's `PowerManager` thermal status
when opened or resumed on API 29+ and its ten-second `getThermalHeadroom(10)`
forecast on API 30+. Android recommends not polling headroom more often than
about once per second; the Settings card only queries on screen refresh.
Headroom estimates proximity to severe throttling from slow-moving thermal
sensors, not sensor temperature or a direct CPU/GPU performance measurement.
Device makers define thermal behavior, and Android's
developer guidance notes that some devices may report `THERMAL_STATUS_NONE`
even when headroom support is incomplete. Treat it as a useful snapshot, not a
guarantee that sustained workloads are unthrottled. See the
[PowerManager thermal API](https://developer.android.com/reference/android/os/PowerManager#getCurrentThermalStatus()),
[Android thermal headroom guidance](https://developer.android.com/games/optimize/adpf/thermal),
and [AOSP thermal mitigation notes](https://source.android.com/docs/core/power/thermal-mitigation).

On Android 16/API 36+, Settings also queries `SystemHealthManager` for CPU and
GPU capacity headroom on a background thread. With default parameters, CPU
headroom is based on the calling process (PocketDeX), while GPU headroom
describes device GPU capacity. Each is an optional 0–100 estimate and may be
unavailable or unsupported by the device. The CPU API can instead track a
limited set of calling-process thread IDs, but those threads must share core
affinity; the Settings snapshot does not select individual threads. These
calls perform synchronous system-service transactions and may take more than
1 ms, so PocketDeX keeps them off the UI thread. The estimates are not workload
benchmarks or extra compute added to the phone. See the
[CPU headroom parameters](https://developer.android.com/reference/android/os/CpuHeadroomParams.Builder),
[Android 16 headroom overview](https://developer.android.com/about/versions/16/features)
and [SystemHealthManager API](https://developer.android.com/reference/android/os/health/SystemHealthManager).

## Local runtime research

Winlator remains a separate app launch target. PocketDeX records the installed
package version with each launch dispatch, but does not have a supported
interface to enumerate containers or receive process and resource lifecycle
events. Any deeper integration should first identify a stable, documented
Winlator interface and verify its compatibility, licensing, security and
maintenance cost. Keep device/app/backend results in a manually verified
compatibility matrix; a version label is not an app health measurement.
The upstream source manifest currently exports the launcher but keeps its
rendering activity and foreground service private, so PocketDeX cannot safely
dispatch directly to a Windows app through those components.

Android's Linux development environment is an OS-managed Debian VM available
on select devices. PocketDeX can hand off to its Terminal app when the system
publishes a launcher activity, but has no supported interface to inspect or
manage that VM. This state is not included in PocketDeX's runtime session
measurements.
PocketDeX also does not observe Termux process survival, OEM background battery
policy or Linux CPU scheduling; these remain user-managed runtime behaviors.

Android secondary-display feature reporting is only a device capability
advertisement. `ActivityOptions.setLaunchDisplayId` may still be rejected for a
specific display or activity by platform policy; report successful dispatch
separately from the feature flag. PocketDeX now tells the user when an app
launch falls back to the phone after an external-display launch is rejected.
For launch-bound calculations, API 30+ uses a window context on the target
display and its maximum window metrics; older Android versions fall back to
`Display.getRealMetrics()`. Density comes from the target display's
configuration when available. This follows Android's connected-display
guidance and keeps physical-pixel bounds aligned with the display being
targeted. The PocketDeX external-display desktop uses the same helper for its
grid and panel sizing, while density-independent dimensions come from its
display-specific UI context.

## AI and remote compute

The AI provider remains undecided. Provider credentials must not be embedded
in the APK. Any AI feature should be opt-in, disclose the diagnostics sent,
redact secrets and require review before applying generated code or commands.
Remote compute is a separately provisioned workstation accessed through a
remote display protocol; it cannot add physical RAM, CPU or GPU capacity to the
phone.
