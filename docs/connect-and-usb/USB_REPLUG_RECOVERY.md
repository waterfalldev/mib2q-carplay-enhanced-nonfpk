# USB reconnect recovery

Recovery helps wired CarPlay reconnect after a disconnect or rapid unplug/replug.
It preserves Audi's smartphone integrator as the USB lifecycle owner.

## Enabled components

- **SI re-arm patch:** corrects the supported cleanup/retry path so the USB device
  stack can start again after a disconnect.
- **NCM generation fence:** serializes interface start/stop and checks which USB
  connection owns `carplay0`. A delayed teardown cannot remove a newer connection.
- **Cleanup integration:** routes SI cleanup through the generation-aware wrapper.

The managed package checks the firmware and original files before applying these
changes, and restores verified originals during rollback.

The renderer supervisor does not reset USB, restart SI or create a recovery loop.
Renderer ownership and USB connection ownership remain independent.

## Diagnosis

Collect logs before restarting the head unit or rolling back. Keep the complete
capture, including its summary, hook/wrapper logs, USB state and network snapshots.
They distinguish a missing USB stack from a failed NCM interface or CarPlay session.
See [installation and logs](../build-and-deploy/DEPLOY_AND_RELEASE.md#rollback-and-logs).

## Implementation

- [Firmware patcher](../../deploy/binpatch/binpatch.py)
- [NCM start](../../deploy/experimental/usb_replug_recovery/startncm.sh) and
  [stop](../../deploy/experimental/usb_replug_recovery/stopncm.sh)
- [Cleanup wrapper](../../deploy/smartphone_integrator/carplay_ncm_cleanup.sh)
