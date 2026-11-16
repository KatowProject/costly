#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
action="${1:-}"
if [[ $# -gt 0 ]]; then shift; fi

usage() {
    cat <<'EOF'
Costly Android script for Git Bash on Windows.

Examples from project root:
  bash scripts/start-emulator.sh
  bash scripts/run-app.sh
  bash scripts/build-and-run.sh
  bash scripts/build-apk.sh
  bash scripts/list-emulators.sh
  bash scripts/check-android.sh
  bash scripts/stop-emulator.sh

Options:
  --avd NAME              Specify AVD name (also accepts -Avd).
  --boot-timeout SECONDS  Android boot timeout, 1-600 (default 180).
  -h, --help              Show this help message.

Example:
  bash scripts/build-and-run.sh --avd Medium_Phone_API_37.0 --boot-timeout 300

The .sh scripts invoke the same android.ps1 backend as the .cmd versions.
This script is intended for Git Bash on Windows, not WSL, Linux, or macOS.
EOF
}

case "$action" in
    Start|Run|Build|BuildRun|List|Stop|Check) ;;
    *) printf 'FAILED: invalid launcher action: %s\n' "$action" >&2; exit 2 ;;
esac

arguments=(-Action "$action")
while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
        --avd|-Avd)
            if [[ $# -lt 2 || -z "$2" || "$2" == -* ]]; then
                printf 'FAILED: %s requires an AVD name.\n' "$1" >&2
                exit 2
            fi
            arguments+=(-Avd "$2")
            shift 2
            ;;
        --boot-timeout|-BootTimeoutSeconds)
            if [[ $# -lt 2 ]]; then
                printf 'FAILED: %s requires a number of seconds, 1-600.\n' "$1" >&2
                exit 2
            fi
            if [[ ! "$2" =~ ^[0-9]{1,3}$ ]] || (( 10#$2 < 1 || 10#$2 > 600 )); then
                printf 'FAILED: boot timeout must be an integer between 1 and 600.\n' >&2
                exit 2
            fi
            arguments+=(-BootTimeoutSeconds "$((10#$2))")
            shift 2
            ;;
        *)
            printf 'FAILED: unknown option: %s. Use --help.\n' "$1" >&2
            exit 2
            ;;
    esac
done

if ! command -v cygpath >/dev/null 2>&1 || ! command -v powershell.exe >/dev/null 2>&1; then
    printf 'FAILED: run from Git Bash on Windows with powershell.exe available in PATH.\n' >&2
    exit 1
fi

# Git Bash accepts /c/... paths; the shared Windows backend needs C:\... paths.
for variable in ANDROID_HOME ANDROID_SDK_ROOT ANDROID_USER_HOME ANDROID_AVD_HOME ANDROID_EMULATOR_HOME JAVA_HOME COSTLY_JAVA_HOME GRADLE_USER_HOME; do
    value="${!variable-}"
    if [[ -n "$value" ]]; then
        windows_value="$(cygpath -aw -- "$value")"
        export "$variable=$windows_value"
    fi
done

backend="$(cygpath -aw -- "$script_dir/android.ps1")"
# Avoid MSYS rewriting Windows paths or Android command arguments a second time.
export MSYS2_ARG_CONV_EXCL='*'
exec powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "$backend" "${arguments[@]}"
