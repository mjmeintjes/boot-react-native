#!/bin/bash
source /etc/profile

if [[ -s ~/.bash_profile ]] ; then
  source ~/.bash_profile
fi

ANSI_RED="\033[31;1m"
ANSI_GREEN="\033[32;1m"
ANSI_RESET="\033[0m"
ANSI_CLEAR="\033[0K"

TRAVIS_TEST_RESULT=
TRAVIS_CMD=

function travis_cmd() {
  local assert output display retry timing cmd result

  cmd=$1
  TRAVIS_CMD=$cmd
  shift

  while true; do
    case "$1" in
      --assert)  assert=true; shift ;;
      --echo)    output=true; shift ;;
      --display) display=$2;  shift 2;;
      --retry)   retry=true;  shift ;;
      --timing)  timing=true; shift ;;
      *) break ;;
    esac
  done

  if [[ -n "$timing" ]]; then
    travis_time_start
  fi

  if [[ -n "$output" ]]; then
    echo "\$ ${display:-$cmd}"
  fi

  if [[ -n "$retry" ]]; then
    travis_retry eval "$cmd"
  else
    eval "$cmd"
  fi
  result=$?

  if [[ -n "$timing" ]]; then
    travis_time_finish
  fi

  if [[ -n "$assert" ]]; then
    travis_assert $result
  fi

  return $result
}

travis_time_start() {
  travis_timer_id=$(printf %08x $(( RANDOM * RANDOM )))
  travis_start_time=$(travis_nanoseconds)
  echo -en "travis_time:start:$travis_timer_id\r${ANSI_CLEAR}"
}

travis_time_finish() {
  local result=$?
  travis_end_time=$(travis_nanoseconds)
  local duration=$(($travis_end_time-$travis_start_time))
  echo -en "travis_time:end:$travis_timer_id:start=$travis_start_time,finish=$travis_end_time,duration=$duration\r${ANSI_CLEAR}"
  return $result
}

function travis_nanoseconds() {
  local cmd="date"
  local format="+%s%N"
  local os=$(uname)

  if hash gdate > /dev/null 2>&1; then
    cmd="gdate" # use gdate if available
  elif [[ "$os" = Darwin ]]; then
    format="+%s000000000" # fallback to second precision on darwin (does not support %N)
  fi

  $cmd -u $format
}

travis_assert() {
  local result=${1:-$?}
  if [ $result -ne 0 ]; then
    echo -e "\n${ANSI_RED}The command \"$TRAVIS_CMD\" failed and exited with $result during $TRAVIS_STAGE.${ANSI_RESET}\n\nYour build has been stopped."
    travis_terminate 2
  fi
}

travis_result() {
  local result=$1
  export TRAVIS_TEST_RESULT=$(( ${TRAVIS_TEST_RESULT:-0} | $(($result != 0)) ))

  if [ $result -eq 0 ]; then
    echo -e "\n${ANSI_GREEN}The command \"$TRAVIS_CMD\" exited with $result.${ANSI_RESET}"
  else
    echo -e "\n${ANSI_RED}The command \"$TRAVIS_CMD\" exited with $result.${ANSI_RESET}"
  fi
}

travis_terminate() {
  pkill -9 -P $$ &> /dev/null || true
  exit $1
}

travis_wait() {
  local timeout=$1

  if [[ $timeout =~ ^[0-9]+$ ]]; then
    # looks like an integer, so we assume it's a timeout
    shift
  else
    # default value
    timeout=20
  fi

  local cmd="$@"
  local log_file=travis_wait_$$.log

  $cmd &>$log_file &
  local cmd_pid=$!

  travis_jigger $! $timeout $cmd &
  local jigger_pid=$!
  local result

  {
    wait $cmd_pid 2>/dev/null
    result=$?
    ps -p$jigger_pid &>/dev/null && kill $jigger_pid
  }

  if [ $result -eq 0 ]; then
    echo -e "\n${ANSI_GREEN}The command $cmd exited with $result.${ANSI_RESET}"
  else
    echo -e "\n${ANSI_RED}The command $cmd exited with $result.${ANSI_RESET}"
  fi

  echo -e "\n${ANSI_GREEN}Log:${ANSI_RESET}\n"
  cat $log_file

  return $result
}

