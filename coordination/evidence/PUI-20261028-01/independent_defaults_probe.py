"""Non-author contract checks against real local HTTP/SQLite/snapshot CAS."""
import pathlib,sys,os,json,uuid,hashlib,unittest,concurrent.futures
ROOT=pathlib.Path(os.environ.get('YIJ_PMV_SOURCE','/Users/dongge/.codex/tmp/yijiexing-pm-amap-20261027'))
sys.path.insert(0,str(pathlib.Path('/Users/dongge/Documents/New project 2/coordination/evidence/PMV-20261028-01')))
from independent_operations_probe import IndependentOperations as H
class DefaultReview(unittest.TestCase):
 start=H.start;request=H.request;login=H.login;action=H.action;setUp=H.setUp;tearDown=H.tearDown;http=H.http;passenger=H.passenger;pa=H.pa;aa=H.aa;state=H.state;mutate_fixture=H.mutate_fixture;order_payload=H.order_payload
 kinds=[('FAVORITE','favoriteId','favorites','favorite.save','favorite.remove','favorite'),('RIDER','riderId','riders','rider.save','rider.remove','rider'),('INVOICE_TITLE','invoiceTitleId','invoiceTitles','invoice.title.save','invoice.title.remove','invoiceTitle')]
 def seed(self,t):
  data={}
  for kind,key,rows,save,remove,result in self.kinds:
   payload={'FAVORITE':{'label':'默认地址测试','address':'独立本地上车点','latitude':30.6687,'longitude':111.3226},'RIDER':{'name':'独立乘车人','mobile':'13800000111'},'INVOICE_TITLE':{'type':'PERSONAL','title':'独立抬头'}}[kind]
   first=self.pa(t,save,payload)['result'][result];second=self.pa(t,save,{**payload,**({'label':'第二地址'} if kind=='FAVORITE' else {'name':'第二乘车人','mobile':'13800000112'} if kind=='RIDER' else {'title':'第二抬头'})})['result'][result]
   data[kind]=(first,second,payload)
  return data
 def set(self,t,kind,id,**kwargs):return self.pa(t,'defaults.set',{'kind':kind,'id':id},**kwargs)
 def check(self,t,kind,id,server=None):
  key,rows=next((k,r) for x,k,r,*_ in self.kinds if x==kind);state=self.state(t,server=server);self.assertEqual(state['defaults'][key],id);marked=[r['id'] for r in state[rows] if r['isDefault']];self.assertEqual(marked,[] if id is None else [id]);
  if id is not None:self.assertEqual(state[rows][0]['id'],id)
  return state
 def test_defaults_persist_across_server_restart_and_accounts(self):
  a=self.passenger();t=a['token'];b=self.passenger('13800000102')['token'];items=self.seed(t)
  for kind,*_ in self.kinds:self.set(t,kind,items[kind][1]['id']);self.check(t,kind,items[kind][1]['id'])
  reboot=self.start('driver')
  for kind,*_ in self.kinds:self.check(t,kind,items[kind][1]['id'],server=reboot);self.check(b,kind,None)
 def test_bad_references_and_types_have_no_write_side_effects(self):
  t=self.passenger()['token'];other=self.passenger('13800000102')['token'];items=self.seed(t);mine=self.seed(other)
  for kind,*_ in self.kinds:
   self.set(t,kind,items[kind][0]['id']);before=self.state(t);generation=self.remote.generation
   self.set(t,kind,mine[kind][0]['id'],expected=404)
   self.assertEqual(self.state(t)['defaults'],before['defaults']);self.assertEqual(self.remote.generation,generation)
  for p in [{'kind':'FAVORITE'},{'kind':'FAVORITE','id':''},{'kind':'FAVORITE','id':True},{'kind':'X','id':None},{'kind':'RIDER','id':None,'passengerId':'other'}]:self.pa(t,'defaults.set',p,expected=400)
  for kind,key,rows,save,remove,result in self.kinds:
   before=self.state(t);self.pa(t,save,{**items[kind][2],'isDefault':1},expected=400);after=self.state(t);self.assertEqual(before[rows],after[rows]);self.assertEqual(before['defaults'],after['defaults'])
 def test_edit_preserve_clear_and_delete_fallback(self):
  t=self.passenger()['token'];items=self.seed(t)
  for kind,key,rows,save,remove,result in self.kinds:
   first,second,payload=items[kind];self.set(t,kind,second['id']);self.pa(t,save,{**payload,'id':second['id'],**({'mobile':second['mobile']} if kind=='RIDER' else {})});self.check(t,kind,second['id']);self.pa(t,save,{**payload,'id':first['id'],'isDefault':False});self.check(t,kind,second['id']);self.pa(t,save,{**payload,'id':first['id'],'isDefault':True});self.check(t,kind,first['id']);self.pa(t,remove,{'id':second['id']});self.check(t,kind,first['id']);self.pa(t,remove,{'id':first['id']});self.check(t,kind,None)
  reboot=self.start('driver')
  for kind,*_ in self.kinds:self.check(t,kind,None,server=reboot)
 def test_idempotent_old_result_cannot_revert_current_state(self):
  t=self.passenger()['token'];items=self.seed(t)
  for kind,key,*_ in self.kinds:
   one,two,_=items[kind];rid='old-default-'+kind;self.set(t,kind,one['id'],rid=rid);self.set(t,kind,two['id']);reply=self.set(t,kind,one['id'],rid=rid);self.assertEqual(reply['defaults'][key],two['id']);self.check(t,kind,two['id']);self.set(t,kind,two['id'],rid=rid,expected=409);self.set(t,kind,None);self.check(t,kind,None)
 def test_failed_cas_and_cross_instance_competition(self):
  t=self.passenger()['token'];items=self.seed(t);kind='FAVORITE';one,two,_=items[kind];self.set(t,kind,one['id']);self.remote.fail_write=True
  try:self.set(t,kind,two['id'],rid='failed-default',expected=503)
  finally:self.remote.fail_write=False
  self.check(t,kind,one['id']);self.set(t,kind,two['id'],rid='failed-default');self.check(t,kind,two['id']);second=self.start('driver')
  def choose(arg):
   server,id=arg
   return self.pa(t,'defaults.set',{'kind':kind,'id':id},rid='compete-'+id,server=server,retry_conflict=True)
  with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(choose,[(self.driver,one['id']),(second,two['id'])]))
  state=self.state(t);self.assertIn(state['defaults']['favoriteId'],[one['id'],two['id']]);self.assertEqual(sum(bool(x['isDefault']) for x in state['favorites']),1)
 def test_defaults_never_rewrite_order_or_receipt_snapshots(self):
  t=self.passenger()['token'];items=self.seed(t)
  self.set(t,'RIDER',items['RIDER'][0]['id']);self.set(t,'INVOICE_TITLE',items['INVOICE_TITLE'][0]['id'])
  created=self.pa(t,'order.create',self.order_payload())['result']['order'];self.assertNotEqual(created.get('rider',{}).get('id'),items['RIDER'][0]['id'])
  def complete(svc):
   o=svc.shared_order(created['id']);o.update(stage='DONE',paid=True,paymentStatus='SUCCESS');svc.save_shared(o,'PUI_SYNTHETIC_COMPLETION')
  self.mutate_fixture(complete)
  explicit=self.pa(t,'order.create',{**self.order_payload(),'riderId':items['RIDER'][0]['id']})['result']['order']
  frozen=next(o for o in self.state(t)['orders'] if o['id']==explicit['id'])
  invoice=self.pa(t,'invoice.apply',{'orderId':created['id'],'titleId':items['INVOICE_TITLE'][0]['id']})['result']['invoice']
  self.pa(t,'rider.save',{**items['RIDER'][2],'id':items['RIDER'][0]['id'],'name':'后续改名'})
  self.pa(t,'invoice.title.save',{'id':items['INVOICE_TITLE'][0]['id'],'type':'PERSONAL','title':'后续改抬头'})
  self.set(t,'RIDER',items['RIDER'][1]['id']);self.set(t,'INVOICE_TITLE',items['INVOICE_TITLE'][1]['id'])
  self.pa(t,'rider.remove',{'id':items['RIDER'][0]['id']});self.pa(t,'invoice.title.remove',{'id':items['INVOICE_TITLE'][0]['id']})
  state=self.state(t);current=next(o for o in state['orders'] if o['id']==explicit['id']);self.assertEqual(current['rider'],frozen['rider']);self.assertEqual(current['fare'],frozen['fare']);self.assertEqual(current['originLat'],frozen['originLat']);historic=next(i for i in state['invoices'] if i['id']==invoice['id']);self.assertEqual(historic['titleSnapshot'],invoice['titleSnapshot']);self.assertEqual(historic['amount'],invoice['amount'])

if __name__=='__main__':
 import subprocess
 source=subprocess.check_output(['git','-C',str(ROOT),'rev-parse','HEAD'],text=True).strip();files=sorted((ROOT/'apps/cloud-service').glob('*.py'));hashes={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files};suite=unittest.defaultTestLoader.loadTestsFromTestCase(DefaultReview);r=unittest.TextTestRunner(verbosity=2).run(suite);out={'testedSha':source,'sourceHashes':hashes,'sourceUnchanged':hashes=={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in files},'testsRun':r.testsRun,'errors':[t.id() for t,_ in r.errors],'failures':[t.id() for t,_ in r.failures],'scope':'Local HTTP/SQLite/MemoryCAS only; no real keys/cloud data','nativeUi':'NOT_RUN in this probe'};path=pathlib.Path(__file__).with_name('DEFAULTS_HTTP_RESULT.json');path.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n');raise SystemExit(0 if r.wasSuccessful() and out['sourceUnchanged'] else 1)
