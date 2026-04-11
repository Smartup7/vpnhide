# vpnhide -- LSPosed module

Hooks `writeToParcel()` in `system_server` to strip VPN data before Binder serialization reaches target apps. Part of [vpnhide](../README.md).

Zero presence in the target app's process -- only "System Framework" is needed in the LSPosed scope.

## What it hooks

`writeToParcel()` on three classes inside `system_server`:

| Class | Effect |
|---|---|
| `NetworkCapabilities` | VPN transport and capability flags stripped before serialization. Covers `hasTransport(VPN)`, `getAllNetworks()` + VPN scan, `getTransportInfo()`. |
| `NetworkInfo` | VPN type rewritten to WIFI before serialization |
| `LinkProperties` | VPN interface name and routes stripped before serialization |

Uses a ThreadLocal save/restore pattern so the original values are preserved for non-target callers.

### Per-UID filtering

Filtering is controlled by `Binder.getCallingUid()` -- only apps whose UID appears in the target list see the filtered view. System services, VPN clients, and everything else see real data.

### Target management

Target UIDs are loaded from `/data/system/vpnhide_uids.txt` (written by the [kmod](../kmod/) or [zygisk](../zygisk/) `service.sh`). A `FileObserver` (inotify) watches for changes and reloads the list immediately -- no reboot needed.

## Install

1. Build the APK (`./gradlew assembleDebug`).
2. Install: `adb install app/build/outputs/apk/debug/app-debug.apk`.
3. Open LSPosed/Vector manager, go to Modules, enable **VPN Hide**.
4. Add **"System Framework"** to the module's scope. No other apps should be in scope.
5. Reboot.
6. Manage target apps via [kmod](../kmod/) or [zygisk](../zygisk/) WebUI, which writes UIDs to `/data/system/vpnhide_uids.txt`.

## Combined use with kmod

For apps with aggressive anti-tamper SDKs, full VPN hiding requires covering both native and Java API paths without any hooks in the target app's process:

- **[kmod](../kmod/)** covers native: `ioctl`, `getifaddrs` (netlink), `/proc/net/route`.
- **This module** covers Java APIs: `NetworkCapabilities`, `NetworkInfo`, `LinkProperties` via `writeToParcel()` in `system_server`.

Together they provide complete VPN hiding with zero footprint in the target process.

## Debugging

```bash
adb logcat | grep VpnHide
```

## Build

```bash
./gradlew assembleDebug
```

Requires JDK 17. Output: `app/build/outputs/apk/debug/app-debug.apk`.

## License

MIT. See [LICENSE](../LICENSE).
