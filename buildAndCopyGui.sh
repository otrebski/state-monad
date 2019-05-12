#!/bin/bash
set -x
cd gui/vending-gui
yarn build
rm -rf ../../src/main/resources/gui
cp -r  build ../../src/main/resources/gui
cd ../..