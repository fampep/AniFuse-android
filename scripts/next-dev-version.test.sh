#!/usr/bin/env bash
# Exercises scripts/next-dev-version.sh against a throwaway VERSION file.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/scripts"
cp "$root/scripts/next-dev-version.sh" "$tmp/scripts/next-dev-version.sh"
chmod +x "$tmp/scripts/next-dev-version.sh"

write_name() {
  printf '%s\n' "# test" "name=$1" "code=$2" > "$tmp/VERSION"
}

expect() {
  local got="$1"
  local want="$2"
  if [[ "$got" != "$want" ]]; then
    echo "expected [$want], got [$got]" >&2
    exit 1
  fi
}

write_name "1.2.12-dev.28" 257
expect "$(bash "$tmp/scripts/next-dev-version.sh")" "1.2.12b29 258"

write_name "1.2.12b29" 258
expect "$(bash "$tmp/scripts/next-dev-version.sh")" "1.2.12b30 259"

write_name "1.2.12" 300
expect "$(bash "$tmp/scripts/next-dev-version.sh")" "1.2.13b0 301"

write_name "1.2.12b29" 258
expect "$(bash "$tmp/scripts/next-dev-version.sh" --stable)" "1.2.12 259"

write_name "1.2.12-dev.28" 257
expect "$(bash "$tmp/scripts/next-dev-version.sh" --stable)" "1.2.12 258"

write_name "1.2.12" 300
expect "$(bash "$tmp/scripts/next-dev-version.sh" --stable)" "1.2.12 300"

echo "next-dev-version: ok"
