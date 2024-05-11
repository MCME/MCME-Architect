#!/bin/bash
echo compiling Human RP zips
cd Human-Common
git pull
cd ..
cd Human-Vanilla
git pull
cd ..
cd Human-Sodium
git pull
cd ..

rm -r release
mkdir release
cp -f -r Human-Common/* release
cp -f -r Human-Vanilla/* release
#packsquash packsquash.toml

cd release
7z a -y Human-Vanilla.zip *
mv  Human-Vanilla.zip ..
cd ..

cp -f Footprints/activator_rail.png release/assets/minecraft/textures/block/activator_rail.png

cd release
7z a -y Human-Vanilla-Footprints.zip *
mv -f Human-Vanilla-Footprints.zip ..
cd ..

rm -r release
mkdir release
cp -f -r Human-Common/* release
cp -f -r Human-Sodium/* release
#packsquash packsquash.toml

cd release
7z a -y Human-Sodium.zip *
mv  Human-Sodium.zip ..
cd ..

cp -f Footprints/activator_rail.png release/assets/minecraft/textures/block/activator_rail.png

cd release
7z a -y Human-Sodium-Footprints.zip *
mv -f Human-Sodium-Footprints.zip ..
cd ..


echo releasing Human RP zips

# GitHub CLI api
# https://cli.github.com/manual/gh_api
gh api --method POST -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" /repos/$1/$2/releases -f "tag_name=$3" -f "target_commitish=master" -f "name=$4" -f "body=Version $3 for MC 1.19.4 (Vanilla) and MC 1.20.1 (Sodium)" -F "draft=false" -F "prerelease=false" -F "generate_release_notes=false"
gh release upload $3 Human-Vanilla.zip Human-Vanilla-Footprints.zip Human-Sodium.zip Human-Sodium-Footprints.zip -R $1/$2 --clobber