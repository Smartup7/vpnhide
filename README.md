# vpnhide

Hide an active Android VPN connection from selected apps. Three components work together to cover all detection vectors — from Java APIs down to kernel syscalls.

## Components

| Directory | What | How |
|-----------|------|-----|
| **[zygisk/](zygisk/)** | Zygisk module (Rust) | Inline-hooks `libc.so` via [shadowhook](https://github.com/nicknisi/nicknisi): `ioctl`, `getifaddrs`, `openat` (`/proc/net/*`), `recvmsg` (netlink). Catches every caller regardless of load order — including Flutter/Dart and late-loaded native libs. |
| **[lsposed/](lsposed/)** | LSPosed/Xposed module (Kotlin) | Hooks Java network APIs in app processes (`NetworkCapabilities`, `NetworkInterface`, `LinkProperties`, etc.) and `writeToParcel` in `system_server` for cross-process Binder filtering. |
| **[kmod/](kmod/)** | Kernel module (C) | `kretprobe` hooks on `dev_ioctl`, `rtnl_fill_ifinfo`, `fib_route_seq_show`. Invisible to any userspace anti-tamper SDK. |

## Which modules do I need?

- **Most apps**: `zygisk` + `lsposed`. Almost all apps check VPN status through Java network APIs (`NetworkCapabilities`, `NetworkInterface`, etc.), so both modules are needed for full coverage.
- **Apps with aggressive anti-tamper SDKs**: use `kmod` + `lsposed`. Some SDKs detect userspace hooks via raw `svc #0` syscalls and ELF integrity checks — only kernel-level filtering is invisible to them.

## Configuration

All three modules share a target list. Use the WebUI (KernelSU/Magisk manager → module settings) to select which apps should not see the VPN. The WebUI writes to:
- `targets.txt` — package names (read by zygisk and lsposed)
- `/proc/vpnhide_targets` — resolved UIDs (read by kmod)
- `/data/system/vpnhide_uids.txt` — resolved UIDs (read by lsposed system_server hooks)

## Building

Each component has its own build system:

- **zygisk**: `cd zygisk && ./build-zip.sh` (requires Rust + Android NDK + cargo-ndk)
- **lsposed**: `cd lsposed && ./gradlew assembleDebug` (requires JDK 17)
- **kmod**: `cd kmod && ./build-zip.sh` (requires kernel source + clang cross-compiler). See [kmod/BUILDING.md](kmod/BUILDING.md) for details.

## Verified against

- [RKNHardering](https://github.com/xtclovver/RKNHardering/) — all detection vectors clean
- [YourVPNDead](https://github.com/loop-uh/yourvpndead) — all detection vectors clean

Both implement the official Russian Ministry of Digital Development VPN/proxy detection methodology.

## Split tunneling

Works correctly with split-tunnel VPN configurations. Only the apps in the target list are affected — all other apps see normal VPN state.

## Known limitations

- `kmod` requires a GKI kernel with `CONFIG_KPROBES=y` (standard on Pixel 6–9a with `android14-6.1`)
- `lsposed` requires LSPosed or a compatible Xposed framework
- Some anti-tamper SDKs could theoretically be updated to detect kernel-level filtering, but this hasn't been observed in practice
