#!/bin/bash
# Redraw the source-code QR from res/raw/about.txt. Requires qrencode.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v qrencode >/dev/null || { echo "Install qrencode first (brew install qrencode / apt install qrencode)." >&2; exit 1; }
source_url="$(sed -n 's/^source=//p' res/raw/about.txt)"
[[ "$source_url" = https://* ]] || { echo "Missing HTTPS source URL in res/raw/about.txt." >&2; exit 1; }
qrencode -t PNG -s 12 -m 4 -l M -o res/drawable-nodpi/qr_source.png "$source_url"
