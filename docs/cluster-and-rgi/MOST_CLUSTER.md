# Cluster guidance

On coding 541=1, the analogue instrument cluster receives map and arrows pictures
over MOST. CarPlay guidance replaces the maneuver picture in Audi's arrows view
and supplies navigation text, lane information and distance updates.

## Display behavior

- Audi keeps ownership of view selection and terminal 1.
- `MostPresentation` substitutes the CarPlay arrows context while guidance is active.
- `maneuver_render` draws the arrows at the stock view's dimensions.
- Audi's original picture is restored when guidance ends or CarPlay disconnects.
- Cluster maps use their own setting; turning maps off does not disable arrows.

The renderer reports its window size and readiness before Java composes the view.
Resize and reconnect events retire the old window before presenting a replacement.
Display service calls run on the HMI event thread.

## Maps and controls

[Maps on Cluster](MOST_MAP_VIEW.md) supports Off, When navigating and Always on.
Steering-wheel zoom applies while the CarPlay map is live and displayed. The
cluster's Light / Dark / Automatic setting controls map appearance; Waze ignores
these zoom and appearance requests.

[Calls and contacts](CARPLAY_TELEPHONY.md) use the cluster telephone tab independently
of map and guidance preferences.

## Implementation

- [Presentation engine](../../java_patch/com/luka/carplay/cluster/MostPresentation.java)
- [Renderer](../../maneuver_render/)
- [Runtime components](../reference/ARCHITECTURE.md)
