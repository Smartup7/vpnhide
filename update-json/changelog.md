## v0.6.1

### Added
- Detect gb.sweetlifecl as a Russian-vendor package in the app picker filter, so it shows up under the Russian-only filter alongside other domestic apps.

### Fixed
- Magisk versions before v28 failed to install vpnhide-ports and vpnhide-kmod with an unpack error — restored the META-INF/com/google/android/{update-binary,updater-script} entries the older managers expect.
- Ozon and other apps with root-detection scanning /proc/self/fd no longer hang when vpnhide-zygisk is installed. The module's zygote-side on_load was leaking the module-dir fd, which every forked app process inherited — root-tamper scans detected a descriptor pointing inside /data/adb/modules/ and refused to continue. The fd is now explicitly closed before any app fork.

## v0.6.0

### Added
- App hiding mode in Protection — hide selected apps from selected observer apps at the PackageManager level. Observer apps can no longer list, resolve, or query hidden apps.
- Ports hiding mode plus new vpnhide-ports.zip module — block selected apps from reaching 127.0.0.1 / ::1 ports to hide locally running VPN/proxy daemons (Clash, sing-box, V2Ray, Happ, etc.).
- Ports module integration in the dashboard — shows install state, active rules, observer count, and version mismatch/update warnings.

### Changed
- The old Apps tab is now Protection, split into three modes: Tun, Apps, and Ports.
- Ports rules apply immediately on Save and are restored automatically on boot.
- vpnhide-ports.zip is now included in the release/update pipeline with Magisk/KernelSU update metadata.

### Fixed
- Fixed LinkProperties filtering so VPN routes are stripped more reliably from app-visible network snapshots.
- Fixed SIOCGIFCONF filtering on some Android 12/13 5.10 kernels where the previous hook could succeed but never fire.
- Fixed debug log collection so app logcat entries are captured reliably on devices where logcat via su misses them.

## v0.5.3

### Added
- Debug log export — open the Diagnostics tab and tap "Collect debug log" at the bottom. The app gathers dmesg, check results, device info, module status, kernel symbols, targets, interfaces, routing tables, and logcat into a zip. Save to disk or share directly.
- Kernel module debug logging toggle — all 6 kretprobe hooks now log detailed info (UID, target status, interface name, filter decisions) when debug mode is active. Enabled automatically during debug log collection.

## v0.5.2

### Fixed
- Fixed SIOCGIFCONF filtering on kernel 5.10 (tun0 was visible in interface enumeration)
- Fixed zygisk first-launch race: dashboard no longer shows false "inactive" status
- Added recv hook in zygisk for netlink filtering on Android 10
- Fixed hardcoded v0.1.0 in module installer messages

## v0.5.1

### Fixed
- Fixed false "LSPosed/Vector not installed" warning when LSPosed uses non-standard module path (e.g. zygisk_lsposed)
- Fixed false LSPosed config warnings when hooks are already active at runtime
- "No target apps configured" now checks all modules, not just LSPosed