travis_jigger() {
  # helper method for travis_wait()
  local cmd_pid=$1
  shift
  local timeout=$1 # in minutes
  shift
  local count=0

  # clear the line
  echo -e "\n"

  while [ $count -lt $timeout ]; do
    count=$(($count + 1))
    echo -ne "Still running ($count of $timeout): $@\r"
    sleep 60
  done

  echo -e "\n${ANSI_RED}Timeout (${timeout} minutes) reached. Terminating \"$@\"${ANSI_RESET}\n"
  kill -9 $cmd_pid
}

travis_retry() {
  local result=0
  local count=1
  while [ $count -le 3 ]; do
    [ $result -ne 0 ] && {
      echo -e "\n${ANSI_RED}The command \"$@\" failed. Retrying, $count of 3.${ANSI_RESET}\n" >&2
    }
    "$@"
    result=$?
    [ $result -eq 0 ] && break
    count=$(($count + 1))
    sleep 1
  done

  [ $count -gt 3 ] && {
    echo -e "\n${ANSI_RED}The command \"$@\" failed 3 times.${ANSI_RESET}\n" >&2
  }

  return $result
}

travis_fold() {
  local action=$1
  local name=$2
  echo -en "travis_fold:${action}:${name}\r${ANSI_CLEAR}"
}

decrypt() {
  echo $1 | base64 -d | openssl rsautl -decrypt -inkey ~/.ssh/id_rsa.repo
}

# XXX Forcefully removing rabbitmq source until next build env update
# See http://www.traviscistatus.com/incidents/6xtkpm1zglg3
if [[ -f /etc/apt/sources.list.d/rabbitmq-source.list ]] ; then
  sudo rm -f /etc/apt/sources.list.d/rabbitmq-source.list
fi

mkdir -p $HOME/build
cd       $HOME/build


travis_fold start system_info
  echo -e "\033[33;1mBuild system information\033[0m"
  echo -e "Build language: android"
  if [[ -f /usr/share/travis/system_info ]]; then
    cat /usr/share/travis/system_info
  fi
travis_fold end system_info

echo
echo "options rotate
options timeout:1

nameserver 8.8.8.8
nameserver 8.8.4.4
nameserver 208.67.222.222
nameserver 208.67.220.220
" | sudo tee /etc/resolv.conf &> /dev/null
sudo sed -e 's/^\(127\.0\.0\.1.*\)$/\1 '`hostname`'/' -i'.bak' /etc/hosts
test -f /etc/mavenrc && sudo sed -e 's/M2_HOME=\(.\+\)$/M2_HOME=${M2_HOME:-\1}/' -i'.bak' /etc/mavenrc
sudo sed -e 's/^127\.0\.0\.1\(.*\) localhost \(.*\)$/127.0.0.1 localhost \1 \2/' -i'.bak' /etc/hosts 2>/dev/null
# apply :home_paths
for path_entry in $HOME/.local/bin $HOME/bin ; do
  if [[ ${PATH%%:*} != $path_entry ]] ; then
    export PATH="$path_entry:$PATH"
  fi
done

mkdir -p $HOME/.ssh
chmod 0700 $HOME/.ssh
touch $HOME/.ssh/config
echo -e "Host *
  UseRoaming no
" | cat - $HOME/.ssh/config > $HOME/.ssh/config.tmp && mv $HOME/.ssh/config.tmp $HOME/.ssh/config
export GIT_ASKPASS=echo

