#!/bin/bash
echo updating $1 custom inventory

cd $1-Inventories/inventories/
git pull
cp -f ./*.yml ../../../servers-mcme/baseline-server/plugins/MCME-Architect/inventories/block/$1/
cd ../..
