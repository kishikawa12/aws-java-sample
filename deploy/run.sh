#!/bin/sh
# Invoked by s3sample.service on the systemd timer.
# DT_RELEASE_VERSION is injected by systemd from /opt/s3sample/release.env,
# which CI/CD writes on every deploy. Fall back to the initial label for manual runs.

set -eu

export DT_RELEASE_PRODUCT="aws-java-sample"
export DT_RELEASE_VERSION="${DT_RELEASE_VERSION:-1.0-sdkv1}"
export DT_RELEASE_STAGE="demo"
export DT_TAGS="app=s3sample"

exec /usr/bin/java -jar /opt/s3sample/app.jar
