"""Account-owned defaults through actual HTTP/SQLite/snapshot CAS, no providers."""
import concurrent.futures
import json
import threading
import unittest
import urllib.error
import urllib.request
import policy_helpers as fixtures

GROUPS = {
    'FAVORITE': ('favoriteId', 'favorites', 'favorite.save', 'favorite.remove', 'favorite'),
    'RIDER': ('riderId', 'riders', 'rider.save', 'rider.remove', 'rider'),
    'INVOICE_TITLE': ('invoiceTitleId', 'invoiceTitles', 'invoice.title.save', 'invoice.title.remove', 'invoiceTitle'),
}


class PassengerDefaultsTests(fixtures.PolicyCase):
    def ps(self, p, **kw):
        return self.request('/api/passenger/state', token=p['token'], **kw)

    def payload(self, kind, i=0, **extra):
        p = {'FAVORITE': {'label':'地址'+str(i),'address':'本人常用地址'+str(i),
                         'latitude':30.6687+i*.001,'longitude':111.3226},
             'RIDER': {'name':'本人乘车人'+str(i),'mobile':f'13800002{i:03d}'},
             'INVOICE_TITLE': {'type':'PERSONAL','title':'本人抬头'+str(i)}}[kind]
        return {**p, **extra}

    def save(self, p, kind, i=0, **extra):
        group=GROUPS[kind]
        return self.pa(p['token'],group[2],self.payload(kind,i,**extra))['result'][group[4]]

    def select(self, p, kind, record_id, **kw):
        return self.pa(p['token'],'defaults.set',{'kind':kind,'id':record_id},**kw)

    def assert_one(self, p, kind, record_id):
        key,collection,*_=GROUPS[kind];data=self.ps(p)
        self.assertEqual(data['defaults'][key],record_id)
        self.assertEqual([r['id'] for r in data[collection] if r['isDefault']], [] if record_id is None else [record_id])
        if record_id is not None:self.assertEqual(data[collection][0]['id'],record_id)

    def contents(self):
        return self.mutate_fixture(lambda s:{t:[tuple(r) for r in s.db.execute('SELECT * FROM '+t+' ORDER BY rowid')]
            for t in ('passengers','passenger_extras','passenger_orders','passenger_actions','audits')})

    def test_no_default_old_client_save_and_explicit_unique_selection(self):
        p=self.passenger_login();self.assertEqual(self.ps(p)['defaults'],dict.fromkeys(('favoriteId','riderId','invoiceTitleId')))
        for kind in GROUPS:
            first,second=self.save(p,kind,0),self.save(p,kind,1)
            self.assertIs(first['isDefault'],False);self.assert_one(p,kind,None)
            self.select(p,kind,first['id']);self.assert_one(p,kind,first['id'])
            self.select(p,kind,second['id']);self.assert_one(p,kind,second['id'])
        before=self.ps(p)['defaults']
        self.select(p,'FAVORITE',None);self.assert_one(p,'FAVORITE',None)
        current=self.ps(p)['defaults'];self.assertEqual(current['riderId'],before['riderId']);self.assertEqual(current['invoiceTitleId'],before['invoiceTitleId'])

    def test_save_flag_edit_preserves_and_false_only_clears_this_record(self):
        p=self.passenger_login()
        for kind in GROUPS:
            a=self.save(p,kind,0,isDefault=True);b=self.save(p,kind,1,isDefault=True)
            self.assert_one(p,kind,b['id'])
            self.save(p,kind,0,id=a['id'],isDefault=False);self.assert_one(p,kind,b['id'])
            self.save(p,kind,2,id=b['id']);self.assert_one(p,kind,b['id'])
            self.save(p,kind,2,id=b['id'],isDefault=False);self.assert_one(p,kind,None)

    def test_delete_nondefault_preserves_and_delete_default_clears_without_replacement(self):
        p=self.passenger_login()
        for kind,g in GROUPS.items():
            a=self.save(p,kind,0);b=self.save(p,kind,1,isDefault=True)
            self.pa(p['token'],g[3],{'id':a['id']});self.assert_one(p,kind,b['id'])
            self.save(p,kind,2)
            self.pa(p['token'],g[3],{'id':b['id']});self.assert_one(p,kind,None)

    def test_cross_account_set_edit_delete_and_invalid_references_have_zero_side_effects(self):
        p=self.passenger_login();other=self.passenger_login('13800000102')
        own={k:self.save(p,k,isDefault=True) for k in GROUPS}
        for kind in GROUPS:self.save(other,kind,3,isDefault=True)
        before=self.contents()
        for kind,g in GROUPS.items():
            rid=own[kind]['id']
            self.select(other,kind,rid,expected=404)
            self.pa(other['token'],g[2],self.payload(kind,4,id=rid,isDefault=True),expected=404)
            self.pa(other['token'],g[3],{'id':rid},expected=404)
            self.select(p,kind,'UNKNOWN-ID',expected=404)
        for kind in (None,True,1,[],{},'UNKNOWN'):
            self.select(p,kind,None,expected=400)
        for rid in ('',True,7,[],{},'x'*121,'bad/id'):
            self.select(p,'RIDER',rid,expected=400)
        for payload in ({'kind':'RIDER'},{'id':None},{'kind':'RIDER','id':None,'passengerId':other['passenger']['id']}):
            self.pa(p['token'],'defaults.set',payload,expected=400)
        self.assertEqual(before,self.contents())

    def test_invalid_default_flag_rejects_atomically_before_saving(self):
        p=self.passenger_login();before=self.contents()
        for kind,g in GROUPS.items():
            for value in (None,0,1,'true',{},[]):
                self.pa(p['token'],g[2],self.payload(kind,isDefault=value),expected=400)
        self.assertEqual(before,self.contents())

    def test_repeat_rid_does_not_reapply_old_default_or_delete_new_selection(self):
        p=self.passenger_login()
        for kind,g in GROUPS.items():
            a=self.save(p,kind,0);b=self.save(p,kind,1)
            rid='default-once-'+kind
            self.select(p,kind,a['id'],rid=rid)
            self.select(p,kind,b['id'])
            self.select(p,kind,a['id'],rid=rid);self.assert_one(p,kind,b['id'])
            self.select(p,kind,b['id'],rid=rid,expected=409)
            self.pa(p['token'],g[3],{'id':a['id']},rid='delete-once-'+kind)
            self.pa(p['token'],g[3],{'id':a['id']},rid='delete-once-'+kind)
            self.assert_one(p,kind,b['id'])

    def test_restart_and_account_isolation(self):
        p=self.passenger_login();other=self.passenger_login('13800000102')
        for kind in GROUPS:self.save(p,kind,isDefault=True)
        current=self.ps(p);reboot=self.start()
        restored=self.ps(p,server=reboot)
        for key in ('defaults','favorites','riders','invoiceTitles'):self.assertEqual(restored[key],current[key])
        self.assertEqual(self.ps(other,server=reboot)['defaults'],dict.fromkeys(current['defaults']))
        for key in ('favorites','riders','invoiceTitles'):self.assertEqual(self.ps(other)[key],[])

    def test_cas_failure_rolls_back_record_and_default_lost_ack_retry_once(self):
        p=self.passenger_login();a=self.save(p,'FAVORITE',isDefault=True)
        self.remote.fail_write=True
        self.pa(p['token'],'favorite.save',self.payload('FAVORITE',1,isDefault=True),rid='failed-record',expected=503)
        self.remote.fail_write=False;self.assert_one(p,'FAVORITE',a['id']);self.assertEqual(len(self.ps(p)['favorites']),1)
        self.remote.uncertain_write=True
        body=self.payload('FAVORITE',1,isDefault=True)
        self.pa(p['token'],'favorite.save',body,rid='lost-record',expected=503)
        replay=self.pa(p['token'],'favorite.save',body,rid='lost-record')
        b=replay['result']['favorite'];self.assert_one(p,'FAVORITE',b['id']);self.assertEqual(len(self.ps(p)['favorites']),2)
        self.remote.fail_write=True;self.pa(p['token'],'favorite.remove',{'id':b['id']},rid='failed-delete',expected=503)
        self.remote.fail_write=False;self.assert_one(p,'FAVORITE',b['id'])

    def test_two_instances_different_kinds_cas_retry_preserves_both(self):
        p=self.passenger_login();a=self.save(p,'FAVORITE');b=self.save(p,'RIDER');other=self.start()
        requests=[('FAVORITE',a['id'],self.driver),('RIDER',b['id'],other)]
        self.remote.barrier=threading.Barrier(2)
        def send(item):
            kind,rid,server=item
            req=urllib.request.Request(f'http://127.0.0.1:{server.server_port}/api/passenger/action',
                data=json.dumps({'action':'defaults.set','payload':{'kind':kind,'id':rid},'requestId':'race-'+kind}).encode(),
                headers={'Authorization':'Bearer '+p['token'],'Content-Type':'application/json'})
            try:
                with urllib.request.urlopen(req,timeout=20) as r:r.read();return r.status
            except urllib.error.HTTPError as e:status=e.code;e.read();e.close();return status
        try:
            with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:statuses=list(pool.map(send,requests))
        finally:self.remote.barrier=None
        self.assertEqual(sorted(statuses),[200,503])
        loser=requests[statuses.index(503)]
        self.select(p,loser[0],loser[1],rid='race-'+loser[0])
        self.assert_one(p,'FAVORITE',a['id']);self.assert_one(p,'RIDER',b['id'])

    def test_same_kind_competition_has_only_one_default(self):
        p=self.passenger_login();a,b=self.save(p,'RIDER',0),self.save(p,'RIDER',1)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(lambda row:self.select(p,'RIDER',row['id']),[a,b]))
        data=self.ps(p);selected=[r['id'] for r in data['riders'] if r['isDefault']]
        self.assertEqual(selected,[data['defaults']['riderId']]);self.assertIn(selected[0],(a['id'],b['id']))

    def test_dangling_legacy_references_project_unset_without_rewriting(self):
        p=self.passenger_login();other=self.passenger_login('13800000102');foreign=self.save(other,'RIDER')
        own=self.save(p,'RIDER')
        def corrupt(s):
            row=s.db.execute('SELECT payload FROM passenger_extras WHERE pid=?',(p['passenger']['id'],)).fetchone()
            data=json.loads(row['payload']);data['defaults']={'riderId':foreign['id'],'favoriteId':'missing','invoiceTitleId':True}
            data['riders'][0]['isDefault']=True
            s.db.execute('UPDATE passenger_extras SET payload=? WHERE pid=?',(json.dumps(data),p['passenger']['id']))
        self.mutate_fixture(corrupt);before=self.contents()
        self.assertEqual(self.ps(p)['defaults'],dict.fromkeys(('favoriteId','riderId','invoiceTitleId')))
        self.assertFalse(self.ps(p)['riders'][0]['isDefault']);self.assertEqual(before,self.contents())
        self.select(p,'RIDER',own['id']);self.assert_one(p,'RIDER',own['id'])

    def test_defaults_never_choose_rider_or_apply_invoice_implicitly(self):
        p=self.passenger_login();r=self.save(p,'RIDER',isDefault=True);t=self.save(p,'INVOICE_TITLE',isDefault=True)
        o=self.create(p['token']);self.assertIsNone(o['rider']['id']);self.assertEqual(o['phoneTail'],'0101')
        self.pa(p['token'],'order.cancel',{'id':o['id'],'reason':'本人选择兼容'})
        o=self.create(p['token'],riderId=r['id']);self.assertEqual(o['rider']['id'],r['id'])
        def complete(s):
            order=s.shared_order(o['id']);order.update(stage='DONE',paid=True);s.save_shared(order)
        self.mutate_fixture(complete)
        self.pa(p['token'],'invoice.apply',{'orderId':o['id']},expected=404)
        self.assertEqual(self.ps(p)['invoices'],[])
        self.pa(p['token'],'invoice.apply',{'orderId':o['id'],'titleId':t['id']})

    def test_default_changes_and_deletes_do_not_mutate_active_or_history_snapshots(self):
        p,d=self.setup_pair();r1=self.save(p,'RIDER',0,isDefault=True);r2=self.save(p,'RIDER',1)
        f1=self.save(p,'FAVORITE',0,isDefault=True);f2=self.save(p,'FAVORITE',1)
        t1=self.save(p,'INVOICE_TITLE',0,isDefault=True);t2=self.save(p,'INVOICE_TITLE',1)
        o=self.create(p['token'],riderId=r1['id']);self.da(d['token'],'order.accept',{'id':o['id']})
        def canonical(s):return s.db.execute('SELECT payload FROM passenger_orders WHERE id=?',(o['id'],)).fetchone()[0]
        before=self.mutate_fixture(canonical)
        for kind,row in (('RIDER',r2),('FAVORITE',f2),('INVOICE_TITLE',t2)):self.select(p,kind,row['id'])
        self.save(p,'RIDER',2,id=r1['id']);self.pa(p['token'],'rider.remove',{'id':r1['id']})
        self.pa(p['token'],'favorite.remove',{'id':f1['id']})
        self.assertEqual(self.mutate_fixture(canonical),before)
        self.finish(d['token'],o['id'],tail=r1['mobile'][-4:]);self.pa(p['token'],'order.pay',{'id':o['id']})
        invoice=self.pa(p['token'],'invoice.apply',{'orderId':o['id'],'titleId':t1['id']})['result']['invoice']
        self.save(p,'INVOICE_TITLE',2,id=t1['id']);self.pa(p['token'],'invoice.title.remove',{'id':t1['id']})
        self.assertEqual(self.ps(p)['invoices'][0],invoice)
        self.assertEqual(self.get_order(p['token'],o['id'])['rider']['name'],r1['name'])
        self.assertEqual(self.ps(p)['defaults']['invoiceTitleId'],t2['id'])
