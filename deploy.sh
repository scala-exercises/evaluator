#!/bin/sh

function decipherKeys {
   echo $KEYS_PASSPHRASE | gpg --passphrase-fd 0 keys.tar.gz.gpg
   tar xfv keys.tar.gz
}

function publish {
   sbt publishSignedAll
}

function release {
    decipherKeys
    publish
}

if [[ $TRAVIS_BRANCH == 'master' ]]; then
   echo "Master branch, releasing..."
   release
else
    echo "Not in master branch, skipping release"
fi