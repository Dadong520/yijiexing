#!/bin/bash
SCRIPT_DIR=$(cd -- "$(dirname -- "$0")" && pwd)
if [[ -z $SCRIPT_DIR ]]; then exit 1; fi
/bin/bash "$SCRIPT_DIR/cloud-control.sh" resume
RESULT=$?
echo
if [[ $RESULT -eq 0 ]]; then
  echo '按回车键关闭此窗口。'
else
  echo '操作没有全部完成，请查看上方提示。按回车键关闭此窗口。'
fi
if [[ -t 0 ]]; then read -r _; fi
exit "$RESULT"
