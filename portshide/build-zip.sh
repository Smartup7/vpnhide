#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

OUT="vpnhide-ports.zip"
rm -f "$OUT"
(cd module && zip -qr "../$OUT" .)

echo
echo "Built: $OUT"
ls -lh "$OUT"
