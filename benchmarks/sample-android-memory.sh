#!/usr/bin/env bash
# Samples total PSS (KB) of the example app every second until killed.
#   benchmarks/sample-android-memory.sh [package] > memory.csv
pkg="${1:-software.gmi.betterpdf.example}"
echo "epoch_ms,total_pss_kb,native_heap_kb,graphics_kb,java_heap_kb"
while true; do
  out=$(adb shell dumpsys meminfo "$pkg" 2>/dev/null)
  total=$(echo "$out" | awk '/TOTAL PSS:/ {print $3; exit}')
  native=$(echo "$out" | awk '/^ *Native Heap:/ {print $3; exit}')
  graphics=$(echo "$out" | awk '/^ *Graphics:/ {print $2; exit}')
  java=$(echo "$out" | awk '/^ *Java Heap:/ {print $3; exit}')
  [ -n "$total" ] && echo "$(($(date +%s) * 1000)),$total,$native,$graphics,$java"
  sleep 1
done
