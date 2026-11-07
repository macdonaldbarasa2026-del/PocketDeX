# Runtime measurement research

## Remote PC measurements

PocketDeX currently measures an HTTPS response from the configured Guacamole
gateway. This is a gateway reachability measurement only. It does not cover
authentication, tunnel creation, the selected RDP/VNC host, display updates or
input processing.

Apache Guacamole's JavaScript API exposes client state changes and errors. A
Guacamole client reports a connected state after its tunnel and protocol
handshake succeed, which is a useful session-level signal when the client is
instrumented. Guacamole also loads JavaScript supplied by server-side
extensions. Both mechanisms require code running inside the Guacamole page;
Android's WebView wrapper does not have that signal when it hosts an arbitrary
user-managed gateway.

Input round-trip latency needs a request and a corresponding acknowledgment.
The normal keyboard and pointer events do not provide a generic acknowledgment
that an RDP/VNC server processed the event or rendered its result. Measuring
time from Android dispatch to local WebView input handling would only measure
the client path. A defensible end-to-end estimate needs a controlled remote
echo workload (for example, an agreed test screen that visibly responds to
input) or explicit instrumentation on both the Guacamole client and remote
host. Guacamole's protocol synchronization keeps display updates ordered; it
should not be presented as proof that a specific user input was applied.

### Safe implementation path

1. Keep the existing HTTPS gateway probe labeled as gateway response time.
2. If a gateway owner installs a compatible Guacamole extension, let that
   extension expose connection state and tunnel error events through a
   documented, versioned page integration. Make the integration opt-in and
   show “not available” for gateways without it.
3. Add input latency only with a documented acknowledgment method and report
   its measurement scope (client-to-gateway, tunnel round trip, or full remote
   visual response). Do not infer it from a gateway HEAD request or input send
   time.
4. Keep credentials, clipboard contents, file names and remote screen data out
   of PocketDeX's diagnostic history. Store only bounded session state and
   aggregate timings unless a user explicitly exports diagnostics.

Apache references: [Guacamole JavaScript client API](https://guacamole.apache.org/doc/guacamole-common-js/Guacamole.Client.html),
[Guacamole extension capabilities](https://guacamole.apache.org/doc/gug/guacamole-ext.html),
and [Guacamole protocol synchronization](https://guacamole.apache.org/doc/gug/protocol-reference.html).

## Local runtime research

Winlator remains a separate app launch target. PocketDeX does not have a
supported interface to enumerate Winlator containers or receive process and
resource lifecycle events. Any deeper integration should first identify a
stable, documented Winlator interface and verify its compatibility, licensing,
security and maintenance cost. Until then, report launch dispatch only and
keep device/app/backend results in a manually verified compatibility matrix.

## AI and remote compute

The AI provider remains undecided. Provider credentials must not be embedded
in the APK. Any AI feature should be opt-in, disclose the diagnostics sent,
redact secrets and require review before applying generated code or commands.
Remote compute is a separately provisioned workstation accessed through a
remote display protocol; it cannot add physical RAM, CPU or GPU capacity to the
phone.
