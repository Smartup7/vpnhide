#!/usr/bin/env bash
# Build the Rust native library for the test app and copy it to jniLibs.
set -euo pipefail

cd "$(dirname "$0")/native"

# Auto-detect NDK
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    ANDROID_NDK_HOME="$(find "$HOME/Android/Sdk/ndk" -maxdepth 1 -mindepth 1 -type d 2>/dev/null | sort -V | tail -1)"
fi
export ANDROID_NDK_HOME

cargo ndk -t arm64-v8a build --release

SO="target/aarch64-linux-android/release/libvpnhide_test.so"
DEST="../app/src/main/jniLibs/arm64-v8a"
mkdir -p "$DEST"
cp "$SO" "$DEST/"

echo "Copied $(ls -lh "$DEST/libvpnhide_test.so" | awk '{print $5}') to $DEST/"
