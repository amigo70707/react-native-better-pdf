#!/usr/bin/env bash
# iOS simulator benchmark (memory = host-process physical footprint of the
# simulated app; NOT representative of device memory — use for comparison).
#   benchmarks/run-ios-sim.sh <simulator-udid> [fixtures...]
#   SOURCE=remote benchmarks/run-ios-sim.sh …   # open from the fixture server: load includes the download
# Requires a Release build of the example installed, and the fixture server.
set -uo pipefail
cd "$(dirname "$0")"
sim="$1"; shift
fixtures=("${@:-small medium large extreme}")
[ $# -eq 0 ] && fixtures=(small medium large extreme)
app=software.gmi.betterpdf.example
mkdir -p results
out="results/ios-sim-$(date +%Y%m%dT%H%M%S).txt"
echo "| Fixture | Library | Load (ms) | Peak footprint (MB) | Footprint after run (MB) | JS callbacks while scrolling |" | tee "$out"
echo "| --- | --- | ---: | ---: | ---: | ---: |" | tee -a "$out"
for fixture in "${fixtures[@]}"; do
  for library in better react-native-pdf; do
    xcrun simctl terminate "$sim" "$app" 2>/dev/null
    sleep 2
    xcrun simctl launch "$sim" "$app" >/dev/null
    sleep 4
    pid=$(xcrun simctl spawn "$sim" launchctl list | awk '/betterpdf/ {print $1}')
    start=$(date "+%Y-%m-%d %H:%M:%S")
    maestro --device "$sim" test -e LIBRARY="$library" -e FIXTURE="$fixture" -e SOURCE="${SOURCE:-local}" ios-sim-bench.yaml > "/tmp/ios-sim-bench.log" 2>&1 || echo "  (maestro failed: see /tmp/ios-sim-bench.log)"
    peak=$(footprint -p "$pid" 2>/dev/null | awk '/phys_footprint_peak/ {print $2}')
    now=$(footprint -p "$pid" 2>/dev/null | awk '/phys_footprint:/ {print $2}')
    json=$(xcrun simctl spawn "$sim" log show --style compact --start "$start" --predicate 'eventMessage CONTAINS "BETTERPDF_BENCH_RESULT"' 2>/dev/null | grep -o '{.*}' | tail -1)
    load=$(echo "$json" | python3 -c "import json,sys; d=json.loads(sys.stdin.read() or '{}'); print(d.get('timeToLoadMs','–'))" 2>/dev/null)
    callbacks=$(echo "$json" | python3 -c "import json,sys; d=json.loads(sys.stdin.read() or '{}'); print(d.get('jsCallbacksDuringScroll','–'))" 2>/dev/null)
    echo "| $fixture | $library | $load | $peak | $now | $callbacks |" | tee -a "$out"
  done
done
