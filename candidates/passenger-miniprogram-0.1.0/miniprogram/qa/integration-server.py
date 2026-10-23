#!/usr/bin/env python3
"""Local-only isolated fixture. No deploy, provider call, existing database or secret."""
import sys, json, tempfile, threading, pathlib, uuid, os
PACKAGED=pathlib.Path(__file__).resolve().parents[2]/'backend-frozen-v8'
SOURCE=pathlib.Path(os.environ.get('YIJIE_BACKEND_SOURCE',str(PACKAGED if PACKAGED.is_dir() else pathlib.Path('/private/tmp/yijiexing-passenger-v8/cloud-service'))))
if not (SOURCE/'passenger.py').is_file(): raise SystemExit('Frozen v8 backend missing: use packaged backend-frozen-v8 or set YIJIE_BACKEND_SOURCE')
sys.path.insert(0,str(SOURCE))
from server import make_server
from common import dumps,ApiError
with tempfile.TemporaryDirectory(prefix='yx-mini-integration-') as tmp:
    server=make_server(pathlib.Path(tmp)/'fixture.sqlite',0)
    svc=server.service
    # Create a local synthetic driver through real login; remove only seed orders.
    with svc.lock:
        c=svc.code('13900000991');d=svc.login({'mobile':'13900000991','code':c['verificationCode']})
        driver=svc.authenticate(d['token']);st=json.loads(svc.account(driver)['state']);st['orders']=[];svc.save_state(driver,st)
        # Negotiation is disabled by Yichang default; enable only this disposable fixture.
        row=svc.db.execute("SELECT published FROM configs WHERE scope='city' AND target='420500'").fetchone()
        cfg=json.loads(row['published'])
        for level in cfg.get('levels',{}).values():level.setdefault('params',{})['offlineNegotiation']=True
        svc.db.execute("UPDATE configs SET published=? WHERE scope='city' AND target='420500'",(dumps(cfg),))
        svc.action(driver,'driver.online',{'online':True},str(uuid.uuid4()));svc.db.commit()
    threading.Thread(target=server.serve_forever,daemon=True).start()
    print(json.dumps({'port':server.server_port,'driverId':driver}),flush=True)
    for line in sys.stdin:
        try:
            req=json.loads(line)
            with svc.lock:
                if req['action']=='fixture.adjustFare':
                    from admin_console import AdminConsole
                    result=AdminConsole(svc).order_action('order.adjustFare',req['payload'],'LOCAL_INTEGRATION_FIXTURE')
                else:result=svc.action(driver,req['action'],req.get('payload',{}),str(uuid.uuid4()))
                svc.db.commit()
            print(json.dumps({'ok':True,'result':result}),flush=True)
        except ApiError as e:
            svc.db.rollback();print(json.dumps({'ok':False,'message':e.message,'status':e.status}),flush=True)
        except Exception as e:
            print(json.dumps({'ok':False,'message':str(e)}),flush=True)
    server.shutdown();server.service_close()
