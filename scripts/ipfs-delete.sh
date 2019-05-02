#!/usr/bin/env bash

echo $IPFS_PATH
sudo rm -rf $IPFS_PATH

echo $GOROOT
sudo rm -rf $GOROOT

echo $GOPATH
sudo rm -rf $GOPATH

cp ~/.bashrc.bak ~/.bashrc
