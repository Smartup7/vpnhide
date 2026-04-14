package dev.okhsunrog.vpnhide

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.NetworkInterface

private const val TAG = "VPNHideTest"
private val VPN_PREFIXES = listOf("tun", "wg", "ppp", "tap", "ipsec", "xfrm")

data class CheckResult(
    val name: String,
    val passed: Boolean?,
    val detail: String,
)

private data class CheckResults(
    val native: List<CheckResult>,
    val java: List<CheckResult>,
) {
    val all get() = native + java
}

private suspend fun isVpnActive(): Boolean =
    withContext(Dispatchers.IO) {
        val (exitCode, output) = suExec("ls /sys/class/net/ 2>/dev/null")
        if (exitCode != 0) return@withContext false
        val vpnIfaces =
            output
                .lines()
                .map { it.trim() }
                .filter { name ->
                    name.isNotEmpty() && VPN_PREFIXES.any { name.startsWith(it) }
                }
        if (vpnIfaces.isEmpty()) return@withContext false
        vpnIfaces.any { iface ->
            val (_, state) =
                suExec("cat /sys/class/net/$iface/operstate 2>/dev/null")
            state.trim() == "unknown" || state.trim() == "up"
        }
    }

@Composable
fun DiagnosticsScreen(
    selfNeedsRestart: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cm = context.getSystemService(ConnectivityManager::class.java)

    var vpnDetected by remember { mutableStateOf<Boolean?>(null) }
    var results by remember { mutableStateOf<CheckResults?>(null) }
    var networkBlocked by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    val summaryFmt = stringResource(R.string.summary_format)

    fun updateResults(r: CheckResults) {
        results = r
        networkBlocked = r.all.any { it.detail.startsWith("NETWORK_BLOCKED:") }
        val scored = r.all.filter { it.passed != null }
        val passed = scored.count { it.passed == true }
        summary = String.format(summaryFmt, passed, scored.size)
    }

    LaunchedEffect(Unit) {
        vpnDetected = isVpnActive()
        if (vpnDetected == true && !selfNeedsRestart) {
            updateResults(runAllChecks(cm, context))
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

        if (vpnDetected == false) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                StatusBanner(
                    text = stringResource(R.string.banner_no_vpn),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            return@Column
        }

        if (selfNeedsRestart) {
            StatusBanner(
                text = stringResource(R.string.banner_added_self),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            StatusBanner(
                text = stringResource(R.string.banner_ready),
                containerColor = Color(0xFF1B5E20).copy(alpha = 0.15f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (networkBlocked) {
            Spacer(Modifier.height(6.dp))
            StatusBanner(
                text = stringResource(R.string.banner_network_blocked),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        if (summary != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = summary!!,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        results?.let { r ->
            Spacer(Modifier.height(16.dp))

            SectionHeader(stringResource(R.string.section_native))
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (check in r.native) {
                    CheckCard(check)
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader(stringResource(R.string.section_java))
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (check in r.java) {
                    CheckCard(check)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusBanner(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun CheckCard(r: CheckResult) {
    val darkTheme = isSystemInDarkTheme()
    val actualColor =
        if (darkTheme) {
            when (r.passed) {
                true -> Color(0xFF1B5E20).copy(alpha = 0.3f)
                false -> Color(0xFFB71C1C).copy(alpha = 0.3f)
                null -> MaterialTheme.colorScheme.surfaceVariant
            }
        } else {
            when (r.passed) {
                true -> Color(0xFFE8F5E9)
                false -> Color(0xFFFFEBEE)
                null -> MaterialTheme.colorScheme.surfaceVariant
            }
        }

    val badgeText =
        stringResource(
            when (r.passed) {
                true -> R.string.badge_pass
                false -> R.string.badge_fail
                null -> R.string.badge_info
            },
        )

    val badgeColor =
        when (r.passed) {
            true -> Color(0xFF2E7D32)
            false -> Color(0xFFC62828)
            null -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = actualColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = r.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = badgeText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = badgeColor,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = r.detail,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            )
        }
    }
}

// ==========================================================================
//  Check runner — runs directly in the main process
// ==========================================================================

private fun runAllChecks(
    cm: ConnectivityManager,
    context: android.content.Context,
): CheckResults {
    Log.i(TAG, "========================================")
    Log.i(TAG, "=== VPNHide — starting all checks ===")
    Log.i(TAG, "========================================")

    val res = context.resources

    val native =
        listOf(
            nativeCheck(res.getString(R.string.check_ioctl_flags)) { NativeChecks.checkIoctlSiocgifflags() },
            nativeCheck(res.getString(R.string.check_ioctl_mtu)) { NativeChecks.checkIoctlSiocgifmtu() },
            nativeCheck(res.getString(R.string.check_ioctl_conf)) { NativeChecks.checkIoctlSiocgifconf() },
            nativeCheck(res.getString(R.string.check_getifaddrs)) { NativeChecks.checkGetifaddrs() },
            nativeCheck(res.getString(R.string.check_netlink_getlink)) { NativeChecks.checkNetlinkGetlink() },
            nativeCheck(res.getString(R.string.check_netlink_getlink_recv)) { NativeChecks.checkNetlinkGetlinkRecv() },
            nativeCheck(res.getString(R.string.check_netlink_getroute)) { NativeChecks.checkNetlinkGetroute() },
            nativeCheck(res.getString(R.string.check_proc_route)) { NativeChecks.checkProcNetRoute() },
            nativeCheck(res.getString(R.string.check_proc_ipv6_route)) { NativeChecks.checkProcNetIpv6Route() },
            nativeCheck(res.getString(R.string.check_proc_if_inet6)) { NativeChecks.checkProcNetIfInet6() },
            nativeCheck(res.getString(R.string.check_proc_tcp)) { NativeChecks.checkProcNetTcp() },
            nativeCheck(res.getString(R.string.check_proc_tcp6)) { NativeChecks.checkProcNetTcp6() },
            nativeCheck(res.getString(R.string.check_proc_udp)) { NativeChecks.checkProcNetUdp() },
            nativeCheck(res.getString(R.string.check_proc_udp6)) { NativeChecks.checkProcNetUdp6() },
            nativeCheck(res.getString(R.string.check_proc_dev)) { NativeChecks.checkProcNetDev() },
            nativeCheck(res.getString(R.string.check_proc_fib_trie)) { NativeChecks.checkProcNetFibTrie() },
            nativeCheck(res.getString(R.string.check_sys_class_net)) { NativeChecks.checkSysClassNet() },
            checkNetworkInterfaceEnum(res.getString(R.string.check_net_iface_enum)),
            checkProcNetRouteJava(res.getString(R.string.check_proc_route_java)),
        )

    val java =
        listOf(
            checkHasTransportVpn(cm, res.getString(R.string.check_has_transport_vpn)),
            checkHasCapabilityNotVpn(cm, res.getString(R.string.check_has_capability_not_vpn)),
            checkTransportInfo(cm, res.getString(R.string.check_transport_info)),
            checkAllNetworksVpn(cm, res.getString(R.string.check_all_networks_vpn)),
            checkActiveNetworkVpn(cm, res.getString(R.string.check_active_network_vpn)),
            checkLinkPropertiesIfname(cm, res.getString(R.string.check_link_properties)),
            checkLinkPropertiesRoutes(cm, res.getString(R.string.check_link_properties_routes)),
            checkProxyHost(res.getString(R.string.check_proxy_host)),
        )

    val all = native + java
    val scored = all.filter { it.passed != null }
    val passed = scored.count { it.passed == true }
    Log.i(TAG, "=== SUMMARY: $passed/${scored.size} passed ===")

    return CheckResults(native = native, java = java)
}

private fun nativeCheck(
    name: String,
    block: () -> String,
): CheckResult =
    try {
        val raw = block()
        val passed =
            when {
                raw.startsWith("PASS") -> true
                raw.startsWith("NETWORK_BLOCKED:") -> null
                else -> false
            }
        Log.i(TAG, "[$name] $raw")
        CheckResult(name, passed, raw)
    } catch (e: Exception) {
        val detail = "FAIL: exception: ${e.message}"
        Log.e(TAG, "[$name] $detail", e)
        CheckResult(name, false, detail)
    }

// ==========================================================================
//  Java API checks
// ==========================================================================

private fun checkHasTransportVpn(
    cm: ConnectivityManager,
    name: String,
): CheckResult {
    val net = cm.activeNetwork ?: return CheckResult(name, true, "PASS: no active network")
    val caps = cm.getNetworkCapabilities(net) ?: return CheckResult(name, true, "PASS: no capabilities")
    val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    val hasCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    val detail =
        if (!hasVpn) {
            "PASS: hasTransport(VPN)=false, WIFI=$hasWifi, CELLULAR=$hasCellular"
        } else {
            "FAIL: hasTransport(VPN)=true, WIFI=$hasWifi, CELLULAR=$hasCellular"
        }
    return CheckResult(name, !hasVpn, detail)
}

private fun checkHasCapabilityNotVpn(
    cm: ConnectivityManager,
    name: String,
): CheckResult {
    val net = cm.activeNetwork ?: return CheckResult(name, true, "PASS: no active network")
    val caps = cm.getNetworkCapabilities(net) ?: return CheckResult(name, true, "PASS: no capabilities")
    val notVpn = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
    val detail = if (notVpn) "PASS: NOT_VPN capability present" else "FAIL: NOT_VPN capability MISSING"
    return CheckResult(name, notVpn, detail)
}

private fun checkTransportInfo(
    cm: ConnectivityManager,
    name: String,
): CheckResult {
    val net = cm.activeNetwork ?: return CheckResult(name, true, "PASS: no active network")
    val caps = cm.getNetworkCapabilities(net) ?: return CheckResult(name, true, "PASS: no capabilities")
    val info = caps.transportInfo
    val className = info?.javaClass?.name ?: "null"
    val isVpn = className.contains("VpnTransportInfo")
    val detail = if (!isVpn) "PASS: transportInfo=$className" else "FAIL: VpnTransportInfo: $info"
    return CheckResult(name, !isVpn, detail)
}

private fun checkNetworkInterfaceEnum(name: String): CheckResult =
    try {
        val ifaces =
            NetworkInterface.getNetworkInterfaces()
                ?: return CheckResult(name, true, "PASS: returned null")
        val allNames = mutableListOf<String>()
        val vpnNames = mutableListOf<String>()
        for (iface in ifaces) {
            allNames.add(iface.name)
            if (VPN_PREFIXES.any { iface.name.startsWith(it) }) vpnNames.add(iface.name)
        }
        val detail =
            if (vpnNames.isEmpty()) {
                "PASS: ${allNames.size} ifaces [${allNames.joinToString()}], no VPN"
            } else {
                "FAIL: VPN [${vpnNames.joinToString()}] in [${allNames.joinToString()}]"
            }
        CheckResult(name, vpnNames.isEmpty(), detail)
    } catch (e: Exception) {
        CheckResult(name, false, "FAIL: ${e.message}")
    }

@Suppress("DEPRECATION")
private fun checkAllNetworksVpn(
    cm: ConnectivityManager,
    name: String,
): CheckResult {
    val networks = cm.allNetworks
    if (networks.isEmpty()) return CheckResult(name, true, "PASS: no networks")
    val vpnNetworks =
        networks.filter { net ->
            cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    val detail =
        if (vpnNetworks.isEmpty()) {
            "PASS: ${networks.size} networks, none have TRANSPORT_VPN"
        } else {
            "FAIL: ${vpnNetworks.size} network(s) with TRANSPORT_VPN"
        }
    return CheckResult(name, vpnNetworks.isEmpty(), detail)
}

private fun checkActiveNetworkVpn(
    cm: ConnectivityManager,
    name: String,
): CheckResult {
    val net = cm.activeNetwork ?: return CheckResult(name, true, "PASS: no active network")
    val caps = cm.getNetworkCapabilities(net) ?: return CheckResult(name, true, "PASS: no capabilities")
    val transports = mutableListOf<String>()
    mapOf(
        NetworkCapabilities.TRANSPORT_CELLULAR to "CELLULAR",
        NetworkCapabilities.TRANSPORT_WIFI to "WIFI",
        NetworkCapabilities.TRANSPORT_BLUETOOTH to "BLUETOOTH",
        NetworkCapabilities.TRANSPORT_ETHERNET to "ETHERNET",
        NetworkCapabilities.TRANSPORT_VPN to "VPN",
        NetworkCapabilities.TRANSPORT_WIFI_AWARE to "WIFI_AWARE",
    ).forEach { (id, label) -> if (caps.hasTransport(id)) transports.add(label) }
    val hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    val detail =
        if (!hasVpn) {
            "PASS: transports=[${transports.joinToString()}], no VPN"
        } else {
            "FAIL: transports include VPN: [${transports.joinToString()}]"
        }
    return CheckResult(name, !hasVpn, detail)
}

private fun checkLinkPropertiesIfname(
    cm: ConnectivityManager,
    name: String,
): CheckResult {
    val net = cm.activeNetwork ?: return CheckResult(name, true, "PASS: no active network")
    val lp = cm.getLinkProperties(net) ?: return CheckResult(name, true, "PASS: no link properties")
    val ifname = lp.interfaceName ?: "(null)"
    val routes = lp.routes.map { "${it.destination} via ${it.gateway} dev ${it.`interface`}" }
    val dns = lp.dnsServers.map { it.hostAddress ?: "?" }
    val isVpn = VPN_PREFIXES.any { ifname.startsWith(it) }
    val detail =
        if (!isVpn) {
            "PASS: ifname=$ifname, ${routes.size} routes, dns=[${dns.joinToString()}]"
        } else {
            "FAIL: ifname=$ifname is a VPN interface"
        }
    return CheckResult(name, !isVpn, detail)
}

private fun checkLinkPropertiesRoutes(
    cm: ConnectivityManager,
    name: String,
): CheckResult {
    val net = cm.activeNetwork ?: return CheckResult(name, true, "PASS: no active network")
    val lp = cm.getLinkProperties(net) ?: return CheckResult(name, true, "PASS: no link properties")
    val routes = lp.routes
    val vpnRoutes =
        routes.filter { route ->
            val iface = route.`interface` ?: return@filter false
            VPN_PREFIXES.any { iface.startsWith(it) }
        }
    val detail =
        if (vpnRoutes.isEmpty()) {
            "PASS: ${routes.size} routes, none via VPN interfaces"
        } else {
            "FAIL: ${vpnRoutes.size} route(s) via VPN"
        }
    return CheckResult(name, vpnRoutes.isEmpty(), detail)
}

private fun checkProxyHost(name: String): CheckResult {
    val httpHost = System.getProperty("http.proxyHost")
    val socksHost = System.getProperty("socksProxyHost")
    val hasProxy = !httpHost.isNullOrEmpty() || !socksHost.isNullOrEmpty()
    val detail =
        if (!hasProxy) {
            "PASS: no proxy (http=$httpHost, socks=$socksHost)"
        } else {
            val httpPort = System.getProperty("http.proxyPort")
            val socksPort = System.getProperty("socksProxyPort")
            "FAIL: proxy found — http=$httpHost:$httpPort, socks=$socksHost:$socksPort"
        }
    return CheckResult(name, !hasProxy, detail)
}

private fun checkProcNetRouteJava(name: String): CheckResult =
    try {
        val allLines = mutableListOf<String>()
        val vpnLines = mutableListOf<String>()
        BufferedReader(InputStreamReader(java.io.FileInputStream("/proc/net/route"))).use { br ->
            var line: String?
            while (br.readLine().also { line = it } != null) {
                allLines.add(line!!)
                if (VPN_PREFIXES.any { line!!.startsWith(it) }) vpnLines.add(line!!.take(60))
            }
        }
        val detail =
            if (vpnLines.isEmpty()) {
                "PASS: ${allLines.size} lines, no VPN entries"
            } else {
                "FAIL: ${vpnLines.size} VPN lines:\n${vpnLines.joinToString("\n") { "  $it" }}"
            }
        CheckResult(name, vpnLines.isEmpty(), detail)
    } catch (e: Exception) {
        val msg = e.message ?: ""
        if (msg.contains("EACCES") || msg.contains("Permission denied")) {
            CheckResult(name, true, "PASS: access denied by SELinux")
        } else {
            CheckResult(name, false, "FAIL: ${e.message}")
        }
    }
