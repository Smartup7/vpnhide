cmake_minimum_required(VERSION 3.22.1)
project("vpnhide_test")

add_library(vpnhide_test SHARED native-lib.cpp)

# 16 KB page size alignment for Android 15+ / Pixel 8 Pro
target_link_options(vpnhide_test PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(vpnhide_test
    android
    log
)
