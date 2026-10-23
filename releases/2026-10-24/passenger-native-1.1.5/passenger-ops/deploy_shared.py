"""Deploy reviewed shared service only into the existing isolated project."""
import importlib.util,sys,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]/'yijiexing-cloud-20261023'
spec=importlib.util.spec_from_file_location('existing_ops',ROOT/'ops/provision.py')
ops=importlib.util.module_from_spec(spec);spec.loader.exec_module(ops)
ops.IMAGE='us-west1-docker.pkg.dev/yijiexing-driver-demo-1023/demo/service:20261023-passenger-v7'
if sys.argv[1]=='deploy':
    ops.IMAGE='us-west1-docker.pkg.dev/yijiexing-driver-demo-1023/demo/service@sha256:b3cb3e2f61e4a724dd95b6dfac0d357d2bbd793b9e225402965b8fedb5ff0d3d'
if sys.argv[1]=='build':ops.build()
elif sys.argv[1]=='deploy':ops.deploy()
else:raise SystemExit('Expected build or deploy')
