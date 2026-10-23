#!/bin/zsh
set -e
PREVIEW_DIR="${0:A:h}"
python3 "$PREVIEW_DIR/start_preview.py" --open
