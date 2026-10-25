import base64
import datetime as dt
import json
import pathlib
import sys
import tempfile
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from common import ApiError, SAMPLE_IMAGE, dumps, now, stamp
from order_options import PassengerOrderOptionsMixin
from test_passenger_extras import ExtrasService


class OptionsService(PassengerOrderOptionsMixin, ExtrasService):
    def __init__(self, path):
        super().__init__(path)
        self.db.execute('CREATE TABLE IF NOT EXISTS passenger_orders(id TEXT PRIMARY KEY,payload TEXT)')
        for o in self.orders.values():
            o.update(kind='STANDARD', driverId='D1', complaints=[{'id':'C1','content':'原投诉'}], events=[])
    def save_shared(self, o, event=None):
        self.orders[o['id']] = o
        if event:o['events'].append({'type':event})
        self.db.execute('INSERT OR REPLACE INTO passenger_orders VALUES (?,?)',(o['id'],dumps(o)))
    def passenger_view(self, o):return o.copy()
    def driver_public(self, driver):return {'id':driver,'name':'体验司机','plate':'鄂E·测试'}


class PassengerOptionsTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.s=OptionsService(str(pathlib.Path(self.tmp.name)/'test.sqlite'))
    def tearDown(self):self.s.db.close();self.tmp.cleanup()
    def act(self, action, p, pid='P1'):return self.s.passenger_order_option(pid,action,p)
    def assert_error(self, status, action, p, pid='P1'):
        with self.assertRaises(ApiError) as ctx:self.act(action,p,pid)
        self.assertEqual(status,ctx.exception.status)

    def test_block_requires_owned_assigned_order_and_unblock_isolated(self):
        self.assert_error(404,'order.blockDriver',{'id':'O2'})
        self.s.orders['O3']['stage']='PICKUP'
        self.assert_error(409,'order.blockDriver',{'id':'O3'})
        row=self.act('order.blockDriver',{'id':'O1'})['blockedDriver']
        self.assertFalse(self.s.passenger_driver_allowed('P1','D1'))
        self.assertTrue(self.s.passenger_driver_allowed('P2','D1'))
        self.act('order.blockDriver',{'id':'O1'})
        self.assertEqual(1,len(self.s.passenger_extra_data('P1')['blockedDrivers']))
        self.assert_error(404,'order.unblockDriver',{'driverId':'D1'},'P2')
        self.act('order.unblockDriver',{'driverId':'D1'})
        self.assertTrue(self.s.passenger_driver_allowed('P1','D1'))

    def test_timeout_only_available_bids_after_server_deadline(self):
        for oid,kind,stage,deadline in [('T1','BID','AVAILABLE',-1),('T2','BID','AVAILABLE',100),('T3','BID','PREPAY',-1),('T4','STANDARD','AVAILABLE',-1)]:
            self.s.save_shared({'id':oid,'kind':kind,'stage':stage,'expiresAt':stamp(now()+dt.timedelta(seconds=deadline)),'paid':False,'events':[]})
        self.assertEqual(1,self.s.expire_passenger_bids())
        self.assertEqual('CANCELLED',self.s.orders['T1']['stage'])
        self.assertEqual('AVAILABLE',self.s.orders['T2']['stage'])
        self.assertEqual('PREPAY',self.s.orders['T3']['stage'])
        self.assertEqual('AVAILABLE',self.s.orders['T4']['stage'])
        self.assertEqual(0,self.s.expire_passenger_bids())

    def test_attachment_ownership_media_limits_and_no_partial_write(self):
        self.assert_error(404,'order.complaint.attach',{'id':'O2','complaintId':'C1','imagesBase64':[SAMPLE_IMAGE]})
        self.assert_error(400,'order.complaint.attach',{'id':'O1','complaintId':'C1','imagesBase64':[SAMPLE_IMAGE,'https://example.com/private.jpg']})
        self.s._complaint_file_table()
        self.assertEqual(0,self.s.db.execute('SELECT COUNT(*) FROM passenger_complaint_files').fetchone()[0])
        result=self.act('order.complaint.attach',{'id':'O1','complaintId':'C1','imagesBase64':[SAMPLE_IMAGE]})
        fid=result['attachments'][0]['id']
        mime,raw=self.s.passenger_complaint_file('P1',fid)
        self.assertEqual('image/png',mime);self.assertEqual(base64.b64decode(SAMPLE_IMAGE),raw)
        with self.assertRaises(ApiError):self.s.passenger_complaint_file('P2',fid)
        self.assert_error(400,'order.complaint.attach',{'id':'O1','complaintId':'C1','imagesBase64':[SAMPLE_IMAGE]*3})
        self.assertEqual(1,self.s.db.execute('SELECT COUNT(*) FROM passenger_complaint_files').fetchone()[0])

    def test_remark_rejects_completed_and_ownership(self):
        self.assert_error(409,'order.remark',{'id':'O1','remark':'修改备注'})
        self.s.orders['O3']['stage']='WAIT_CONTINUE'
        self.act('order.remark',{'id':'O3','remark':'行李较多'})
        self.assertEqual('行李较多',self.s.orders['O3']['remark'])
        self.assert_error(404,'order.remark',{'id':'O3','remark':'别人的备注'},'P2')

if __name__=='__main__':unittest.main()
