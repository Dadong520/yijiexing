"""BIZ-08 qualification replacement through actual authenticated HTTP/CAS."""
import datetime as dt
import json
from unittest.mock import patch
from common import now, SAMPLE_IMAGE
from policy_helpers import PolicyCase


class QualificationReplacementTests(PolicyCase):
    def change(self, d, **fields):
        def fn(svc):
            driver=d['profile']['driver']['id'];profile=json.loads(svc.account(driver)['profile'])
            profile['certificates'][0].update(fields)
            profile['driver']['certificationStatus']='APPROVED' if all(c['status']=='APPROVED' for c in profile['certificates']) else 'PENDING'
            svc.save_profile(driver,profile)
        self.mutate_fixture(fn)

    def cert(self,d):
        return self.request('/api/state',token=d['token'])['profile']['certificates'][0]

    def submit(self,d,version=0,expected=200):
        return self.da(d['token'],'certificate.submit',{'type':'insurance','expectedVersion':version,
            'validFrom':now().date().isoformat(),'validUntil':(now().date()+dt.timedelta(days=365)).isoformat(),
            'imageBase64':SAMPLE_IMAGE},expected=expected)

    def audit(self,d,c,approved,expected=200,**extra):
        if not hasattr(self,'admin'):self.admin=self.start('admin')
        p={'driverId':d['profile']['driver']['id'],'type':'insurance','approved':approved,'reason':'本地审核原因'}
        if c.get('pending'):p.update(expectedVersion=c['version'],submissionId=c['pending']['submissionId'])
        p.update(extra)
        return self.request('/api/local/admin/action',{'action':'certificate.audit','payload':p},
            server=self.admin,admin=True,expected=expected)

    def test_61_60_59_zero_minus_one_long_term_unknown_server_flags(self):
        p,d=self.setup_pair();today=now().date()
        for days in (61,60,59,0,-1):
            self.change(d,validFrom=(today-dt.timedelta(days=365)).isoformat(),validUntil=(today+dt.timedelta(days=days)).isoformat())
            c=self.cert(d);self.assertEqual(c['daysRemaining'],days)
            self.assertEqual(c['canUpdate'],days<=60);self.assertEqual(c['reminder'],days<=60)
        self.change(d,validUntil='LONG_TERM');c=self.cert(d)
        self.assertEqual(c['validity'],'LONG_TERM');self.assertFalse(c['canUpdate']);self.assertFalse(c['reminder'])
        self.change(d,validUntil=None);c=self.cert(d)
        self.assertEqual(c['validity'],'UNKNOWN');self.assertIsNone(c['daysRemaining']);self.assertTrue(c['canUpdate'])
        self.change(d,validFrom=None,validUntil=(today+dt.timedelta(days=100)).isoformat())
        self.assertEqual(self.cert(d)['validity'],'UNKNOWN');self.assertTrue(self.cert(d)['canUpdate'])

    def test_utc8_expiry_day_boundary_is_inclusive(self):
        p,d=self.setup_pair();self.change(d,validFrom='2026-01-01',validUntil='2026-11-01')
        for text,days,validity in [('2026-11-01T23:59:59+08:00',0,'EXPIRING'),('2026-11-02T00:00:00+08:00',-1,'EXPIRED')]:
            with patch('qualifications.now',return_value=dt.datetime.fromisoformat(text)):
                c=self.cert(d);self.assertEqual(c['daysRemaining'],days);self.assertEqual(c['validity'],validity)

    def test_60_day_update_window_and_61_day_rejection(self):
        p,d=self.setup_pair();today=now().date()
        self.change(d,validUntil=(today+dt.timedelta(days=61)).isoformat())
        self.submit(d,expected=409)
        self.change(d,validUntil=(today+dt.timedelta(days=60)).isoformat())
        c=self.submit(d)['result']['certificate'];self.assertIsNotNone(c['pending']);self.assertFalse(c['canUpdate'])
        self.assertEqual(c['daysRemaining'],60);self.assertEqual(c['status'],'APPROVED')
        self.submit(d,version=1,expected=409)

    def test_valid_active_survives_reject_then_approval_atomically_replaces(self):
        p,d=self.setup_pair();old=self.cert(d)
        c=self.submit(d)['result']['certificate']
        self.assertEqual(c['validUntil'],old['validUntil']);self.assertEqual(c['status'],'APPROVED')
        self.assertIsNone(c.get('fileId'));self.assertIsNotNone(c['pending']['fileId'])
        o=self.create(p['token']);self.da(d['token'],'order.accept',{'id':o['id']})
        self.pa(p['token'],'order.cancel',{'id':o['id'],'reason':'待审期间仍可接单'})
        self.audit(d,c,False);rejected=self.cert(d)
        self.assertEqual(rejected['status'],'APPROVED');self.assertEqual(rejected['reviewStatus'],'REJECTED')
        self.assertEqual(rejected['validUntil'],old['validUntil']);self.assertIsNone(rejected['pending'])
        self.submit(d,version=0,expected=409)
        c=self.submit(d,version=rejected['version'])['result']['certificate']
        self.audit(d,c,True,expected=409,submissionId='stale-submission')
        self.audit(d,c,True,expected=409,expectedVersion=c['version']-1)
        candidate=c['pending'];self.audit(d,c,True);approved=self.cert(d)
        self.assertEqual(approved['validUntil'],candidate['validUntil']);self.assertEqual(approved['fileId'],candidate['fileId'])
        self.assertEqual(approved['version'],c['version']+1);self.assertIsNone(approved['pending'])
        self.assertEqual(len(approved['auditRecords']),2)
        self.audit(d,approved,True,expected=409)
        restored=self.request('/api/state',token=d['token'],server=self.start())['profile']['certificates'][0]
        self.assertEqual(restored,approved)

    def test_expiry_during_review_blocks_new_but_inflight_and_late_payment_finish(self):
        p,d=self.setup_pair();o=self.create(p['token']);self.da(d['token'],'order.accept',{'id':o['id']})
        c=self.submit(d)['result']['certificate']
        self.change(d,validUntil=(now().date()-dt.timedelta(days=1)).isoformat())
        self.finish(d['token'],o['id']);self.assertEqual(self.get_order(p['token'],o['id'])['stage'],'WAIT_PAY')
        self.da(d['token'],'driver.online',{'online':False})
        self.da(d['token'],'driver.online',{'online':True},expected=403)
        self.pa(p['token'],'order.pay',{'id':o['id']})
        self.audit(d,self.cert(d),True);self.da(d['token'],'driver.online',{'online':True})
        self.assertEqual(sum(e['title']=='行程收入 '+o['id'] for e in self.state(d['token'])['entries']),1)

    def test_expired_reserved_departure_rechecks_certificate(self):
        p,d=self.setup_pair();o=self.create(p['token'],'RESERVED');self.da(d['token'],'order.accept',{'id':o['id']})
        self.change(d,validUntil=(now().date()-dt.timedelta(days=1)).isoformat())
        self.da(d['token'],'order.advance',{'id':o['id']},expected=403)
        self.assertEqual(self.get_order(p['token'],o['id'])['stage'],'RESERVED')

    def test_legacy_pending_reject_can_resubmit_even_long_or_far_expiry(self):
        p,d=self.setup_pair();self.change(d,status='PENDING',validUntil='LONG_TERM')
        self.audit(d,self.cert(d),False)
        for expiry in ('LONG_TERM',(now().date()+dt.timedelta(days=400)).isoformat()):
            self.change(d,validUntil=expiry)
            c=self.cert(d);self.assertEqual(c['status'],'REJECTED');self.assertTrue(c['canUpdate'])
        self.da(d['token'],'driver.online',{'online':True},expected=403)
        c=self.submit(d,version=self.cert(d)['version'])['result']['certificate']
        self.assertEqual(c['status'],'REJECTED')
        self.audit(d,c,True);self.assertEqual(self.cert(d)['status'],'APPROVED')
        self.da(d['token'],'driver.online',{'online':True})

    def test_legacy_pending_approval_preserves_audit_compatibility(self):
        p,d=self.setup_pair();self.change(d,status='PENDING',validUntil=(now().date()+dt.timedelta(days=365)).isoformat())
        self.audit(d,self.cert(d),True)
        self.assertEqual(self.cert(d)['status'],'APPROVED')

    def test_failed_storage_does_not_publish_candidate_and_lost_ack_is_once(self):
        p,d=self.setup_pair();self.remote.fail_write=True;self.submit(d,expected=503)
        self.remote.fail_write=False;self.assertIsNone(self.cert(d)['pending'])
        body={'type':'insurance','expectedVersion':0,'validFrom':now().date().isoformat(),
            'validUntil':(now().date()+dt.timedelta(days=365)).isoformat(),'imageBase64':SAMPLE_IMAGE}
        self.remote.uncertain_write=True;self.da(d['token'],'certificate.submit',body,rid='lost-cert',expected=503)
        reply=self.da(d['token'],'certificate.submit',body,rid='lost-cert')
        self.assertEqual(reply['result']['certificate']['version'],1)
        self.assertEqual(self.cert(d)['version'],1)

    def test_approval_note_persists_without_becoming_rejection_message(self):
        p,d=self.setup_pair();c=self.submit(d)['result']['certificate']
        for reason in (None,123,True,{},[],'字'*501):
            self.audit(d,c,True,reason=reason,expected=400)
        for reason in ('','字'):
            self.audit(d,c,False,reason=reason,expected=400)
        self.audit(d,c,True,reason='  已核对原件，准予通过  ')
        approved=self.cert(d)
        self.assertEqual(approved['auditRecords'][-1]['reason'],'已核对原件，准予通过')
        self.assertEqual(approved['rejectionReason'],'');self.assertEqual(approved['reply'],'')
        event=self.mutate_fixture(lambda svc:json.loads(svc.db.execute(
            "SELECT after_value FROM audits WHERE action='certificate.audit' ORDER BY id DESC LIMIT 1").fetchone()[0]))
        self.assertIs(event['approved'],True);self.assertEqual(event['reason'],'已核对原件，准予通过')
        restored=self.request('/api/state',token=d['token'],server=self.start())['profile']['certificates'][0]
        self.assertEqual(restored['auditRecords'][-1]['reason'],'已核对原件，准予通过')

    def test_approval_empty_or_omitted_note_remains_compatible(self):
        p,d=self.setup_pair();self.admin=self.start('admin')
        for omit in (True,False):
            self.change(d,validUntil=(now().date()+dt.timedelta(days=60)).isoformat())
            c=self.submit(d,version=self.cert(d)['version'])['result']['certificate']
            payload={'driverId':d['profile']['driver']['id'],'type':'insurance','approved':True,
                'expectedVersion':c['version'],'submissionId':c['pending']['submissionId']}
            if not omit:payload['reason']=''
            self.request('/api/local/admin/action',{'action':'certificate.audit','payload':payload},
                server=self.admin,admin=True)
            approved=self.cert(d)
            self.assertEqual(approved['auditRecords'][-1]['reason'],'')
            self.assertEqual(approved['rejectionReason'],'');self.assertEqual(approved['reply'],'')
