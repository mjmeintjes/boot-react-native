mkdir -p ~/tmp/
cd ~/tmp
git clone https://github.com/facebook/watchman.git
cd watchman
git checkout v4.1.0  # the latest stable release
./autogen.sh
mkdir -p /home/travis/watchman
./configure --enable-statedir=/home/travis/watchman
make
ln -s `pwd`/watchman ~/bin/watchman