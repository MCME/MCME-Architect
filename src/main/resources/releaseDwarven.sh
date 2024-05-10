#!/bin/bash
echo compiling Dwarven RP zips
cd Dwarven-Vanilla
git pull
cd ..

rm -r release
mkdir release
cp -f -r Dwarven-Vanilla/* release

cd release
#packsquash packsquash.toml
7z a -y Dwarven.zip * -x!inventories
mv  Dwarven.zip ..
cd ..

cp -f Footprints/activator_rail.png release/assets/minecraft/textures/block/activator_rail.png

cd Dwarven-Vanilla
#packsquash packsquash.toml
7z a -y Dwarven-footprints.zip *  -x!inventories
mv -f Dwarven-footprints.zip ..
cd ..


echo releasing Dwarven RP zips

# GitHub CLI api
# https://cli.github.com/manual/gh_api
gh api --method POST -H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28" /repos/$1/$2/releases -f "tag_name=$3" -f "target_commitish=master" -f "name=$4" -f "body=Version $3 for MC 1.19.4" -F "draft=false" -F "prerelease=false" -F "generate_release_notes=false"
gh release upload $3 Dwarven.zip Dwarven-footprints.zip -R $1/$2 --clobber