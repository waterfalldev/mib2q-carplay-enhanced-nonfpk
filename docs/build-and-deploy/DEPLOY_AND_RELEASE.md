# Installation and rollback

Use the managed SD package from the
[package repository](https://github.com/waterfalldev/mib2q-carplay-enhanced-tooling).
Its [installation/recovery guide](https://github.com/waterfalldev/mib2q-carplay-enhanced-tooling/blob/main/docs/install-and-recovery.md)
describes input validation, ownership and recovery.

## Install

1. Choose the package for the exact firmware. Current prepared support is MU1329,
   `MHI2Q_ER_AUG22_P5092`, on the tested MOST car.
2. Remove any other CarPlay cluster/AltScreen mod or NavActiveIgnore patch using
   its own uninstaller. Unknown modified files are refused.
3. Extract the SD ZIP to the M.I.B. card root: `mod/` must be directly on the card.
4. Disconnect CarPlay and run **M.I.B. → Advanced Settings → Run Custom Script**
   (older M.I.B.: **Run individual script**).
5. Wait for result `0`, then a few more seconds. Restart the head unit manually
   with CarPlay disconnected.

The installer checks the HMI identity and every affected file, stages changes,
keeps verified backups and records managed state last. It does not reboot the unit
or stop its processes automatically. Keep the complete card and backup directory.

## Rollback and logs

After a successful install the card is armed for rollback. Run the same entry
again to collect logs and restore the original files, then restart manually.
Collect the live capture before rollback when diagnosing an issue. Roll back
before a firmware update.

If installation refuses a fingerprint or backup, retain its log and use the
package guide's recovery procedure. Do not bypass identity checks or delete a
backup to make the installer continue. For a unit stuck at the Audi logo, see
[the recovery reference](https://github.com/luka-dev/mib2q-carplay-rgi/issues/24#issuecomment-5849641990).

## On-unit files

| Component | Location |
| --- | --- |
| Hook, renderers, atlas and runtime scripts | `/mnt/app/root/hooks/` |
| Java patch | `/mnt/app/eso/hmi/lsd/jars/carplay_hook.jar` |
| SI and DIO configuration | `/mnt/system/etc/eso/production/` |
| Managed backups | `/mnt/app/root/carplay-rgi-backup/` |
| Managed state | `/mnt/app/root/hooks/carplay_rgi_install.state` |

The package also replaces the verified SI binary, `libairplay.so`, MCD/config and
USB/NCM scripts. Originals are pinned in the package and verified for rollback.
See the [firmware changes](../stock-patches/CONFIGURATION_AND_HOOKS.md).

The legacy SD paths `mod/carplay-rgi` and on-unit backup names remain for upgrade
compatibility. On MOST, `maneuver_render` owns the arrows and map windows;
`altscreen_render` is not started for that cluster path. VC's firewall support and
advanced-audio payloads are not installed.
