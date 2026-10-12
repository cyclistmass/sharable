#!/usr/bin/env bash
# WGBG_PYTHON may point to a local Python executable; no machine-specific path is shipped.
set -euo pipefail
exec "${WGBG_PYTHON:-python}" "$(dirname "$0")/adb_lane2.py" "$@"
