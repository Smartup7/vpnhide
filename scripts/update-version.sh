#!/usr/bin/env bash
# Reads VERSION file and updates all version references in source files.
# Does NOT touch update-json — run update-json.sh after the release is published.
set -euo pipefail
cd "$(dirname "$0")/.."

VERSION="$(tr -d '[:space:]' < VERSION)"

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "error: VERSION must be MAJOR.MINOR.PATCH, got '$VERSION'" >&2
    exit 1
fi

IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"
VERSION_CODE=$(( MAJOR * 10000 + MINOR * 100 + PATCH ))

echo "Version: $VERSION (versionCode: $VERSION_CODE)"

# kmod module.prop
sed -i "s/^version=.*/version=v${VERSION}/" kmod/module/module.prop
sed -i "s/^versionCode=.*/versionCode=${VERSION_CODE}/" kmod/module/module.prop

# zygisk module.prop
sed -i "s/^version=.*/version=v${VERSION}/" zygisk/module/module.prop
sed -i "s/^versionCode=.*/versionCode=${VERSION_CODE}/" zygisk/module/module.prop

# portshide module.prop
sed -i "s/^version=.*/version=v${VERSION}/" portshide/module/module.prop
sed -i "s/^versionCode=.*/versionCode=${VERSION_CODE}/" portshide/module/module.prop

# zygisk Cargo.toml (first version = line only)
sed -i '0,/^version = ".*"/s//version = "'"${VERSION}"'"/' zygisk/Cargo.toml

# lsposed app/build.gradle.kts
sed -i 's/versionCode = [0-9]*/versionCode = '"${VERSION_CODE}"'/' lsposed/app/build.gradle.kts
sed -i 's/versionName = ".*"/versionName = "'"${VERSION}"'"/' lsposed/app/build.gradle.kts

# lsposed native Cargo.toml
sed -i '0,/^version = ".*"/s//version = "'"${VERSION}"'"/' lsposed/native/Cargo.toml

echo "Updated:"
echo "  kmod/module/module.prop"
echo "  zygisk/module/module.prop"
echo "  portshide/module/module.prop"
echo "  zygisk/Cargo.toml"
echo "  lsposed/app/build.gradle.kts"
echo "  lsposed/native/Cargo.toml"
