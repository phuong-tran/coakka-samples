#!/bin/sh
set -eu

ROOT=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
CONFIG=${BENCHMARK_CONFIG:-config/go-pairs.json}
MODE=${BENCHMARK_MODE:-measure}
ECOSYSTEM=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["ecosystem"])' "$ROOT/$CONFIG")
CAMPAIGN_ID=${CAMPAIGN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-$ECOSYSTEM-pairs}
OUTPUT="$ROOT/evidence/$CAMPAIGN_ID"
STATE="$OUTPUT/host-control"
RESTORED=0

restore_host() {
  if [ "$RESTORED" -eq 0 ] && [ -f "$STATE/control-started" ]; then
    sudo "$ROOT/scripts/restore-host.sh" "$STATE"
    RESTORED=1
  fi
}

# shellcheck disable=SC2329  # Invoked by the signal traps below.
interrupted() {
  STATUS=$1
  restore_host
  trap - EXIT
  exit "$STATUS"
}

trap restore_host EXIT
trap 'interrupted 129' HUP
trap 'interrupted 130' INT
trap 'interrupted 143' TERM
mkdir -p "$OUTPUT"
sudo "$ROOT/scripts/quiesce-host.sh" "$STATE"
set +e
taskset -c 3 python3 "$ROOT/scripts/run-pairs.py" \
  --config "$ROOT/$CONFIG" --output "$OUTPUT" --mode "$MODE" "$@"
STATUS=$?
set -e
restore_host
trap - EXIT HUP INT TERM
if ! python3 "$ROOT/scripts/summarize-pairs.py" --evidence "$OUTPUT"; then
  [ "$STATUS" -ne 0 ] || STATUS=1
fi
if [ "$STATUS" -eq 0 ]; then
  if ! python3 "$ROOT/scripts/capture-restored-host.py" --evidence "$OUTPUT"; then
    STATUS=1
  fi
fi
if [ "$STATUS" -eq 0 ]; then
  if ! python3 "$ROOT/scripts/seal-evidence.py" --evidence "$OUTPUT"; then
    STATUS=1
  fi
fi
exit "$STATUS"
