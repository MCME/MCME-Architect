#!/bin/bash
echo pushing $1 custom inventory to GitHub

ls
cd $1-Inventories/inventories/
git pull
cp -f ../../../servers-mcme/baseline-server/plugins/MCME-Architect/inventories/block/$1/*.yml ./
git commit -a -m $2
git push
cd ../..

