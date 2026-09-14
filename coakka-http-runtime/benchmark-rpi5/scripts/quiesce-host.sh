#!/bin/sh
set -eu

if [ "$(id -u)" -ne 0 ]; then
  echo "quiesce-host.sh must run as root" >&2
  exit 1
fi
if [ "$#" -ne 1 ]; then
  echo "usage: quiesce-host.sh HOST_CONTROL_DIRECTORY" >&2
  exit 1
fi

STATE=$1
if [ -e "$STATE/control-started" ]; then
  echo "host-control directory has already been used: $STATE" >&2
  exit 1
fi
mkdir -p "$STATE"

UNITS='avahi-daemon.socket cups.socket cups.path triggerhappy.socket avahi-daemon.service bluetooth.service cron.service cups-browsed.service cups.service ModemManager.service rtkit-daemon.service getty@tty1.service serial-getty@ttyAMA10.service triggerhappy.service'
TIMERS='apt-daily.timer apt-daily-upgrade.timer dpkg-db-backup.timer e2scrub_all.timer fstrim.timer logrotate.timer man-db.timer systemd-tmpfiles-clean.timer'
USER_UNITS='filter-chain.service pipewire-pulse.service wireplumber.service pipewire.service pipewire-pulse.socket pipewire.socket'
CONTROL_USER=${SUDO_USER:-}
CONTROL_UID=''
if [ -n "$CONTROL_USER" ] && [ "$CONTROL_USER" != root ]; then
  CONTROL_UID=$(id -u "$CONTROL_USER")
fi

user_systemctl() {
  runuser -u "$CONTROL_USER" -- env XDG_RUNTIME_DIR="/run/user/$CONTROL_UID" \
    systemctl --user "$@"
}

date --iso-8601=seconds > "$STATE/quiesce-started-at.txt"
systemctl list-units --all --no-pager --plain > "$STATE/units-before.txt"
systemctl list-timers --all --no-pager > "$STATE/timers-before.txt"
: > "$STATE/stopped-units.txt"
: > "$STATE/stopped-timers.txt"
: > "$STATE/stopped-user-units.txt"
: > "$STATE/runtime-masked-units.txt"
: > "$STATE/governors-before.txt"
printf '%s\n' "$CONTROL_USER" > "$STATE/control-user.txt"
if [ -n "$CONTROL_UID" ]; then
  user_systemctl list-units --state=running --no-pager --plain \
    > "$STATE/user-units-before.txt"
else
  : > "$STATE/user-units-before.txt"
fi

for governor in /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_governor; do
  [ -f "$governor" ] || continue
  printf '%s %s\n' "$governor" "$(cat "$governor")" >> "$STATE/governors-before.txt"
done
date --iso-8601=seconds > "$STATE/control-started"

ACTIVE_UNITS=''
for unit in $UNITS; do
  if systemctl is-active --quiet "$unit"; then
    printf '%s\n' "$unit" >> "$STATE/stopped-units.txt"
    ACTIVE_UNITS="$ACTIVE_UNITS $unit"
  fi
done
if systemctl is-active --quiet avahi-daemon.service ||
   systemctl is-active --quiet avahi-daemon.socket; then
  systemctl mask --runtime avahi-daemon.service avahi-daemon.socket
  printf '%s\n' avahi-daemon.service avahi-daemon.socket \
    > "$STATE/runtime-masked-units.txt"
fi
if [ -n "$ACTIVE_UNITS" ]; then
  # One systemd transaction lets dependent service/socket pairs stop together.
  # shellcheck disable=SC2086
  systemctl stop $ACTIVE_UNITS
fi
for timer in $TIMERS; do
  if systemctl is-active --quiet "$timer"; then
    printf '%s\n' "$timer" >> "$STATE/stopped-timers.txt"
    systemctl stop "$timer"
  fi
done
if [ -n "$CONTROL_UID" ]; then
  for unit in $USER_UNITS; do
    if user_systemctl is-active --quiet "$unit"; then
      printf '%s\n' "$unit" >> "$STATE/stopped-user-units.txt"
      user_systemctl stop "$unit"
    fi
  done
fi

for governor in /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_governor; do
  [ -f "$governor" ] || continue
  printf '%s\n' performance > "$governor"
done

sync
sleep 10
systemctl list-units --state=running --no-pager --plain > "$STATE/running-units-quiesced.txt"
systemctl list-timers --all --no-pager > "$STATE/timers-quiesced.txt"
if [ -n "$CONTROL_UID" ]; then
  user_systemctl list-units --state=running --no-pager --plain \
    > "$STATE/user-units-quiesced.txt"
else
  : > "$STATE/user-units-quiesced.txt"
fi
for governor in /sys/devices/system/cpu/cpu[0-9]*/cpufreq/scaling_governor; do
  [ ! -f "$governor" ] || printf '%s %s\n' "$governor" "$(cat "$governor")"
done > "$STATE/governors-quiesced.txt"
date --iso-8601=seconds > "$STATE/quiesced"
