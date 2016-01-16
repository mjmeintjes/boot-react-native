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
  echo -e "Build language: objective-c"
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
export TRAVIS_LANGUAGE=objective-c
export TRAVIS_TAG=''
export TRAVIS_RUBY_VERSION=default
export TRAVIS_XCODE_SDK=''
export TRAVIS_XCODE_SCHEME=''
export TRAVIS_XCODE_PROJECT=''
export TRAVIS_XCODE_WORKSPACE=''
travis_cmd type\ rvm\ \&\>/dev/null\ \|\|\ source\ \~/.rvm/scripts/rvm --timing
echo rvm_remote_server_url3\=https://s3.amazonaws.com/travis-rubies/binaries'
'rvm_remote_server_type3\=rubies'
'rvm_remote_server_verify_downloads3\=1 > $rvm_path/user/db

if [[ -f .ruby-version ]]; then
  echo -e "\033[33;1mBETA: Using Ruby version from .ruby-version. This is a beta feature and may be removed in the future.\033[0m"
  travis_fold start rvm
    travis_cmd rvm\ use\ \$\(\<\ .ruby-version\)\ --install\ --binary\ --fuzzy --assert --echo --timing
  travis_fold end rvm
else
  travis_fold start rvm
    travis_cmd rvm\ use\ default --assert --echo --timing
  travis_fold end rvm
fi

if [[ -f ${BUNDLE_GEMFILE:-Gemfile} ]]; then
  travis_cmd export\ BUNDLE_GEMFILE\=\$PWD/Gemfile --echo
fi

travis_cmd ruby\ --version --echo
travis_cmd rvm\ --version --echo
travis_cmd bundle\ --version --echo

travis_fold start announce
  travis_cmd xcodebuild\ -version\ -sdk --echo
  travis_cmd xctool\ -version --echo
  travis_cmd xcrun\ simctl\ list --echo
travis_fold end announce

if [[ -f Rakefile && "$(cat Rakefile)" =~ require\ [\"\']motion/project ]]; then
  travis_cmd motion\ --version --echo
fi

if [[ -f Podfile ]]; then
  travis_cmd pod\ --version --echo
fi

if [[ -f ${BUNDLE_GEMFILE:-Gemfile} ]]; then
  if [[ -f ${BUNDLE_GEMFILE:-Gemfile}.lock ]]; then
    travis_fold start install.bundler
      travis_cmd bundle\ install\ --jobs\=3\ --retry\=3\ --deployment --assert --echo --retry --timing
    travis_fold end install.bundler
  else
    travis_fold start install.bundler
      travis_cmd bundle\ install\ --jobs\=3\ --retry\=3 --assert --echo --retry --timing
    travis_fold end install.bundler
  fi
fi

if [[ -f Podfile ]]; then
  if ! ([[ -f ./Podfile.lock && -f ./Pods/Manifest.lock ]] && cmp --silent ./Podfile.lock ./Pods/Manifest.lock); then
    travis_fold start install.cocoapods
      echo -e "\033[33;1mInstalling Pods with 'pod install'\033[0m"
      travis_cmd pushd\ . --assert --echo --timing
      travis_cmd pod\ install --assert --echo --retry --timing
      travis_cmd popd --assert --echo --timing
    travis_fold end install.cocoapods
  fi
fi

travis_fold start before_script
  travis_cmd cd\ example/app/ios --assert --echo --timing
travis_fold end before_script

if [[ -f Rakefile && "$(cat Rakefile)" =~ require\ [\"\']motion/project && -f Gemfile ]]; then
  travis_cmd bundle\ exec\ rake\ spec --echo --timing
elif [[ -f Rakefile && "$(cat Rakefile)" =~ require\ [\"\']motion/project ]]; then
  travis_cmd rake\ spec --echo --timing
else
  travis_cmd echo\ -e\ \"\\033\[33\;1mWARNING:\\033\[33m\ Using\ Objective-C\ testing\ without\ specifying\ a\ scheme\ and\ either\ a\ workspace\ or\ a\ project\ is\ deprecated.\" --timing
  travis_cmd echo\ \"\ \ Check\ out\ our\ documentation\ for\ more\ information:\ http://about.travis-ci.org/docs/user/languages/objective-c/\" --timing
fi

travis_result $?
echo -e "\nDone. Your build exited with $TRAVIS_TEST_RESULT."

travis_terminate $TRAVIS_TEST_RESULT
