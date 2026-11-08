# CarPlay child supervisor

The package builder installs these scripts and merges
[`carplay_child.json`](carplay_child.json) into the stock SI configuration.
Use the [managed package](../../docs/build-and-deploy/DEPLOY_AND_RELEASE.md);
this directory is an implementation reference, not a separate installer.

## Startup

`children.carplay.envs` has no `LD_PRELOAD`. `carplay_startup.sh` applies the hook
only to its direct `dio_manager` child, retaining
`IPL_CONFIG_DIR_DIO_MANAGER=/etc/eso/production`.

Startup validates local files, publishes an owner PID, forks the monitor and
immediately executes dio. Network probes, renderer initialization and USB recovery
do not run in this startup path.

The wired MOST package installs the hook, `maneuver_render`, `altscreen_render`,
flag atlas and supervisor helpers under `/mnt/app/root/hooks`. It does not install
APAC/UpMix libraries, model blobs or the VC firewall change.

## Renderer ownership

- The monitor waits one second for dio to execute, then adopts or starts renderers.
  A replacement dio generation replaces the owner; obsolete monitors stop acting.
- Normal checks use recorded numeric PIDs and `/proc/<pid>`. Initial adoption or
  a stale PID uses a bounded `pidin ar` scan and verifies the executable identity.
- Renderers survive ordinary dio exits. Missing renderers restart after a
  five-second backoff. Cold VC startup staggers their EGL initialization.
- On MOST, the monitor does not start `altscreen_render`; `maneuver_render` owns
  both arrows and the map window.
- SI cleanup has no generation identity. It calls Audi's
  `/etc/scripts/carplay_cleanup.sh` for mdnsd/PPS cleanup and leaves persistent
  renderers alone. Never overwrite that stock script.

The monitor neither signals dio nor touches USB/OTG. Physical recovery belongs to
SI and the matching NCM generation fence. `restartDelay: 3000`,
`stopTimeout: 8000` and `portResetTime: 250` retain the intended settling delay.

## Diagnostics

`/tmp/carplay_wrapper.log` should show `exec dio_manager` before the monitor's
`generation=... active`, then renderer adoption/start. An ordinary dio exit records
that persistent renderers remain alive.

[Runtime ownership](../../docs/reference/ARCHITECTURE.md#process-ownership) ·
[USB recovery](../../docs/connect-and-usb/USB_REPLUG_RECOVERY.md) ·
[Upstream supervisor and QNX audit](https://github.com/luka-dev/mib2q-carplay-rgi/blob/21f501611b0b28dbdeb2849e8443ce90ec9517ff/deploy/smartphone_integrator/README.md)
