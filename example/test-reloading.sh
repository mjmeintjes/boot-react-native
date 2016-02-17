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
echo "Running integration tests"
echo "Please ensure that Android device is connected to adb, otherwise these tests won't work."
adb reverse tcp:8081 tcp:8081 # packager
adb reverse tcp:8079 tcp:8079 # reloading
adb reverse tcp:9001 tcp:9001 # repl

boot dev --platform android & #2>&1 1>/dev/null &
appium & #2>&1 1>/dev/null &
echo "Waiting for boot to start up"
wait-for-url "http://localhost:8081/index.android.bundle?platform=android"
echo "Starting integration-tests.boot"
./integration-tests.boot
