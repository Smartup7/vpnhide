package dev.okhsunrog.vpnhide

object NativeChecks {
    init {
        System.loadLibrary("vpnhide_checks")
    }

    external fun checkIoctlSiocgifflags(): String

    external fun checkIoctlSiocgifmtu(): String

    external fun checkIoctlSiocgifconf(): String

    external fun checkGetifaddrs(): String

    external fun checkProcNetRoute(): String

    external fun checkProcNetIfInet6(): String

    external fun checkNetlinkGetlink(): String

    external fun checkNetlinkGetlinkRecv(): String

    external fun checkNetlinkGetroute(): String

    external fun checkProcNetIpv6Route(): String

    external fun checkProcNetTcp(): String

    external fun checkProcNetTcp6(): String

    external fun checkProcNetUdp(): String

    external fun checkProcNetUdp6(): String

    external fun checkProcNetDev(): String

    external fun checkProcNetFibTrie(): String

    external fun checkSysClassNet(): String
}
