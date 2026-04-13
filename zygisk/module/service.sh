#!/system/bin/sh
# Copies zygisk targets to module dir and resolves lsposed targets at boot.
# zygisk targets → module dir (read by Zygisk via get_module_dir() fd)
# lsposed targets → /data/system/vpnhide_uids.txt

ZYGISK_TARGETS="/data/adb/vpnhide_zygisk/targets.txt"
LSPOSED_TARGETS="/data/adb/vpnhide_lsposed/targets.txt"
MODULE_DIR="${0%/*}"
SS_UIDS_FILE="/data/system/vpnhide_uids.txt"

# Copy zygisk targets to module dir so Zygisk can read via get_module_dir() fd.
if [ -f "$ZYGISK_TARGETS" ]; then
    cp "$ZYGISK_TARGETS" "$MODULE_DIR/targets.txt" 2>/dev/null
fi

# Wait for PackageManager to be ready
for i in $(seq 1 30); do
    pm list packages >/dev/null 2>&1 && break
    sleep 1
done

# Migration: if lsposed targets don't exist yet, seed from zygisk targets
if [ ! -f "$LSPOSED_TARGETS" ] && [ -f "$ZYGISK_TARGETS" ]; then
    mkdir -p /data/adb/vpnhide_lsposed 2>/dev/null
    cp "$ZYGISK_TARGETS" "$LSPOSED_TARGETS"
    log -t vpnhide "migrated zygisk targets to lsposed targets"
fi

# Get all packages with UIDs in one call
ALL_PACKAGES="$(pm list packages -U 2>/dev/null)"

# resolve_uids <targets_file> — prints resolved UIDs to stdout
resolve_uids() {
    local targets_file="$1"
    [ -f "$targets_file" ] || return
    local uids=""
    while IFS= read -r line || [ -n "$line" ]; do
        pkg="$(echo "$line" | tr -d '[:space:]')"
        [ -z "$pkg" ] && continue
        case "$pkg" in \#*) continue ;; esac
        uid="$(echo "$ALL_PACKAGES" | grep "^package:${pkg} " | sed 's/.*uid://')"
        if [ -n "$uid" ]; then
            if [ -z "$uids" ]; then uids="$uid"; else uids="${uids}
${uid}"; fi
        else
            log -t vpnhide "package not found: $pkg"
        fi
    done < "$targets_file"
    [ -n "$uids" ] && echo "$uids"
}

# Resolve lsposed targets → /data/system/vpnhide_uids.txt
# Create persist dir if needed (for first-time installs)
mkdir -p /data/adb/vpnhide_lsposed 2>/dev/null
if [ -f "$LSPOSED_TARGETS" ]; then
    LSPOSED_UIDS="$(resolve_uids "$LSPOSED_TARGETS")"
    if [ -n "$LSPOSED_UIDS" ]; then
        echo "$LSPOSED_UIDS" > "$SS_UIDS_FILE"
        chmod 644 "$SS_UIDS_FILE"
        chcon u:object_r:system_data_file:s0 "$SS_UIDS_FILE" 2>/dev/null
        count="$(echo "$LSPOSED_UIDS" | wc -l)"
        log -t vpnhide "zygisk: wrote $count lsposed UIDs to $SS_UIDS_FILE"
    else
        echo > "$SS_UIDS_FILE"
        chmod 644 "$SS_UIDS_FILE"
        log -t vpnhide "zygisk: no lsposed UIDs resolved"
    fi
fi
