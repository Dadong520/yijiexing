#!/bin/zsh
cd "$(dirname "$0")"
exec python3 ops/start_gateway.py
