#!/usr/bin/env bash

set -e

# create a backup for bashrc
 cp ~/.bashrc ~/.bashrc.bak

# ipfs daemon
wget https://dist.ipfs.io/go-ipfs/v0.4.17/go-ipfs_v0.4.17_linux-amd64.tar.gz
tar xvfz go-ipfs_v0.4.17_linux-amd64.tar.gz
rm go-ipfs_v0.4.17_linux-amd64.tar.gz
sudo mv go-ipfs/ipfs /usr/local/bin
rm -rf go-ipfs

# init ipfs
sudo mkdir -p $IPFS_PATH
sudo chown ubuntu:ubuntu $IPFS_PATH
ipfs init

# copy swarm.key
sudo cp swarm.key $IPFS_PATH

#install midori browser
sudo apt-add-repository ppa:midori/ppa
sudo apt-get update
sudo apt-get install midori

#install go
wget https://dl.google.com/go/go1.11.linux-amd64.tar.gz
tar -xvf go1.11.linux-amd64.tar.gz
sudo chown -R ubuntu:ubuntu ./go
sudo mv go /usr/lib
rm go1.11.linux-amd64.tar.gz

#install git
sudo apt-get install git

#install go-ipfs-api
go get -u github.com/ipfs/go-ipfs-api