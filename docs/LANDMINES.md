# Engineering constraints

Implementation rules to preserve when changing MHI2Q CarPlay. Detailed derivations and earlier incidents are kept in the [archived engineering notes](archive/snapshots-2026-10-29/LANDMINES.md). Read the relevant current subsystem guide before editing its contracts.

## 📡 SI-owned wireless channel boundaries

- Wireless ASI needs source ownership before native execution. [Reference](wireless-carplay/WIRELESS_PRODUCTION_CHANNELS.md).
- Artwork transfer needs Start and Success/Cancel messages on its negotiated file session, in addition to link ACKs.
- `procmgr_symlink` takes target then link. Preserve fixture syscall and socket semantics when testing native QNX behavior.

## ⚙️ Build, toolchain and compile

- Never use `__thread` / `static __thread` in an LD_PRELOAD'd .so on QNX 6.5.
- Build host-side float/DSP render tools with `-ffp-contract=off` (no fast-math).
- Declare `CFArrayAppendCString(arr, str, -1)` and `CFDictionaryGetInt64(dict, key, NULL)` with their three-argument ABI.
- Keep `.init_array` compiler-only (size 000004); no eager module constructors.
- The hook's dynamic export set is an exact allowlist — don't widen it.
- Rust std for QNX 6.5 armv7 needs `+strict-align` (procnto runs SCTLR.A=1).
- Go cross-built for QNX commits its heap reservation — patch `MAP_LAZY`.
- Use the pinned Docker `qnx65-armv7-toolchain` image and build scripts. Record its version with each artifact.
- Never call `std::fmin` / `std::fmax` on floats with the QNX GCC 8.5 toolchain — use `::fminf` / `::fmaxf`. [Reference](cluster-and-rgi/LANE_GUIDANCE.md).
- Keep disabled tracing from constructing argument strings or performing other runtime work. [Reference](build-and-deploy/BUILD_SYSTEM.md#logging-options).

## 🔧 QNX runtime

- Never return a negative from the QSA write interposer to stock dio.
- Never realloc/free a buffer owned by the hooked library unless its allocator is proven, and always keep the skip-on-doubt guard.
- Env gates must check the VALUE (`env_on()`), never bare `getenv()!=NULL`.
- Instrument with `LOG_WARN`, not `LOG_INFO` — the live threshold is WARN.
- Launch the renderers with `GRAPHICS_ROOT=/proc/boot` or EGL SIGSEGVs.
- An exported symbol is not automatically interposable — check the PLT/GOT call path, and `DT_SYMBOLIC`. [Reference](altscreen-and-video/archive/ALTSCREEN_RE_HISTORY.md).
- The unit's `printf` prints its format once and drops extra arguments: one conversion per argument.
- Renderers must reopen stderr in append mode (`"a"`), or supervisor truncation never bounds the log. [Reference](build-and-deploy/archive/PRODUCTION_AUDITS.md).

## 📦 Deploy and SSH

Domain: [Deploy and Release](build-and-deploy/DEPLOY_AND_RELEASE.md).

**A Mac-written M.I.B. card carries `._name` AppleDouble files next to every payload file.** The installer used to copy them onto the unit, back up `._deva-ctrl-qc.so` as a stock file, and a `._carplay_hook.jar` in `lsd/jars` lands on j9's bootclasspath. `custom.sh` now skips `._*` and `.DS_Store` and deletes copies an older install left. · tester report 2026-09-24 · [Deploy and Release](build-and-deploy/DEPLOY_AND_RELEASE.md#-the-mib-sd-install).

**`find` on some FAT cards exits 1 with a complete list.** QNX fs-dos cannot stat `..` inside the card's folders and reports `./dir/..: Filename too long`; the installer treated any `find` failure as fatal and stopped with `FAILED listing payload` before copying anything. The installer now checks each file from `mod/carplay/manifest.txt` (written by `prepare_install.sh`) on its own and never walks the card; only a hand-staged card without a manifest falls back to `find`, which tolerates exactly that message and still fails on any other error or an empty list (`list_payload()` in `install_MoreIncredibleBash/mod/custom.sh`, `scripts/test_install_listing.sh`). · tester car 2026-09-24 · [Deploy and Release](build-and-deploy/DEPLOY_AND_RELEASE.md#-the-mib-sd-install).

**M.I.B.'s NavActiveIgnore jar must not sit next to ours.** "NavActiveIgnore on" copies `navignore_audi.jar` / `navignore_vw.jar` to `lsd/jars/NavActiveIgnore.jar`; it replaces `org.dsi.ifc.carplay.AppState` so `getAppStateID()` and `getOwner()` always return 0, and the HMI loses which resources CarPlay owns. The installer deletes it without a backup. · 2026-09-25 · [Deploy and Release](build-and-deploy/DEPLOY_AND_RELEASE.md#-the-mib-sd-install).

**Our own narrow navignore seam hides the phone's navigation from stock too.** `CarplayDSILifecycleController.convertAppState` still blanks CarPlay's incoming NAVI app state (ID 2), so stock never ends its own route guidance when the phone takes "turns". `BAPBridge` cancels native guidance once, at connect. The MMI resumed its saved route about 7 s after CarPlay connected, and the Audi voice then guided alongside CarPlay for the rest of the drive. The CarPlay log showed "turns accessory" at 52.8 s and "turns controller" again at 62.8 s. `NativeRouteStop` now stops native guidance on every change of the owner to the phone. iOS already ends its own guidance when the car takes turns, so the latest one started wins, as in stock. · fork, car logs 2026-10-24.

**NEVER reboot / kill j9 / restart the unit yourself — ask every single time.** The user is usually in the car; killing j9 blacks the screen ~50 s. Approval to deploy is not approval to reboot, and approval once is not approval next time. · standing directive · `memory/feedback_never_reboot_unit.md`.

**Deploying `carplay_hook.jar` = a full unit reboot (j9 `-Xbootclasspath`).** The jar does nothing until the JVM restarts, and killing j9 is a critical-process death → whole-system restart (~50 s, SSH drops). Contrast the C hook: only dio_manager needs a new generation (per-fork, invisible). The build writes `build/carplay_hook.jar` (`scripts/java/build.sh`); on-unit path `/mnt/app/eso/hmi/lsd/jars/carplay_hook.jar` after `mount -uw /mnt/app`. · measured 2026-08-22 · `memory/java_jar_deploy_reboots_unit.md`.

**The M.I.B. script entry runs `command.sh` on release builds and `custom.sh` on `main`.** Release zips up to V3.7.1 show "Run individual script" and run `/mod/command.sh`; M.I.B. `main` since `e531867` (2024-07-05) shows "Run Custom Script" and runs `/mod/custom.sh`. Shipping only `custom.sh` made the release entry print "Nothing to do!". The package ships both; `command.sh` forwards to `custom.sh` (no symlink: the card is FAT) and does nothing when the M.I.B. launcher sources it. · 2026-09-24 · [Deploy and Release](build-and-deploy/DEPLOY_AND_RELEASE.md#-the-mib-sd-install).

**SSH to the head unit has no scp/tar/cksum on the far side — read back to verify.**

1. No remote `scp` / `tar` / `sha256sum`; push via `ssh "cat > f.new" < local` + chmod + mv + sync; verify by read-back + local `cmp`.
2. sshd rate-limits password auth → use one ControlMaster with a SHORT `ControlPath` (`/tmp/audi-cm/sock`, not a scratchpad path >104 B).
3. `ssh` in a `while read` eats the loop's stdin → `ssh -n`.
4. zsh won't word-split unquoted `$OPTS` → put deploy snippets in a `#!/bin/bash` script.

`/mnt/app` is read-only until `mount -uw`; the login banner prints on stdout even for `ssh host cmd`. · each cost a failed attempt · `memory/audi_ssh_transport_traps.md`.

**The password in `audi_ssh.sh` (outside this repo, at the `AUDI_2` root) is STALE — the working root password is supplied by the user; never write it into the tree.** Deploys failed on auth until the correct password was used, and repeated wrong attempts make the unit reject even the right password for a while — do one careful login check before any transfer ([Upmix Listening and Deploy Log](audio/UPMIX_LISTENING_AND_DEPLOY_LOG.md)). The old in-repo deploy helpers (`package_mib.py`, `package_protected.sh`, `deploy_over_ssh.sh`, `package_cabin_candidate.py`) were deleted. · `memory/audi_ssh_transport_traps.md`.

**Activate a release by same-partition rename, published last; renderers are persistent services.** The SI JSON is published last by a same-partition rename, which is atomic per file, not a transaction (`docs/build-and-deploy/DEPLOY_AND_RELEASE.md:48`). Don't add renderer processes as `servicemgr external:true` services — that breaks the generation model (`docs/build-and-deploy/DEPLOY_AND_RELEASE.md:30`). Never overwrite the stock `/etc/scripts/carplay_cleanup.sh`; the custom cleanup calls it only for mdnsd/PPS (`docs/build-and-deploy/DEPLOY_AND_RELEASE.md:27-28`, `deploy/smartphone_integrator/README.md:29-31`). · deployment design.

**Deploy matched halves together: hook with jar, jar with `maneuver_render`.** An old hook sends no `route_generation`, so a new jar's slot identity breaks; an old renderer doesn't understand the jar's half-degree angles (flag `0x04`) or icons 8/9. The new renderer accepts old commands, not the reverse. · on-car · [RGI and BAP Bridge](cluster-and-rgi/RGI_AND_BAP_BRIDGE.md), [HUD Renderer](cluster-and-rgi/HUD_RENDERER.md).

---

## 🌐 Networking, USB and connect

Domains: [USB Replug Recovery](connect-and-usb/USB_REPLUG_RECOVERY.md), [PF Firewall](connect-and-usb/PF_FIREWALL.md), [Reconnect and Lifecycle](altscreen-and-video/RECONNECT_AND_LIFECYCLE.md).

**The live PF ruleset is per-interface (`pf.ecm0.conf` on the LTE car) — verify via `pfctl -sr`.** Four profiles ship; patching `pf.conf` alone silently does nothing (6031/6032 stay blocked, no error). Patch all four; check the ACTIVE ruleset, not the file. · verified 2026-08-28 · `memory/pf_profile_per_interface_trap.md`. Upstream's installer widens `6030` → `6030:6032` in all four profiles (`install_MoreIncredibleBash/mod/custom.sh:35,95-100`); this fork's package does not yet ([Deploy and Release](build-and-deploy/DEPLOY_AND_RELEASE.md#-not-installed)).

**A fast USB replug can leave CarPlay dead until reboot — `carplay0` without RUNNING is a stack/SI problem, not our code.** After the replug `/dev/io-usb/otg_dev` goes missing → `carplay0` UP but not RUNNING (no carrier) → iAP2 never starts → dio misses its budget → SI relaunches it (~500 `TIMEOUT_STARTUP`). A missing `io-usb-dcd` after a failed reconnect is **not** proof that the device stack crashed: it is SI's terminal cleanup state — once the setup retries are spent, final cleanup issues `SwitchPort(direction=0)` with no matching start and waits for a connector event that never comes (the hub is soldered in). · on-car 2026-09-03 + SI decompile 2026-09-04 · `memory/usb_device_stack_replug_wedge.md`, [SI Stack and Replug Race](connect-and-usb/SI_STACK_AND_REPLUG_RACE.md), [USB Replug Recovery](connect-and-usb/USB_REPLUG_RECOVERY.md). Current state: the shipping `deploy/smartphone_integrator/smartphone_integrator` **is** the re-arm-patched SI image (SHA-256 `606236a8…`, `docs/build-and-deploy/DEPLOY_AND_RELEASE.md:80`; patcher `deploy/experimental/usb_replug_recovery/patch_si_rearm.py:15`), whose final cleanup re-arms the stack instead of leaving it stopped. The production `carplay_startup.sh` contains no USB guard. Only the NCM generation fence (`startncm.sh`/`stopncm.sh`, `usblauncher_otg.lua.patch`) remains an offline candidate in `deploy/experimental/usb_replug_recovery/` ("nothing in this directory deploys to the head unit by itself", `README.md:3-4`); whether a unit runs the fence is [open].

**A clean dio exit spares the USB stack; ERROR/TIMEOUT makes SI destroy `carplay0`.** Only `ERROR_RUNTIME` / `TIMEOUT_*` / `ERROR_SETUP` exits escalate SI → `RestartDeviceStack` → `stopncm` → `carplay0` destroyed. A `DEINITIALIZED` exit runs `softPath_noStackRestart()`. ERROR/TIMEOUT ⇒ the fix belongs in dio/hook (exit cleanly), not the USB/firewall layer; diagnose via slog `[CSIStateRunning]` `oldAppState`. · IDA RE of SI · `memory/si_stack_restart_kills_carplay0.md`.

**Debounce the CarPlayApp inactive edge — a fast replug bounces state.** Each ACTIVATING↔INVALID↔ACTIVE edge used to do a full BAPBridge teardown + cluster ctx flap, and a dio reconnect landing mid-teardown collapsed the session. Implemented: `DEACTIVATE_DEBOUNCE_MS = 400L`; `lifecycleLoop` waits on an inactive edge and drops it if a newer edge arrives (`java_patch/com/luka/carplay/core/CarPlayApp.java:228-268`). Don't remove the wait. · on-car build 34c4c9d · `memory/reconnect_flak_lifecycle_debounce.md` (its "proposed" wording is stale).

**Don't re-investigate `-w 10000` or startncm en-dashes — proven harmless by decompile.** IPv6 DAD costs ~1 s/connect; the only lever is the global `net.inet6.ip6.dad_count` (set 0 before `start_network`). · decompile 2026-08-24 · `memory/ncm_connect_speed_stock.md`.

**Don't patch SI `verifyPortReset` 12 → 14 — it is the wrong branch for CarPlay.** The verifier returns immediately for CarPlay (app ID 0), which follows the connector-type cleanup branch instead; the patch would not fix CarPlay and could regress other apps. The 2026-08-04 reading was superseded on 2026-09-04. · SI decompile · [SI Stack and Replug Race](connect-and-usb/SI_STACK_AND_REPLUG_RACE.md).

**Don't raise SI `maxSetupRetries` (2) as a fix.** More stop/delay/start cycles remove neither race and turn a permanent failure into a long restart storm. · SI decompile · [SI Stack and Replug Race](connect-and-usb/SI_STACK_AND_REPLUG_RACE.md), [USB Replug Recovery](connect-and-usb/USB_REPLUG_RECOVERY.md).

**Don't put a directory under `/tmp`, and don't call `rmdir`, in any on-unit script.** `/tmp` is `/dev/shmem`: `mkdir` there fails with `Function not implemented`. The unit has no `rmdir` binary. Use `/ramdisk` (RAM, cleared at boot) for lock directories and `rm -rf` to remove them. The first NCM fence bundle did both, and on 2026-09-25 every CarPlay connect failed until it was rolled back; host tests on macOS cannot catch this, so run such scripts on the unit (stub binaries, dummy interface) first. · car · [USB Replug Recovery](connect-and-usb/USB_REPLUG_RECOVERY.md#-shipped-2026-09-25-fence-on-the-car).

**Never use `reset port 3 250 1` or switch media port 1 to OTG as recovery.** The port reset made it worse (`drivers_running` 1 → 0); switching port 1 to the OTG hub stopped the phone enumerating, and switching back did not restore it — the command is reversible, the state was not. · on-car · [USB Replug Recovery](connect-and-usb/USB_REPLUG_RECOVERY.md).

**`/proc/<pid>/cmdline` is empty for `usblauncher` on QNX — read argv with `pidin -F "%a %A"`.** The first relaunch guard silently did nothing because of this; `usblauncher` is also not on `PATH` (it lives in `/mnt/app/armle/bin/`). · on-car · [USB Replug Recovery](connect-and-usb/USB_REPLUG_RECOVERY.md).

**Never let two listeners share the negative preferred port `-6030`.** Stock `ServerSocketOpen` treats a negative port as "preferred, fall back to dynamic"; two listeners race, one silently falls back, and main 110 hits the stock 10 s accept timeout → teardown. Type 111 opens `-6031` (`hook/altscreen/altscreen_hook.c:1659-1661`). · decompile + on-car · [Session Lifecycle](connect-and-usb/SESSION_LIFECYCLE.md).

**iAP2 `0x1D03` is IdentificationRejected, not "Identify end".** MFi R32 95.2.4 names `0x1D03` the device's rejection (with the rejected params and message IDs); `0x1D02` is IdentificationAccepted. The framework constant `IAP2_MSG_IDENTIFY_END` and `HOOK_EVENT_IDENTIFY_END` carry the old name, so a route-guidance retry tied to that event fires on a rejection. Any new Identify addition must meet the phone's acceptance rule instead of reacting to `0x1D03`: iOS rejects `VehicleStatusComponent` 21 unless its three messages are declared, so `hook/vehicle` appends it only when they are. · spec 2026-10-10 · [Vehicle Data To Phone](connect-and-usb/VEHICLE_DATA_TO_PHONE.md) · 

**PASCD must come from a wheel-speed sensor, never from GNSS, and go out about once per second.** MFi R32 41.1.2: one NUL-terminated sentence per `NMEASentence`, sample count 1–50, `R` if reverse was engaged at any time in the interval. Stock advertises it only if `dio_manager.json` `"nmea"` has bit `0x08`. · spec 2026-10-10 · `hook/vehicle/vehicle_hook.c` · 

**A `0x5001` now-playing update with an unchanged media item is invisible to the HMI.** Cinemo calls the listener only when `MediaItem::compare` reports a change; a fresh `0x5000` is what rebuilds the phone-side state. · decompile · [Now Playing Cold Start](connect-and-usb/NOW_PLAYING_COLD_START.md).

---

## ⚡ Audio — upmix tuning and measurement

- Never tune on 6-second prefixes; use ≥60 s fragments.
- Treat any LFE-default change as a bass edit and re-verify.
- LFE is not a monotonic sub-volume knob.
- The input-EQ bank choice dwarfs every other bass lever.
- The realtime upmix guard uses a 12 ms budget per 16 ms period. Measure execution time on the target when changing DSP cost. [Reference](audio/STEREO_TO_5_1_UPMIX.md).
- Don't trigger the realtime guard on real `deviceUnderruns`. [Reference](audio/STEREO_TO_5_1_UPMIX.md).
- Preserve SoundCheck programme-gain semantics when changing the audio pipeline. [Reference](audio/STEREO_TO_5_1_UPMIX.md#output-order).
- Level-match playback when comparing UpMix modes; front/rear balance and perceived loudness are separate measurements.
- Keep type-103 `arrivalToRenderLatencyMs` consistent with the selected renderer: SCENE reports 11 ms of extra latency relative to the 512-frame baseline. [Reference](audio/BUFFERED_AUDIO_PATH.md).
- A buffered pause is not a stream break — never reset the upmix on `SETRATE 0`. [Reference](audio/BUFFERED_AUDIO_PATH.md).
- The scene mask worker drops controls when it falls behind — `skipped=N` is the first number to read after a car session. [Reference](build-and-deploy/SCENE_BACKEND_DEPLOY.md).

## 🔧 Audio — amp and 5.1 routing

- Mirror `mpl5_dio_ent` caps to `mpl1` exactly — you cannot fix 5.1 from the PCM side.
- The six-channel capability geometry still permits stereo through QSA conversion. [Reference](audio/DISCRETE_5_1_ROUTING.md).
- Verify audio through CarPlay, never with `qsa_play`.
- The amp input matrix is OUTPUT-rows / INPUT-columns — don't transpose it. [Reference](audio/archive/UPMIX_CALIBRATION_AND_TUNING.md#the-corrected-matrix-changed-the-diagnosis).
- Internal amp has no stock six-channel CarPlay route — keep the installer gate. [Reference](audio/INTERNAL_AMP_PATH.md).
- `eSNK_SPEAKERS_MC` (=31) is a DEAD alias of stereo — never target it for 6ch.
- Never code an internal-amp car as `Sound_System=2` — keep 1 and patch the MIBRoot pair. [Reference](audio/INTERNAL_AMP_PATH.md).
- `MIB_AudioTable_*.xml` must stay gzip-compressed, well-formed XML. [Reference](audio/INTERNAL_AMP_PATH.md).
- The `0x10be3` COPP topology A/B is a no-op — use `0x1031b`. [Reference](audio/QNX_AUDIO_TOPOLOGY.md).
- Don't hand-edit the AudioProcess src-7 width byte at file `0x1d3f94`. [Reference](audio/QNX_AUDIO_TOPOLOGY.md).
- `sources/acdb/Speaker_cal.acdb` is the PATCHED `0x10312` variant, not stock [Reference](audio/QNX_AUDIO_TOPOLOGY.md).
- Amp mute sentinels differ per block — never apply one block's sentinel to another. [Reference](audio/SHARC_DSP_BLOCKS.md).
- Level-EQ bank 0 ("disabled") is not flat [Reference](audio/SHARC_DSP_BLOCKS.md).
- "SHARC control 0x10 / 0x13" are the B-library API16 / API19 in hex, not new controls. [Reference](audio/SHARC_B_PROCESSORS.md).

## 📊 Audio — spatial and codec wireformat

- The inline magic cookie is the codec authority — never switch decoders on a stream index field.
- CarPlay realtime spatial is AAC-ELD 48k 5.1.2 (bit 42), NOT PCM 5.1 (bit 44).
- Phone-side realtime spatial is gated by `ScreenSpatialAudioReceiver` — no HU advert can flip it.
- The 8→6 fold is mandatory — there is no 8-channel route.
- 210 libairplay is PCM-passthrough — don't add in-lib AAC/OMX/FDK decode.
- Type-103 legacy `audioOutputFormats` must be ONE codec family (`0x400000`). [Reference](audio/BUFFERED_AUDIO_PATH.md).
- Answer `mainBuffered` only when the phone proposed it, and always together with `/info` `mainBufferedInfo`. [Reference](audio/BUFFERED_AUDIO_PATH.md).
- `audioBufferSize` is the maximum over the whole format offer. [Reference](audio/BUFFERED_AUDIO_PATH.md).
- Hand the Cinemo decoder a heap ASC header and never free it after a successful Initialize. [Reference](audio/CINEMO_AAC_DECODER.md).
- Never splice type-100 PCM into a period while type 103 owns media. [Reference](audio/BUFFERED_PLAYBACK_LIFECYCLE.md).
- Don't blank `updateFeedback` — its `sr` is the sender clock. [Reference](audio/BUFFERED_AUDIO_PATH.md).
- Run the APAP receiver / QSA writer at `SCHED_RR` 18, not the default 10. [Reference](audio/BUFFERED_PLAYBACK_LIFECYCLE.md).
- Never pull a full `sloginfo` during playback. [Reference](audio/BUFFERED_PLAYBACK_LIFECYCLE.md).
- Don't scope the APAC codec by the substring "APAC". [Reference](audio/APAC_CODEC.md).

## 🔍 Cluster contexts

- DisplayManager id 103 is NOT creatable — reuse stock backings 101/102.
- Don't switch the altScreen advert off to fix a cluster flap — production ships it ON.
- `AltScreenModule` is the single cluster-context writer; the stock view-mode chain is dead on FPK.
- `setActiveDisplayable` / encoder re-point fires only on a context CHANGE.
- KSS rejects MOST InfoStates=6 — patch KSSApplication @0x108F42C, or use the LVDS-map path.
- Don't try to block the VC side drawers from the HU. [Reference](cluster-and-rgi/VC_FPK_STATE_MACHINE.md).
- Don't force KDK opacity from the phone's target View or a timer. [Reference](cluster-and-rgi/KDK_GEOMETRY_AND_ANIMATION.md).
- Don't clear MapVisibility (FctID 44) to hide the VC NavStatus bar. [Reference](altscreen-and-video/ALTSCREEN_CLUSTER_VIDEO.md).

## 🔧 RGI and BAP

- Never send exitViewNum=1 — it overwrites the maneuver icon (keep 0).
- Toggle the exitView variant EU↔NAR on every send.
- Call `forceClusterRouteInfoState(true)` on start even for BAP-only routes
- Don't tear down RGI on `visible_in_app` (TLV 0x0F) == 0.
- Don't clear per-slot data on transient `maneuver_count=0`.
- During CarPlay, keep the FctID18 distance model valid alongside the bargraph. [Reference](cluster-and-rgi/DISTANCE_BARGRAPH.md).
- LRU slot lookup must refresh seq on an existing-slot hit and guard active slots.
- App-specific RGI depends on the phone app’s semantic output. Check the supported behavior before changing HU parsing. [Reference](cluster-and-rgi/THIRD_PARTY_NAV_APPS.md).
- `CMD_ALT_ZOOM` is LIVE at `0x0110` — do not re-drop it.
- Keep the full 1440×542 view-area frame — a reduced visible frame is rejected at activation
- Keep FctID 22 at `timeInfoType = 1`; never toggle 1 ↔ 0. [Reference](cluster-and-rgi/RGI_AND_BAP_BRIDGE.md).
- Never publish `""` in FctID 19 — publish a neutral `…`. [Reference](cluster-and-rgi/RGI_AND_BAP_BRIDGE.md).
- Never replace the outgoing iAP2 LocationInfo with `0x5200` — inject `0x5200` as an extra packet via stock `SendIAP2`. [Reference](cluster-and-rgi/IAP2_ROUTE_GUIDANCE_TLVS.md).
- The injection `generation` names a link session, never a send. [Reference](reference/ARCHITECTURE.md#cinemo-nme-and-iap2-injection-abi).
- Keep the ±1000 angle sentinels and presence guards. [Reference](cluster-and-rgi/MANEUVER_MAPPING.md).
- Ramps are slight TURNs: never send `EXIT_RIGHT/A0` or `EXIT_LEFT/60`, and don't restore the old CarPlay type table. [Reference](cluster-and-rgi/MANEUVER_MAPPING.md).
- The HUD arrow selector is the raw FD62 state index, not the `layout_pages.json` list index. [Reference](cluster-and-rgi/MANEUVER_MAPPING.md).
- Use `glBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA)`, not `glBlendFunc(SRC_ALPHA, …)`. [Reference](cluster-and-rgi/LANE_GUIDANCE.md).

## ⚡ AltScreen and video

- forceKeyFrame / cluster IDR must be keyed by `uuid`, NOT `displayUUID`.
- Keep TCP_NODELAY on the hook→renderer socket. [Reference](altscreen-and-video/VIDEO_PIPELINE.md).
- Mailbox may return only decoded slots the GPU has never read. [Reference](altscreen-and-video/VIDEO_PIPELINE.md#-decoded-output-mailbox-ownership).
- Receiving another IDR is not presentation-health confirmation. [Reference](altscreen-and-video/ALTSCREEN_CLUSTER_VIDEO.md#idr-recovery).
- A ≥1 s renderer stall must never become a generation restart. [Reference](altscreen-and-video/VIDEO_PIPELINE.md#negotiation-and-presentation-readiness).
- Keep idle/recovery waits independent of wall time. [Reference](altscreen-and-video/ALTSCREEN_PACING_VALIDATION.md#-idle-observation-timebase).
- Do not upgrade capabilities from late HELLO or trust unnegotiated STATUS. [Reference](altscreen-and-video/ALTSCREEN_PACING_VALIDATION.md#-hello-and-status-boundaries).
- The altScreen gate is the control-SETUP *response* `enabledFeatures` containing `"altScreen"` (+ `"viewAreas"`) — not `/info` `extendedFeatures`. [Reference](altscreen-and-video/ALTSCREEN_CLUSTER_VIDEO.md).
- Cluster panel is 1440×542, NOT 1920×720
- `main_disp_modernize` must use the REAL ScreenCopyMain uuid — never fabricate.
- Don't remove `glFinish` / per-frame external-image rebind in altscreen_render "to look faster"
- Decode AirPlay failures using the [status-code reference](reference/CARPLAY_STATUS_CODES.md); retain the operation and session context with the numeric code.
- Never add an output-age filter to `altscreen_render`. [Reference](altscreen-and-video/ALTSCREEN_FRAME_RATE.md).
- Never create a stock `ScreenStream` for 111. [Reference](altscreen-and-video/ALTSCREEN_CLUSTER_VIDEO.md).
- A per-stream TEARDOWN must not stop 111 unless its `streams` list contains 111. [Reference](altscreen-and-video/ALTSCREEN_CLUSTER_VIDEO.md).
- Accept an empty avcC VideoConfig (`bodySize 0`) and the 8-byte size-only cover-art Setup — both are valid. [Reference](altscreen-and-video/VIDEO_PIPELINE.md).
- Keep the 3-failure / 30 s cooldown on OMX decoder start. [Reference](altscreen-and-video/VIDEO_PIPELINE.md).
- Don't patch libosal or raise `setUpdateRate` to 60 for a smoother cluster. [Reference](altscreen-and-video/ALTSCREEN_FRAME_RATE.md).
- Keep immutable selected zoom age. [Reference](altscreen-and-video/ALTSCREEN_INPUT_COMMANDS.md).
- Validate CF construction before replacing pending state. [Reference](altscreen-and-video/ALTSCREEN_INPUT_COMMANDS.md).

## 🔧 Java and HMI

- `CarplayBus.on()` must never wait for `dispatchLock`. [Reference](reference/HOOK_TO_JAVA_BUS.md#-java-side).
- Target Foundation Profile 1.1 (~J2SE 1.4): no generics/enum/enhanced-for/autoboxing/StringBuilder.
- A 1.5 API method compiles then NoSuchMethodError on-unit — gate with the firmware JCL.
- `"isNativeLittleEndian"` is a decompiler artifact for `""` — and javac INLINES it.
- The only Java source tree is `java_patch/`.
- Renderers are persistent supervised services — Java never spawns or kills them. [Reference](reference/HOOK_TO_JAVA_BUS.md).
- Never delete a replaced stock Java class. [Reference](build-and-deploy/archive/PRODUCTION_AUDITS.md).
- `IMMICombiScreenChangeManager` must not be a required service. [Reference](cluster-and-rgi/SMARTPHONE_TAB_AND_KOMBI_DSI.md).
- Never subclass a stock helper class outside `org.dsi` / the classes the jar already uses, and never build a module's listeners in `CarPlayApp`'s static init.

## 📚 Further reading

**Target Foundation Profile 1.1 (~J2SE 1.4): no generics/enum/enhanced-for/autoboxing/StringBuilder.** Runtime is IBM J9 CDC1.1+FP1.1, `-noverify`, bytecode major 48. Build: `-source 1.4 -target 1.4` (`scripts/java/build.sh:53`). · live core dump · `MEMORY.md` §"Java runtime".

**A 1.5 API method compiles then NoSuchMethodError on-unit — gate with the firmware JCL.** `-target 1.4` limits bytecode, not API surface (e.g. `Class.getSimpleName`). Gate with `Patches/jxe2jar/tools/recompile_check.py --jcl …/jcl.jar`; extract the .class from the firmware jar to see the real class. · on-car · `MEMORY.md` §"method-level 1.5 API trap".

**`"isNativeLittleEndian"` is a decompiler artifact for `""` — and javac INLINES it.** 36 poisoned `static final String = "isNativeLittleEndian"` in the compile jar get inlined by value, so a clean *source* grep misses them. After every Java build grep the JAR *bytecode* (`unzip -p … '*.class' | strings | grep -c isNativeLittleEndian` == 0); `scripts/build_java.sh` does not automate this check. · bytecode audit · `memory/isnativelittleendian_inlined_constant.md`.

**The only Java source tree is `java_patch/`.** `scripts/java/build.sh:78` builds `"$SOURCE_ROOT/java_patch"`; `java_patch_new_replace`, `java_patch_new_tab` and `build_variant.sh` do not exist. The `MEMORY.md` §"Canonical Java trees" entry is stale; the reconnect note that names `java_patch/` as live is correct. · repo state.

**Renderers are persistent supervised services — Java never spawns or kills them.** `carplay_startup.sh` forks `carplay_monitor.sh`, which (re)starts `maneuver_render` / `altscreen_render` (`deploy/smartphone_integrator/carplay_monitor.sh:85-95`). Java only listens: `RendererServer` on :19800 for `maneuver_render` (`java_patch/com/luka/carplay/rgd/RendererServer.java:44`) and `AltscreenControlServer` on :19832 (`java_patch/com/luka/carplay/core/AltscreenControlServer.java:32`); the renderers connect in. No `Runtime.exec`/`ProcessBuilder` exists under `java_patch/`. See [Hook to Java Bus](reference/HOOK_TO_JAVA_BUS.md).

**Never delete a replaced stock Java class.** Deleting it silently restores the stock class and the hook disappears; "unused" methods in it may still be called by stock code. · 2026-08-15 cleanup audit · [Production Audits](build-and-deploy/PRODUCTION_AUDITS.md).

**`IMMICombiScreenChangeManager` must not be a required service.** MU1316 has no implementation, so requiring it means a silent infinite retry; `SteeringWheelInputModule` treats it as optional (`java_patch/com/luka/carplay/core/SteeringWheelInputModule.java:59`). · decompile · [Smartphone Tab and Kombi DSI](cluster-and-rgi/SMARTPHONE_TAB_AND_KOMBI_DSI.md).

**Never subclass a stock helper class outside `org.dsi` / the classes the jar already uses, and never build a module's listeners in `CarPlayApp`'s static init.** On 2026-10-10 `VehicleDataModule$KombiListener extends de.audi.remotehmi.util.dsi.DSICarKombiListenerAdapter` compiled and passed the offline linkage audit, but on the car it threw `NoClassDefFoundError`; `CarPlayApp.<clinit>` failed with it and took `AppTerminalMode` and `AppNavi` down: "Initialising navigation" forever, no CarPlay. The audit cannot see which jar a stock class lives in on the unit. Fix: listener bases generated from the `org.dsi` interfaces (`KombiListenerBase`, `GeneralVehicleStatesListenerBase`, CN patch `java_patch_cn/kombi_listener_base.patch`), listeners created in `start()` under a `Throwable` guard. Triage: `/mnt/ota/system/logs/error_*.zip` holds the stack trace. · on-car 2026-10-10 · `java_patch/com/luka/carplay/core/VehicleDataModule.java` · 

---

## 🔍 RE methodology

Domains: [HSSR Model](audio/HSSR_MODEL.md).

**12-class supervised separation on 240 songs cannot work — the rear needs one mask, not twelve.** The model collapsed to ≈ mute+drums (raw-scalar control == init; disabled == mute); the band-power interface structurally can't separate lead/backing or keys/guitar. Mis-posed, not under-trained. A control arm that equals the model's init or equals mute proves nothing — a candidate must beat a static 2×2 Wiener mask and the TRAIN-fitted raw scalar ([Upmix Training History](audio/UPMIX_TRAINING_HISTORY.md)). · independent Q2 audit · `memory/upmix_q2_independent_audit.md`, `docs/research/HSSR_UPMIX_MODEL.md`.

**[retired with Universal 2026-10-09] Loading exported int8 weights into PyTorch is NOT the shipped C inference — validate against the C path.** Universal's runtime = standardize→clamp±6→int8 GEMV with rational sigmoid/tanh + reset-after GRU. The scene net's check of the same kind is `tools/stereo-upmix-render/port/test_mask.py` (torch on the C features against the float NEON runtime). · training audit · `docs/audio/UPMIX_DATASETS_AND_TEACHERS.md`, `docs/research/HSSR_UPMIX_MODEL.md §3`.

**Don't refuse MPS over 3e-8 nondeterminism — it made the 4-core NAS the real bottleneck.** Research needs sealed weights after the fact, not bit-exact restart; the M3 Max (≈20–50× the NAS) was refused purely on that self-imposed contract, so iteration scale — not rigor — was the limit. · compute audit · `memory/upmix_compute_mac_vs_nas.md`.

**Don't fit amateur mic-sweep passbands as an EQ / filter bank.** Reported speaker ranges are author observations; fitting rectangular passbands and applying after the existing electronic EQ is an invented filter bank. They cross-check physical grouping only. Its embedded wiring chart also mislabels pin pairs 3/15, 13/14 and 24/32 — use the Audi connector page ([J525 Wiring and Speakers](audio/J525_WIRING_AND_SPEAKERS.md)). · workbook review · `docs/audio/J525_WIRING_AND_SPEAKERS.md`.

**[retired with Universal 2026-10-09] Dolby's kernel call period is unknown — delays are not convertible to ms; don't hard-code the ms table.** If the real period is 1024 samples (not the 64-sample hypothesis) the delays are 64–660 ms (not decorrelation) and the borrowed delay table must be revised. · Dolby teardown · `docs/audio/UPMIX_CALIBRATION_AND_TUNING.md §5`, `docs/research/DOLBY_ATMOS_TEARDOWN.md`.

**[retired with Universal 2026-10-09] "Dolby is better than HSSR" has never been tested — don't cite it as a reason to switch.** The Dolby principles were ported behind flags and never A/B'd in-car against plain HSSR. Production enabled the bed-delay/attack pieces (`AUDIO_HSSR_BED_DELAYS=on`, `AUDIO_HSSR_BED_ATTACK=owner`, `carplay_startup.sh` at tag `upmix-universal-last-2026-10-09`) after a headphone A/B ("chosen by Luka after the headphone A/B"), not after an in-car measured comparison; the scene renderer dropped the bed and its knobs. · distribution plan status · `docs/research/DOLBY_ATMOS_TEARDOWN.md`.

**[retired with Universal 2026-10-09] HSSR q6d slot order is defined by the cache builder: slot 4 = strings/winds + unknown background, slot 5 = keys** (`CLASS_OF`, `tools/stereo-upmix-render/q6_hssr_moises_cache.py:21` at tag `upmix-universal-last-2026-10-09`; the file left the tree with the model). Older copies of the long-form papers had 4/5 reversed — check the code. · code · [Upmix Training History](audio/UPMIX_TRAINING_HISTORY.md).

**MoisesDB's "mix" is a unit-gain stem sum, not a master — simulate mastering before using it.** · dataset audit · [Upmix Datasets and Teachers](audio/UPMIX_DATASETS_AND_TEACHERS.md).

**`audio-separator` normalises each stem to 0.9 independently, and BS-RoFormer runs natively at 44.1 kHz.** Per-stem rescaling moved fitted gains by up to ~3 dB; use one documented mixture gain, float outputs, and resample explicitly. · dataset audit · [Upmix Datasets and Teachers](audio/UPMIX_DATASETS_AND_TEACHERS.md).

**AST and CLAP can't judge separated stems.** AST drops content above 8 kHz and CLAP reported a snare on silence — content audit only. · dataset audit · [Upmix Datasets and Teachers](audio/UPMIX_DATASETS_AND_TEACHERS.md).

**[retired with Universal 2026-10-09] FFT512 at 48 kHz leaves dead bands (60–90 / 125–170 / 220–280 Hz) — their rear targets were always zero.** 93.75 Hz bins put no bin in those bands. The scene engine analyses at 1024/256 (46.9 Hz bins) and its net at 2048 (23.4 Hz), so the bands exist there; the lesson is to check the bin grid against every band target before training. · dataset audit · [Upmix Datasets and Teachers](audio/UPMIX_DATASETS_AND_TEACHERS.md).

**AlbumDB channel order comes from the paper's Table 2 — the WAVs have no channel mask; track 06 is PCM24.** · dataset audit · [Upmix Datasets and Teachers](audio/UPMIX_DATASETS_AND_TEACHERS.md).

**Some `sources/papers` files are failed downloads** (`bharitkar*.pdf`, `song_ambient_phase*.pdf`, `cupgan2026.bin`) — login pages or error JSON, never cite them. · literature review · [Upmix Literature](audio/UPMIX_LITERATURE.md).

**Validate a decompressor by content, not output size.** A hand-ported lzo1x reached the expected ~76 MB but produced garbage; the format was lzo1z. · MU1439 port · [MHI2 MU1439 Port](reference/MHI2_MU1439_PORT.md).

---

## ✅ Resolved P0s (production audit @9295b04)

The full audit found three shipping blockers. All three are fixed in the current code; each stays here as a durable rule so it isn't reintroduced.

| P0 | Symptom | Rule | Fixed in code |
|---|---|---|---|
| Cold-boot deadlock | Whole-HU hang at start (ABBA `CarPlayApp.lock` ↔ `AltScreenNavStatusGate.class`) | Never hold `CarPlayApp.lock` across module `start()` or any external call | Lifecycle runs on a separate `lifecycleLock` worker; module start/stop happens outside `CarPlayApp.lock` (`java_patch/com/luka/carplay/core/CarPlayApp.java:38-39,184-222,328-331`) |
| Cover-art dead | No album art | The `NmeTransport::Recv` seam must have a live caller | Framework Recv interposition feeds `coverart_on_transport_recv` (`hook/coverart/coverart_hook.c:692-694,768,828`) |
| Cluster permanently black | Cluster never shows | Key cluster IDR by `uuid`, never `displayUUID` | `alt_params_new(1)` sets `uuid` (`hook/altscreen/altscreen_hook.c:2367,2502-2523`) |

Provenance: `memory/production_full_audit_9295b04.md`. `docs/sources/PRODUCTION_FULL_AUDIT.md` is no longer in the tree — [open] where the audit write-up now lives.

---

## 🔗 See also

- [Home](README.md) — docs hub.
- [iOS CarPlay Spatial Wireformat](reference/IOS_CARPLAY_SPATIAL_WIREFORMAT.md) — the audio wireformat RE behind the spatial group.
- [Hook to Java Bus](reference/HOOK_TO_JAVA_BUS.md) — the TCP bus and renderer channels behind the RGI/AltScreen groups.
- [Record Replay Tooling](reference/RECORD_REPLAY_TOOLING.md) — how the from-source stack is validated without a phone.
- [Reconnect and Lifecycle](altscreen-and-video/RECONNECT_AND_LIFECYCLE.md) — the debounce and lifecycle worker in context.
