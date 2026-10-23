#!/bin/bash
# 仅控制本次独立演示环境；不读取或修改 gcloud 默认项目/账户配置。
set -u
set -o pipefail
export PATH="${PATH:-/usr/bin:/bin}:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin"
export CLOUDSDK_CORE_DISABLE_PROMPTS=1

readonly DEMO_PROJECT='yijiexing-driver-demo-1023'
readonly DEMO_REGION='us-west1'
readonly DEMO_SERVICES=('driver-demo' 'driver-admin')

usage() {
  cat <<'HELP'
宜捷行云端演示启停
用法：cloud-control.sh pause|resume|status [--dry-run]
  pause   暂停手机接口与管理后台，保留云端数据。
  resume  恢复按需使用：无人请求时可自动缩到 0，最多 1 个实例。
  status  只读查看当前设置。
  --dry-run  只显示目标与操作，不连接 Google Cloud。
固定范围：yijiexing-driver-demo-1023 / us-west1
服务：driver-demo、driver-admin
暂停计算后，保留的数据和镜像仍可能产生小额存储费用。
HELP
}

if [[ $# -lt 1 || $# -gt 2 ]]; then usage; exit 2; fi
ACTION=$1
DRY_RUN=0
if [[ $# -eq 2 ]]; then
  if [[ $2 != '--dry-run' ]]; then usage; exit 2; fi
  DRY_RUN=1
fi
case "$ACTION" in pause|resume|status) ;; *) usage; exit 2 ;; esac

echo '宜捷行独立演示环境'
echo "项目：$DEMO_PROJECT"
echo "地区：$DEMO_REGION"
echo '范围：手机接口 driver-demo、管理后台 driver-admin'

if [[ $DRY_RUN -eq 1 ]]; then
  echo '【预览模式】不会连接云端，也不会更改任何资源。'
  for DEMO_SERVICE in "${DEMO_SERVICES[@]}"; do
    case "$ACTION" in
      pause) echo "gcloud run services update $DEMO_SERVICE --project=$DEMO_PROJECT --region=$DEMO_REGION --scaling=0 --quiet" ;;
      resume) echo "gcloud run services update $DEMO_SERVICE --project=$DEMO_PROJECT --region=$DEMO_REGION --scaling=auto --min=0 --max=1 --quiet" ;;
      status) echo "gcloud run services describe $DEMO_SERVICE --project=$DEMO_PROJECT --region=$DEMO_REGION --format=json" ;;
    esac
  done
  exit 0
fi

GCLOUD_BIN=$(command -v gcloud || true)
PYTHON_BIN=$(command -v python3 || true)
if [[ -z $GCLOUD_BIN ]]; then
  echo '无法操作：这台 Mac 尚未找到 Google Cloud 命令工具。' >&2
  echo '请先安装官方 Google Cloud CLI，然后重新打开此文件。' >&2
  exit 1
fi
if [[ -z $PYTHON_BIN ]]; then
  echo '无法核对服务状态：这台 Mac 尚未找到 Python 3。' >&2
  exit 1
fi

STATE_DIR=$(mktemp -d "${TMPDIR:-/tmp}/yijiexing-cloud-control.XXXXXX") || exit 1
trap 'rm -rf "$STATE_DIR"' EXIT
trap 'exit 130' INT TERM
chmod 700 "$STATE_DIR"

read_config() {
  "$GCLOUD_BIN" run services describe "$1" \
    --project="$DEMO_PROJECT" --region="$DEMO_REGION" --format=json \
    > "$STATE_DIR/$1.json"
}

