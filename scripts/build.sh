#!/usr/bin/env bash

cd ../

echo "Building BetterSandMining..."
rm -r target
mvn clean package

echo "Copying to run/"
mkdir run/plugins/
cp target/*.jar run/plugins/bettersandmining-1.12.2_VERSION.jar