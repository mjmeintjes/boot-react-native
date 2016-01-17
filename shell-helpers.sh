wait-empty-dir() {
    until [ "$(ls -A "$1")" = "" ]
    do
        echo Waiting for $1 to be empty
        sleep 5
    done
}

wait-for-bg() {
    wait-empty-dir ~/tmp/background
}
run-bg() {
    md5=`echo $1 | md5sum | cut -f1 -d" "`
    mkdir -p ~/tmp/background
    (touch ~/tmp/background/$md5 && eval $1; rm ~/tmp/background/$md5) &
}
