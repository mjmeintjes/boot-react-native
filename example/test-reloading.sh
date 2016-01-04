#! /bin/bash
set -euf -o pipefail
shutdown() {
    trap - SIGINT SIGTERM EXIT
    # Get our process group id
    PGID=$(ps -o pgid= $$ | grep -o [0-9]*)
    echo "Shutting down PGID $PGID"

    # Kill it in a new new process group
    setsid kill -- -$PGID
    exit 0
}

trap "shutdown" SIGINT SIGTERM EXIT

wait-for-url() {
    until $(curl --output /dev/null --silent --head --fail $1); do
        printf '.'
        sleep 5
    done
    echo $1 found
}
echo "Running fast-build"
boot fast-build &
BOOTPID=$!
echo "Current process ID - $$"
wait-for-url "http://localhost:8081/index.android.bundle?platform=android"
