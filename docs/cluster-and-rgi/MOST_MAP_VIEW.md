# MOST MAP view

The MOST map path puts the phone's decoded CarPlay map into the stock MAP view
on coding 541=1. It does not mirror the MMI screen or use the VC context owner.

## Map preference

The MOST MMI dropdown is **Off / When navigating / Always on**:

- Off restores Audi MAP.
- When navigating shows the live CarPlay map during guidance or when the phone
  owns navigation. This includes apps that supply no route-guidance messages.
- Always on shows the live CarPlay map outside routes too, throughout the connection.
  Missing stream/disconnect still restores stock. This matches the VC's On behavior.

Legacy On and the default select Always on. Stored values remain Off=0, Always=1,
When navigating=2; the UI converts them into the displayed order. The native enable
byte/file field remains boolean. A settings change re-evaluates the current state
without waiting for another phone event.

Guidance arrows are independent. MOST hides both VC map-card options and, when
Features.UPMIX is false, the Spatial Audio/UpMix rows. Stored values are retained.

## One engine, two views

[MostPresentation](../../java_patch/com/luka/carplay/cluster/MostPresentation.java)
shares the request, sizing, readiness, placement and restoration machinery.

| | Arrows | MAP |
| --- | --- | --- |
| Stock → composed context | 73 → 83 | 72 → 82 |
| Renderer displayable | 98 | 99 |
| Size source | stock KDK 20 | stock MAP 33 |
| Measured MU1329 size | 800 × 252 | 800 × 298 |
| Unknown extents | KVS fallback; diagnostic timeout allowed | wait for a measured size |
| Window while idle | kept | removed when released |
| Full-rate request (stock 10) | 30 | 15 |

These are requested stream rates, not a measured end-to-end FPS guarantee.
Alternate stock map context 76/source 58 remains stock. Logical-context reporting
continues to tell stock its own 72/73, including buffered requests.

## The phone's cluster display

The phone offers a second CarPlay display as AirPlay stream 111. The MOST hook
receives/decrypts it and supplies H.264 to `maneuver_render`'s hardware decoder.
`ClusterVideo` reports live-stream state; composition also requires a measured
stock size and the renderer's matching readiness report.

[Receiver](../../hook/mostmap/most_map_hook.c) ·
[Stream lifecycle](../../java_patch/com/luka/carplay/cluster/ClusterVideo.java)

The request/readiness files are `/tmp/carplay_most_map_output` and its `_ready`
companion. A readiness token identifies the presented window. A resize or new
token triggers stock restoration and fresh composition, rather than cropping a
window that no longer exists. A missing request releases the renderer's map window.

## Android Auto on the analogue cluster

The Android Auto hook in `gal` captures its 1920x1080 secondary-display H.264
stream into a separate shared-memory ring. On MOST the renderer selects this ring
for an active Android Auto session, crops the centered 1440x540 viewport and
scales it into the measured 800x298 MAP window. It does not route the map through
Virtual Cockpit or start `altscreen_render`.

The existing **When navigating** and **Always on** map preference also controls
Android Auto; an active Android Auto session counts as navigation because this
receiver hook does not expose a separate map-route lifecycle. Off restores Audi's
MAP. Disconnect also withdraws the MAP request and returns to CarPlay's ring when
that session is active.

The hook yields the shared QCOM decoder before `gal` changes its main-screen
decoder state. `maneuver_render` publishes its ownership and holds the decoder
until three seconds after the last yield request, then resumes from the latest
configuration and key frame.

The firmware investigation confirmed the P5092/MU1329 `gal` and
`libautoreceiver.so` match the existing hook's supported profiles. The Android
Auto native hook still needs to be built with `scripts/build_aa_hook.sh`. For an
SD release, its package builder must install `libaa_cluster_hook.so` and
`aa_startup.sh`, and merge `deploy/smartphone_integrator/aa_child.json` into
`children.gal`; that separate package-tooling repository is not part of this
source tree.

## Cluster platform selection

MOST keeps stock ownership of terminal 1. The VC `altscreen_render` path is not
started for this platform, and its 80/81 context pin is not applied. MOST's map
receiver uses a stock-allowed network path; this package does not enable the
separate firewall change needed by the VC map.

## Input, theme and handover

The wheel zooms CarPlay only while its map is live and composed; otherwise stock
keeps that input. Light/Dark/Automatic follows the cluster setting, with Automatic
using the MMI's night mode. Waze ignores the phone-side zoom/theme requests.

Phone navigation ownership drives the navigation-only policy even without RGI
messages. `NativeRouteStop` handles Audi guidance when the phone takes ownership.
Maps Off does not start an Audi route or disable CarPlay's guidance arrows.

## Validation boundary

`MostMapViewTest` exercises measured/confirmed windows, stock restoration, mode
changes, stream/owner changes, Android Auto activation, independent arrows, zoom
and theme against the shipping Java classes. `CarPlaySettingsAvailabilityTest`
checks real widget shape, label order and saved-choice boot restoration. The
Android Auto source geometry, hardware decode, encoder and phone behavior still
need vehicle evidence for this integration.
