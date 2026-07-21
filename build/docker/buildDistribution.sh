#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
OPENFIRE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd -P)"
OUTPUT_DIR="${OPENFIRE_ROOT}/target"
DOCKER_BUILD_ARGS=()
DOCKER_BUILD_ARG_COUNT=0

usage() {
    cat <<EOF
Usage: $(basename "$0") [options]

Build an Openfire tar.gz using the repository Dockerfile. The archive includes
the Spaces, REST API, HTTP File Upload, and Monitoring plugins. Java 25 must be
installed on the computer where the archive is run.

Options:
  -o, --output-dir DIR     Write the archive to DIR (default: target)
      --version VERSION    Override the Openfire version used in the filename
      --build-arg ARG      Pass an additional Docker build argument (repeatable)
      --no-cache           Build without Docker's layer cache
  -h, --help               Show this help
EOF
}

while (($# > 0)); do
    case "$1" in
        -o|--output-dir)
            [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 2; }
            OUTPUT_DIR="$2"
            shift 2
            ;;
        --version)
            [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 2; }
            DOCKER_BUILD_ARGS+=(--build-arg "OPENFIRE_VERSION=$2")
            DOCKER_BUILD_ARG_COUNT=$((DOCKER_BUILD_ARG_COUNT + 1))
            shift 2
            ;;
        --build-arg)
            [[ $# -ge 2 ]] || { echo "Missing value for $1" >&2; exit 2; }
            DOCKER_BUILD_ARGS+=(--build-arg "$2")
            DOCKER_BUILD_ARG_COUNT=$((DOCKER_BUILD_ARG_COUNT + 1))
            shift 2
            ;;
        --no-cache)
            DOCKER_BUILD_ARGS+=(--no-cache)
            DOCKER_BUILD_ARG_COUNT=$((DOCKER_BUILD_ARG_COUNT + 1))
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

command -v docker >/dev/null 2>&1 || {
    echo "Docker is required to build the distribution." >&2
    exit 1
}

TEMP_OUTPUT="$(mktemp -d "${TMPDIR:-/tmp}/openfire-distribution.XXXXXX")"
trap 'rm -rf "${TEMP_OUTPUT}"' EXIT

DOCKER_COMMAND=(docker build \
    --target distribution \
    --output "type=local,dest=${TEMP_OUTPUT}")
if ((DOCKER_BUILD_ARG_COUNT > 0)); then
    DOCKER_COMMAND+=("${DOCKER_BUILD_ARGS[@]}")
fi
DOCKER_COMMAND+=("${OPENFIRE_ROOT}")

"${DOCKER_COMMAND[@]}"

ARCHIVES=("${TEMP_OUTPUT}"/openfire-*.tar.gz)
if [[ ${#ARCHIVES[@]} -ne 1 || ! -f "${ARCHIVES[0]}" ]]; then
    echo "Expected one Openfire archive, found ${#ARCHIVES[@]}." >&2
    exit 1
fi

mkdir -p "${OUTPUT_DIR}"
cp "${ARCHIVES[0]}" "${OUTPUT_DIR}/"

ARCHIVE_PATH="${OUTPUT_DIR}/$(basename "${ARCHIVES[0]}")"
echo "Created ${ARCHIVE_PATH}"
echo "Extract it and run: ./openfire/bin/openfire.sh"
