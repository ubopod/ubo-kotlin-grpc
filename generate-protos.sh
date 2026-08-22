#!/bin/bash
# Sync proto sources from the parent ubo_app repo into the Kotlin library's
# src/main/proto/ tree. Code generation itself is performed by the Gradle
# protobuf plugin during `./gradlew :lib:assemble`; this script only mirrors
# the .proto files so the Gradle build has up-to-date inputs.
#
# By default the proto sources are read from the sibling Python tree at
# ../ubo_app/rpc/proto. A local ./proto directory (or symlink) is honoured
# if present for environments that vendor the protos.
#
# Flags:
#   --check         Compare ./protos/src/main/proto against the source tree and
#                   exit non-zero on drift. Used by CI.
#   --proto-dir P   Override the proto source directory.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_PROTO_DIR="$SCRIPT_DIR/../ubo_app/rpc/proto"
LOCAL_PROTO_DIR="$SCRIPT_DIR/proto"
DEST_DIR="$SCRIPT_DIR/protos/src/main/proto"

PROTO_DIR=""
CHECK_MODE=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --check)
            CHECK_MODE=1
            shift
            ;;
        --proto-dir)
            PROTO_DIR="$2"
            shift 2
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
done

if [ -z "$PROTO_DIR" ]; then
    if [ -d "$LOCAL_PROTO_DIR" ]; then
        PROTO_DIR="$LOCAL_PROTO_DIR"
    else
        PROTO_DIR="$DEFAULT_PROTO_DIR"
    fi
fi

if [ ! -d "$PROTO_DIR" ]; then
    echo "Error: proto directory not found at $PROTO_DIR" >&2
    echo "Hint: clone the parent ubo-apple-apps repo, or pass --proto-dir." >&2
    exit 1
fi

if [ ! -f "$PROTO_DIR/ubo/v1/ubo.proto" ]; then
    echo "Error: $PROTO_DIR/ubo/v1/ubo.proto is missing." >&2
    echo "Run 'uv run poe proto:generate' from the ubo-apple-apps root first." >&2
    exit 1
fi

if [ "$CHECK_MODE" -eq 1 ]; then
    TARGET_DIR="$(mktemp -d)"
    trap 'rm -rf "$TARGET_DIR"' EXIT
else
    TARGET_DIR="$DEST_DIR"
    rm -rf "$TARGET_DIR"
    mkdir -p "$TARGET_DIR"
fi

echo "Syncing proto sources..."
echo "  proto source: $PROTO_DIR"
echo "  destination:  $TARGET_DIR"

mkdir -p "$TARGET_DIR"
# Copy each proto package directory we know about. Keeps the destination
# clean and avoids accidentally vendoring unrelated files (e.g. README,
# IDE caches) that may live under the source tree.
for pkg in package_info ubo store secrets; do
    src="$PROTO_DIR/$pkg"
    if [ ! -d "$src" ]; then
        echo "Error: expected proto package '$pkg' under $PROTO_DIR" >&2
        exit 1
    fi
    mkdir -p "$TARGET_DIR/$pkg"
    cp -R "$src/." "$TARGET_DIR/$pkg/"
done

if [ "$CHECK_MODE" -eq 1 ]; then
    if diff -r -q "$DEST_DIR" "$TARGET_DIR" > /dev/null 2>&1; then
        echo "OK: $DEST_DIR matches the source proto tree."
        exit 0
    fi
    echo "DRIFT: $DEST_DIR is out of date relative to $PROTO_DIR." >&2
    diff -r "$DEST_DIR" "$TARGET_DIR" || true
    echo "Run ./generate-protos.sh to refresh." >&2
    exit 1
fi

echo "Proto sources synced into $DEST_DIR"
echo "Next: run ./gradlew :lib:assemble to (re)generate Kotlin/Java/gRPC stubs."
