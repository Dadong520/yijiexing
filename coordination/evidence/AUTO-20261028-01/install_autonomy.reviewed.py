#!/usr/bin/env python3
"""Python 3.9+: install policy for the CURRENT coordinator; never dispatch/deploy.
Default is validation/preview. --apply creates new, task-scoped files only.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from datetime import datetime, timezone

POLICY_NAME = "AUTONOMY_POLICY.md"
CONFIG_NAME = "AUTONOMY_CONFIG.json"
EXECUTABLE_STATES = {"ASSIGNED", "IN_PROGRESS"}


class InstallError(RuntimeError):
    pass


class PartialInstallError(InstallError):
    def __init__(self, cause, created, remaining):
        super().__init__(str(cause))
        self.created = list(created)
        self.remaining = list(remaining)


def load_json(path):
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(value, dict):
            raise ValueError("expected object")
        return value
    except (OSError, ValueError) as exc:
        raise InstallError(f"无法读取有效的 JSON 对象：{path.name}") from exc


def run(command):
    result = subprocess.run(command, capture_output=True, text=True, timeout=30)
    if result.returncode:
        raise InstallError(f"前置检查失败（退出码 {result.returncode}）：" +
                           result.stdout.strip()[:1200] + result.stderr.strip()[:600])
    return result.stdout.strip()


def allowed(relative, scopes):
    if not isinstance(scopes, list):
        return False
    for scope in scopes:
        if not isinstance(scope, str) or not scope.strip():
            continue
        prefix = Path(scope)
        if prefix.is_absolute() or ".." in prefix.parts or str(prefix) == ".":
            continue
        if relative == prefix or relative.is_relative_to(prefix):
            return True
    return False


def build_plan(control_root, source_root, session_id, task_id, policy_path):
    control = Path(control_root).resolve(strict=True)
    source = Path(source_root).resolve(strict=True)
    coordination = {}
    for root in dict.fromkeys((control, source)):
        directory = (root / "coordination").resolve(strict=True)
        if not directory.is_relative_to(root):
            raise InstallError("协调目录的真实路径越出已授权根目录")
        coordination[root] = directory
    if not re.fullmatch(r"[A-Z]+-\d{8}-\d+", task_id):
        raise InstallError("任务编号不符合现有格式")
    registry = load_json(control / "coordination/SESSION_REGISTRY.json")
    if Path(registry.get("canonicalControlRoot", "")).resolve() != control:
        raise InstallError("控制目录与中央登记不一致")
    owner = registry.get("sessions", {}).get("coordinator", {})
    if (not session_id or owner.get("threadId") != session_id or
            session_id in registry.get("retiredCoordinatorThreadIds", [])):
        raise InstallError("只能由当前真实登记的原协调者安装；不能抄用他人会话 ID")
    if owner.get("memoryStatus") == "HANDOFF_REQUIRED":
        raise InstallError("原协调者需要先完成规定的交接")
    task = load_json(control / f"coordination/tasks/{task_id}.json")
    board = load_json(control / "coordination/BOARD.json")
    if task_id not in board.get("activeTaskIds", []):
        raise InstallError("接入任务尚未由原协调者登记进中央任务板")
    if task.get("id") != task_id or task.get("owner") != "coordinator":
        raise InstallError("任务必须明确归属于原协调者")
    if task.get("status") not in EXECUTABLE_STATES:
        raise InstallError("接入任务须为 ASSIGNED 或 IN_PROGRESS，不能越过阻塞或未知状态")
    declared_source = task.get("sourceRoot")
    if (not isinstance(declared_source, str) or not Path(declared_source).is_absolute()
            or Path(declared_source).resolve() != source):
        raise InstallError("源码目录与已分配任务不一致")
    base_sha = task.get("baseSha", "")
    if not isinstance(base_sha, str) or not re.fullmatch(r"[a-f0-9]{40}", base_sha):
        raise InstallError("任务缺少准确的基线 SHA")
    branch = run(["git", "-C", str(source), "branch", "--show-current"])
    if branch in ("", "main", "master") or branch != task.get("branch"):
        raise InstallError("必须在该接入任务的独立分支运行")
    run(["git", "-C", str(source), "merge-base", "--is-ancestor", base_sha, "HEAD"])
    gate = source / "tools/check_coordination.py"
    if not gate.is_file():
        raise InstallError("项目的既有角色检查器缺失")
    run([sys.executable, str(gate), "--root", str(source),
         "--memory-role", "coordinator", "--session-id", session_id])
    policy = Path(policy_path).read_text(encoding="utf-8")
    config = {
        "schemaVersion": 1, "mode": "feedback_driven_autonomous_delivery",
        "activation": "POLICY_INSTALLED_PENDING_COORDINATOR_ACK",
        "ownerThreadId": session_id, "registryGeneration": registry.get("generation"),
        "runtime": "existing_chatgpt_coordinator", "feedbackEntry": "原 ChatGPT 协调会话",
        "controlRoot": str(control), "previewProject": "yijiexing-driver-demo-1023",
        "previewRegion": "us-west1", "pauseAfterEachRelease": True,
        "backgroundServiceInstalled": False, "newCoordinatorCreated": False,
        "authorization": "用户只在体验发布后验证与反馈；沿用原协调者和既有发布授权",
        "policy": "coordination/AUTONOMY_POLICY.md",
    }
    config_text = json.dumps(config, ensure_ascii=False, indent=2) + "\n"
    files = {}
    receipt_relative = Path(f"coordination/evidence/{task_id}/AUTONOMY_INSTALL.json")
    scopes = task.get("allow_paths", [])
    for relative in (Path("coordination") / POLICY_NAME,
                     Path("coordination") / CONFIG_NAME, receipt_relative):
        if not allowed(relative, scopes):
            raise InstallError(f"目标超出任务允许范围：{relative}")
    for root in coordination:
        for name, text in ((POLICY_NAME, policy), (CONFIG_NAME, config_text)):
            path = root / "coordination" / name
            if not path.resolve().is_relative_to(coordination[root]):
                raise InstallError("目标路径越出协调目录")
            if path.exists() and path.read_text(encoding="utf-8") != text:
                raise InstallError(f"已有不同版本文件，需原协调者审阅后处理：{path}")
            files[path] = text
    receipt = control / receipt_relative
    if not receipt.resolve().is_relative_to(coordination[control]):
        raise InstallError("回执路径越出协调目录")
    hashes = {str(path): hashlib.sha256(text.encode()).hexdigest()
              for path, text in files.items()}
    if receipt.exists():
        existing = load_json(receipt)
        if existing.get("files") != hashes or existing.get("ownerThreadId") != session_id:
            raise InstallError("已有不同安装回执，停止以免覆盖历史")
    else:
        files[receipt] = json.dumps({
            "taskId": task_id, "ownerThreadId": session_id,
            "installedAt": datetime.now(timezone.utc).isoformat(),
            "status": "POLICY_INSTALLED_PENDING_COORDINATOR_ACK", "files": hashes,
            "note": "仅安装约定；未启动服务、派单、发布或替换协调者。"
        }, ensure_ascii=False, indent=2) + "\n"
    return files


def atomic_create(path, text):
    """Same-directory hard-link publication: a complete file, without overwriting."""
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary_name = tempfile.mkstemp(prefix=".autonomy-write-", dir=path.parent)
    temporary = Path(temporary_name)
    intended = temporary.resolve()
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as output:
            output.write(text)
            output.flush()
            os.fsync(output.fileno())
        os.link(temporary, path)  # Fails if the destination already exists.
    finally:
        # Only our exact, verified temporary file may be removed; never the target.
        if (temporary.resolve() == intended and intended.parent == path.parent.resolve()
                and temporary.name.startswith(".autonomy-write-")):
            try:
                temporary.unlink(missing_ok=True)
            except OSError:
                pass  # Preserve the completed target even if temporary cleanup fails.


def install(control_root, source_root, session_id, task_id, policy_path, apply=False):
    plan = build_plan(control_root, source_root, session_id, task_id, policy_path)
    if apply:
        plan = build_plan(control_root, source_root, session_id, task_id, policy_path)
        created, confirmed = [], []
        try:
            for path, text in plan.items():
                if path.exists():
                    if path.read_text(encoding="utf-8") != text:
                        raise InstallError("文件在检查后变化，停止而不覆盖")
                else:
                    atomic_create(path, text)
                    created.append(path)
                confirmed.append(path)
        except (OSError, InstallError) as exc:
            if created:
                raise PartialInstallError(exc, created,
                                          [p for p in plan if p not in confirmed]) from exc
            raise
    return plan


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--control-root", required=True)
    parser.add_argument("--source-root", required=True)
    parser.add_argument("--session-id", required=True, help="本协调会话真实 ID，不得抄用")
    parser.add_argument("--task-id", default="AUTO-20261028-01")
    parser.add_argument("--policy", default=str(Path(__file__).with_name(POLICY_NAME)))
    parser.add_argument("--apply", action="store_true", help="默认只验证和预览；此项才创建新文件")
    args = parser.parse_args()
    try:
        plan = install(args.control_root, args.source_root, args.session_id,
                       args.task_id, args.policy, args.apply)
    except PartialInstallError as exc:
        print(f"PARTIALLY_APPLIED: {exc}")
        for path in exc.created:
            print(f"CREATED: {path}")
        for path in exc.remaining:
            print(f"NOT_CONFIRMED: {path}")
        print("先只读核实已有文件和角色状态；解决原因后先预览，再幂等续装，勿删除成果。")
        return 2
    except (InstallError, OSError, subprocess.TimeoutExpired) as exc:
        print(f"NOT_APPLIED: {exc}")
        return 1
    print("POLICY_INSTALLED_PENDING_COORDINATOR_ACK" if args.apply else "DRY_RUN_ONLY")
    for path in plan:
        print(path)
    print("没有启动模型或服务，没有部署，没有替换协调者。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
