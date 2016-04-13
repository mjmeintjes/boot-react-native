#!/bin/bash

set -euo pipefail
set -x

abspath() {
    python -c "import os,sys; print os.path.abspath(sys.argv[1])" "$1"
}

copy_bundle() {
    bundle="$proot/app/dist/main.jsbundle"
    dest="$tdir/main.jsbundle"

    rm -f "$dest"

    if [[ -f "$bundle" ]]; then
        cp "$bundle" "$dest"
    fi
}

proot="$(abspath $(dirname "$0")/../..)"

tdir="${TARGET_BUILD_DIR}/${UNLOCALIZED_RESOURCES_FOLDER_PATH}"

copy_bundle
