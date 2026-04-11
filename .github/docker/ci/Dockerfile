# CI image for vpnhide.
#
# Bakes in everything needed for all three modules:
#   - Rust + NDK + cargo-ndk (zygisk)
#   - JDK 17 (lsposed, test-app)
#   - Google AOSP clang + cross-compile tools (kmod)
#
# Rebuilt by ci-image.yml when this Dockerfile changes (and monthly).

FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        ca-certificates curl unzip zip xz-utils \
        build-essential pkg-config \
        cmake ninja-build \
        openjdk-17-jdk-headless \
        bc kmod cpio \
        binutils-aarch64-linux-gnu \
        git && \
    rm -rf /var/lib/apt/lists/*

# ── Rust toolchain (for zygisk) ──────────────────────────────────────
ENV RUSTUP_HOME=/usr/local/rustup \
    CARGO_HOME=/usr/local/cargo \
    PATH=/usr/local/cargo/bin:$PATH

RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | \
        sh -s -- -y --default-toolchain stable --profile minimal --no-modify-path && \
    rustup target add aarch64-linux-android && \
    cargo install cargo-ndk --locked && \
    chmod -R a+w /usr/local/rustup /usr/local/cargo

# ── Android NDK (for zygisk) ─────────────────────────────────────────
ENV ANDROID_NDK_VERSION=r28b
ENV ANDROID_NDK_HOME=/opt/android-ndk

RUN curl -fsSL -o /tmp/ndk.zip \
        "https://dl.google.com/android/repository/android-ndk-${ANDROID_NDK_VERSION}-linux.zip" && \
    unzip -q /tmp/ndk.zip -d /opt && \
    mv "/opt/android-ndk-${ANDROID_NDK_VERSION}" "${ANDROID_NDK_HOME}" && \
    rm /tmp/ndk.zip && \
    chmod -R a+rx "${ANDROID_NDK_HOME}"

# ── Google AOSP clang (for kmod) ─────────────────────────────────────
# Same toolchain that built the Pixel GKI kernel — avoids ABI mismatches
# that cause bootloops with distro clang.
ENV AOSP_CLANG_VERSION=clang-r487747c
ENV AOSP_CLANG_DIR=/opt/aosp-clang

RUN git clone --depth=1 --filter=blob:none --sparse \
        -b android-gs-shusky-6.1-android16 \
        https://android.googlesource.com/platform/prebuilts/clang/host/linux-x86 \
        /tmp/clang-repo && \
    cd /tmp/clang-repo && \
    git sparse-checkout set ${AOSP_CLANG_VERSION} && \
    mv ${AOSP_CLANG_VERSION} ${AOSP_CLANG_DIR} && \
    rm -rf /tmp/clang-repo && \
    chmod -R a+rx ${AOSP_CLANG_DIR}
