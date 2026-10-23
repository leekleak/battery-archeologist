#!/usr/bin/env bash
# Build and run the development-only Battery Historian golden oracle.
#
# Usage:
#   tools/oracle/run.sh -input tools/fixtures/api36/checkin.txt \
#       -out-dir tools/fixtures/api36/expected
#
# Relative paths are resolved inside the repository, which is mounted at /work.
# Absolute paths under the repository root are rewritten to relative paths.
#
# Environment:
#   ORACLE_ENGINE      docker or podman (default: first working engine)
#   ORACLE_IMAGE       image tag (default: battery-archeologist-oracle:local)
#   ORACLE_SKIP_BUILD  set to 1 to reuse an existing image
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IMAGE="${ORACLE_IMAGE:-battery-archeologist-oracle:local}"

if [[ -n "${ORACLE_ENGINE:-}" ]]; then
  ENGINE="$ORACLE_ENGINE"
elif docker info >/dev/null 2>&1; then
  ENGINE=docker
elif podman info >/dev/null 2>&1; then
  ENGINE=podman
else
  echo "error: no working docker or podman engine" >&2
  echo "hint: on this host docker.sock is root-only; podman is available rootless" >&2
  exit 1
fi

if [[ "${ORACLE_SKIP_BUILD:-0}" != "1" ]]; then
  "$ENGINE" build -f "$ROOT/tools/oracle/Dockerfile" -t "$IMAGE" "$ROOT"
fi

rewrite_path() {
  local arg="$1"
  if [[ "$arg" == "$ROOT/"* ]]; then
    printf '%s' "${arg#"$ROOT"/}"
    return
  fi
  case "$arg" in
    -input=*)
      local value="${arg#-input=}"
      if [[ "$value" == "$ROOT/"* ]]; then
        printf -- '-input=%s' "${value#"$ROOT"/}"
        return
      fi
      ;;
    -out-dir=*)
      local value="${arg#-out-dir=}"
      if [[ "$value" == "$ROOT/"* ]]; then
        printf -- '-out-dir=%s' "${value#"$ROOT"/}"
        return
      fi
      ;;
  esac
  printf '%s' "$arg"
}

args=()
for arg in "$@"; do
  args+=("$(rewrite_path "$arg")")
done

# :z keeps SELinux hosts working; it is ignored by engines without SELinux.
# Rootless podman otherwise maps a numeric --user to a subuid, so keep the
# invoking uid for podman and use --user only for docker.
engine_args=(run --rm)
if [[ "$ENGINE" == "podman" ]]; then
  engine_args+=(--userns=keep-id)
else
  engine_args+=(--user "$(id -u):$(id -g)")
fi

exec "$ENGINE" "${engine_args[@]}" \
  -v "$ROOT:/work:z" \
  -w /work \
  "$IMAGE" "${args[@]}"
