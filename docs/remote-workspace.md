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
3. Select an available remote connection. Use a hardware keyboard and pointer
   for desktop interaction; the Android device and display must support the
   desired windowed/external-display behavior.
4. Choose **Disconnect** to stop the page and clear the WebView cookies. Sign
   out from the Guacamole page first if the server provides a sign-out action.

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
- This first client displays the Guacamole web app in a resizable Android
  activity. It does not yet expose native per-application windows, remote file
  mounts, host setup, or provider-side resource management.
- Cross-origin single sign-on redirects are blocked by the WebView origin rule.
