# Build system

The repository contains native/Java component builds and runtime checks. The
[SD package builder](https://github.com/waterfalldev/mib2q-carplay-enhanced-tooling)
combines them with verified stock firmware inputs and the managed installer.

## Prerequisites

- PowerShell 7, Git with Git Bash, and Docker.
- `qnx65-armv7-toolchain:latest`, built from the
  [QNX toolchain source](https://github.com/luka-dev/qnx65-armv7-toolchain).
- `eclipse-temurin:8-jdk-jammy` for Java 1.4-compatible output.
- A host-test image built from this checkout:

```sh
docker build --platform linux/amd64 -t carplay-rgi-host-tests:local -f tests/Dockerfile.host .
```

Prepare the firmware profile, reconstructed stock Java library, original HMI
image/configs and seven stock runtime inputs described in the
[package input guide](https://github.com/waterfalldev/mib2q-carplay-enhanced-tooling/blob/main/docs/install-and-recovery.md).
Stock firmware inputs are not supplied in Git. A host JDK is not required.

## Create an SD package

Run from the package-builder checkout, using clean committed source revisions:

```powershell
./packaging/Build-Package.ps1 -SourceRepository <app-checkout> `
    -Ref HEAD -ToolingRef HEAD -FirmwareRoot <stock-firmware-folder> `
    -Dependencies <dependency-cache> -WorkRoot <scratch-folder> `
    -OutputRoot <packages-folder>
```

Keep firmware inputs, dependency caches and generated output outside the source
checkouts. The output includes the SD overlay, installer, verified rollback inputs
and validation results. Follow [installation and rollback](DEPLOY_AND_RELEASE.md)
to use the package.

## Build profile and validation

The wired profile uses `HOOK_PHONE_DATA=1`, `HOOK_WIRELESS=0`, `HOOK_AUDIO=0` and
`Features.UPMIX=false`. Telephone/car data, short-press Siri and the telephone popup
skip are enabled. Audio processing and wireless components are disabled.

Package preparation checks Java/native behavior, stock linkage, protocol contracts,
firmware fingerprints, installation/rollback and package checksums. Hardware and
phone behavior also require validation on the target vehicle.

For component development, use `scripts/java/docker.sh`, `scripts/build_hook.sh`,
`scripts/build_renderers.sh` and `scripts/run_tests.sh`.

Check documentation with:

```sh
python3 scripts/check_docs.py
```
