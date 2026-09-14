#!/bin/sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
  echo "restore-host.sh must run as root" >&2
  exit 1
fi
if [ "$#" -ne 1 ]; then
  echo "usage: restore-host.sh HOST_CONTROL_DIRECTORY" >&2
  exit 1
fi

STATE=$1
if [ ! -f "$STATE/control-started" ]; then
  echo "missing host-control state: $STATE" >&2
  exit 1
fi

CONTROL_USER=$(cat "$STATE/control-user.txt" 2>/dev/null || true)
CONTROL_UID=''
if [ -n "$CONTROL_USER" ] && [ "$CONTROL_USER" != root ]; then
  CONTROL_UID=$(id -u "$CONTROL_USER")
fi

user_systemctl() {
  runuser -u "$CONTROL_USER" -- env XDG_RUNTIME_DIR="/run/user/$CONTROL_UID" \
    systemctl --user "$@"
}

while IFS=' ' read -r governor value; do
  [ -n "$governor" ] || continue
  [ ! -f "$governor" ] || printf '%s\n' "$value" > "$governor"
done < "$STATE/governors-before.txt"

if [ -s "$STATE/runtime-masked-units.txt" ]; then
  # shellcheck disable=SC2046
  systemctl unmask --runtime $(cat "$STATE/runtime-masked-units.txt")
fi

while IFS= read -r timer; do
  [ -n "$timer" ] || continue
  systemctl start "$timer"
done < "$STATE/stopped-timers.txt"
while IFS= read -r unit; do
  [ -n "$unit" ] || continue
  systemctl start "$unit"
done < "$STATE/stopped-units.txt"
if [ -n "$CONTROL_UID" ] && [ -f "$STATE/stopped-user-units.txt" ]; then
  while IFS= read -r unit; do
    [ -n "$unit" ] || continue
    user_systemctl start "$unit"
  done < "$STATE/stopped-user-units.txt"
fi

systemctl list-units --state=running --no-pager --plain > "$STATE/running-units-restored.txt"
systemctl list-timers --all --no-pager > "$STATE/timers-restored.txt"
if [ -n "$CONTROL_UID" ]; then
  user_systemctl list-units --state=running --no-pager --plain \
    > "$STATE/user-units-restored.txt"
fi
for governor in /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_governor; do
  [ ! -f "$governor" ] || printf '%s %s\n' "$governor" "$(cat "$governor")"
done > "$STATE/governors-restored.txt"
date --iso-8601=seconds > "$STATE/restored-at.txt"
