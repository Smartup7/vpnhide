# vpnhide-kmod

A Linux kernel module that hides VPN network interfaces from selected
Android apps by intercepting kernel-level network operations via
kretprobes. Unlike userspace hooking (Zygisk inline hooks, LSPosed),
this approach leaves **zero footprint** in the target app's process —
no modified function prologues, no Xposed framework classes, no
anonymous memory regions — making it invisible to aggressive
anti-tamper SDKs (such as those used in banking apps for NFC
contactless payments).

Part of a three-module suite for hiding VPN on Android:

- [okhsunrog/vpnhide](https://github.com/okhsunrog/vpnhide) — LSPosed
  module for Java API hooks. Has app-process mode (default) and
  system_server mode (for anti-tamper SDK apps, managed through this module's
  WebUI).
- [okhsunrog/vpnhide-zygisk](https://github.com/okhsunrog/vpnhide-zygisk)
  — Zygisk module for libc inline hooks (`ioctl`, `getifaddrs`). Works
  for apps without anti-tamper SDKs.
- **This module** — kernel-level kretprobes covering the same native
  detection paths as vpnhide-zygisk, but with zero footprint in
  userspace. Required for apps where userspace hooks trigger anti-tamper
  crashes or silent NFC payment degradation.

## What it hooks

| kretprobe target | What it filters | Detection path covered |
|---|---|---|
| `dev_ioctl` | `SIOCGIFFLAGS`, `SIOCGIFNAME`: returns `-ENODEV` for VPN interfaces. `SIOCGIFCONF`: compacts VPN entries out of the returned array. | Direct `ioctl()` calls from native code (Flutter/Dart, JNI, C/C++) |
| `rtnl_fill_ifinfo` | Returns `-EMSGSIZE` for VPN devices during RTM_GETLINK netlink dumps, causing the kernel to skip them | `getifaddrs()` (which uses netlink internally), any netlink-based interface enumeration |
| `fib_route_seq_show` | Rewinds `seq->count` to hide lines with VPN interface names | `/proc/net/route` reads |

All filtering is **per-UID**: only processes whose UID appears in
`/proc/vpnhide_targets` see the filtered view. Everyone else
(system services, VPN client, NFC subsystem) sees the real data.

## Why not just use vpnhide-zygisk?

Apps that bundle aggressive anti-tamper SDKs (common in banking apps)
have native anti-tamper that detects:

- **LSPosed/Xposed** — ART method entry point trampolines →
  hard crash in `LibContentProvider.attachInfo()`
- **shadowhook inline hooks** — modified function prologues in
  libc.so + `[anon:shadowhook-*]` regions in `/proc/self/maps` →
  silent NFC contactless payment degradation (no crash, payment
  just stops working)

The anti-tamper SDK reads `/proc/self/maps` via **raw `svc #0` syscalls**
(bypassing any libc hook) and checks ELF relocation integrity. No
userspace interposition can hide from it.

A kernel module operates below the SDK's inspection capability:
kretprobes modify kernel function behavior, not userspace code.
The target app's process memory, ELF tables, and `/proc/self/maps`
are completely untouched.

## Verified working

Pixel 8 Pro, crDroid 12.8, Android 16 (API 36), kernel
6.1.145-android14-11:

- **Flutter app with native VPN detection** (libc ioctl): VPN hidden ✅
- **Banking app with aggressive anti-tamper SDK**: launches without
  crash, NFC contactless payment works ✅
- Both **RKNHardering** and **YourVPNDead** detection apps report
  clean (when combined with LSPosed companion for Java API coverage
  on non-anti-tamper-SDK apps)

## GKI compatibility

The module is built against the Android Common Kernel (ACK) source
for `android14-6.1`. All symbols it uses (`register_kretprobe`,
`proc_create`, `seq_read`, etc.) are part of the stable GKI KMI,
so the same `Module.symvers` CRCs work across all devices running
the same GKI generation.

KernelSU bypasses the kernel's vermagic check, so no runtime
patching is needed. `post-fs-data.sh` simply runs `insmod` directly.

### Current build target

- **`android14-6.1`** — Pixel 8/9 series, Samsung Galaxy S24/S25,
  OnePlus 12/13, Xiaomi 14/15, and most 2024 flagships on
  Android 14/15.

### TODO: multi-generation support

The C source is the same across GKI generations — only the
`Module.symvers` CRCs and kernel headers differ. To support other
generations, build against the corresponding ACK branch:

| GKI generation | ACK branch | Devices |
|---|---|---|
| `android13-5.15` | `android13-5.15` | Pixel 7, some 2023 devices |
| `android14-5.15` | `android14-5.15` | Some Samsung on Android 14 |
| `android14-6.1` | `android14-6.1` | **Current build** |
| `android15-6.1` | `android15-6.1` | Pixel 8/9 on Android 15 QPR |
| `android15-6.6` | `android15-6.6` | Future devices |

Each generation needs a separate `.ko`. The build steps are
identical — only the kernel source checkout and `Module.symvers`
change. A future CI matrix build could produce all variants from
one commit.

## Build

### Prerequisites

- Android Common Kernel source for `android14-6.1`:
  ```bash
  # If you have the Pixel kernel tree:
  # The source is at <kernel_tree>/aosp/ with remote
  # https://android.googlesource.com/kernel/common
  #
  # Or clone directly:
  git clone --depth=1 -b android14-6.1 \
      https://android.googlesource.com/kernel/common \
      /path/to/kernel-source
  ```

- Android clang toolchain (ships with the kernel tree under
  `prebuilts/clang/host/linux-x86/clang-r*`)

- The kernel source must be **prepared** before building modules.
  This requires several steps (documented below).

### Preparing the kernel source

The kernel source needs `.config`, generated headers, and
`Module.symvers` before out-of-tree modules can compile.

**1. Set `.config`:**
Pull the running kernel's config from a device:
```bash
adb shell "su -c 'gzip -d < /proc/config.gz'" > /path/to/kernel-source/.config
```
Or use the GKI defconfig:
```bash
cd /path/to/kernel-source
make ARCH=arm64 LLVM=1 gki_defconfig
```

**2. Generate headers (`make prepare`):**
```bash
CLANG=/path/to/prebuilts/clang/host/linux-x86/clang-r487747c/bin

# Create empty abi_symbollist if missing (GKI build expects it)
touch abi_symbollist.raw

make ARCH=arm64 LLVM=1 LLVM_IAS=1 \
    CC=$CLANG/clang LD=$CLANG/ld.lld AR=$CLANG/llvm-ar \
    NM=$CLANG/llvm-nm OBJCOPY=$CLANG/llvm-objcopy \
    OBJDUMP=$CLANG/llvm-objdump STRIP=$CLANG/llvm-strip \
    CROSS_COMPILE=aarch64-linux-gnu- \
    olddefconfig prepare
```

If `make prepare` fails on `tools/bpf/resolve_btfids` (common with
system clang version mismatches), the module can still build — the
error only affects BTF generation which is optional.

**3. Generate `Module.symvers`:**
The CRCs must match the running kernel. Extract them from existing
`.ko` modules shipped with your ROM:

```bash
# Get the prebuilt .ko files from your ROM's kernel package
# (e.g., from the device tree's -kernels repo)
for ko in /path/to/prebuilt/*.ko; do
    modprobe --dump-modversions "$ko" 2>/dev/null
done | sort -u -k2 | \
    awk '{printf "%s\t%s\tvmlinux\tEXPORT_SYMBOL\t\n", $1, $2}' \
    > /path/to/kernel-source/Module.symvers
```

**4. Generate `scripts/module.lds`:**
```bash
$CLANG/clang -E -Wp,-MD,scripts/.module.lds.d -nostdinc \
    -I arch/arm64/include -I arch/arm64/include/generated \
    -I include -I include/generated \
    -include include/linux/kconfig.h \
    -D__KERNEL__ -DCC_USING_PATCHABLE_FUNCTION_ENTRY \
    --target=aarch64-linux-gnu -x c scripts/module.lds.S \
    2>/dev/null | grep -v '^#' > scripts/module.lds

# Fix ARM64 page-size literal that ld.lld can't parse
sed -i 's/((1UL) << 12)/4096/g' scripts/module.lds
```

**5. Set vermagic:**
For a universal build with runtime vermagic patching, use a long
placeholder:
```bash
PLACEHOLDER="6.1.999-placeholder-$(printf 'x%.0s' {1..100})"
echo "#define UTS_RELEASE \"$PLACEHOLDER\"" \
    > include/generated/utsrelease.h
echo -n "$PLACEHOLDER" > include/config/kernel.release
```

For a device-specific build, use the exact running kernel version:
```bash
KVER="$(adb shell uname -r)"
echo "#define UTS_RELEASE \"$KVER\"" \
    > include/generated/utsrelease.h
```

### Building the module

```bash
cd /path/to/vpnhide-kmod

KSRC=/path/to/kernel-source \
CLANG=/path/to/clang/bin \
make -C $KSRC M=$(pwd) \
    ARCH=arm64 LLVM=1 LLVM_IAS=1 \
    CC=$CLANG/clang LD=$CLANG/ld.lld \
    AR=$CLANG/llvm-ar NM=$CLANG/llvm-nm \
    OBJCOPY=$CLANG/llvm-objcopy \
    OBJDUMP=$CLANG/llvm-objdump \
    STRIP=$CLANG/llvm-strip \
    CROSS_COMPILE=aarch64-linux-gnu- \
    modules
```

Output: `vpnhide_kmod.ko`

### Building the KSU module zip

```bash
cp vpnhide_kmod.ko module/
./build-zip.sh
# Output: vpnhide-kmod.zip
```

## Install

1. `adb push vpnhide-kmod.zip /sdcard/Download/`
2. KernelSU-Next manager → Modules → Install from storage
3. Reboot

On boot:
- `post-fs-data.sh` runs `insmod` to load the kernel module
- `service.sh` resolves package names from
  `/data/adb/vpnhide_kmod/targets.txt` to UIDs via
  `pm list packages -U` and writes them to `/proc/vpnhide_targets`

### Picking target apps

**WebUI (recommended):** open the module in KernelSU-Next manager
and tap the WebUI entry. Select apps, save. The WebUI writes to
**three places** simultaneously:
1. `targets.txt` — persistent package names (survives module updates)
2. `/proc/vpnhide_targets` — resolved UIDs for the kernel module
3. `/data/system/vpnhide_uids.txt` — resolved UIDs for the
   [vpnhide](https://github.com/okhsunrog/vpnhide) LSPosed module's
   system_server hooks (live reload via inotify)

All changes apply immediately — no reboot needed.

**Shell:**
```bash
# Write package names to the persistent config
adb shell su -c 'echo "com.example.targetapp" > /data/adb/vpnhide_kmod/targets.txt'

# Or write UIDs directly to the kernel module
adb shell su -c 'echo 10423 > /proc/vpnhide_targets'
```

### Manual loading (without KSU module)

```bash
adb push vpnhide_kmod.ko /data/local/tmp/
adb shell su -c 'insmod /data/local/tmp/vpnhide_kmod.ko'
adb shell su -c 'echo 10423 > /proc/vpnhide_targets'
```

## Combined use with system_server hooks

For apps with aggressive anti-tamper SDKs (common in banking apps),
full VPN hiding requires covering both native and Java API detection
paths — without placing any hooks in the target app's process:

- **vpnhide-kmod** (this module) covers the native side: `ioctl`
  (`SIOCGIFFLAGS` / `SIOCGIFNAME` / `SIOCGIFCONF`), `getifaddrs()`
  (via `rtnl_fill_ifinfo`), and `/proc/net/route` (via
  `fib_route_seq_show`).
- **[vpnhide](https://github.com/okhsunrog/vpnhide) system_server
  hooks** cover the Java API side: `NetworkCapabilities.writeToParcel()`,
  `NetworkInfo.writeToParcel()`, `LinkProperties.writeToParcel()` —
  stripping VPN data before Binder serialization reaches the app.

Together they provide complete VPN hiding without any hooks in the
target app's process. The anti-tamper SDK cannot detect either
component.

### Setup

1. Install **vpnhide-kmod** as a KSU module (this module).
2. Install **[vpnhide](https://github.com/okhsunrog/vpnhide)** as an
   LSPosed/Vector module and add **"System Framework"** to its scope.
3. Pick target apps in vpnhide-kmod's WebUI — it manages targets for
   both the kernel module and the system_server hooks.
4. **Remove** banking apps from vpnhide's LSPosed app-process scope
   (if they were added previously). Only "System Framework" should be
   in scope for anti-tamper SDK apps — loading the module into the
   target app's process will trigger the SDK's anti-tamper detection.

For apps without aggressive anti-tamper SDKs, the standard combination
of vpnhide (app-process hooks) + vpnhide-zygisk provides more complete
Java + native coverage and does not require this kernel module.

## Architecture notes

### Why kretprobes work here

kretprobes instrument kernel functions by replacing their return
address on the stack. Unlike userspace inline hooks (which modify
instruction bytes), kretprobes:

- Don't modify the target function's code in a way visible to
  userspace — `/proc/self/maps` and the function's ELF bytes are
  unchanged
- Can't be detected by the target app — the app can only inspect
  its own process memory, not kernel data structures
- Work on any function visible in `/proc/kallsyms`, including
  static (non-exported) functions

### dev_ioctl calling convention (GKI 6.1, arm64)

```c
int dev_ioctl(struct net *net,       // x0
              unsigned int cmd,       // x1
              struct ifreq *ifr,      // x2 — KERNEL pointer
              void __user *data,      // x3 — userspace pointer
              bool *need_copyout)     // x4
```

**Important:** `x2` is a kernel-space pointer (the caller already
did `copy_from_user`). Using `copy_from_user` on it will EFAULT on
ARM64 with PAN enabled. The return handler reads via direct pointer
dereference.

### rtnl_fill_ifinfo trick

To skip a VPN interface during a netlink dump without corrupting
the message stream, the return handler sets the return value to
`-EMSGSIZE`. The dump iterator interprets this as "skb too small
for this entry" and moves to the next device without adding the
current one — effectively skipping it. The entry is never seen by
userspace.

## TODO

- [ ] Multi-GKI-generation CI build (see GKI compatibility section)
- [ ] `/proc/net/tcp`, `tcp6` filtering (`tcp4_seq_show` /
      `tcp6_seq_show`) — low priority, only matters for proxy-based
      VPN clients with open local ports
- [ ] `connect()` filter on localhost proxy ports (`__sys_connect`)
      — same caveat as above
- [x] ~~system_server LSPosed hooks~~ — implemented in
      [okhsunrog/vpnhide](https://github.com/okhsunrog/vpnhide) and
      managed through this module's WebUI

## License

GPL-2.0 (required for kernel modules using GPL-only symbols like
`register_kretprobe`).
