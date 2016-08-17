#!/usr/bin/env bash

# Usage
#
# patch-rn.sh path/to/patch-file path/to/app-dir

set -euo pipefail

patch_file="$1"
app_path="$2"
rel_path=node_modules/react-native
opts=(--silent -u --reject-file - --batch --force -p1 -d "${app_path}/${rel_path}")

ptch() {
    ret=0
    patch "${opts[@]}" --forward  < "$patch_file" >/dev/null || ret=$?

    if [[ $ret == 0 ]]; then
        echo "patch-rn.sh: patch successful"
    else
        echo "patch-rn.sh: patch failed ($ret)"
    fi
    exit $ret
}

main() {
    ret=0
    patch "${opts[@]}" --dry-run --reverse  < "$patch_file" >/dev/null || ret=$?

    if [[ $ret == 0 ]]; then
        echo "patch-rn.sh: React Native patch is already applied"
    elif [[ $ret == 1 ]]; then
        echo "patch-rn.sh: React Native patch not yet applied, patching now..."
        ptch
    else
        echo "patch-rn.sh: failed to check if patch has been applied yet"
        exit 1
    fi
}

main