travis_fold start git.checkout
  if [[ ! -d mjmeintjes/boot-react-native/.git ]]; then
    travis_cmd git\ clone\ --depth\=50\ --branch\=\'\'\ git@github.com:mjmeintjes/boot-react-native.git\ mjmeintjes/boot-react-native --assert --echo --retry --timing
  else
    travis_cmd git\ -C\ mjmeintjes/boot-react-native\ fetch\ origin --assert --echo --retry --timing
    travis_cmd git\ -C\ mjmeintjes/boot-react-native\ reset\ --hard --assert --echo
  fi
  travis_cmd cd\ mjmeintjes/boot-react-native --echo
  travis_cmd git\ checkout\ -qf\  --assert --echo
travis_fold end git.checkout

if [[ -f .gitmodules ]]; then
  travis_fold start git.submodule
    echo Host\ github.com'
    '\	StrictHostKeyChecking\ no'
    ' >> ~/.ssh/config
    travis_cmd git\ submodule\ init --assert --echo --timing
    travis_cmd git\ submodule\ update --assert --echo --retry --timing
  travis_fold end git.submodule
fi

rm -f ~/.ssh/source_rsa

travis_fold start apt
  echo -e "\033[33;1mInstalling APT Packages (BETA)\033[0m"
  travis_cmd export\ DEBIAN_FRONTEND\=noninteractive --echo
  travis_cmd sudo\ -E\ apt-get\ -yq\ update\ \&\>\>\ \~/apt-get-update.log --echo --timing
  travis_cmd sudo\ -E\ apt-get\ -yq\ --no-install-suggests\ --no-install-recommends\ --force-yes\ install\ gcc\ g\+\+ --echo --timing
travis_fold end apt

export PS4=+
export TRAVIS=true
export CI=true
export CONTINUOUS_INTEGRATION=true
export HAS_JOSH_K_SEAL_OF_APPROVAL=true
export TRAVIS_PULL_REQUEST=false
export TRAVIS_SECURE_ENV_VARS=false
export TRAVIS_BUILD_ID=''
export TRAVIS_BUILD_NUMBER=''
export TRAVIS_BUILD_DIR=$HOME/build/mjmeintjes/boot-react-native
export TRAVIS_JOB_ID=''
export TRAVIS_JOB_NUMBER=''
export TRAVIS_BRANCH=''
export TRAVIS_COMMIT=''
export TRAVIS_COMMIT_RANGE=''
export TRAVIS_REPO_SLUG=mjmeintjes/boot-react-native
export TRAVIS_OS_NAME=linux
export TRAVIS_LANGUAGE=android
export TRAVIS_TAG=''

if [[ -f build.gradle ]]; then
  travis_cmd export\ TERM\=dumb --echo
fi

echo -e "\033[33;1mNo build-tools version is specified in android.components. Consider adding one of:\033[0m"
travis_cmd android\ list\ sdk\ --extended\ --no-ui\ --all\ \|\ awk\ -F\\\"\ \'/\^id.\*build-tools/\ \{print\ \$2\}\' --assert
echo -e "\033[33;1mThe following versions are pre-installed:\033[0m"
travis_cmd for\ v\ in\ \$\(ls\ /usr/local/android-sdk/build-tools/\ \|\ sort\ -r\ 2\>/dev/null\)\;\ do\ echo\ build-tools-\$v\;\ done\;\ echo --assert
travis_cmd java\ -Xmx32m\ -version --echo
travis_cmd javac\ -J-Xmx32m\ -version --echo

travis_fold start before_script.1
  travis_cmd .\ ./shell-helpers.sh --assert --echo --timing
travis_fold end before_script.1

travis_fold start before_script.2
  travis_cmd rm\ -rf\ \~/background\ \&\&\ mkdir\ \~/background --assert --echo --timing
travis_fold end before_script.2

travis_fold start before_script.3
  travis_cmd cd\ example/app/android --assert --echo --timing
travis_fold end before_script.3

travis_fold start before_script.4
  travis_cmd echo\ no\ \|\ android\ create\ avd\ --force\ -n\ test\ -t\ android-23\ --abi\ x86 --assert --echo --timing
travis_fold end before_script.4

