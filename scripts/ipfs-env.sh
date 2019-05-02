set -e
echo 'export IPFS_PATH=/data/ipfs' >>~/.bashrc
echo 'export GOPATH=$HOME/work' >>~/.bashrc
echo 'export GOROOT=/usr/lib/go' >>~/.bashrc
echo 'export PATH=$PATH:$GOROOT/bin:$GOPATH/bin' >>~/.bashrc
source ~/.bashrc