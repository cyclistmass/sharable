#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
result=$({
  printf 'Flags,IntendedVsync,FrameCompleted,\n'
  for ((i=100;i>=1;i--)); do printf '0,1000000000,%s,\n' "$((1000000000+i*1000000))"; done
  printf '1,1000000000,9000000000,\n0,1000000000,9223372036854775807,\n0,0,1000000,\n'
} | awk -f tools/perf_framestats.awk)
[[ "$result" == '100,95.000,99.000' ]]
[[ "$(printf 'No frames\n' | awk -f tools/perf_framestats.awk)" == '0,NA,NA' ]]
echo 'Framestats: 2 fixtures passed'
