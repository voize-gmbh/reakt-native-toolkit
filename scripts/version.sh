#!/bin/bash

VERSION=${1}
GIT_TAG=v${VERSION}
BASEDIR=$(dirname $(readlink -f "$0"))
(
    cd "$BASEDIR/.."
    sed -i "s/version=.*/version=${VERSION}/g" kotlin/gradle.properties
    sed -i "/\"version\":/c\  \"version\": \"${VERSION}\"," js/package.json
    sed -i "s/val reaktNativeToolkitVersion = \".*\"/val reaktNativeToolkitVersion = \"${VERSION}\"/g" example/android/shared/build.gradle.kts
    sed -i "/\#\# unreleased/a \#\# ${GIT_TAG}" CHANGELOG.md
    git commit -m "version ${VERSION}" kotlin/gradle.properties js/package.json example/android/shared/build.gradle.kts CHANGELOG.md
    git tag -a $GIT_TAG -m "version ${VERSION}"
)
