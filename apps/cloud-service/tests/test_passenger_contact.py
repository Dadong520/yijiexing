"""Authenticated HTTP and MemoryCAS tests; phone values are synthetic fixture inputs."""
import contextlib
import io
import json
import urllib.error
import urllib.parse
import urllib.request
from policy_helpers import PolicyCase


class PassengerContact(PolicyCase):
    def contact(self, token, oid, expected=200, server=None):
        req=urllib.request.Request(f'http://127.0.0.1:{(server or self.driver).server_port}/api/driver/passenger-contact?orderId='+urllib.parse.quote(oid),
            headers={'Authorization':'Bearer '+token})
        try:response=urllib.request.urlopen(req,timeout=10)
        except urllib.error.HTTPError as exc:response=exc
        with response:
            self.assertEqual(response.status,expected)
            self.assertEqual(response.headers.get('Cache-Control'),'no-store')
            body=json.loads(response.read())
        return body.get('data',body)

    def rows(self):
        return self.mutate_fixture(lambda svc:[tuple(row) for row in svc.db.execute('SELECT * FROM passenger_orders ORDER BY id')])

    def test_http_ownership_roles_stages_and_read_only(self):
        p,d=self.setup_pair();other=self.login('13800000002');oid=self.create(p['token'])['id']
        self.contact(d['token'],oid,404)  # Unassigned hall listing is not permission.
        self.da(d['token'],'order.accept',{'id':oid})
        before=self.rows()
        result=self.contact(d['token'],oid)
        self.assertEqual(result,dict(orderId=oid,mobile='13800000101',name=p['passenger']['name'],contactMode='DIRECT_DIAL',relayAvailable=False))
        self.assertEqual(before,self.rows())
        self.contact(other['token'],oid,404)
        self.contact(p['token'],oid,401)
        self.contact('expired-token',oid,401)
        for stage in ('RESERVED','PICKUP','ARRIVED','TRIP','PREPAY','WAIT_PAY','DONE','CANCELLED','WAIT_CONTINUE'):
            self.mutate_fixture(lambda svc:self._stage(svc,oid,stage))
            self.contact(d['token'],oid,200 if stage in ('RESERVED','PICKUP','ARRIVED','TRIP') else 409)
        def reassign(svc):
            order=svc.shared_order(oid);order.update(stage='PICKUP',driverId=other['profile']['driver']['id']);svc.save_shared(order)
        self.mutate_fixture(reassign)
        self.contact(d['token'],oid,404)
        self.contact(other['token'],oid)
        admin=self.start(role='admin')
        self.contact(d['token'],oid,404,server=admin)

    @staticmethod
    def _stage(svc,oid,stage):
        o=svc.shared_order(oid);o['stage']=stage;svc.save_shared(o)

    def test_rider_edit_delete_restart_and_no_snapshot_leak(self):
        p,d=self.setup_pair();token=p['token']
        rider=self.pa(token,'rider.save',dict(name='代叫乘车人',mobile='13900000987'))['result']['rider']
        o=self.create(token,riderId=rider['id']);oid=o['id']
        self.da(d['token'],'order.accept',{'id':oid})
        self.pa(token,'rider.save',dict(id=rider['id'],name='后改乘车人',mobile='13900000654'))
        self.pa(token,'rider.remove',dict(id=rider['id']))
        current=self.contact(d['token'],oid)
        self.assertEqual((current['mobile'],current['name']),('13900000987','代叫乘车人'))
        reboot=self.start();self.assertEqual(self.contact(d['token'],oid,server=reboot),current)
        normal=json.dumps(self.request('/api/state',token=d['token']))
        self.assertNotIn('13900000987',normal)
        self.assertNotIn('13900000654',normal)
        before=self.rows()
        for row in before:self.assertNotIn('13900000987',row[3])
        self.contact(d['token'],oid)
        self.assertEqual(before,self.rows())

    def test_legacy_no_snapshot_unavailable_without_payload_write_or_wrong_booker(self):
        p,d=self.setup_pair();oid=self.create(p['token'])['id'];self.da(d['token'],'order.accept',{'id':oid})
        self.mutate_fixture(lambda svc:svc.db.execute('DELETE FROM passenger_order_contacts WHERE order_id=?',(oid,)))
        before=self.rows();response=self.contact(d['token'],oid,409)
        self.assertEqual(response['details']['reason'],'PASSENGER_CONTACT_UNAVAILABLE')
        self.assertEqual(before,self.rows())

    def test_idempotent_create_has_one_private_snapshot_and_hall_does_not_leak(self):
        p,d=self.setup_pair()
        payload=dict(kind='STANDARD',origin='测试上车点',destination='测试终点',originLat=30.,originLng=111.,destinationLat=30.1,destinationLng=111.1)
        first=self.pa(p['token'],'order.create',payload,rid='contact-once')
        second=self.pa(p['token'],'order.create',payload,rid='contact-once')
        self.assertEqual(first['result']['order']['id'],second['result']['order']['id'])
        count=self.mutate_fixture(lambda svc:svc.db.execute('SELECT count(*) FROM passenger_order_contacts').fetchone()[0])
        self.assertEqual(count,1)
        hall=json.dumps(self.request('/api/state',token=d['token']))
        self.assertNotIn('13800000101',hall)
        self.contact(d['token'],first['result']['order']['id'],404)
        self.request('/api/driver/passenger-contact?orderId=x&orderId=y',token=d['token'],expected=400)
        self.request('/api/driver/passenger-contact?orderId=x&mobile=ignored',token=d['token'],expected=400)

    def test_blank_duplicate_or_unknown_query_key_is_not_silently_dropped(self):
        p,d=self.setup_pair();oid=self.create(p['token'])['id']
        self.da(d['token'],'order.accept',dict(id=oid))
        self.contact(d['token'],oid)  # This valid assigned ID would otherwise return200.
        for query in (f'orderId={oid}&orderId=',f'orderId={oid}&unused=','orderId='):
            with self.subTest(query=query):
                self.request('/api/driver/passenger-contact?'+query,token=d['token'],expected=400)

    def test_snapshot_failure_rolls_back_order_and_retry_is_atomic(self):
        p,d=self.setup_pair()
        self.mutate_fixture(lambda svc:svc.db.execute("CREATE TRIGGER reject_contact BEFORE INSERT ON passenger_order_contacts BEGIN SELECT RAISE(ABORT,'test contact unavailable'); END"))
        payload=dict(kind='STANDARD',origin='测试上车点',destination='测试终点',originLat=30.,originLng=111.,destinationLat=30.1,destinationLng=111.1)
        self.pa(p['token'],'order.create',payload,rid='atomic-contact',expected=500)
        self.assertEqual(self.rows(),[])
        self.mutate_fixture(lambda svc:svc.db.execute('DROP TRIGGER reject_contact'))
        self.pa(p['token'],'order.create',payload,rid='atomic-contact')
        self.assertEqual(len(self.rows()),1)

    def test_new_negotiation_claim_snapshots_booker_but_legacy_replay_does_not_backfill(self):
        import test_negotiation as fixture
        passenger=self.passenger_login()
        driver,oid=fixture.NegotiationIntegration.offer(self)
        offer=self.da(driver['token'],'order.pairOffer',dict(id=oid))['result']
        order=self.pa(passenger['token'],'negotiation.claim',dict(pairCode=offer['pairCode']))['result']['order']
        self.contact(driver['token'],oid,409)  # PREPAY still blocked.
        self.pa(passenger['token'],'order.pay',dict(id=oid,quoteVersion=1,amount=order['fare'],outcome='SUCCESS'))
        self.assertEqual(self.contact(driver['token'],oid)['mobile'],'13800000101')
        self.mutate_fixture(lambda svc:svc.db.execute('DELETE FROM passenger_order_contacts WHERE order_id=?',(oid,)))
        replay=self.pa(passenger['token'],'negotiation.claim',dict(pairCode=offer['pairCode']))
        self.assertTrue(replay['result']['idempotent'])
        self.contact(driver['token'],oid,409)

    def test_explicit_read_does_not_log_number_or_audit_it(self):
        p,d=self.setup_pair();oid=self.create(p['token'])['id'];self.da(d['token'],'order.accept',{'id':oid})
        audit_before=self.mutate_fixture(lambda svc:[tuple(x) for x in svc.db.execute('SELECT * FROM audits')])
        captured=io.StringIO()
        with contextlib.redirect_stderr(captured):self.contact(d['token'],oid)
        self.assertNotIn('13800000101',captured.getvalue())
        audit_after=self.mutate_fixture(lambda svc:[tuple(x) for x in svc.db.execute('SELECT * FROM audits')])
        self.assertEqual(audit_before,audit_after)
