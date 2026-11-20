#!/usr/bin/env bash
# Guard: store copy must not imply per-app connection history / packet capture.
set -eu
cd "$(dirname "$0")/.."
if grep -rniE "connection history" README.md fastlane/metadata; then
  echo "FAIL: 'connection history' found in README/fastlane metadata" >&2
  exit 1
fi
echo "OK"
