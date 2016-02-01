#!/bin/bash

set -euo pipefail
set -x

abspath() {
    python -c "import os,sys; print os.path.abspath(sys.argv[1])" "$1"
}

copy_bundle() {
    bundle="$proot/app/main.jsbundle"
    dest="$tdir/main.jsbundle"

    rm -f "$dest"

    cp "$bundle" "$dest"
}

proot="$(abspath $(dirname "$0")/../..)"

tdir="${TARGET_BUILD_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH}"

copy_bundle
