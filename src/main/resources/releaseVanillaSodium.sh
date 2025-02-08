#!/bin/bash

# $1 RP Name
# $2 GitHub Owner
# $3 GitHub Repository
# $4 Version tag
# $5 Release name

echo compiling $1 RP zips
cd $1-Common
git pull
cd ..
cd $1-Vanilla
git pull
cd ..
cd $1-Sodium
git pull
cd ..

rm -r release
mkdir release
cp -f -r $1-Common/* release 
cp -f -r $1-Vanilla/* release
#packsquash packsquash.toml

cd release
7z a -y $1-Vanilla.zip *
mv  $1-Vanilla.zip ..
cd ..

cp -f Footprints/activator_rail.png release/assets/minecraft/textures/block/activator_rail.png

cd release
7z a -y $1-Vanilla-Footprints.zip *
mv -f $1-Vanilla-Footprints.zip ..
cd ..

rm -r release
mkdir release
cp -f -r $1-Common/* release 
cp -f -r $1-Sodium/* release
#packsquash packsquash.toml

cd release
7z a -y $1-Sodium.zip *
mv  $1-Sodium.zip ..
cd ..

cp -f Footprints/activator_rail.png release/assets/minecraft/textures/block/activator_rail.png

cd release
7z a -y $1-Sodium-Footprints.zip *
mv -f $1-Sodium-Footprints.zip ..
cd ..


echo releasing $1 RP zips

gh release create $4 -R $2/$3 -t "$5" -n "Version $4 for MC 1.19.4 (Vanilla) and MC 1.20.1 (Sodium)"
gh release upload $4 $1-Vanilla.zip $1-Vanilla-Footprints.zip $1-Sodium.zip $1-Sodium-Footprints.zip -R $2/$3 
