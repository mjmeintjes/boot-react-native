# http://stackoverflow.com/questions/24412721/elegant-solution-to-implement-timeout-for-bash-commands-and-functions/24413646
# Usage: run_with_timeout N cmd args...
#    or: run_with_timeout cmd args...
# In the second case, cmd cannot be a number and the timeout will be 10 seconds.
run-with-timeout () { 
    local time=10
    if [[ $1 =~ ^[0-9]+$ ]]; then time=$1; shift; fi
    # Run in a subshell to avoid job control messages
    ( "$@" &
      child=$!
      # Avoid default notification in non-interactive shell for SIGTERM
      trap -- "" SIGTERM
      ( sleep $time
        kill $child 2> /dev/null ) &
      wait $child
    )
}

wait-for-avd() {
    local bootanim=""
    PATH=$(dirname $(dirname $(which android)))/platform-tools:$PATH
    until [[ "$bootanim" =~ "stopped" ]]; do
        sleep 5
        bootanim=$(adb -e shell getprop init.svc.bootanim 2>&1)
        echo "emulator status=$bootanim"
    done
}
wait-empty-dir() {
    until [ "$(ls -A "$1")" = "" ]
    do
        echo Waiting for $1 to be empty, currently contains:
        ls $1
        sleep 5
    done
}

wait-for-url() {
    until $(curl --output /dev/null --silent --head --fail $1); do
        echo "Waiting for $1"
        sleep 5
    done
    echo $1 found
}

wait-for-bg() {
    wait-empty-dir ~/tmp/background
}
run-bg() {
    md5=`echo $1 | md5sum | cut -f1 -d" "`
    mkdir -p ~/tmp/background
    echo "****** RUNNING '$1' IN BACKGROUND ******"
    (touch ~/tmp/background/$md5 && eval $1; rm ~/tmp/background/$md5) &
}
