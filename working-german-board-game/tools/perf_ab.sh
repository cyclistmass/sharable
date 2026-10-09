#!/usr/bin/env bash
# Run only on an idle device. Both APKs must use the same signing key/package.
set -euo pipefail
: "${ANDROID_SERIAL:?ANDROID_SERIAL must be set explicitly (lane 2: emulator-5556)}"
if [[ $# != 2 || ! -f "$1" || ! -f "$2" ]]; then
  echo "Usage: $0 <apkA> <apkB>" >&2
  exit 2
fi
command -v adb >/dev/null
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
out="${PERF_OUT:-$PWD/perf-results/$(date +%Y%m%d-%H%M%S)}"
mkdir -p -- "$out"
printf 'variant,run,total_frames,janky_frames,janky_percent,p50_ms,p90_ms,p95_ms,slow_ui_thread\n' > "$out/summary.csv"
apk_a=$1
apk_b=$2
# Always leave the baseline installed, including after an interrupted measurement.
trap 'adb -s "$ANDROID_SERIAL" install -r "$apk_a" > "$out/restore-A.txt"' EXIT
for run in 1 2; do
  for variant in A B; do
    apk=$apk_a
    [[ $variant == B ]] && apk=$apk_b
    prefix="$out/${variant}-${run}"
    # No uninstall/clear: preserve the same preferences for both candidates.
    adb -s "$ANDROID_SERIAL" install -r "$apk" > "$prefix-install.txt"
    adb -s "$ANDROID_SERIAL" shell am force-stop com.mauro.wgbg
    adb -s "$ANDROID_SERIAL" shell am start -W -n com.mauro.wgbg/.MainActivity -e start base > "$prefix-start.txt"
    sleep 10
    adb -s "$ANDROID_SERIAL" shell dumpsys gfxinfo com.mauro.wgbg reset > "$prefix-reset.txt"
    sleep 15
    adb -s "$ANDROID_SERIAL" shell dumpsys gfxinfo com.mauro.wgbg > "$prefix-gfxinfo.txt"
    printf '%s,%s,' "$variant" "$run" >> "$out/summary.csv"
    awk -f "$script_dir/perf_gfxinfo.awk" "$prefix-gfxinfo.txt" >> "$out/summary.csv"
  done
done
printf 'variant,run,total_time_ms\n' > "$out/cold-start.csv"
for run in 1 2 3; do
  for variant in A B; do
    apk=$apk_a
    [[ $variant == B ]] && apk=$apk_b
    adb -s "$ANDROID_SERIAL" install -r "$apk" > "$out/$variant-cold-$run-install.txt"
    adb -s "$ANDROID_SERIAL" shell am force-stop com.mauro.wgbg
    adb -s "$ANDROID_SERIAL" shell am start -W -n com.mauro.wgbg/.MainActivity -e start base > "$out/$variant-cold-$run.txt"
    printf '%s,%s,%s\n' "$variant" "$run" "$(awk '/TotalTime:/ {print $2}' "$out/$variant-cold-$run.txt" | tr -d '\r')" >> "$out/cold-start.csv"
    sleep 10
  done
done
cat "$out/summary.csv"
echo "Raw dumps and am start timings: $out"
