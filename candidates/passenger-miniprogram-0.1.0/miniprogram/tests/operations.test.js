'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const ops=require('../lib/operations');
function state(id='A'){return {authenticated:true,busy:false,data:{passenger:{id},operations:{announcements:[{id:'ANN-1',version:3,title:'有效公告',content:'纯文本内容'}]}}};}
test('legacy feature defaults, published closure, and unknown kinds remain distinct',()=>{
  assert.equal(ops.kindEnabled({},'STANDARD'),true);
  const s={operations:{passengerFeatures:{features:{instant:false,reservation:true,bidding:false,invoice:false}}}};
  assert.equal(ops.kindEnabled(s,'STANDARD'),false);assert.equal(ops.kindEnabled(s,'RESERVED'),true);assert.equal(ops.kindEnabled(s,'BID'),false);assert.equal(ops.kindEnabled(s,'OTHER'),false);assert.equal(ops.features(s).invoice,false);
});
test('silent or hidden state reads do not claim an announcement',async()=>{
  let calls=0;const present=ops.createAnnouncementPresenter();
  await present({store:{snapshot:()=>state(),action:async()=>{calls++;}},wx:{showModal(){}},isVisible:()=>false});assert.equal(calls,0);
});
test('claim firstDisplay only, one in-flight across repeated subscriptions',async()=>{
  let sent=0,shown=0,release;const gate=new Promise(r=>release=r),snapshot=state();
  const store={snapshot:()=>snapshot,action:async(action,payload)=>{sent++;assert.equal(action,'ops.announcement.ack');assert.deepEqual(payload,{id:'ANN-1',version:3,client:'MINIPROGRAM'});await gate;return {result:{firstDisplay:true}};}};
  const wx={showModal(o){shown++;assert.equal(o.content,'纯文本内容');o.success({confirm:true});}};
  const present=ops.createAnnouncementPresenter();const first=present({store,wx,isVisible:()=>true});await present({store,wx,isVisible:()=>true});assert.equal(sent,1);release();await first;assert.equal(shown,1);
  store.action=async()=>({result:{firstDisplay:false}});await present({store,wx,isVisible:()=>true});assert.equal(shown,1);
});
test('a response from the previous account cannot open a modal for the new account',async()=>{
  let current=state('A'),shown=0;const store={snapshot:()=>current,action:async()=>{current=state('B');return {result:{firstDisplay:true}};}};
  await ops.createAnnouncementPresenter()({store,wx:{showModal(){shown++;}},isVisible:()=>true});assert.equal(shown,0);
});
test('unconfigured or disabled company never creates a dialable number',async()=>{
  assert.equal(ops.companyView({name:'车队文本'}).supportPhone,'');
  assert.equal(ops.companyView({status:'CONFIGURED',name:'公司A',supportPhone:null}).statusText,'客服电话未配置');
  assert.equal(ops.companyView({status:'DISABLED',supportPhone:'4000000000'}).supportPhone,'');
  let calls=0;await assert.rejects(()=>ops.dialCompany({makePhoneCall(){calls++;}},{status:'UNCONFIGURED'}),/未配置/);assert.equal(calls,0);
});
test('company phone opens only after user confirmation',async()=>{
  const c={status:'CONFIGURED',name:'公司A',supportPhone:'400-123-4567'};let calls=0;
  await ops.dialCompany({showModal:o=>o.success({confirm:false}),makePhoneCall(){calls++;}},c);assert.equal(calls,0);
  await ops.dialCompany({showModal:o=>o.success({confirm:true}),makePhoneCall:o=>{calls++;assert.equal(o.phoneNumber,c.supportPhone);o.success();}},c);assert.equal(calls,1);
});
test('transport declares the exact mini client for login, state, and action',async()=>{
  const calls=[];const api=require('../lib/api').createApi({request:o=>{calls.push(o);o.success({statusCode:200,data:{code:0,data:{}}});}});
  const credentials={baseUrl:require('../lib/api').ORIGIN,access:'synthetic-fixture-only',token:'synthetic-session-only'};
  await api.request('state',{},credentials);await api.request('login',{mobile:'13800000001',code:'000000'},credentials);await api.request('action',{action:'ops.announcement.ack',payload:{id:'A',version:1,client:'MINIPROGRAM'},requestId:'fixture-id'},credentials);
  assert.match(calls[0].url,/\/api\/passenger\/state\?client=MINIPROGRAM$/);
  assert.equal(calls[1].data.client,'MINIPROGRAM');assert.equal(calls[2].data.client,'MINIPROGRAM');
  assert.equal(calls[2].data.payload.version,1);
});
