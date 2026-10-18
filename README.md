# MHI2Q CarPlay Enhanced

_The iPhone's own CarPlay map and turn-by-turn on the instrument cluster of an Audi with the MHI2Q head unit, including analogue clusters with a colour centre display (map-over-MOST)._

A fork of [luka-dev/mib2q-carplay-rgi](https://github.com/luka-dev/mib2q-carplay-rgi), which
does all of the Virtual Cockpit, route-guidance, hook and toolchain work. This fork adds the MOST
cluster and builds its packages with
[mib2q-carplay-enhanced-tooling](https://github.com/waterfalldev/mib2q-carplay-enhanced-tooling).
Thanks to [Luka](https://github.com/luka-dev) for the project this is built on.

> [!WARNING]
> **Disclaimer:** Use at your own risk. These patches modify firmware binaries and system
> configuration on your infotainment unit. The installer keeps a backup of every file it replaces,
> but back up anything you change by hand as well. The authors are not responsible for any damage,
> bricked units or warranty issues resulting from their use.
>
> Update the unit to the latest firmware for your region **before** you install (US `P5087`, ER
> `P5092`, CN `K1004`), and **roll back before** any later firmware update. A package built for another firmware can leave
> the MMI on the Audi logo; the installer refuses one that does not match, but if you are ever stuck,
> see the [recovery guide](https://github.com/luka-dev/mib2q-carplay-rgi/issues/24#issuecomment-5849641990).

> [!WARNING]
> **Remove any other CarPlay cluster or AltScreen mod first** (MIBSI, the CarPlay AltScreen toolbox,
> mibr and similar), with its own uninstaller or by reflashing the stock firmware. Only one mod can
> own the cluster: another one takes the cluster video and rewrites the CarPlay configuration, and its
> leftovers do the same (upstream issues
> [#39](https://github.com/luka-dev/mib2q-carplay-rgi/issues/39),
> [#53](https://github.com/luka-dev/mib2q-carplay-rgi/issues/53)).

## What you get

Plug in the iPhone and start navigating in CarPlay; there is nothing to switch on.

- **MOST clusters** (coding 541=1): the phone's own CarPlay map in the cluster's MAP view, zoomed with
  the steering-wheel roller and following the cluster's Light / Dark / Automatic setting, and
  CarPlay's next turn in the arrows view. Works with Apple Maps, Google Maps and Waze (Waze ignores
  zoom and theme). Tested on MU1329.
- **Everywhere:** CarPlay turn-by-turn, lane arrows and distance on the cluster and head-up display,
  cover art on the cluster, the MMI touchpad moving the CarPlay focus, and parking views no longer
  hiding CarPlay.
- **Calls and car data:** CarPlay calls, recents, favourites and the phone's battery on the
  cluster's phone tab; range, outside temperature and wheel speed sent to the phone.
- **Siri and the phone app:** a short press of the wheel's voice button starts Siri, and opening
  the phone app no longer stops at the "iPhone is in Apple CarPlay mode" popup.
- **Virtual Cockpit** (541=2): upstream's cluster map and route text are built in, but this fork's
  packages do not yet open the firewall port the map needs, so expect it not to appear. Untested.

<a id="not-shipped"></a>Not included (upstream has them; off in this fork's builds): 5.1 surround
and the UpMix setting, and wireless CarPlay ([details](docs/README.md#-this-fork)).

## Install on the car

You need a package for your firmware and an SD card with
[M.I.B.](https://github.com/Mr-MIBonk/M.I.B._More-Incredible-Bash) set up on the unit. Ready-made
packages are on the [Releases](https://github.com/waterfalldev/mib2q-carplay-enhanced-nonfpk/releases)
page, one per firmware (so far MU1329, `MHI2Q_ER_AUG22_P5092`); for any other firmware,
[create a package](#create-a-package).

1. Unzip the release (or copy everything inside a built package's `sdcard/` folder) to the root
   of the M.I.B. SD card, so the card holds `mod/`.
2. Insert the card, **disconnect CarPlay**, then in the green engineering menu run
   **M.I.B. → Advanced Settings → Run Custom Script** (older M.I.B. releases: **Run individual
   script**).
3. Wait for the result line at the end. `0` means installed. Wait a few seconds more, then
   restart the head unit with CarPlay still disconnected.
4. Connect the iPhone and start a route.

The installer checks the firmware and every file before it writes anything, and keeps verified
backups of what it changes. If you already have Luka's own release or M.I.B.'s NavActiveIgnore patch
installed, remove that with its own uninstaller first; the installer stops if it finds either.

**To remove it,** run the same menu entry again with the same card, then restart the head unit. The
card remembers that the next run is a rollback; it saves the logs to the card first, then puts
the original files back.

## Create a package

Packages are built on Windows by the
[tooling repository](https://github.com/waterfalldev/mib2q-carplay-enhanced-tooling) from a commit of
this one. You need PowerShell 7, Git (with Git Bash) and Docker Desktop.

1. Build the QNX cross-compiler image once:

   ```sh
   git clone https://github.com/luka-dev/qnx65-armv7-toolchain
   cd qnx65-armv7-toolchain
   ./host-scripts/qnx-run.sh build
   ```

2. Clone this repository and the tooling repository side by side, and build the test image from
   this one:

   ```sh
   docker build --platform linux/amd64 -t carplay-rgi-host-tests:local -f tests/Dockerfile.host .
   ```

3. Prepare a firmware folder from your unit's stock firmware: `Firmware.psd1`, the reconstructed
   `lsd.jar`, the original `lsd.jxe`, and byte-exact stock `smartphone_integrator.json` and
   `dio_manager.json`. What goes in `Firmware.psd1`:
   [inputs and build](https://github.com/waterfalldev/mib2q-carplay-enhanced-tooling/blob/main/docs/install-and-recovery.md#inputs-and-build).
   Keep it outside both repositories; never use files from a modified unit.

4. From the tooling repository:

   ```powershell
   ./packaging/Build-Package.ps1 -SourceRepository <this-checkout> `
       -FirmwareRoot <firmware-folder> -Dependencies <cache-folder> `
       -WorkRoot <scratch-folder> -OutputRoot <packages-folder> -Ref HEAD -ToolingRef HEAD
   ```

It builds everything in Docker, runs all the checks (about 3 minutes) and writes
`mib2q-carplay-enhanced_<firmware>_source_<commit>_tooling_<commit>/` with the `sdcard/` folder to
copy. That folder holds no personal data and can be shared with owners of the same firmware;
keep the build log beside it to yourself, as it records your local paths.

<a id="logging"></a>

## Logs

Everything logs to `/tmp` on the unit (RAM, cleared on restart):

| File | From |
| --- | --- |
| `/tmp/carplay_hook.log` | the native hook in `dio_manager` |
| `/tmp/carplay_java.log` | the Java patch |
| `/tmp/maneuver_render.log` | the cluster renderer (and the MOST map decoder) |
| `/tmp/carplay_wrapper.log` | startup and the renderer monitor |

With a shell on the unit, `touch /mnt/app/carplay_verbose` turns on detailed logging from the next
phone connection. Without one, a rollback is the only way to get the logs onto the card for now.
When reporting a problem, include the log lines from the moment it happened.

## For developers

- Component builds and tests (no PowerShell needed): `./scripts/build_java.sh`,
  `./scripts/build_hook.sh`, `./scripts/build_renderers.sh`, `./scripts/run_tests.sh`; inputs and
  variants are in [Build System](docs/build-and-deploy/BUILD_SYSTEM.md).
- What a package installs, and what upstream's installer did beyond it:
  [Deploy and Release](docs/build-and-deploy/DEPLOY_AND_RELEASE.md).
- All documentation: [docs/](docs/README.md). How the MOST cluster works:
  [MOST Cluster](docs/cluster-and-rgi/MOST_CLUSTER.md) and
  [MOST MAP view](docs/cluster-and-rgi/MOST_MAP_VIEW.md). Read [Landmines](docs/LANDMINES.md)
  before changing a subsystem.
- Changes to the Virtual Cockpit, audio and other upstream features are best sent
  [upstream](https://github.com/luka-dev/mib2q-carplay-rgi).

## License

GPL-3.0-or-later ([LICENSE](LICENSE)). Stock firmware files, fonts and other third-party material
keep their owners' terms ([NOTICE](NOTICE.md)).

Thanks for prior work: [ludwig-v](https://github.com/ludwig-v/wireless-carplay-dongle-reverse-engineering),
[EthanArbuckle](https://github.com/EthanArbuckle/iPhone18-3_26.1_23B85_Restore),
[adi961](https://github.com/adi961/mib2-android-auto-vc), [@fifthBro](https://t.me/fifthBro) and
[xPaiiN](https://github.com/xPaiiN) for telephony.
