#!/usr/bin/env bash
# uBlock Origin style version names.
#   stable:     1.2.12
#   dev/alpha:  1.2.13b0, 1.2.13b1, ...
# A legacy 1.2.12-dev.28 continues as 1.2.12b29 so the counter stays newer.
# versionCode increases by 1, except --stable when the name is already x.y.z.
#
#   scripts/next-dev-version.sh           write the next dev build
#   scripts/next-dev-version.sh --stable  strip bN / -dev.N and write that stable name
set -euo pipefail

mode="dev"
if [[ "${1:-}" == "--stable" ]]; then
  mode="stable"
elif [[ -n "${1:-}" ]]; then
  echo "Usage: next-dev-version.sh [--stable]" >&2
  exit 1
fi

root="$(cd "$(dirname "$0")/.." && pwd)"
file="$root/VERSION"

name="$(grep -E '^name=' "$file" | head -1 | cut -d= -f2- | tr -d '[:space:]')"
code="$(grep -E '^code=' "$file" | head -1 | cut -d= -f2- | tr -d '[:space:]')"

if [[ ! "$code" =~ ^[0-9]+$ ]]; then
  echo "VERSION code must be an integer, got: $code" >&2
  exit 1
fi

write_version() {
  local next="$1"
  local next_code="$2"
  cat > "$file" <<EOF
# Android versionName / versionCode.
# Dev and alpha builds use the uBlock Origin form: 1.2.13b0
# Stable builds are 1.2.13. The release workflow commits this file with [skip ci].
name=${next}
code=${next_code}
EOF
  echo "${next} ${next_code}"
}

if [[ "$mode" == "stable" ]]; then
  if [[ "$name" =~ ^(.+)-dev\.[0-9]+$ ]] || [[ "$name" =~ ^(.+)b[0-9]+$ ]]; then
    write_version "${BASH_REMATCH[1]}" "$((code + 1))"
  elif [[ "$name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    write_version "$name" "$code"
  else
    echo "VERSION name must be x.y.z, x.y.zbN, or x.y.z-dev.N, got: $name" >&2
    exit 1
  fi
  exit 0
fi

if [[ "$name" =~ ^(.+)-dev\.([0-9]+)$ ]]; then
  next="${BASH_REMATCH[1]}b$((BASH_REMATCH[2] + 1))"
elif [[ "$name" =~ ^(.+)b([0-9]+)$ ]]; then
  next="${BASH_REMATCH[1]}b$((BASH_REMATCH[2] + 1))"
elif [[ "$name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  next="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.$((BASH_REMATCH[3] + 1))b0"
else
  echo "VERSION name must be x.y.z, x.y.zbN, or x.y.z-dev.N, got: $name" >&2
  exit 1
fi

write_version "$next" "$((code + 1))"