travis_fold start before_script.5
  travis_cmd emulator\ -avd\ test\ -no-audio\ -no-window\ \& --assert --echo --timing
travis_fold end before_script.5

travis_fold start before_script.6
  travis_cmd rm\ -rf\ \~/.nvm\ \&\&\ git\ clone\ https://github.com/creationix/nvm.git\ \~/.nvm\ \&\&\ \(cd\ \~/.nvm\ \&\&\ git\ checkout\ \`git\ describe\ --abbrev\=0\ --tags\`\)\ \&\&\ source\ \~/.nvm/nvm.sh\ \&\&\ nvm\ install\ 4 --assert --echo --timing
travis_fold end before_script.6

travis_fold start before_script.7
  travis_cmd npm\ install\ --no-progress --assert --echo --timing
travis_fold end before_script.7

travis_fold start before_script.8
  travis_cmd export\ PATH\=\~/bin:\$PATH --assert --echo --timing
travis_fold end before_script.8

travis_fold start before_script.9
  travis_cmd export\ BOOT_VERSION\=2.5.5 --assert --echo --timing
travis_fold end before_script.9

travis_fold start before_script.10
  travis_cmd export\ BOOT_JVM_OPTIONS\=\"-Xmx2g\ -client\ -XX:-OmitStackTraceInFastThrow\ -XX:\+TieredCompilation\ -XX:TieredStopAtLevel\=1\ -XX:MaxPermSize\=256m\ -XX:\+UseConcMarkSweepGC\ -XX:\+CMSClassUnloadingEnabled\ -Xverify:none\" --assert --echo --timing
travis_fold end before_script.10

travis_fold start before_script.11
  travis_cmd \(mkdir\ \~/bin\ \&\&\ cd\ \~/bin\ \&\&\ curl\ -fsSLo\ boot\ https://github.com/boot-clj/boot-bin/releases/download/latest/boot.sh\ \&\&\ chmod\ 755\ boot\) --assert --echo --timing
travis_fold end before_script.11

travis_fold start before_script.12
  travis_cmd run-bg\ \"./gradlew\ assembleDebug\ -PdisablePreDex\ -Pjobs\=1\" --assert --echo --timing
travis_fold end before_script.12

travis_fold start before_script.13
  travis_cmd run-bg\ \"npm\ install\ -g\ appium\ --no-optional\ --no-progress\" --assert --echo --timing
travis_fold end before_script.13

travis_fold start before_script.14
  travis_cmd \(cd\ ../../..\ \&\&\ boot\ inst\ \&\&\ cd\ example\ \&\&\ boot\ fast-build\)\ \& --assert --echo --timing
travis_fold end before_script.14

travis_fold start before_script.15
  travis_cmd run-with-timeout\ 600\ wait-for-bg --assert --echo --timing
travis_fold end before_script.15

travis_fold start before_script.16
  travis_cmd run-with-timeout\ 600\ wait-for-avd --assert --echo --timing
travis_fold end before_script.16

travis_fold start before_script.17
  travis_cmd ./gradlew\ installDebug\ -PdisablePreDex\ -Pjobs\=1 --assert --echo --timing
travis_fold end before_script.17

travis_fold start before_script.18
  travis_cmd adb\ shell\ input\ keyevent\ 82\ \& --assert --echo --timing
travis_fold end before_script.18

travis_cmd cd\ ../..\ \&\&\ ls --echo --timing
travis_result $?
travis_cmd appium\ \& --echo --timing
travis_result $?
travis_cmd run-with-timeout\ 600\ wait-for-url\ \"http://localhost:8081/index.android.bundle\?platform\=android\" --echo --timing
travis_result $?
travis_cmd boot\ rn/print-android-log\ wait\ \& --echo --timing
travis_result $?
travis_cmd ./integration-tests.boot --echo --timing
travis_result $?
echo -e "\nDone. Your build exited with $TRAVIS_TEST_RESULT."

travis_terminate $TRAVIS_TEST_RESULT
