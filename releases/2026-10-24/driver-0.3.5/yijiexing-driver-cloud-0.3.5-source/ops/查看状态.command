#!/bin/bash
SCRIPT_DIR=$(cd -- "$(dirname -- "$0")" && pwd)
/bin/bash "$SCRIPT_DIR/cloud-control.sh" status
RESULT=$?
echo
if [[ -t 0 ]]; then read -r -p '按回车键关闭此窗口。' _; fi
exit "$RESULT"
