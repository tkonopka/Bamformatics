#!/bin/bash -l

## script to create a release zip file
## script takes one argument, the version number (e.g. 0.2.5)

newrelease=Bamformatics-$1

mkdir $newrelease

cp ../dist/Bam* $newrelease/
cp -r ../dist/* $newrelease/
rm $newrelease/README*

zip -r $newrelease.zip $newrelease
rm -fr $newrelease


