"""Shared driver views never expose passenger-owned complaint records."""
import json
from policy_helpers import PolicyCase
from admin_console import AdminConsole
from common import SAMPLE_IMAGE

class DriverProjectionPrivacyTests(PolicyCase):
    def test_passenger_complaint_reply_and_attachments_private_driver_ticket_retained(self):
        p,d,o=self.shared_wait_pay();oid=o['id'];pt,dt=p['token'],d['token']
        complaint=self.pa(pt,'order.complaint',{'id':oid,'reason':'乘客隐私问题','content':'passenger-private-body-secret'})['result']['order']['complaints'][0]
        self.pa(pt,'order.complaint.attach',{'id':oid,'complaintId':complaint['id'],'imagesBase64':[SAMPLE_IMAGE]})
        own=self.da(dt,'order.complaint',{'id':oid,'reason':'司机自己的投诉','content':'driver-own-body-readable'},rid='driver-ticket')['result']['complaint']
        self.mutate_fixture(lambda svc:AdminConsole(svc).reply({'source':'ORDER_COMPLAINT','ownerId':oid,'id':complaint['id'],'reply':'passenger-private-reply-secret'},'ADMIN:fixture'))
        self.mutate_fixture(lambda svc:AdminConsole(svc).reply({'source':'DRIVER_COMPLAINT','ownerId':d['profile']['driver']['id'],'id':own['id'],'reply':'driver-own-reply-readable'},'ADMIN:fixture'))
        for reply in (self.request('/api/state',token=dt),self.da(dt,'order.complaint',{'id':oid,'reason':'司机自己的投诉','content':'driver-own-body-readable'},rid='driver-ticket')):
            raw=json.dumps(reply);self.assertNotIn('passenger-private',raw);self.assertNotIn(complaint['id'],raw)
            projected=next(x for x in reply['state']['orders'] if x['id']==oid);self.assertNotIn('complaints',projected)
            self.assertEqual(reply['profile']['complaints'][0]['reply'],'driver-own-reply-readable')
        canonical=self.get_order(pt,oid);self.assertEqual(canonical['complaints'][0]['reply'],'passenger-private-reply-secret');self.assertEqual(len(canonical['complaints'][0]['attachments']),1)

    def test_pre_fix_cached_shared_response_scrubbed_without_destroying_driver_complaint(self):
        p,d,o=self.shared_wait_pay();oid=o['id'];payload={'id':oid,'reason':'司机投诉测试','content':'driver retained content'}
        response=self.da(d['token'],'order.complaint',payload,rid='old-response');cid=response['result']['complaint']['id']
        def old_cache(svc):
            row=svc.db.execute('SELECT result FROM actions WHERE driver=? AND request_id=?',(d['profile']['driver']['id'],'old-response')).fetchone();value=json.loads(row['result'])
            value['order']['complaints']=[{'id':'PC-OLD-SECRET','content':'old-secret-body','reply':'old-secret-reply','attachments':[{'id':'private-file-secret'}]}]
            value['order']['passengerId']='private-owner';svc.db.execute('UPDATE actions SET result=? WHERE driver=? AND request_id=?',(json.dumps(value),d['profile']['driver']['id'],'old-response'))
        self.mutate_fixture(old_cache)
        result=self.da(d['token'],'order.complaint',payload,rid='old-response');self.assertNotIn('old-secret',json.dumps(result));self.assertNotIn('private-file-secret',json.dumps(result));self.assertNotIn('complaints',result['result']['order'])
        self.assertEqual(result['result']['complaint']['id'],cid);self.assertEqual(result['profile']['complaints'][0]['content'],'driver retained content')
