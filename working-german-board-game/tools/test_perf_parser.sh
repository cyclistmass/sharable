#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
actual=$(printf '%s\n' \
  'Total frames rendered: 903' 'Janky frames: 19 (2.10%)' \
  'Janky frames (legacy): 46 (5.09%)' '50th percentile: 5ms' \
  '90th percentile: 11ms' '95th percentile: 15ms' 'Number Slow UI thread: 8' | awk -f "$script_dir/perf_gfxinfo.awk")
[[ $actual == '903,19,2.10,5,11,15,8' ]]
actual=$(printf '%s\n' 'No gfxinfo data' | awk -f "$script_dir/perf_gfxinfo.awk")
[[ $actual == 'NA,NA,NA,NA,NA,NA,NA' ]]
bash -n "$script_dir/perf_ab.sh"
if actual=$(env -u ANDROID_SERIAL bash "$script_dir/perf_ab.sh" 2>&1); then
  echo 'ERROR: unset ANDROID_SERIAL was accepted' >&2
  exit 1
fi
[[ $actual == *'ANDROID_SERIAL must be set explicitly'* ]]
echo 'perf parser: 2 fixtures, serial guard and A/B syntax passed (no device commands run)'
