# Remote PC workspace

PocketDeX's Remote PC app connects to a user-managed Apache Guacamole web
application. Guacamole's server side bridges the browser client to configured
RDP or VNC hosts. Windows/Linux applications execute on those hosts; PocketDeX
shows the streamed session and forwards interaction. This does not add remote
CPU, RAM or GPU capacity to Android processes.

## Requirements

- A reachable Apache Guacamole web application with RDP or VNC connections
  already configured.
- An HTTPS URL with a valid certificate. PocketDeX rejects HTTP URLs and
  cancels TLS certificate errors.
- Network access from the phone to the Guacamole web application. The Guacamole
  server itself must be able to reach the configured RDP/VNC host.

Apache documents the server and RDP connection setup in its
[configuration manual](https://guacamole.apache.org/doc/gug/configuring-guacamole.html).
Guacamole supports RDP connections and can expose a remote application or a
full desktop depending on the host configuration.

## Connect

1. In PocketDeX Settings → **Remote PC workspace**, enter the base HTTPS URL,
   such as `https://desktop.example.net/guacamole/`.
2. Choose **Save and connect**. PocketDeX opens the Guacamole web client, where
   you authenticate with the account configured by the server owner.
   Settings can optionally route this activity to an attached external display.
   Android must advertise activity launches on secondary displays, and the
   display must be on, non-private and allowed for this activity; otherwise
   PocketDeX opens it on the current display.
   On desktop-windowing systems, Remote PC requests a near-full-display window
   on that external display and exposes Android's optional **New window**
   affordance for a separate session. Each system-opened window gets its own
   local runtime-session record. Android may ignore window-size hints.
3. Select an available remote connection. Use a hardware keyboard and pointer
   for desktop interaction; the Android device and display must support the
   desired windowed/external-display behavior.
4. Use **Reconnect** to return to the gateway if its page loses connection.
   The in-app status line reports gateway page/network errors, and a separate
   measurement reports the HTTPS gateway response time and HTTP status. This
   probe does not measure RDP/VNC input latency. The remote connection state is
   shown only when the gateway page has the optional PocketDeX integration
   below; otherwise PocketDeX reports that state as unavailable.
5. Choose **Disconnect** to stop the page and clear the WebView cookies. Sign
   out from the Guacamole page first if the server provides a sign-out action.

Settings → **Runtime session status** shows recent launch handoff and gateway
page state. Android and Winlator are reported as dispatched because PocketDeX
does not receive lifecycle callbacks from those other applications. Guacamole
gateway readiness also does not prove an RDP/VNC connection is active.

When the gateway requests an upload, PocketDeX opens Android's system file
picker. PocketDeX returns only `content://` URIs with a read grant from a
registered provider outside its own package; it does not grant broad storage
access. Downloads from the configured HTTPS server
prompt for a destination through Android's system save picker. Redirects are
limited to the configured server origin, and the session cookie is not sent to a
different host. For each redirect, PocketDeX obtains the WebView cookie header
for that exact HTTPS URL, so cookies are selected for that URL rather than
reusing the initial path's header. The server and Guacamole connection must have
file transfer enabled for these controls to work.
The picker uses WebView's documented file chooser callback and validates the
returned provider and read grant before handing the selected file to the page. See the
[Android WebView file chooser API](https://developer.android.com/reference/android/webkit/WebChromeClient#onShowFileChooser(android.webkit.WebView,android.webkit.ValueCallback,android.webkit.WebChromeClient.FileChooserParams))
and [Guacamole file transfer configuration](https://guacamole.apache.org/doc/gug/configuring-guacamole.html#rdp).
Cookie selection uses Android's [CookieManager URL lookup](https://developer.android.com/reference/android/webkit/CookieManager#getCookie(java.lang.String)),
which returns cookies for the requested URL; HTTP cookie rules scope cookies by
request path as well as host ([RFC 6265](https://www.rfc-editor.org/rfc/rfc6265.html#section-5.1.4)).
Android makes the provider app visible to PocketDeX when that URI grant is
provided, so file selection does not require global package visibility.

### Optional gateway connection status

A gateway administrator can bridge Guacamole client lifecycle events to
PocketDeX using the origin-limited `PocketDeXRuntime` WebView message object.
Send only the version, event, and coarse state; do not include credentials,
connection names, or other session data:

```javascript
window.PocketDeXRuntime.postMessage(JSON.stringify({
  version: 1,
  event: "connection",
  state: "connected"
}));
```

Supported states are `connecting`, `connected`, `disconnected`, and `error`.
The bridge is available only to the configured HTTPS origin, and messages from
subframes or other origins are ignored. The label remains explicitly
gateway-reported because PocketDeX cannot independently verify the gateway's
claim. This integration does not measure input or display latency. See
[Guacamole client state events](https://guacamole.apache.org/doc/guacamole-common-js/Guacamole.Client.html#onstatechange)
and Android's [origin-limited WebView messaging API](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addWebMessageListener(android.webkit.WebView,java.lang.String,java.util.Set,androidx.webkit.WebViewCompat.WebMessageListener)).

PocketDeX stores the server URL only. It does not store the Guacamole password
or remote host credentials. It enables JavaScript and DOM storage because the
Guacamole client requires a browser runtime, but disables file/content access,
file-origin access and mixed HTTP content. It provides no JavaScript-to-native
bridge and cancels TLS certificate failures. Top-level navigation stays on the
configured HTTPS origin.

## Current limits

- The remote server, network, account, RDP/VNC host and any cloud compute are
  provisioned and paid for separately by the user.
- Responsiveness depends on the network route, server load, encoding and phone
  display. PocketDeX does not guarantee a particular latency or application
  compatibility.
- The gateway response-time probe is a single HTTPS HEAD request to the
  configured gateway. It does not represent frame delivery, input round-trip
  time, or the health of a selected RDP/VNC connection.
- Measuring selected-connection state requires instrumentation inside the
  Guacamole page. Its `Client.onstatechange` and `Client.onsync` hooks are
  available to a gateway page integration, but a generic input event has no
  acknowledgment proving that the remote host processed and displayed it, so
  input latency needs a defined echo workload or equivalent remote-side
  instrumentation. See
  [runtime measurement research](runtime-measurements.md).
- This first client displays the Guacamole web app in a resizable Android
  activity. It does not yet expose native per-application windows, a mounted
  remote filesystem, host setup, or provider-side resource management.
- Cross-origin single sign-on redirects are blocked by the WebView origin rule.
