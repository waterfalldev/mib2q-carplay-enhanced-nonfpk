# Architecture

The application adds a native hook and Java patch to stock MHI2Q CarPlay, with
persistent renderers supervised for each dio generation. The package builder owns
installation; the application owns runtime behavior and component tests.

## Process ownership

```text
smartphone_integrator
  carplay_startup.sh → exec dio_manager + libcarplay_hook.so
  carplay_monitor.sh → persistent renderers for that generation
j9 / lsd
  carplay_hook.jar → module lifecycle, BAP adapters, MMI and display control
```

On MOST, `maneuver_render` owns both the arrows scene (window 98) and decoded map
(window 99). The monitor does not start `altscreen_render` for that platform.
The VC uses its separate AltScreen context/renderer path.

The supervisor, hook bus, renderer connection and USB/NCM interface have their own
identity/generation checks. Do not collapse them into one PID or restart owner.
See [renderer supervisor](../../deploy/smartphone_integrator/README.md).

## Native hook

[hook/main.c](../../hook/main.c) registers modules over shared framework seams.
[airplay_seams.c](../../hook/framework/airplay_seams.c) owns shared AirPlay
interposers; subscribers do not add competing definitions of those symbols.

The wired profile includes route guidance, MOST map reception, cover art,
telephone/car data and decoder-related hooks. Advanced audio and wireless modules
are compiled out. Stock main audio, calls and microphone routing remain in use.

## Java patch

- `CarPlayApp` owns module startup/stop and transport publication.
- `ClusterPlatform` resolves MOST, VC or stock/RGI-only presentation from coding.
- `MostPresentation` composes only the requested MOST pictures, on the HMI thread,
  with measured extents/readiness and stock restoration.
- `RouteGuidance` and `BAPBridge` deliver guidance while respecting stock BAP contracts.
- The gated telephone services publish CarPlay calls; the Directory retains Audi's
  own address-book provider and uses an explicit refresh after reconnect.
- `CarPlaySettingsUi` stores preferences in HMI persistence and updates native models.

[Bus protocol](../../hook/framework/bus_protocol.h) ·
[MOST map](../cluster-and-rgi/MOST_MAP_VIEW.md) ·
[Telephony](../cluster-and-rgi/CARPLAY_TELEPHONY.md)

## MMI touchpad

The Java bridge converts touchpad movements into D-pad focus events. Native
trackpad focus is disabled because it triggered an Apple Music focus crash.

## Navigation handover

The Java patch suppresses only incoming CarPlay navigation ownership from stock at the
DSI seam. Since stock no longer sees that takeover, `NativeRouteStop` ends Audi's
route when the phone takes navigation. Preserve the independent phone/speech and
outgoing serialization contracts.

## Validation

Java/native regressions cover state, transport and stock-linkage contracts. The
package workflow also validates firmware fingerprints and managed installation.
Host checks do not simulate the physical encoder, all phone behavior or a new car
configuration. See [feature support](../FEATURES.md) for supported capabilities.
