#!/bin/sh
# Run only against a disposable loopback server; no cloud credentials required.
set -eu
cd "$(dirname "$0")/../.."
node --test miniprogram/tests/integration-v8.test.js