# 只输出启停相关字段，不打印环境变量、凭据或服务完整配置。
inspect_config() {
  "$PYTHON_BIN" - "$STATE_DIR/$1.json" "$2" <<'PY'
import json, sys
with open(sys.argv[1], encoding='utf-8') as stream:
    data = json.load(stream)
mode_requested = sys.argv[2]
annotations = data.get('metadata', {}).get('annotations', {})
scaling = data.get('scaling', {})
mode = str(scaling.get('scalingMode') or annotations.get('run.googleapis.com/scalingMode') or 'automatic').lower()
manual = int(scaling.get('manualInstanceCount', annotations.get('run.googleapis.com/manualInstanceCount', 0)))
minimum = int(scaling.get('minInstanceCount', annotations.get('run.googleapis.com/minScale', 0)))
maximum = scaling.get('maxInstanceCount', annotations.get('run.googleapis.com/maxScale'))
maximum = int(maximum) if maximum is not None else None
traffic = data.get('traffic') or data.get('spec', {}).get('traffic', [])
tags = [str(item.get('tag')) for item in traffic if item.get('tag')]
if mode_requested == 'preflight':
    if tags:
        print('检测到此演示服务存在版本标签，普通暂停可能无法关闭标签地址。请先由维护人员检查标签。', file=sys.stderr)
        sys.exit(1)
    sys.exit(0)
is_manual = mode == 'manual'
if is_manual and manual == 0:
    print('已暂停（实例数量 0）。')
elif is_manual:
    print(f'手动运行，实例数量 {manual}。')
else:
    print(f'按需运行，最少 {minimum} 个实例，最多 {maximum if maximum is not None else "平台默认"} 个实例。')
if tags:
    print('注意：存在版本标签，标签地址不受普通服务暂停影响。')
if mode_requested == 'pause' and (not is_manual or manual != 0):
    print('暂停状态尚未确认，请在 Google Cloud 控制台核对。', file=sys.stderr)
    sys.exit(1)
if mode_requested == 'resume' and (is_manual or minimum != 0 or maximum != 1):
    print('按需运行参数尚未确认，请在 Google Cloud 控制台核对。', file=sys.stderr)
    sys.exit(1)
PY
}

echo
echo '正在核对连接及服务范围……'
for DEMO_SERVICE in "${DEMO_SERVICES[@]}"; do
  if ! read_config "$DEMO_SERVICE"; then
    echo "无法读取 $DEMO_SERVICE，未执行任何启停。请确认已登录且有本项目的 Cloud Run 权限。" >&2
    exit 1
  fi
  if [[ $ACTION != 'status' ]] && ! inspect_config "$DEMO_SERVICE" preflight; then
    echo '检查未通过，未执行任何启停。' >&2
    exit 1
  fi
done

FAILED=0
for DEMO_SERVICE in "${DEMO_SERVICES[@]}"; do
  echo
  echo "正在处理：$DEMO_SERVICE"
  if [[ $ACTION == 'pause' ]]; then
    if ! "$GCLOUD_BIN" run services update "$DEMO_SERVICE" \
      --project="$DEMO_PROJECT" --region="$DEMO_REGION" --scaling=0 --quiet; then
      echo "$DEMO_SERVICE 暂停失败，另一项服务仍会继续处理。" >&2
      FAILED=1
      continue
    fi
  elif [[ $ACTION == 'resume' ]]; then
    if ! "$GCLOUD_BIN" run services update "$DEMO_SERVICE" \
      --project="$DEMO_PROJECT" --region="$DEMO_REGION" \
      --scaling=auto --min=0 --max=1 --quiet; then
      echo "$DEMO_SERVICE 恢复失败，另一项服务仍会继续处理。" >&2
      FAILED=1
      continue
    fi
  fi
  if ! read_config "$DEMO_SERVICE" || ! inspect_config "$DEMO_SERVICE" "$ACTION"; then
    echo "$DEMO_SERVICE 的最终状态未能核实。" >&2
    FAILED=1
  fi
done

echo
if [[ $FAILED -ne 0 ]]; then
  echo '有服务未完成，请保留上方错误信息并联系维护人员；不要假定两个服务已经一起停下或恢复。' >&2
  exit 1
fi
case "$ACTION" in
  pause)
    echo '完成：手机接口和管理后台均已暂停。'
    echo '当前进行中的请求可以完成；随后 App 会提示服务不可用。'
    echo '数据与镜像保留，仍可能产生小额存储费用。'
    ;;
  resume)
    echo '完成：手机接口和管理后台均恢复按需使用。'
    echo '首次打开 App 可能需要等待冷启动；无人请求时可自动缩到 0。'
    ;;
  status) echo '检查完成。' ;;
esac
