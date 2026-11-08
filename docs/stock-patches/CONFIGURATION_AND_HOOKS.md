# Firmware changes

The SD package prepares these changes from verified stock files for MU1329 /
`MHI2Q_ER_AUG22_P5092`. Firmware identity checks and managed backups protect
installation and rollback.

| Component | Change | Purpose |
| --- | --- | --- |
| Smartphone integrator binary | USB cleanup/re-arm correction | Allow wired CarPlay to reconnect after disconnecting. |
| NCM start/stop and OTG launcher scripts | Connection-generation tracking and serialized cleanup | Prevent an old disconnect from removing a newer connection. |
| `libairplay.so` | Reduce the two supported video-decoder queue depths from 4 to 2 | Reduce queued video buffering. |
| MCD binary and configuration | Reduce repeated scanning work and background priority | Reduce unnecessary media-processing load. |
| SI child configuration | Start dio through the hook wrapper and renderer supervisor | Load the hook into dio and retain renderers across session restarts. |
| DIO configuration | Register hook messages and required vehicle-data notifications | Carry guidance, telephone and vehicle information. |
| Hook, renderer and Java patch | Install the application components | Provide MOST maps, guidance, controls and contacts refresh. |

The package leaves the stock audio path and firewall profiles unchanged. It does
not install spatial/UpMix processing, audio-driver patches or wireless CarPlay.

For configuration and recovery, see [installation](../build-and-deploy/DEPLOY_AND_RELEASE.md).
For runtime behavior, see [USB recovery](../connect-and-usb/USB_REPLUG_RECOVERY.md),
[cluster maps](../cluster-and-rgi/MOST_MAP_VIEW.md) and
[architecture](../reference/ARCHITECTURE.md).
