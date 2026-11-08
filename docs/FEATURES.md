# Feature support

## Supported configuration

The SD package targets MU1329 / `MHI2Q_ER_AUG22_P5092`, with an analogue
instrument cluster and colour map-over-MOST display (coding 541=1).
Virtual Cockpit requires additional firewall configuration and has not been validated.

## Included

- CarPlay maps on the cluster: Off, When navigating or Always on.
- Guidance arrows, lane guidance, distance and navigation text.
- Steering-wheel map zoom and Light / Dark / Automatic map appearance.
  Waze ignores zoom and appearance requests.
- Cover art, calls, recents, favourites and phone battery on the cluster.
- Directory contacts through Audi's address-book service, refreshed on reconnect.
- Vehicle range, outside temperature and wheel speed sent to the phone.
- Short-press Siri and automatic dismissal of the CarPlay telephone popup.
- MMI touchpad D-pad input and CarPlay visibility during parking popups.
- USB reconnect recovery, smaller video-decoder queues and reduced media scanning.

Maps on Cluster and guidance arrows are independent settings. The standard Audi
stereo path remains in use.

## Not enabled

| Feature | Limitation |
| --- | --- |
| Wireless CarPlay | The package uses wired CarPlay. |
| Spatial audio, stereo UpMix and 5.1 | Audio processing is disabled; multichannel output on the internal amplifier needs separate routing/DSP support. |
| Native touchpad focus | Disabled following an Apple Music focus crash; the Java D-pad bridge handles touchpad input. |
| Enhanced Siri | Short-press activation is supported; enhanced voice mode is not enabled. |
| CarPlay video playback and partial-360 display | Not enabled by the parking-popup behavior. |

Disabled components may remain in the source tree. See the
[architecture](reference/ARCHITECTURE.md) and [build profile](build-and-deploy/BUILD_SYSTEM.md#build-profile-and-validation)
for implementation details.
