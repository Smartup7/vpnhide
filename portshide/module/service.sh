#!/system/bin/sh
# Applies iptables rules from observers.txt at boot time.
# iptables rules are in-memory — this restores them after reboot.

MODDIR="${0%/*}"
APPLY="$MODDIR/vpnhide_ports_apply.sh"

# Wait for netd to finish its own iptables setup so our rules survive
# netd's initial chain rebuild. Check for the bw_OUTPUT chain as a signal
# that netd has populated its baseline rules.
for i in $(seq 1 60); do
    iptables -L bw_OUTPUT -n >/dev/null 2>&1 && break
    sleep 1
done

sh "$APPLY" && log -t vpnhide_ports "applied iptables rules at boot"
